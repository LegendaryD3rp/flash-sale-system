#!/usr/bin/env python3
"""
万人同场·绝对E2E仿真 — 吕芳 呈皇上

综合压力测试脚本，支持秒杀/购物/混合三种模式，
集成 JWT验证、缓存穿透、限流、MQ异步、AI聊天、WebSocket推送 等验证模块。
输出 JSON 报告 + Plotly HTML 可视化报告。

用法:
  python3 wanren_tongchang.py --mode mixed --orders 10000 --verify basic --cleanup light
"""

import argparse
import asyncio
import json
import os
import random
import sys
import time

import traceback
from datetime import datetime, timedelta

import aiohttp
import requests as sync_requests

BASE = "http://localhost:8080"

# ==================== 参数 ====================
parser = argparse.ArgumentParser(description="万人同场·绝对E2E仿真")
parser.add_argument("--mode", choices=["seckill", "shopping", "mixed"], default="seckill",
                    help="秒杀/购物/混合 三种模式")
parser.add_argument("--orders", type=int, default=10000,
                    help="总订单数 (默认10000)")
parser.add_argument("--verify", choices=["none", "basic", "full"], default="basic",
                    help="验证等级: none=纯跑 basic=限流+JWT+非法ID+MQ异步 full=basic+WebSocket推送验证")
parser.add_argument("--cleanup", choices=["none", "light", "full"], default="light",
                    help="清理级别: none=不清理 light=活动+商品 full=活动+商品+用户")
parser.add_argument("--verify-intensity", choices=["light","medium","heavy"], default="medium",
                    help="限流验证强度: light=500 medium=1500 heavy=3000 (默认medium)")
parser.add_argument("--reuse-users", action="store_true",
                    help="复用上次注册的用户（跳过注册，直接登录），需 reuse_users.json")
args = parser.parse_args()


# ==================== 状态 ====================
class State:
    def __init__(self):
        self.admin_token = ""
        self.admin_uid = 0
        self.products = []
        self.seckill_activities = []
        self.act_product = {}
        self.normal_products = []
        self.users = []
        self.results = {
            "total_orders": 0,
            "paid_orders": 0,
            "shipped_orders": 0,
            "received_orders": 0,
            "failed_orders": 0,
        }
        self.t_start = 0.0
        self.seckill_lats = []
        self.shopping_lats = []
        self.seckill_errs = {}
        self.shop_errs = {}
        self.verifications = []
        self.has_psutil = False
        self.time_series = []
        self.cpu_samples = []
        self.mem_samples = []
        self.stock_before = {}   # aid -> {"name":..., "stock":..., "bought":...}
        self.stock_after = {}
        self._pending_warmup_activities = []
        self.reuse_file = "reuse_users.json"


state = State()
_shop_errs_lock = asyncio.Lock()


# ==================== 同步工具（仅 setup/cleanup） ====================
def api(method, path, **kwargs):
    """同步 HTTP 请求，用于 setup/cleanup 等非压测场景"""
    url = f"{BASE}{path}"
    hdrs = kwargs.pop("headers", {})
    to = kwargs.pop("timeout", 15)
    t = kwargs.pop("token", None)
    if t:
        hdrs["Authorization"] = f"Bearer {t}"
    if "json" in kwargs or method in ("POST", "PUT", "PATCH"):
        hdrs.setdefault("Content-Type", "application/json")
    try:
        r = sync_requests.request(method, url, headers=hdrs, timeout=to, **kwargs)
        try:
            return r.status_code, (r.json() if r.text else {}), r.elapsed.total_seconds()
        except Exception:
            return r.status_code, {"raw": r.text[:500]}, r.elapsed.total_seconds()
    except Exception as e:
        return 0, {"error": str(e)}, 0


async def async_api(method, path, **kwargs):
    """异步 HTTP 请求，用于 verify 等非压测场景"""
    url = f"{BASE}{path}"
    hdrs = kwargs.pop("headers", {})
    to = kwargs.pop("timeout", 15)
    t = kwargs.pop("token", None)
    if t:
        hdrs["Authorization"] = f"Bearer {t}"
    if "json" in kwargs or method in ("POST", "PUT", "PATCH"):
        hdrs.setdefault("Content-Type", "application/json")
    try:
        timeout_obj = aiohttp.ClientTimeout(total=to)
        async with aiohttp.ClientSession(timeout=timeout_obj) as session:
            async with session.request(method, url, headers=hdrs, **kwargs) as resp:
                try:
                    data = await resp.json() if resp.text else {}
                except Exception:
                    raw_text = await resp.text()
                    data = {"raw": raw_text[:500]}
                return resp.status, data, 0
    except Exception as e:
        return 0, {"error": str(e)}, 0


# ==================== 启动检查 ====================
def check_deps():
    """检查关键依赖，缺失则提前退出"""
    missing = []
    try:
        import aiohttp
    except ImportError:
        missing.append("aiohttp")
    try:
        import requests
    except ImportError:
        missing.append("requests")
    if missing:
        print(f"❌ 缺少依赖: {', '.join(missing)}")
        print(f"   安装: pip install {' '.join(missing)}")
        sys.exit(1)

    if args.verify == "full":
        try:
            import websockets
        except ImportError:
            print("⚠️  --verify=full 需要 websockets 库")
            print("   安装: pip install websockets")
            sys.exit(1)

    # 可选依赖
    try:
        import plotly
        state.has_plotly = True
    except ImportError:
        state.has_plotly = False
    try:
        import psutil
        state.has_psutil = True
    except ImportError:
        state.has_psutil = False


# ==================== 设置 ====================
def setup():
    """admin登录 -> 获取商品列表 -> 创建50个秒杀活动(stock=200, startTime=now-1s)
       预热延迟到注册阶段并行完成"""
    print("\n  >>> 初始化测试数据...")
    state.t_start = time.time()

    # 1. admin 登录
    code, data, _ = api("POST", "/api/user/login", json={
        "username": "admin", "password": "admin123"
    })
    if code == 200 and data.get("code") == 0 and data.get("data", {}).get("token"):
        state.admin_token = data["data"]["token"]
        state.admin_uid = data["data"].get("userId", 0)
        print("  ✅ admin登录OK")
    else:
        print(f"  ❌ admin登录失败: {data.get('message', '')}")
        return False

    # 2. 获取商品列表
    code, data, _ = api("GET", "/api/product", params={"page": 1, "size": 100})
    if code == 200 and data.get("code") == 0:
        all_products = data.get("data", {}).get("records", [])
        if not all_products:
            print("  ⚠️ 无商品，正在创建...")
            for i in range(10):
                ts = str(int(time.time() * 1000) + i)
                c, d, _ = api("POST", "/api/product", json={
                    "name": f"普通商品_{ts}", "description": "E2E自动创建",
                    "price": random.randint(1000, 999900),
                    "stock": 9999, "category": "通用", "imageUrl": ""
                }, token=state.admin_token)
                if c == 200 and d.get("code") == 0 and d.get("data"):
                    pid = d["data"]
                    all_products.append({"id": pid, "name": f"普通商品_{ts}",
                                         "price": 1000, "stock": 9999})
            print(f"  ✅ 创建{len(all_products)}个商品")
        state.products = all_products
        state.normal_products = [p for p in all_products if p.get("id")]
        print(f"  ✅ 获取到{len(state.products)}个商品")
    else:
        print(f"  ❌ 获取商品失败: {data.get('message', '')}")
        return False

    # 3. 创建50个秒杀活动 (stock=200 each, startTime=now-1s → 预热完即生效)
    now = datetime.now()
    start_dt = now - timedelta(seconds=1)
    end_dt = now + timedelta(hours=2)
    start_str = start_dt.strftime("%Y-%m-%dT%H:%M:%S")
    end_str = end_dt.strftime("%Y-%m-%dT%H:%M:%S")

    activities = []
    for i in range(50):
        pid = state.normal_products[i % len(state.normal_products)]["id"]
        code, data, _ = api("POST", "/api/seckill/activity", json={
            "productId": pid,
            "seckillPrice": random.randint(100, 9900),
            "totalStock": 200,
            "startTime": start_str,
            "endTime": end_str
        }, token=state.admin_token)
        if code == 200 and data.get("code") == 0 and data.get("data"):
            aid = data["data"]
            activities.append({"id": aid, "productId": pid})
            state.act_product[aid] = pid
            # 转 PENDING
            api("PATCH", f"/api/seckill/activity/{aid}/status",
                json={"status": "PENDING"}, token=state.admin_token,
                headers={"X-User-Role": "ADMIN"})
        else:
            print(f"  ⚠️ 创建活动{i}失败: {data.get('message', '')}")

    if not activities:
        print("  ❌ 秒杀活动创建全部失败")
        return False

    state.seckill_activities = activities
    print(f"  ✅ 创建{len(activities)}个秒杀活动(stock=200 each)")

    # 4. 预热延迟到注册阶段并行处理（见 main），这里不阻塞
    state._pending_warmup_activities = activities
    print("  ⏳ 预热将在注册期间并行完成")

    # 5. 创建一些普通商品专门给购物用
    if len(state.normal_products) < 10:
        for i in range(10 - len(state.normal_products)):
            ts = str(int(time.time() * 1000) + i + 10000)
            c, d, _ = api("POST", "/api/product", json={
                "name": f"购物商品_{ts}", "description": "E2E购物用",
                "price": 199900, "stock": 99999, "category": "通用", "imageUrl": ""
            }, token=state.admin_token)
            if c == 200 and d.get("code") == 0 and d.get("data"):
                pid = d["data"]
                state.normal_products.append({"id": pid, "name": f"购物商品_{ts}",
                                              "price": 199900, "stock": 99999})

    print("  ✅ 初始化完成\n")
    return True


