#!/usr/bin/env python3
"""
万人同场·秒杀+普通商品·50品全覆盖·10000单
皇上御准方案：
  限流: seckill-capacity=2000 (已调大)
  用户: 10000人 × 每人秒杀1次 = 10000单
  商品: 50种全部建秒杀活动，各库存200 = 总10000
  保底: 每种商品定向分配前200人中的第1号
  方式: Gateway全链路

吕芳 呈皇上
"""

import asyncio, json, time, os, sys, random
import aiohttp
from datetime import datetime, timedelta
import requests

BASE = "http://localhost:8080"

class State:
    def __init__(self):
        self.admin_token = ""
        self.products = []       # [(id, name), ...]
        self.activities = []     # [activity_id, ...]
        self.act_product = {}    # {activity_id: product_id}
        self.user_tokens = []    # [(token, userId), ...]
        self.user_count = 0

state = State()
t_start = 0.0

# ==================== 同步工具 ====================
def api(method, path, **kwargs):
    url = f"{BASE}{path}"
    hdrs = kwargs.pop("headers", {})
    to = kwargs.pop("timeout", 10)
    if "token" in kwargs:
        hdrs["Authorization"] = f"Bearer {kwargs.pop('token')}"
    if method in ("POST","PUT","PATCH"):
        hdrs.setdefault("Content-Type","application/json")
    try:
        r = requests.request(method, url, headers=hdrs, timeout=to, **kwargs)
        return r.status_code, r.json() if r.text else {}
    except Exception as e:
        return 0, {"error": str(e)}

