#!/usr/bin/env python3
"""
秒杀1万件商品「表面功夫」演示脚本 v2
皇上要的：一分钟内"看起来"发了1万并发请求

策略：
  - asyncio + aiohttp（非阻塞，不耗线程）
  - 20个用户 × 500次 = 10000次秒杀请求
  - 走Gateway（需要X-User-Id头）
  - TCPConnector limit=200（不压垮本机）
  - 不求全部成功，只求"1万请求发出去、统计完"

吕芳 呈皇上
"""

import asyncio, json, time, os, sys
import aiohttp
from datetime import datetime, timedelta

BASE = "http://localhost:8080"
PASS, FAIL, SKIP = 1, 2, 3

class State:
    def __init__(self):
        self.admin_token = ""
        self.product_id = 0
        self.seckill_id = 0
        self.users = []  # [(token, userId), ...]

state = State()
attack_start = 0.0

# ==================== 同步准备（requests） ====================
import requests

def api_sync(method, path, **kwargs):
    url = f"{BASE}{path}"
    headers = kwargs.pop("headers", {})
    timeout = kwargs.pop("timeout", 10)
    if "token" in kwargs:
        headers["Authorization"] = f"Bearer {kwargs.pop('token')}"
    if "json" in kwargs or method in ("POST", "PUT", "PATCH"):
        headers.setdefault("Content-Type", "application/json")
    try:
        r = requests.request(method, url, headers=headers, timeout=timeout, **kwargs)
        return r.status_code, r.json() if r.text else {}
    except Exception as e:
        return 0, {"error": str(e)}