# ==================== 并行预热 ====================
def warm_up_activities(activities):
    """后台预热：ThreadPoolExecutor 并行发预热请求，不阻塞主流程"""
    from concurrent.futures import ThreadPoolExecutor, as_completed
    warmed = 0
    total = len(activities)
    with ThreadPoolExecutor(max_workers=20) as pool:
        futures = {
            pool.submit(api, "POST", f"/api/seckill/activity/{a['id']}/warm-up",
                        json={}, token=state.admin_token): a
            for a in activities
        }
        for f in as_completed(futures):
            try:
                code, data, _ = f.result()
                if code == 200 and data.get("code") == 0:
                    warmed += 1
            except Exception:
                pass
    return warmed


# ==================== 注册用户 ====================
async def register_users(n):
    """异步批量注册+登录n个用户。如果 --reuse-users 则跳过注册，直接批量登录"""
    if args.reuse_users:
        return await _reuse_users(n)

    print(f"  >>> 注册{n}个用户...")
    users = []
    sem = asyncio.Semaphore(200)

    async def do_register(session, idx):
        async with sem:
            ts = str(int(time.time() * 1000) + idx)
            uname = f"u_{ts}"
            email = f"{uname}@wanren.com"
            try:
                async with session.post(f"{BASE}/api/user/register",
                                        json={"username": uname, "email": email,
                                              "password": "test123456"},
                                        timeout=aiohttp.ClientTimeout(total=8)) as resp:
                    if resp.status != 200:
                        return None
                    reg_data = await resp.json()
                    if reg_data.get("code") != 0:
                        return None
            except Exception:
                return None

            try:
                async with session.post(f"{BASE}/api/user/login",
                                        json={"username": uname, "password": "test123456"},
                                        timeout=aiohttp.ClientTimeout(total=8)) as resp:
                    if resp.status != 200:
                        return None
                    login_data = await resp.json()
                    if login_data.get("code") != 0:
                        return None
                    tok = login_data.get("data", {}).get("token", "")
                    uid = login_data.get("data", {}).get("userId", 0)
                    if not tok:
                        return None
                    return {"username": uname, "email": email, "password": "test123456",
                            "token": tok, "uid": uid}
            except Exception:
                return None

    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [do_register(session, i) for i in range(n)]
        batch_size = min(200, n)
        for i in range(0, n, batch_size):
            batch = tasks[i:i + batch_size]
            results = await asyncio.gather(*batch)
            for r in results:
                if r:
                    users.append(r)
            print(f"    [{i + len(batch)}/{n}] 已注册{len(users)}个成功",
                  end="\r" if i + batch_size < n else "\n", flush=True)

    state.users = users
    print(f"  ✅ 注册完成: {len(users)}/{n} 用户成功")

    # 保存凭据供下次复用
    if users:
        creds = [{"username": u["username"], "password": u["password"]} for u in users]
        with open(state.reuse_file, "w") as f:
            json.dump(creds, f, indent=2)
        print(f"  💾 用户凭据已保存至 {state.reuse_file}（下次 --reuse-users 可用）")
    return users


async def _reuse_users(n):
    """跳过注册，从文件加载凭据，批量登录"""
    if not os.path.exists(state.reuse_file):
        print(f"  ❌ {state.reuse_file} 不存在，无法复用用户")
        sys.exit(1)

    with open(state.reuse_file) as f:
        creds = json.load(f)

    # 如果文件里不够，就取前 n 个
    if len(creds) < n:
        print(f"  ⚠️ {state.reuse_file} 只有 {len(creds)} 个用户，不足 {n}")
        n = len(creds)

    total = n
    users = []
    sem = asyncio.Semaphore(150)

    async def do_login(session, idx):
        async with sem:
            c = creds[idx]
            try:
                async with session.post(f"{BASE}/api/user/login",
                                        json={"username": c["username"],
                                              "password": c["password"]},
                                        timeout=aiohttp.ClientTimeout(total=8)) as resp:
                    if resp.status != 200:
                        return None
                    data = await resp.json()
                    if data.get("code") != 0:
                        return None
                    tok = data.get("data", {}).get("token", "")
                    uid = data.get("data", {}).get("userId", 0)
                    if not tok:
                        return None
                    return {"username": c["username"], "email": "",
                            "password": c["password"],
                            "token": tok, "uid": uid}
            except Exception:
                return None

    print(f"  >>> 复用 {total} 个用户（仅登录）...")
    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = [do_login(session, i) for i in range(total)]
        batch_size = min(200, total)
        for i in range(0, total, batch_size):
            batch = tasks[i:i + batch_size]
            results = await asyncio.gather(*batch)
            for r in results:
                if r:
                    users.append(r)
            print(f"    [{i + len(batch)}/{total}] 已登录{len(users)}个成功",
                  end="\r" if i + batch_size < total else "\n", flush=True)

    state.users = users
    print(f"  ✅ 登录完成: {len(users)}/{total} 用户成功")
    return users


# ==================== 资源采样器 ====================
async def resource_sampler(interval=1.0):
    """后台任务，定期记录 CPU/内存"""
    if not state.has_psutil:
        return
    try:
        import psutil
    except ImportError:
        return

    try:
        while True:
            state.cpu_samples.append(psutil.cpu_percent(interval=0.1))
            mem = psutil.virtual_memory()
            state.mem_samples.append(mem.percent)
            await asyncio.sleep(interval)
    except asyncio.CancelledError:
        pass