# ==================== 阶段1：准备 ====================
def setup():
    print("=" * 70)
    print("  万人同场 · 50品全覆盖 · 10000单")
    print(f"  {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"  限流: seckill-capacity=2000 | 用户: 10000 | 商品: 50×200 | 全随机")
    print("=" * 70)
    print("\n  [1/5] 准备50种商品和秒杀活动...", end=" ", flush=True)

    c, d = api("POST", "/api/user/login", json={"username":"admin","password":"admin123"})
    if c != 200: print("admin登录失败"); return False
    state.admin_token = d["data"]["token"]
    print("admin", end=" ", flush=True)

    # 获取现有的50种商品
    c, d = api("GET", "/api/product", params={"page":1,"size":200}, token=state.admin_token)
    products = d.get("data",{}).get("records",[])
    print(f"系统有{len(products)}种商品", end=" ", flush=True)

    if len(products) < 50:
        print(f"商品不足50"); return False

    # 取前50种，每个建秒杀活动
    for i, p in enumerate(products[:50]):
        pid = p["id"]
        name = p["name"]
        state.products.append((pid, name))

        now = datetime.now()
        c, d = api("POST", "/api/seckill/activity", json={
            "productId": pid, "seckillPrice": 9900, "totalStock": 200,
            "startTime": (now - timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%S"),
            "endTime": (now + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S")
        }, token=state.admin_token)
        if c != 200 or d.get("code") != 0 or not d.get("data"):
            print(f"  ❌ 建活动#{i}失败: {d.get('message','')}"); continue
        aid = d["data"]
        state.activities.append(aid)
        state.act_product[aid] = pid

        # 上架+预热
        api("PATCH", f"/api/seckill/activity/{aid}/status", json={"status":"PENDING"},
            token=state.admin_token, headers={"X-User-Role":"ADMIN"})
        api("POST", f"/api/seckill/activity/{aid}/warm-up", json={}, token=state.admin_token)

        if (i+1) % 10 == 0:
            print(f"{i+1}", end=" ", flush=True)

    print(f"\n       共{len(state.activities)}个活动预热OK", flush=True)
    return True


# ==================== 阶段2：注册用户 ====================
async def reg_login(session, uname, pwd):
    try:
        await session.post(f"{BASE}/api/user/register",
            json={"username":uname,"email":f"{uname}@x.com","password":pwd},
            timeout=aiohttp.ClientTimeout(total=10))
        async with session.post(f"{BASE}/api/user/login",
            json={"username":uname,"password":pwd},
            timeout=aiohttp.ClientTimeout(total=10)) as r:
            j = await r.json()
            tok = j.get("data",{}).get("token","")
            uid = j.get("data",{}).get("userId",0)
            return (tok, uid) if tok else None
    except:
        return None


async def register_users(count):
    """并发注册N个用户"""
    print(f"\n  [2/5] 注册{count}个用户...", end=" ", flush=True)
    batch_size = 500  # 分批次注册
    all_users = []

    async with aiohttp.ClientSession(connector=aiohttp.TCPConnector(limit=100)) as session:
        for batch_start in range(0, count, batch_size):
            n = min(batch_size, count - batch_start)
            ts_base = int(time.time() * 1000)
            tasks = []
            for i in range(n):
                uname = f"w_{ts_base + i + batch_start}"
                tasks.append(asyncio.create_task(reg_login(session, uname, "w123456")))
            users = await asyncio.gather(*tasks)
            all_users.extend([u for u in users if u])
            print(f"{len(all_users)}", end=" ", flush=True)

    state.user_tokens = all_users
    state.user_count = len(all_users)
    print(f"\n       实际注册成功{len(all_users)}人", flush=True)
    return len(all_users)


# ==================== 阶段3：秒杀冲击 ====================
async def flash_one(session, sem, tok, uid, aid):
    """单个秒杀请求"""
    async with sem:
        headers = {
            "Authorization": f"Bearer {tok}",
            "Content-Type": "application/json",
            "X-User-Id": str(uid)
        }
        body = json.dumps({"activityId": aid}).encode()
        try:
            async with session.post(f"{BASE}/api/seckill/flash",
                data=body, headers=headers,
                timeout=aiohttp.ClientTimeout(total=30)) as resp:
                status = resp.status
                try:
                    j = await resp.json()
                    code = j.get("code", -1)
                except:
                    code = -1
                return {"status": status, "code": code, "aid": aid, "uid": uid}
        except asyncio.TimeoutError:
            return {"status": 0, "code": -999, "aid": aid, "uid": uid}
        except Exception as e:
            return {"status": 0, "code": -999, "aid": aid, "uid": uid, "err": str(e)[:40]}


async def run_attack():
    """10000人 × 每人1次秒杀，随机选商品，保底每种至少1单"""
    
    # 构造用户→商品的分配
    n_users = len(state.user_tokens)
    n_acts = len(state.activities)
    
    # 保底：每种商品至少1人
    if n_users >= n_acts:
        allocation = []
        for i in range(n_users):
            if i < n_acts:
                aid = state.activities[i]  # 前50人各分配不同商品（保底）
            else:
                aid = random.choice(state.activities)  # 剩余用户随机
            allocation.append(aid)
    else:
        # 用户比商品少 → 每人随机
        allocation = [random.choice(state.activities) for _ in range(n_users)]

    # 打乱顺序（让保底用户也随机出现）
    random.shuffle(allocation)
    
    print(f"\n  [3/5] 秒杀冲击 — {n_users}人 × 每人1次 = {n_users}请求")
    print(f"        {n_acts}个活动 | 每种保底至少1人 | 并发200")
    print(f"        进度: ", end="", flush=True)

    sem = asyncio.Semaphore(200)
    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    results = []

    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = []
        for idx, (tok, uid) in enumerate(state.user_tokens):
            aid = allocation[idx]
            tasks.append(asyncio.create_task(flash_one(session, sem, tok, uid, aid)))

        for i in range(0, len(tasks), 1000):
            batch = tasks[i:i+1000]
            batch_res = await asyncio.gather(*batch)
            results.extend(batch_res)
            print(f"{min(i+1000, len(tasks))}", end=" ", flush=True)

    print(flush=True)
    return results


# ==================== 阶段4：统计 ====================
def report(results):
    print(f"\n  [4/5] 统计结果")
    print("-" * 60)

    total = len(results)
    http_200 = sum(1 for r in results if r["status"] == 200)
    http_409 = sum(1 for r in results if r["status"] == 409)
    http_429 = sum(1 for r in results if r["status"] == 429)
    http_500 = sum(1 for r in results if r["status"] == 500)
    http_400 = sum(1 for r in results if r["status"] == 400)
    timed_out = sum(1 for r in results if r["status"] == 0)
    biz_ok = sum(1 for r in results if r.get("code") == 0)

    # 按商品统计成功数
    act_success = {}
    for r in results:
        if r.get("code") == 0 and r.get("aid"):
            act_success[r["aid"]] = act_success.get(r["aid"], 0) + 1

    # 按用户统计
    user_success_map = {}
    for r in results:
        if r.get("code") == 0 and r.get("uid"):
            uid = r["uid"]
            user_success_map[uid] = user_success_map.get(uid, 0) + 1

    elapsed = time.time() - t_start

    print(f"\n  ┌──── 总览 (限流已调至2000) ─────────────┐")
    print(f"  │ 总请求              {total:>8}")
    print(f"  │ 总耗时              {elapsed:>8.1f} 秒")
    print(f"  │ 吞吐                {total/max(elapsed,0.01):>8.0f} req/s")
    print(f"  │ HTTP 200            {http_200:>8}")
    print(f"  │ HTTP 429(限流)      {http_429:>8}")
    print(f"  │ HTTP 409(重复)      {http_409:>8}")
    print(f"  │ HTTP 400(参数)      {http_400:>8}")
    print(f"  │ HTTP 500            {http_500:>8}")
    print(f"  │ 超时                {timed_out:>8}")
    print(f"  │ 业务成功(code=0)    {biz_ok:>8}")
    print(f"  └──────────────────────────────────────────┘")

    # 商品覆盖统计
    covered = sum(1 for aid in state.activities if act_success.get(aid, 0) > 0)
    uncovered_acts = [aid for aid in state.activities if act_success.get(aid, 0) == 0]

    print(f"\n  ┌──── 商品覆盖 ({covered}/{n_acts}) ─────────────────────┐")
    for aid in state.activities:
        pid = state.act_product[aid]
        nm = ""
        for p in state.products:
            if p[0] == pid: nm = p[1][:12]; break
        cnt = act_success.get(aid, 0)
        # 查Redis库存
        stock = -1
        try:
            r = os.popen(f"redis-cli GET seckill:stock:{aid} 2>/dev/null").read().strip()
            if r: stock = int(r)
        except: pass
        sold = 200 - stock if stock >= 0 else -1
        mark = "✅" if cnt > 0 else "❌"
        bar = "█" * min(cnt, 20)
        print(f"  │ {mark} {nm:<12} 成功{cnt:<4} 库存{sold if sold>=0 else '??':<4} {bar}")
    print(f"  └──────────────────────────────────────────┘")

    # 库存验算
    total_sold = 0
    for aid in state.activities:
        try:
            r = os.popen(f"redis-cli GET seckill:stock:{aid} 2>/dev/null").read().strip()
            if r: total_sold += (200 - int(r))
        except: pass

    print(f"\n  ┌──── 验算 ───────────────────────────────┐")
    print(f"  │ 库存总扣减:       {total_sold:>8}")
    print(f"  │ 业务成功数:       {biz_ok:>8}")
    match = "✅ 一致" if biz_ok == total_sold else f"❌ 不一致(差{abs(biz_ok-total_sold)})"
    print(f"  │ 校验:             {match:>8}")
    print(f"  └──────────────────────────────────────────┘")

    print()
    if http_500 == 0 and covered == n_acts:
        print(f"  ✅ 大获全胜: 0错误 + {covered}/{n_acts}商品全覆盖")
    elif http_500 == 0:
        print(f"  ⚠️ 未全覆盖: {n_acts-covered}个商品无人购买")
    else:
        print(f"  ❌ {http_500}个500")

    print(f"\n  总计: {elapsed:.1f} 秒")


# ==================== 主流程 ====================
if __name__ == "__main__":
    if not setup():
        sys.exit(1)

    t_start = time.time()

    # 注册用户
    n_users = asyncio.run(register_users(10000))
    if n_users < 50:
        print(f"用户数太少({n_users})，中止")
        sys.exit(1)

    # 秒杀冲击
    results = asyncio.run(run_attack())
    n_acts = len(state.activities)

    # 统计
    report(results)

    # 清理活动（不清商品，保留50种正经商品）
    print("\n  [5/5] 清理秒杀活动...", end=" ", flush=True)
    for aid in state.activities:
        try:
            api("DELETE", f"/api/seckill/activity/{aid}", token=state.admin_token, timeout=5)
        except: pass
    print(f"{len(state.activities)}个活动已清理")
    print("=" * 70)