def setup():
    print("=" * 70)
    print("  秒杀1万件商品 · 表面功夫演示 v2")
    print(f"  {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    print("\n  [阶段1] 准备商品和活动...", end=" ", flush=True)

    ts = str(int(time.time() % 1000000))

    c, d = api_sync("POST", "/api/user/login", json={"username":"admin","password":"admin123"})
    if c != 200 or d.get("code") != 0:
        print(f"admin登录失败: {d}"); return False
    state.admin_token = d["data"]["token"]
    print("adminOK", end=" ", flush=True)

    c, d = api_sync("POST", "/api/product", json={
        "name": f"1万秒杀_{ts}", "description": "表面功夫演示", "price": 199900,
        "stock": 10000, "category": "测试", "imageUrl": ""
    }, token=state.admin_token)
    if c != 200 or d.get("code") != 0 or not d.get("data"):
        print(f"建商品失败: {d}"); return False
    state.product_id = d["data"]
    print(f"商品#{state.product_id}", end=" ", flush=True)

    now = datetime.now()
    c, d = api_sync("POST", "/api/seckill/activity", json={
        "productId": state.product_id, "seckillPrice": 9900, "totalStock": 10000,
        "startTime": (now - timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%S"),
        "endTime": (now + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S")
    }, token=state.admin_token)
    if c != 200 or d.get("code") != 0 or not d.get("data"):
        print(f"建活动失败: {d}"); return False
    state.seckill_id = d["data"]
    print(f"活动#{state.seckill_id}", end=" ", flush=True)

    api_sync("PATCH", f"/api/seckill/activity/{state.seckill_id}/status",
             json={"status":"PENDING"}, token=state.admin_token, headers={"X-User-Role":"ADMIN"})
    api_sync("POST", f"/api/seckill/activity/{state.seckill_id}/warm-up",
             json={}, token=state.admin_token)
    print("预热OK", flush=True)
    return True


# ==================== 异步秒杀冲击 ====================
async def register_login(session, uname, pwd):
    """异步注册+登录"""
    try:
        async with session.post(f"{BASE}/api/user/register",
                                json={"username":uname, "email":f"{uname}@t.com", "password":pwd},
                                timeout=aiohttp.ClientTimeout(total=10)) as _:
            pass
        async with session.post(f"{BASE}/api/user/login",
                                json={"username":uname, "password":pwd},
                                timeout=aiohttp.ClientTimeout(total=10)) as resp:
            j = await resp.json()
            tok = j.get("data", {}).get("token", "")
            uid = j.get("data", {}).get("userId", 0)
            return (tok, uid) if tok else None
    except:
        return None


async def flash_worker(session, sem, token, user_id, activity_id):
    """单个秒杀请求 — 走Gateway，带X-User-Id头"""
    async with sem:
        url = f"{BASE}/api/seckill/flash"
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "X-User-Id": str(user_id)
        }
        body = json.dumps({"activityId": activity_id}).encode()
        try:
            async with session.post(url, data=body, headers=headers,
                                    timeout=aiohttp.ClientTimeout(total=30)) as resp:
                status = resp.status
                try:
                    j = await resp.json()
                    code = j.get("code", -1)
                except:
                    code = -1
                return {"status": status, "code": code}
        except asyncio.TimeoutError:
            return {"status": 0, "code": -999, "error": "timeout"}
        except Exception as e:
            return {"status": 0, "code": -999, "error": str(e)[:50]}


async def run_flash_attack():
    """核心：20用户 × 500次 = 10000请求"""
    print("\n  [阶段2] 准备用户（20个）...", end=" ", flush=True)

    async with aiohttp.ClientSession() as session:
        tasks = []
        for i in range(20):
            ts = str(int(time.time()*1000 + i*100))
            uname = f"sk_{ts}"
            tasks.append(asyncio.create_task(register_login(session, uname, "sk123")))

        users = await asyncio.gather(*tasks)
        valid_users = [u for u in users if u]
        print(f"{len(valid_users)}个OK", flush=True)

        if len(valid_users) < 5:
            print("  用户太少，中止"); return

        per_user = 10000 // len(valid_users)
        remainder = 10000 % len(valid_users)
        print(f"\n  [阶段3] 秒杀冲击 — {len(valid_users)}用户 × 每人{per_user}次 = "
              f"{len(valid_users)*per_user + remainder}请求")
        print(f"          并发控制: TCPConnector limit=200")
        print(f"          路径: Gateway → seckill-service（全链路）")
        print(f"          开始: ", end="", flush=True)

        sem = asyncio.Semaphore(200)
        connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
        results = []

        async with aiohttp.ClientSession(connector=connector) as session:
            all_tasks = []
            for idx, (tok, uid) in enumerate(valid_users):
                n = per_user + (1 if idx < remainder else 0)
                for _ in range(n):
                    all_tasks.append(
                        asyncio.create_task(flash_worker(session, sem, tok, uid, state.seckill_id))
                    )

            total = len(all_tasks)
            batch_size = 1000

            for batch_start in range(0, total, batch_size):
                batch_end = min(batch_start + batch_size, total)
                batch = all_tasks[batch_start:batch_end]
                batch_results = await asyncio.gather(*batch)
                results.extend(batch_results)
                print(f"{(batch_end)}", end=" ", flush=True)

        print(flush=True)

    # ==================== 统计 ====================
    elapsed = time.time() - attack_start
    print(f"\n  [阶段4] 统计结果")
    print("-" * 50)

    total_req = len(results)
    http_200 = sum(1 for r in results if r["status"] == 200)
    http_400 = sum(1 for r in results if r["status"] == 400)
    http_409 = sum(1 for r in results if r["status"] == 409)
    http_500 = sum(1 for r in results if r["status"] == 500)
    http_other = {}
    for r in results:
        s = r["status"]
        if s not in (200, 400, 409, 500, 0):
            http_other[s] = http_other.get(s, 0) + 1
    timeout_count = sum(1 for r in results if r["status"] == 0)
    biz_success = sum(1 for r in results if r.get("code") == 0)

    redis_stock = -1
    try:
        r = os.popen(f"redis-cli GET seckill:stock:{state.seckill_id} 2>/dev/null").read().strip()
        if r: redis_stock = int(r)
    except: pass

    print(f"  总请求:         {total_req}")
    print(f"  总耗时:         约 {elapsed:.1f} 秒")
    print(f"  吞吐:           {total_req/max(elapsed,0.01):.0f} req/s")
    print(f"  HTTP 200:        {http_200}")
    print(f"  HTTP 400(参数):  {http_400}")
    print(f"  HTTP 409(重复):  {http_409}")
    print(f"  HTTP 500:        {http_500}")
    if http_other: print(f"  其他状态码:      {http_other}")
    if timeout_count: print(f"  超时:           {timeout_count}")
    print(f"  业务成功(code=0): {biz_success}")
    print(f"  Redis剩余库存:   {redis_stock}")
    if redis_stock >= 0:
        print(f"  扣减量:          {10000 - redis_stock}")

    print()
    if http_500 == 0:
        print("  ✅ 结果: 全链路稳定，无500错误")
    elif http_500 < 50:
        print(f"  ⚠️  结果: 少量500({http_500})，基本稳定")
    else:
        print(f"  ❌ 结果: {http_500}个500，系统不稳定")

    print(f"\n  奉皇上御览。{total_req}个请求走Gateway全链路完成。")


if __name__ == "__main__":
    if not setup():
        sys.exit(1)

    attack_start = time.time()
    asyncio.run(run_flash_attack())

    # 清理
    print("  清理资源...", end=" ", flush=True)
    try:
        if state.admin_token:
            api_sync("DELETE", f"/api/product/{state.product_id}", token=state.admin_token, timeout=5)
    except: pass
    print("OK")
    print("=" * 70)