# ==================== 秒杀模式 ====================
async def run_seckill():
    """10000个用户并发秒杀(round-robin分配活动)"""
    if not state.users or not state.seckill_activities:
        print("  ❌ 秒杀模式: 用户或活动为空")
        return

    n = min(len(state.users), args.orders)
    users = state.users[:n]
    acts = state.seckill_activities
    total = len(users)

    print(f"\n  🎯 秒杀模式: {total}用户 x {len(acts)}活动")
    sem = asyncio.Semaphore(200)
    lock = asyncio.Lock()
    batch_lats = []
    batch_size = 500
    batch_idx = 0

    async def flash(session, user, act_id, seq):
        async with sem:
            t0 = time.time()
            try:
                async with session.post(f"{BASE}/api/seckill/flash",
                                        json={"activityId": act_id},
                                        headers={"Authorization": f"Bearer {user['token']}"},
                                        timeout=aiohttp.ClientTimeout(total=30)) as resp:
                    lat = time.time() - t0
                    if resp.status == 200:
                        data = await resp.json()
                        if data.get("code") == 0:
                            async with lock:
                                state.seckill_lats.append(lat)
                                state.results["total_orders"] += 1
                        else:
                            biz = data.get("code", -1)
                            async with lock:
                                key = f"business_{biz}"
                                state.seckill_errs[key] = state.seckill_errs.get(key, 0) + 1
                                state.results["failed_orders"] += 1
                    elif resp.status == 429:
                        async with lock:
                            state.seckill_errs["rate_limit"] = state.seckill_errs.get("rate_limit", 0) + 1
                            state.results["failed_orders"] += 1
                    elif resp.status == 409:
                        async with lock:
                            state.seckill_errs["conflict"] = state.seckill_errs.get("conflict", 0) + 1
                            state.results["failed_orders"] += 1
                    else:
                        async with lock:
                            key = f"http_{resp.status}"
                            state.seckill_errs[key] = state.seckill_errs.get(key, 0) + 1
                            state.results["failed_orders"] += 1
                    async with lock:
                        batch_lats.append(lat)
            except Exception:
                lat = time.time() - t0
                async with lock:
                    state.seckill_errs["exception"] = state.seckill_errs.get("exception", 0) + 1
                    state.results["failed_orders"] += 1
                    batch_lats.append(lat)

    connector = aiohttp.TCPConnector(limit=500, limit_per_host=500)
    async with aiohttp.ClientSession(connector=connector) as session:
        for batch_start in range(0, total, batch_size):
            batch_end = min(batch_start + batch_size, total)
            tasks = []
            for idx in range(batch_start, batch_end):
                user = users[idx]
                act = acts[idx % len(acts)]
                tasks.append(flash(session, user, act["id"], idx))

            t0 = time.time()
            await asyncio.gather(*tasks)
            elapsed = time.time() - t0

            avg_lat = sum(batch_lats) / len(batch_lats) if batch_lats else 0
            state.time_series.append({
                "mode": "seckill", "batch": batch_idx,
                "start_idx": batch_start, "end_idx": batch_end,
                "duration": round(elapsed, 3),
                "throughput": round((batch_end - batch_start) / max(elapsed, 0.001), 1),
                "avg_latency": round(avg_lat * 1000, 1),
                "pending": 0,
                "success": state.results["total_orders"],
                "fail": state.results["failed_orders"]
            })
            batch_idx += 1
            batch_lats.clear()

            ok_count = state.results["total_orders"]
            fail_count = state.results["failed_orders"]
            print(f"    [{batch_end}/{total}] 成功{ok_count} 失败{fail_count} 耗时{elapsed:.1f}s",
                  end="\r" if batch_end < total else "\n", flush=True)

    print(f"  ✅ 秒杀完成: 成功{state.results['total_orders']}, "
          f"失败{state.results['failed_orders']}")


# ==================== 购物子函数（被 run_shopping 调用） ====================
async def purchase_create_address(session, tok, idx):
    """创建地址，返回 addr_id 或 None"""
    try:
        async with session.post(f"{BASE}/api/address",
                                json={
                                    "receiverName": f"收{idx}",
                                    "receiverPhone": "13800138000",
                                    "province": "北京",
                                    "city": "北京",
                                    "district": "朝阳",
                                    "detailAddress": f"路{idx}号",
                                    "isDefault": 1
                                },
                                headers={"Authorization": f"Bearer {tok}"},
                                timeout=aiohttp.ClientTimeout(total=15)) as resp:
            if resp.status != 200:
                async with _shop_errs_lock:
                    state.shop_errs["addr_fail"] = state.shop_errs.get("addr_fail", 0) + 1
                return None
            addr_data = await resp.json()
            if addr_data.get("code") != 0:
                async with _shop_errs_lock:
                    state.shop_errs["addr_biz_fail"] = state.shop_errs.get("addr_biz_fail", 0) + 1
                return None
            addr_val = addr_data.get("data", {})
            addr_id = addr_val.get("id") if isinstance(addr_val, dict) else addr_val
            return addr_id
    except Exception:
        async with _shop_errs_lock:
            state.shop_errs["addr_exception"] = state.shop_errs.get("addr_exception", 0) + 1
        return None


async def purchase_add_cart(session, tok):
    """加购一个随机商品并获取cart_id，返回 cart_id 或 None"""
    try:
        normal_products = state.normal_products or state.products
        prod = random.choice(normal_products)
        pid = prod["id"] if isinstance(prod, dict) else prod
        async with session.post(f"{BASE}/api/cart/add",
                                json={"productId": pid, "quantity": 1},
                                headers={"Authorization": f"Bearer {tok}"},
                                timeout=aiohttp.ClientTimeout(total=15)) as resp:
            if resp.status != 200:
                async with _shop_errs_lock:
                    state.shop_errs["cart_fail"] = state.shop_errs.get("cart_fail", 0) + 1
                return None
        # 获取购物车列表取 cartId
        async with session.get(f"{BASE}/api/cart/list",
                               headers={"Authorization": f"Bearer {tok}"},
                               timeout=aiohttp.ClientTimeout(total=15)) as resp:
            if resp.status != 200:
                return None
            cart_data = await resp.json()
            items = cart_data.get("data", [])
            if not items:
                return None
            cart_id = items[0]["id"] if isinstance(items[0], dict) else items[0]
            return cart_id
    except Exception:
        async with _shop_errs_lock:
            state.shop_errs["cart_exception"] = state.shop_errs.get("cart_exception", 0) + 1
        return None


async def purchase_create_order(session, tok, cart_id, addr_id):
    """创建订单，返回 oid 或 None"""
    try:
        async with session.post(f"{BASE}/api/order/create",
                                json={"cartIds": [cart_id], "addressId": addr_id},
                                headers={"Authorization": f"Bearer {tok}"},
                                timeout=aiohttp.ClientTimeout(total=30)) as resp:
            if resp.status != 200:
                async with _shop_errs_lock:
                    state.shop_errs["order_fail"] = state.shop_errs.get("order_fail", 0) + 1
                return None
            order_data = await resp.json()
            if order_data.get("code") != 0:
                async with _shop_errs_lock:
                    state.shop_errs["order_biz_fail"] = state.shop_errs.get("order_biz_fail", 0) + 1
                return None
            oid_raw = order_data.get("data", "")
            if isinstance(oid_raw, list):
                if not oid_raw:
                    return None
                oid = oid_raw[0]
            else:
                oid = oid_raw
            return oid
    except Exception:
        async with _shop_errs_lock:
            state.shop_errs["order_exception"] = state.shop_errs.get("order_exception", 0) + 1
        return None


async def purchase_pay_order(session, tok, oid):
    """支付订单，返回 True/False"""
    try:
        async with session.post(f"{BASE}/api/order/{oid}/pay",
                                json={},
                                headers={"Authorization": f"Bearer {tok}"},
                                timeout=aiohttp.ClientTimeout(total=30)) as resp:
            if resp.status != 200:
                async with _shop_errs_lock:
                    state.shop_errs["pay_fail"] = state.shop_errs.get("pay_fail", 0) + 1
                return False
            pay_data = await resp.json()
            if pay_data.get("code") != 0:
                async with _shop_errs_lock:
                    state.shop_errs["pay_biz_fail"] = state.shop_errs.get("pay_biz_fail", 0) + 1
                return False
        async with _shop_errs_lock:
            state.results["paid_orders"] += 1
        return True
    except Exception:
        async with _shop_errs_lock:
            state.shop_errs["pay_exception"] = state.shop_errs.get("pay_exception", 0) + 1
        return False


# ==================== 购物模式 ====================
async def run_shopping():
    """10000个用户: 加购->下单->支付->admin发货->确认收货(精简版)"""
    if not state.users:
        print("  ❌ 购物模式: 用户为空")
        return

    n = min(len(state.users), args.orders)
    users = state.users[:n]
    total = len(users)
    normal_products = state.normal_products or state.products

    print(f"\n  🛒 购物模式: {total}用户 (每用户: 下单->支付->发货->收货)")
    sem = asyncio.Semaphore(100)
    lock = asyncio.Lock()
    batch_lats = []
    batch_size = 200
    batch_idx = 0

    async def full_purchase(session, user, idx):
        """完整购物链路"""
        async with sem:
            t0 = time.time()
            tok = user["token"]
            try:
                addr_id = await purchase_create_address(session, tok, idx)
                if not addr_id:
                    return False
                cart_id = await purchase_add_cart(session, tok)
                if not cart_id:
                    return False
                oid = await purchase_create_order(session, tok, cart_id, addr_id)
                if not oid:
                    return False
                ok = await purchase_pay_order(session, tok, oid)
                if not ok:
                    return False

                # 6. admin 发货
                async with session.post(f"{BASE}/api/admin/order/{oid}/ship",
                                        json={},
                                        headers={"Authorization": f"Bearer {state.admin_token}"},
                                        timeout=aiohttp.ClientTimeout(total=15)) as resp:
                    if resp.status == 200:
                        ship_data = await resp.json()
                        if ship_data.get("code") == 0:
                            async with lock:
                                state.results["shipped_orders"] += 1
                        else:
                            async with lock:
                                state.shop_errs["ship_fail"] = state.shop_errs.get("ship_fail", 0) + 1
                    else:
                        async with lock:
                            state.shop_errs["ship_fail"] = state.shop_errs.get("ship_fail", 0) + 1

                # 7. 确认收货
                async with session.post(f"{BASE}/api/order/{oid}/receive",
                                        json={},
                                        headers={"Authorization": f"Bearer {tok}"},
                                        timeout=aiohttp.ClientTimeout(total=15)) as resp:
                    if resp.status == 200:
                        recv_data = await resp.json()
                        if recv_data.get("code") == 0:
                            async with lock:
                                state.results["received_orders"] += 1
                        else:
                            async with lock:
                                state.shop_errs["receive_fail"] = state.shop_errs.get("receive_fail", 0) + 1
                    else:
                        async with lock:
                            state.shop_errs["receive_fail"] = state.shop_errs.get("receive_fail", 0) + 1

                lat = time.time() - t0
                async with lock:
                    state.shopping_lats.append(lat)
                    state.results["total_orders"] += 1
                    batch_lats.append(lat)
                return True

            except Exception:
                lat = time.time() - t0
                async with lock:
                    state.shop_errs["exception"] = state.shop_errs.get("exception", 0) + 1
                    state.results["failed_orders"] += 1
                    batch_lats.append(lat)
                return False

    connector = aiohttp.TCPConnector(limit=300, limit_per_host=300)
    async with aiohttp.ClientSession(connector=connector) as session:
        for batch_start in range(0, total, batch_size):
            batch_end = min(batch_start + batch_size, total)
            tasks = [full_purchase(session, users[idx], idx) for idx in range(batch_start, batch_end)]

            t0 = time.time()
            await asyncio.gather(*tasks)
            elapsed = time.time() - t0

            avg_lat = sum(batch_lats) / len(batch_lats) if batch_lats else 0
            state.time_series.append({
                "mode": "shopping", "batch": batch_idx,
                "start_idx": batch_start, "end_idx": batch_end,
                "duration": round(elapsed, 3),
                "throughput": round((batch_end - batch_start) / max(elapsed, 0.001), 1),
                "avg_latency": round(avg_lat * 1000, 1),
                "pending": 0,
                "success": state.results["total_orders"],
                "fail": state.results["failed_orders"]
            })
            batch_idx += 1
            batch_lats.clear()

            ok_count = state.results["total_orders"]
            fail_count = state.results["failed_orders"]
            paid = state.results["paid_orders"]
            print(f"    [{batch_end}/{total}] 成功{ok_count} 失败{fail_count} 已付{paid}",
                  end="\r" if batch_end < total else "\n", flush=True)

    print(f"  ✅ 购物完成: 成功{state.results['total_orders']}, "
          f"已付{state.results['paid_orders']}, "
          f"已发{state.results['shipped_orders']}, "
          f"已收{state.results['received_orders']}")


# ==================== 混合模式 ====================
async def run_mixed():
    """n//2 秒杀 + n-n//2 购物"""
    if not state.users:
        print("  ❌ 混合模式: 用户为空")
        return

    n = min(len(state.users), args.orders)
    n_seckill = n // 2
    n_shopping = n - n_seckill

    print(f"\n  🎯 混合模式: {n_seckill}秒杀 + {n_shopping}购物 = {n}总订单")

    old_users = state.users
    seckill_users = state.users[:n_seckill]
    shopping_users = state.users[n_seckill:n]

    state.users = seckill_users
    await run_seckill()

    state.users = shopping_users
    await run_shopping()

    state.users = old_users


# ==================== 验证函数 ====================

async def verify_jwt():
    """JWT验证: no token -> 401/403, fake token -> 401/403, empty token -> 401/403"""
    results = []

    code, data, _ = await async_api("GET", "/api/user/me")
    ok = code in (401, 403) or (code == 200 and data.get("code") != 0)
    results.append({"test": "无token访问", "status": "PASS" if ok else "FAIL",
                    "http": code, "detail": f"期望401/403, 实际{code}"})

    code, data, _ = await async_api("GET", "/api/user/me",
                                    headers={"Authorization": "Bearer fake_token_12345"})
    ok = code in (401, 403) or (code == 200 and data.get("code") != 0)
    results.append({"test": "假token访问", "status": "PASS" if ok else "FAIL",
                    "http": code, "detail": f"期望401/403, 实际{code}"})

    code, data, _ = await async_api("GET", "/api/user/me", headers={"Authorization": ""})
    ok = code in (401, 403) or (code == 200 and data.get("code") != 0)
    results.append({"test": "空token访问", "status": "PASS" if ok else "FAIL",
                    "http": code, "detail": f"期望401/403, 实际{code}"})

    state.verifications.extend(results)
    return results


async def verify_cache_penetration():
    """缓存穿透: 负数ID/不存在ID/空body/非法活动ID"""
    results = []

    code, data, _ = await async_api("GET", "/api/product/-1")
    ok = code != 500
    results.append({"test": "负数ID商品", "status": "PASS" if ok else "FAIL",
                    "http": code, "detail": f"返回{code}" if ok else "500错误(缓存穿透)"})

    code, data, _ = await async_api("GET", "/api/product/99999999")
    ok = code != 500
    results.append({"test": "不存在ID商品", "status": "PASS" if ok else "FAIL",
                    "http": code, "detail": f"返回{code}" if ok else "500错误(缓存穿透)"})

    if state.users:
        tok = state.users[0]["token"]
        code, data, _ = await async_api("POST", "/api/seckill/flash", json={}, token=tok)
        ok = code != 500
        results.append({"test": "空body秒杀", "status": "PASS" if ok else "FAIL",
                        "http": code,
                        "detail": f"返回{code}" if ok else "500错误"})

        code, data, _ = await async_api("POST", "/api/seckill/flash",
                                        json={"activityId": -999}, token=tok)
        ok = code != 500
        results.append({"test": "非法活动ID", "status": "PASS" if ok else "FAIL",
                        "http": code,
                        "detail": f"返回{code}" if ok else "500错误"})
    else:
        results.append({"test": "空body秒杀", "status": "SKIP", "detail": "无用户"})
        results.append({"test": "非法活动ID", "status": "SKIP", "detail": "无用户"})

    state.verifications.extend(results)
    return results


async def verify_rate_limit():
    """限流验证: 3000+并发请求，使用Semaphore(200)，期望部分429，重试1次"""
    results = []

    if not state.users or not state.seckill_activities:
        results.append({"test": "限流验证", "status": "SKIP", "detail": "缺用户/活动"})
        state.verifications.extend(results)
        return results

    tok = state.users[0]["token"]
    aid = state.seckill_activities[0]["id"]

    codes = []
    lock = asyncio.Lock()
    sem = asyncio.Semaphore(200)
    intensity_map = {"light": 500, "medium": 1500, "heavy": 3000}
    n_req = intensity_map[args.verify_intensity]

    async def send():
        async with sem:
            c, _, _ = await async_api("POST", "/api/seckill/flash",
                                      json={"activityId": aid}, token=tok, timeout=10)
            async with lock:
                codes.append(c)

    t0 = time.time()
    tasks = [send() for _ in range(n_req)]
    await asyncio.gather(*tasks)
    elapsed = time.time() - t0

    has_429 = 429 in codes
    retry_codes = []
    if has_429:
        async def retry_one():
            c, _, _ = await async_api("POST", "/api/seckill/flash",
                                      json={"activityId": aid}, token=tok, timeout=10)
            async with lock:
                retry_codes.append(c)
        rtasks = [retry_one() for _ in range(5)]
        await asyncio.gather(*rtasks)

    status = "PASS" if has_429 else "WARN"
    detail = (f"共{n_req}请求, 429出现{sum(1 for c in codes if c==429)}次"
              if has_429 else f"共{n_req}请求, 未触发限流(可能并发不足)")
    if retry_codes:
        detail += f", 重试5次后: {retry_codes}"
    results.append({"test": "限流验证", "status": status, "http": f"429出现{has_429}",
                    "detail": detail, "codes_summary": f"{n_req}req/{elapsed:.1f}s"})

    state.verifications.extend(results)
    return results


async def verify_mq_async():
    """MQ异步验证: 秒杀后立即查订单状态，等3s再查"""
    results = []

    if not state.users or not state.seckill_activities:
        results.append({"test": "MQ异步验证", "status": "SKIP", "detail": "缺用户/活动"})
        state.verifications.extend(results)
        return results

    tok = state.users[0]["token"]
    aid = state.seckill_activities[0]["id"]

    code, data, _ = await async_api("POST", "/api/seckill/flash",
                                    json={"activityId": aid}, token=tok)
    if code != 200 or data.get("code") != 0:
        results.append({"test": "MQ异步验证", "status": "SKIP", "detail": f"秒杀失败: {data.get('message','')}"})
        state.verifications.extend(results)
        return results

    order_id = data.get("data", "")

    # 立即查
    if order_id:
        c0, d0, _ = await async_api("GET", f"/api/order/{order_id}", token=tok)
    else:
        c0, d0 = 0, {}
    status_0 = d0.get("data", {}).get("status", "unknown") if isinstance(d0.get("data"), dict) else "unknown"

    # 等3秒再查
    await asyncio.sleep(3)
    if order_id:
        c3, d3, _ = await async_api("GET", f"/api/order/{order_id}", token=tok)
    else:
        c3, d3 = 0, {}
    status_3 = d3.get("data", {}).get("status", "unknown") if isinstance(d3.get("data"), dict) else "unknown"

    changed = status_0 != status_3
    results.append({"test": "MQ异步验证",
                    "status": "PASS" if changed else "WARN",
                    "detail": f"0s状态={status_0}, 3s状态={status_3}, 状态变化={changed}"})

    state.verifications.extend(results)
    return results


async def verify_ai_chat():
    """AI聊天验证: POST /api/ai/chat, 检查回复"""
    results = []
    code, data, _ = await async_api("POST", "/api/ai/chat",
                                    json={"message": "你好, 介绍一下秒杀系统"},
                                    timeout=30)
    if code == 200:
        reply = data.get("data", "")
        if isinstance(reply, dict):
            reply = reply.get("reply", reply.get("content", str(reply)))
        ok = bool(reply) and len(str(reply)) > 5
        results.append({"test": "AI聊天", "status": "PASS" if ok else "FAIL",
                        "http": code, "detail": f"回复长度={len(str(reply))}"})
    else:
        results.append({"test": "AI聊天", "status": "FAIL",
                        "http": code, "detail": f"状态码{code}"})

    state.verifications.extend(results)
    return results


async def verify_websocket_push():
    """WebSocket推送验证: ws://localhost:8080/ws/seckill (仅full模式)"""
    results = []
    try:
        import websockets
        async with websockets.connect(f"ws://localhost:8080/ws/seckill", timeout=5) as ws:
            try:
                msg = await asyncio.wait_for(ws.recv(), timeout=5)
                results.append({"test": "WebSocket推送", "status": "PASS",
                                "detail": f"收到推送: {str(msg)[:100]}"})
            except asyncio.TimeoutError:
                results.append({"test": "WebSocket推送", "status": "WARN",
                                "detail": "连接成功但5s内无推送"})
    except ImportError:
        results.append({"test": "WebSocket推送", "status": "SKIP",
                        "detail": "缺少websockets库"})
    except Exception as e:
        results.append({"test": "WebSocket推送", "status": "FAIL",
                        "detail": f"{type(e).__name__}: {e}"})

    state.verifications.extend(results)
    return results


async def run_verifications():
    """基于 --verify 等级执行验证"""
    print("\n  >>> 执行验证...")
    call_order = []

    if args.verify in ("basic", "full"):
        call_order.extend([
            ("JWT验证", verify_jwt),
            ("缓存穿透验证", verify_cache_penetration),
            ("限流验证", verify_rate_limit),
            ("MQ异步验证", verify_mq_async),
            ("AI聊天验证", verify_ai_chat),
        ])

    if args.verify == "full":
        call_order.append(("WebSocket推送验证", verify_websocket_push))

    for name, fn in call_order:
        try:
            if asyncio.iscoroutinefunction(fn):
                results = await fn()
            else:
                results = fn()
            passed = sum(1 for r in results if r.get("status") == "PASS")
            failed = sum(1 for r in results if r.get("status") == "FAIL")
            skipped = sum(1 for r in results if r.get("status") == "SKIP")
            icon = "✅" if failed == 0 else "❌"
            print(f"  {icon} {name}: {len(results)}测试 ({passed}通过, {failed}失败, {skipped}跳过)")
        except Exception as e:
            print(f"  ❌ {name}: 异常 {type(e).__name__}: {e}")


# ==================== 清理 ====================
def cleanup():
    """基于 --cleanup 等级清理数据"""
    print("\n  >>> 清理数据...")

    if args.cleanup == "none":
        print("  ⏭️  --cleanup=none，不清理")
        return

    del_count = {"activities": 0, "products": 0, "users": 0}

    # 删除秒杀活动
    for act in state.seckill_activities:
        try:
            aid = act["id"] if isinstance(act, dict) else act
            code, _, _ = api("DELETE", f"/api/seckill/activity/{aid}",
                             token=state.admin_token)
            if code in (200, 204):
                del_count["activities"] += 1
        except Exception as e:
            print(f"  ⚠️ 删除活动失败: {e}")

    # 删除商品
    for pid in state.products:
        try:
            if isinstance(pid, dict):
                pid = pid.get("id", 0)
            code, _, _ = api("DELETE", f"/api/product/{pid}",
                             token=state.admin_token)
            if code in (200, 204):
                del_count["products"] += 1
        except Exception as e:
            print(f"  ⚠️ 删除商品失败: {e}")

    print(f"  ✅ 删除{del_count['activities']}个活动, {del_count['products']}个商品")

    # full模式删除用户
    if args.cleanup == "full":
        for user in state.users:
            try:
                uid = user.get("uid", 0)
                if uid:
                    code, _, _ = api("DELETE", f"/api/admin/user/{uid}",
                                     token=state.admin_token,
                                     headers={"X-User-Role": "ADMIN"})
                    if code in (200, 204):
                        del_count["users"] += 1
            except Exception as e:
                print(f"  ⚠️ 删除用户失败: {e}")
        print(f"  ✅ 删除{del_count['users']}个用户")

    print(f"  ✅ 清理完成")


# ==================== 控制台报告 ====================
def console_report():
    """打印控制台结果表"""
    elapsed = time.time() - state.t_start
    print("\n" + "=" * 60)
    print("  📊 万人同场·E2E仿真报告")
    print("=" * 60)
    print(f"  模式:         {args.mode}")
    print(f"  目标订单数:   {args.orders}")
    print(f"  验证等级:     {args.verify}")
    print(f"  总耗时:       {elapsed:.1f}s")
    print(f"  实际用户数:   {len(state.users)}")
    print("-" * 60)
    print(f"  总订单:       {state.results['total_orders']}")
    print(f"  已支付:       {state.results['paid_orders']}")
    print(f"  已发货:       {state.results['shipped_orders']}")
    print(f"  已收货:       {state.results['received_orders']}")
    print(f"  失败:         {state.results['failed_orders']}")
    print("-" * 60)

    if state.seckill_lats:
        avg_lat = sum(state.seckill_lats) / len(state.seckill_lats)
        max_lat = max(state.seckill_lats)
        print(f"  秒杀延迟:     avg={avg_lat*1000:.1f}ms  max={max_lat*1000:.1f}ms  "
              f"共{len(state.seckill_lats)}样本")
    if state.shopping_lats:
        avg_lat = sum(state.shopping_lats) / len(state.shopping_lats)
        max_lat = max(state.shopping_lats)
        print(f"  购物延迟:     avg={avg_lat*1000:.1f}ms  max={max_lat*1000:.1f}ms  "
              f"共{len(state.shopping_lats)}样本")

    if state.seckill_errs:
        print(f"  秒杀错误:     {dict(sorted(state.seckill_errs.items(), key=lambda x:-x[1])[:5])}")
    if state.shop_errs:
        print(f"  购物错误:     {dict(sorted(state.shop_errs.items(), key=lambda x:-x[1])[:5])}")

    if state.verifications:
        passed = sum(1 for r in state.verifications if r.get("status") == "PASS")
        failed = sum(1 for r in state.verifications if r.get("status") == "FAIL")
        skipped = sum(1 for r in state.verifications if r.get("status") == "SKIP")
        print(f"  验证结果:     {passed}通过  {failed}失败  {skipped}跳过  "
              f"共{len(state.verifications)}项")

    print("=" * 60)


# ==================== 库存快照 ====================
def snapshot_stock(after=False):
    """从 Redis 读取秒杀活动库存（产品表 stock 字段在秒杀中不变）
       before: 用已知初始值（200 stock, 0 bought）
       after:  从 Redis 读取"""
    if not after:
        # 预热前 Redis 尚无数据，用已知初始值
        for act in state.seckill_activities:
            aid = act["id"]
            pid = act.get("productId", "?")
            pname = f"活动{aid}"
            for p in state.products:
                if isinstance(p, dict) and p.get("id") == pid:
                    pname = p.get("name", f"商品{pid}")
                    break
            state.stock_before[aid] = {"name": pname, "stock": 200, "bought": 0}
        return

    import subprocess
    for act in state.seckill_activities:
        aid = act["id"]
        pid = act.get("productId", "?")
        pname = f"活动{aid}"
        for p in state.products:
            if isinstance(p, dict) and p.get("id") == pid:
                pname = p.get("name", f"商品{pid}")
                break
        try:
            stock_str = subprocess.run(
                ["redis-cli", "GET", f"seckill:stock:{aid}"],
                capture_output=True, text=True, timeout=3).stdout.strip()
            bought_str = subprocess.run(
                ["redis-cli", "SCARD", f"seckill:bought:{aid}"],
                capture_output=True, text=True, timeout=3).stdout.strip()
            stock = int(stock_str) if stock_str and stock_str.isdigit() else -1
            bought = int(bought_str) if bought_str and bought_str.isdigit() else -1
        except Exception:
            stock, bought = -1, -1
        state.stock_after[aid] = {"name": pname, "stock": stock, "bought": bought}


# ==================== HTML报告 ====================
def generate_report():
    """保存 stress_report.json + 纯CSS HTML报告 (无需JS/网络)"""
    elapsed = time.time() - state.t_start
    report = {
        "meta": {
            "mode": args.mode,
            "orders": args.orders,
            "verify": args.verify,
            "cleanup": args.cleanup,
            "elapsed_seconds": round(elapsed, 1),
            "user_count": len(state.users),
            "timestamp": datetime.now().isoformat(),
        },
        "results": state.results,
        "latency": {
            "seckill_count": len(state.seckill_lats),
            "seckill_avg_ms": round(sum(state.seckill_lats) / len(state.seckill_lats) * 1000, 1) if state.seckill_lats else 0,
            "seckill_max_ms": round(max(state.seckill_lats) * 1000, 1) if state.seckill_lats else 0,
            "shopping_count": len(state.shopping_lats),
            "shopping_avg_ms": round(sum(state.shopping_lats) / len(state.shopping_lats) * 1000, 1) if state.shopping_lats else 0,
            "shopping_max_ms": round(max(state.shopping_lats) * 1000, 1) if state.shopping_lats else 0,
        },
        "errors": {
            "seckill": dict(sorted(state.seckill_errs.items(), key=lambda x: -x[1])),
            "shopping": dict(sorted(state.shop_errs.items(), key=lambda x: -x[1])),
            "verification": [v for v in state.verifications if v.get("status") == "FAIL"],
        },
        "verifications": state.verifications,
        "time_series": state.time_series,
        "system": {
            "cpu_samples": state.cpu_samples,
            "mem_samples": state.mem_samples,
        },
        "stock_comparison": {
            aid: {
                "name": info["name"],
                "stock_before": info["stock"],
                "bought_before": info["bought"],
                "stock_after": state.stock_after.get(aid, {}).get("stock", "?"),
                "bought_after": state.stock_after.get(aid, {}).get("bought", "?"),
                "delta": info["stock"] - state.stock_after.get(aid, {}).get("stock", info["stock"])
            }
            for aid, info in state.stock_before.items()
            if info["stock"] != state.stock_after.get(aid, {}).get("stock", info["stock"])
        }
    }

    json_path = "stress_report.json"
    with open(json_path, "w") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)
    print(f"\n  📄 JSON报告已保存: {json_path}")

    # ========== 纯CSS HTML报告 (零JS) ==========
    r = state.results
    ts = state.time_series

    # 摘要卡片
    cards_html = f"""
    <div class="cards">
      <div class="card green"><div class="num">{r['total_orders']}</div><div class="lbl">总订单</div></div>
      <div class="card orange"><div class="num">{r['paid_orders']}</div><div class="lbl">已支付</div></div>
      <div class="card blue"><div class="num">{r['shipped_orders']}</div><div class="lbl">已发货</div></div>
      <div class="card purple"><div class="num">{r['received_orders']}</div><div class="lbl">已收货</div></div>
      <div class="card red"><div class="num">{r['failed_orders']}</div><div class="lbl">失败</div></div>
    </div>"""

    # 延迟CSS柱
    seckill_avg = round(sum(state.seckill_lats)/len(state.seckill_lats)*1000,1) if state.seckill_lats else 0
    seckill_max = round(max(state.seckill_lats)*1000,1) if state.seckill_lats else 0
    shop_avg = round(sum(state.shopping_lats)/len(state.shopping_lats)*1000,1) if state.shopping_lats else 0
    shop_max = round(max(state.shopping_lats)*1000,1) if state.shopping_lats else 0

    delay_bars = ""
    if state.seckill_lats:
        pct = min(seckill_avg / max(seckill_avg, shop_avg, 1) * 90, 90)
        delay_bars += f"""
        <div class="bar-row"><span class="bar-lbl">秒杀平均</span><div class="bar-track"><div class="bar-fill red" style="width:{pct}%"></div></div><span class="bar-val">{seckill_avg}ms</span></div>
        <div class="bar-row"><span class="bar-lbl">秒杀最大</span><div class="bar-track"><div class="bar-fill orange" style="width:{min(seckill_max/max(seckill_max,1)*90,90)}%"></div></div><span class="bar-val">{seckill_max}ms</span></div>"""
    if state.shopping_lats:
        pct_s = min(shop_avg / max(shop_avg, seckill_avg, 1) * 90, 90)
        delay_bars += f"""
        <div class="bar-row"><span class="bar-lbl">购物平均</span><div class="bar-track"><div class="bar-fill teal" style="width:{pct_s}%"></div></div><span class="bar-val">{shop_avg}ms</span></div>
        <div class="bar-row"><span class="bar-lbl">购物最大</span><div class="bar-track"><div class="bar-fill blue" style="width:{min(shop_max/max(shop_max,1)*90,90)}%"></div></div><span class="bar-val">{shop_max}ms</span></div>"""

    # 错误分布CSS柱
    all_errs = {}
    for k,v in state.seckill_errs.items(): all_errs[f"秒杀_{k}"] = v
    for k,v in state.shop_errs.items(): all_errs[f"购物_{k}"] = v
    err_bars = ""
    if all_errs:
        max_err = max(all_errs.values())
        for ek, ev in sorted(all_errs.items(), key=lambda x:-x[1]):
            pct = ev / max_err * 100
            err_bars += f"""
        <div class="bar-row"><span class="bar-lbl">{ek}</span><div class="bar-track"><div class="bar-fill red" style="width:{pct}%"></div></div><span class="bar-val">{ev}</span></div>"""

    # 验证结果表
    ver_rows = ""
    for v in state.verifications:
        st = v.get("status","")
        badge = f'<span class="badge {"pass" if st=="PASS" else "skip" if st=="SKIP" else "fail"}">{st}</span>'
        ver_rows += f"<tr><td>{v.get('test','')}</td><td>{badge}</td><td>{v.get('http','')}</td><td>{v.get('detail','')[:80]}</td></tr>"

    # 时间序列表
    ts_rows = ""
    ts_cpu_rows = ""  # CSS棒图
    max_tp = max((s["throughput"] for s in ts), default=1)
    max_lat = max((s["avg_latency"] for s in ts), default=1)
    for s in ts:
        tp_pct = s["throughput"] / max_tp * 100
        lat_pct = s["avg_latency"] / max_lat * 100
        ts_rows += f"""<tr><td>{s['start_idx']}-{s['end_idx']}</td><td>{s['duration']}s</td>
          <td><div class="mini-bar"><div class="bar-fill blue" style="width:{tp_pct}%">{s['throughput']}</div></div></td>
          <td><div class="mini-bar"><div class="bar-fill orange" style="width:{lat_pct}%">{s['avg_latency']}ms</div></div></td></tr>"""

    # 库存前后对比（从 Redis 读取秒杀活动库存）
    stock_rows = ""
    stock_delta_total = 0
    stock_bought_total = 0
    if state.stock_before and state.stock_after:
        for aid in sorted(state.stock_before.keys()):
            b = state.stock_before[aid]
            a = state.stock_after.get(aid)
            if a is None or b["stock"] < 0 or a["stock"] < 0:
                continue
            delta = b["stock"] - a["stock"]
            bought = a["bought"]
            if a["bought"] >= 0:
                stock_bought_total += bought
            if delta > 0:
                stock_delta_total += delta
                color = "#e74c3c"
                direction = "↓"
                pct = min(delta / max(1, b["stock"] + b["bought"]) * 100, 100)
                stock_rows += f"""<tr><td>{b['name']}</td><td>{b['stock']}</td><td>{a['stock']}</td>
                  <td>{bought}</td><td><span style="color:{color};font-weight:bold;">{direction} {abs(delta)}</span></td>
                  <td><div class="mini-bar"><div class="bar-fill red" style="width:{pct}%">{abs(delta)}</div></div></td></tr>"""

    # 系统资源
    res_rows = ""
    # 系统资源 — SVG 折线图
    svg_res = ""
    if state.cpu_samples and state.mem_samples and state.has_psutil:
        n = len(state.cpu_samples)
        # SVG 尺寸
        svg_w, svg_h = 800, 280
        pad_l, pad_r, pad_t, pad_b = 55, 20, 30, 45
        cw = svg_w - pad_l - pad_r
        ch = svg_h - pad_t - pad_b

        # CPU + MEM 合并取范围
        all_vals = state.cpu_samples + state.mem_samples
        y_max = max(all_vals + [1])
        y_max_round = int(((y_max // 10) + 1) * 10)  # 向上取整到10
        if y_max_round < 10:
            y_max_round = 10

        def to_svg(v, idx, total):
            """数据点映射到SVG坐标"""
            x = pad_l + (idx / max(1, total - 1)) * cw
            y = pad_t + ch - (v / y_max_round) * ch
            return f"{x:.1f},{y:.1f}"

        # CPU 折线点
        cpu_pts = " ".join(to_svg(v, i, n) for i, v in enumerate(state.cpu_samples))
        mem_pts = " ".join(to_svg(v, i, n) for i, v in enumerate(state.mem_samples))

        # Y轴刻度线
        y_ticks = ""
        for pct in range(0, y_max_round + 1, max(1, y_max_round // 5)):
            yy = pad_t + ch - (pct / y_max_round) * ch
            y_ticks += f"""<line x1="{pad_l}" y1="{yy:.1f}" x2="{pad_l + cw}" y2="{yy:.1f}" stroke="#eee" stroke-width="1"/>
                <text x="{pad_l - 8}" y="{yy + 4:.1f}" text-anchor="end" font-size="11" fill="#666">{pct}%</text>"""

        # X轴刻度（采样点标记）
        x_tick_step = max(1, n // 8)
        x_ticks = ""
        for i in range(0, n, x_tick_step):
            xx = pad_l + (i / max(1, n - 1)) * cw
            x_ticks += f"""<text x="{xx:.1f}" y="{svg_h - 10}" text-anchor="middle" font-size="10" fill="#666">{i}s</text>"""

        # 面积填充（渐变）
        cpu_area = ""
        if n >= 2:
            last_x = pad_l + cw
            first_pt = f"{pad_l:.1f},{pad_t + ch:.1f}"
            last_pt = f"{last_x:.1f},{pad_t + ch:.1f}"
            cpu_area = cpu_pts.split(" ")
            area_pts = [first_pt] + cpu_area + [last_pt]
            cpu_area = " ".join(area_pts)
            mem_area = ""
            mem_pts_list = mem_pts.split(" ")
            area_pts2 = [first_pt] + mem_pts_list + [last_pt]
            mem_area = " ".join(area_pts2)

            svg_res = f"""<div style="overflow-x:auto;">
        <svg viewBox="0 0 {svg_w} {svg_h}" style="width:100%;max-width:{svg_w}px;">
          <rect x="0" y="0" width="{svg_w}" height="{svg_h}" fill="none"/>
          <!-- 网格 -->
          {y_ticks}
          <!-- 面积填充 -->
          <polygon points="{cpu_area}" fill="rgba(231,76,60,0.10)" />
          <polygon points="{mem_area}" fill="rgba(52,152,219,0.10)" />
          <!-- CPU 折线 -->
          <polyline points="{cpu_pts}" fill="none" stroke="#e74c3c" stroke-width="2.5" stroke-linejoin="round"/>
          <!-- MEM 折线 -->
          <polyline points="{mem_pts}" fill="none" stroke="#3498db" stroke-width="2.5" stroke-linejoin="round"/>
          <!-- X轴标签 -->
          <text x="{pad_l + cw // 2}" y="{svg_h - 2}" text-anchor="middle" font-size="11" fill="#999">时间 (秒)</text>
          <!-- 数据点（每3个标记一个圆） -->
          {''.join(f'<circle cx="{to_svg(v, i, n).split(",")[0]}" cy="{to_svg(v, i, n).split(",")[1]}" r="2.5" fill="#e74c3c"/>' for i, v in enumerate(state.cpu_samples) if i % max(1, n // 20) == 0)}
          {''.join(f'<circle cx="{to_svg(v, i, n).split(",")[0]}" cy="{to_svg(v, i, n).split(",")[1]}" r="2.5" fill="#3498db"/>' for i, v in enumerate(state.mem_samples) if i % max(1, n // 20) == 0)}
          <!-- 图例 -->
          <rect x="{pad_l}" y="8" width="12" height="12" fill="#e74c3c" rx="2"/>
          <text x="{pad_l + 16}" y="18" font-size="12" fill="#333">CPU</text>
          <rect x="{pad_l + 60}" y="8" width="12" height="12" fill="#3498db" rx="2"/>
          <text x="{pad_l + 76}" y="18" font-size="12" fill="#333">内存</text>
          <text x="{pad_l + 130}" y="18" font-size="11" fill="#666">| 峰值 CPU {max(state.cpu_samples):.1f}% / MEM {max(state.mem_samples):.1f}%</text>
        </svg></div>"""
        else:
            # 样本太少，退化为横条
            max_cpu = max(state.cpu_samples + [1])
            max_mem = max(state.mem_samples + [1])
            for i in range(len(state.cpu_samples)):
                cp = state.cpu_samples[i]
                mp = state.mem_samples[i]
                res_rows += f"""<tr><td>{i}</td>
                  <td><div class="mini-bar"><div class="bar-fill red" style="width:{cp / max_cpu * 100}%">{cp}%</div></div></td>
                  <td><div class="mini-bar"><div class="bar-fill blue" style="width:{mp / max_mem * 100}%">{mp}%</div></div></td></tr>"""
            svg_res = f"""<table><thead><tr><th>采样点</th><th>CPU%</th><th>MEM%</th></tr></thead><tbody>{res_rows}</tbody></table>"""

    html = f"""<!DOCTYPE html>
<html><head><meta charset='utf-8'>
<style>
  body{{font-family:sans-serif;padding:20px;background:#f5f5f5;color:#333;}}
  h1{{color:#2c3e50;border-bottom:2px solid #2c3e50;padding-bottom:8px;}}
  h2{{color:#34495e;margin-top:28px;}}
  .cards{{display:flex;flex-wrap:wrap;gap:12px;margin:12px 0;}}
  .card{{padding:14px 22px;border-radius:8px;min-width:110px;text-align:center;}}
  .card .num{{font-size:26px;font-weight:bold;}}
  .card .lbl{{font-size:12px;color:#666;margin-top:2px;}}
  .green{{background:#e8f5e9;}} .orange{{background:#fff3e0;}} .blue{{background:#e3f2fd;}} .purple{{background:#f3e5f5;}} .red{{background:#ffebee;}} .teal{{background:#e0f2f1;}}
  table{{border-collapse:collapse;width:100%;margin:8px 0;font-size:13px;}}
  th,td{{border:1px solid #ddd;padding:6px 10px;text-align:left;}}
  th{{background:#2c3e50;color:white;}}
  tr:nth-child(even){{background:#f8f9fa;}}
  .badge{{display:inline-block;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:bold;color:white;}}
  .badge.pass{{background:#27ae60;}} .badge.skip{{background:#f39c12;}} .badge.fail{{background:#e74c3c;}}
  .bar-row{{display:flex;align-items:center;margin:4px 0;gap:8px;font-size:13px;}}
  .bar-lbl{{width:90px;flex-shrink:0;text-align:right;}}
  .bar-track{{flex:1;height:22px;background:#ecf0f1;border-radius:4px;overflow:hidden;}}
  .bar-fill{{height:100%;border-radius:4px;line-height:22px;padding-left:4px;font-size:11px;color:white;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}}
  .bar-fill.red{{background:#e74c3c;}} .bar-fill.orange{{background:#f39c12;}} .bar-fill.blue{{background:#3498db;}} .bar-fill.teal{{background:#1abc9c;}}
  .bar-val{{width:70px;flex-shrink:0;font-weight:bold;}}
  .mini-bar{{height:22px;background:#ecf0f1;border-radius:4px;overflow:hidden;min-width:40px;}}
  .mini-bar .bar-fill{{font-size:10px;min-width:30px;}}
  .meta{{color:#666;font-size:13px;}}
  .section{{background:white;padding:14px 18px;border-radius:6px;margin:12px 0;box-shadow:0 1px 3px rgba(0,0,0,0.08);}}
</style></head><body>
<h1>万人同场·E2E仿真报告</h1>
<p class="meta">运行时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} | 模式: {args.mode} | 订单: {args.orders} | 验证: {args.verify}</p>

<div class="section">{cards_html}</div>

<div class="section"><h2>延迟</h2>{delay_bars}</div>

<div class="section"><h2>错误分布</h2>{err_bars}</div>

<div class="section"><h2>验证断言</h2><table><thead><tr><th>测试项</th><th>状态</th><th>HTTP</th><th>详情</th></tr></thead><tbody>{ver_rows}</tbody></table></div>

<div class="section"><h2>时间序列 (吞吐量 & 延迟)</h2><table><thead><tr><th>批次</th><th>耗时</th><th>吞吐量(req/s)</th><th>平均延迟</th></tr></thead><tbody>{ts_rows}</tbody></table></div>

<div class="section"><h2>库存前后对比 (减少={stock_delta_total}, 已购={stock_bought_total})</h2><table><thead><tr><th>活动</th><th>前(剩余)</th><th>后(剩余)</th><th>已购</th><th>变化</th><th>趋势</th></tr></thead><tbody>{stock_rows}</tbody></table></div>
"""

    if svg_res:
        html += f"""<div class="section"><h2>系统资源 (CPU / 内存)</h2>{svg_res}</div>"""

    html += "</body></html>"

    html_path = "stress_report.html"
    with open(html_path, "w") as f:
        f.write(html)
    print(f"  📄 HTML报告已保存: {html_path} (纯CSS, 无需JS/网络)")


# ==================== 主流程 ====================
async def main():
    print("=" * 60)
    print("  万人同场·绝对E2E仿真")
    print(f"  模式: {args.mode} | 订单: {args.orders} | "
          f"验证: {args.verify} | 清理: {args.cleanup}")
    print("=" * 60)

    check_deps()

    if not setup():
        print("  ❌ 初始化失败")
        sys.exit(1)

    # 启动资源采样器
    sampler_task = None
    if state.has_psutil:
        sampler_task = asyncio.create_task(resource_sampler(interval=1.0))

    # 库存快照（测试前）
    snapshot_stock(after=False)

    # 预热与注册并行：预热在后台线程跑，注册直接开始
    warmup_future = None
    if state._pending_warmup_activities:
        from concurrent.futures import ThreadPoolExecutor
        executor = ThreadPoolExecutor(max_workers=20)
        warmup_future = executor.submit(warm_up_activities, state._pending_warmup_activities)
        state._pending_warmup_activities = []
        print("  ⏳ 预热已提交后台线程，与用户注册并行执行")

    # 注册用户
    await register_users(args.orders)

    # 等待预热完成（注册比预热久得多，实际上不会等）
    if warmup_future:
        warmed = warmup_future.result()
        print(f"  ✅ 后台预热完成: {warmed}个活动")

    # 运行压测模式
    if args.mode == "seckill":
        await run_seckill()
    elif args.mode == "shopping":
        await run_shopping()
    elif args.mode == "mixed":
        await run_mixed()

    # 停止采样器
    if sampler_task:
        sampler_task.cancel()
        try:
            await sampler_task
        except asyncio.CancelledError:
            pass

    # 验证
    await run_verifications()

    # 库存快照（测试后）
    snapshot_stock(after=True)

    # 报告
    console_report()
    generate_report()

    # 清理
    cleanup()

    elapsed = time.time() - state.t_start
    print(f"\n  ✅ 全部完成! 总耗时: {elapsed:.1f}s\n")


if __name__ == "__main__":
    asyncio.run(main())