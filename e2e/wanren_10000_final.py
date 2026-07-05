#!/usr/bin/env python3
"""
直接数据库批量插入10000用户 + 批量登录拿token
绕过注册接口的BCrypt性能瓶颈

吕芳 呈皇上
"""
import asyncio, json, time, os, sys, random
import aiohttp, bcrypt, subprocess

BASE = "http://localhost:8080"

class State:
    def __init__(self):
        self.users = []  # [(token, userId), ...]

state = State()

# 预计算BCrypt hash（密码: w123456）
SALT = bcrypt.gensalt(rounds=4)  # 降低rounds加快速度，演示用足够了
PWD_HASH = bcrypt.hashpw(b"w123456", SALT).decode()
print(f"BCrypt hash: {PWD_HASH[:30]}... (rounds=4)")

# ==================== 批量插入用户 ====================
def batch_insert_users(count, ts_base):
    """生成SQL批量插入用户"""
    print(f"\n[1/3] 批量插入{count}个用户到数据库...", end=" ", flush=True)

    values = []
    for i in range(count):
        uname = f"bulk_{ts_base}_{i}"
        email = f"bulk_{ts_base}_{i}@x.com"
        # 用转义处理特殊字符
        values.append(f"('{uname}',NULL,'{PWD_HASH}','{email}',NULL,'CUSTOMER','ACTIVE',NOW(),NOW())")

    # 分批插入，每批500
    batch_size = 500
    total = 0
    for start in range(0, len(values), batch_size):
        batch = values[start:start+batch_size]
        sql = f"INSERT IGNORE INTO user (username,nickname,password,email,phone,role,status,created_at,updated_at) VALUES {','.join(batch)};"
        result = subprocess.run(
            ["mysql", "-u", "root", "flashsale", "-e", sql],
            capture_output=True, text=True, timeout=30
        )
        total += len(batch)
        if result.returncode != 0:
            print(f"  ❌ 插入失败: {result.stderr[:100]}")
            return total
        print(f"{total}", end=" ", flush=True)

    print(f"\n       共插入{total}个用户", flush=True)
    return total


# ==================== 批量登录 ====================
async def login_user(session, sem, username, password):
    """登录获取token"""
    async with sem:
        try:
            async with session.post(f"{BASE}/api/user/login",
                json={"username": username, "password": password},
                timeout=aiohttp.ClientTimeout(total=15)) as resp:
                j = await resp.json()
                if resp.status == 200 and j.get("code") == 0:
                    data = j.get("data", {})
                    tok = data.get("token", "")
                    uid = data.get("userId", 0)
                    if tok:
                        return (tok, uid)
                return None
        except:
            return None


async def batch_login(count, ts_base):
    """并发登录获取token"""
    print(f"\n[2/3] 批量登录{count}个用户拿token...", end=" ", flush=True)

    sem = asyncio.Semaphore(200)
    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    all_tokens = []

    async with aiohttp.ClientSession(connector=connector) as session:
        # 分批次登录
        batch_size = 2000
        for batch_start in range(0, count, batch_size):
            n = min(batch_size, count - batch_start)
            tasks = []
            for i in range(n):
                uname = f"bulk_{ts_base}_{batch_start + i}"
                tasks.append(asyncio.create_task(login_user(session, sem, uname, "w123456")))

            users = await asyncio.gather(*tasks)
            valid = [u for u in users if u]
            all_tokens.extend(valid)
            print(f"{len(all_tokens)}", end=" ", flush=True)

    state.users = all_tokens
    print(f"\n       登录成功{len(all_tokens)}人", flush=True)
    return len(all_tokens)


# ==================== 秒杀冲击 ====================
async def flash_one(session, sem, tok, uid, aids):
    """秒杀一个随机活动"""
    async with sem:
        aid = random.choice(aids)
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
                return {"status": status, "code": code, "aid": aid}
        except asyncio.TimeoutError:
            return {"status": 0, "code": -999, "aid": aid}
        except Exception as e:
            return {"status": 0, "code": -999, "aid": aid, "err": str(e)[:40]}


async def run_flash(activities):
    """秒杀冲击"""
    n_users = len(state.users)
    print(f"\n[3/3] 秒杀冲击 — {n_users}人 × 每人1次 = {n_users}请求")
    print(f"       {len(activities)}个活动 | 并发200")
    print(f"       进度: ", end="", flush=True)

    sem = asyncio.Semaphore(200)
    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    results = []

    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = []
        for tok, uid in state.users:
            tasks.append(asyncio.create_task(flash_one(session, sem, tok, uid, activities)))

        for i in range(0, len(tasks), 2000):
            batch = tasks[i:i+2000]
            batch_res = await asyncio.gather(*batch)
            results.extend(batch_res)
            print(f"{min(i+2000, len(tasks))}", end=" ", flush=True)

    print(flush=True)
    return results


# ==================== 统计 ====================
def report(results, activities, act_product, products):
    total = len(results)
    http_200 = sum(1 for r in results if r["status"] == 200)
    http_409 = sum(1 for r in results if r["status"] == 409)
    http_429 = sum(1 for r in results if r["status"] == 429)
    http_500 = sum(1 for r in results if r["status"] == 500)
    timed_out = sum(1 for r in results if r["status"] == 0)
    biz_ok = sum(1 for r in results if r.get("code") == 0)

    act_success = {}
    for r in results:
        if r.get("code") == 0 and r.get("aid"):
            act_success[r["aid"]] = act_success.get(r["aid"], 0) + 1

    elapsed = time.time() - t0

    print(f"\n  ┌──── 总览 ─────────────────────────────┐")
    print(f"  │ 总请求              {total:>8}")
    print(f"  │ 总耗时              {elapsed:>8.1f} 秒")
    print(f"  │ 吞吐                {total/max(elapsed,0.01):>8.0f} req/s")
    print(f"  │ HTTP 200            {http_200:>8}")
    print(f"  │ HTTP 429(限流)      {http_429:>8}")
    print(f"  │ HTTP 409(重复)      {http_409:>8}")
    print(f"  │ HTTP 500            {http_500:>8}")
    print(f"  │ 超时                {timed_out:>8}")
    print(f"  │ 业务成功(code=0)    {biz_ok:>8}")
    print(f"  └──────────────────────────────────────────┘")

    covered = sum(1 for aid in activities if act_success.get(aid, 0) > 0)
    n_acts = len(activities)

    print(f"\n  ┌──── 商品覆盖 ({covered}/{n_acts}) ─────────────────┐")
    for aid in activities:
        pid = act_product.get(aid, 0)
        nm = dict(products).get(pid, "?")[:14] if products else "?"
        cnt = act_success.get(aid, 0)
        mark = "✅" if cnt > 0 else "❌"
        bar = "█" * min(cnt // 5, 30)
        print(f"  │ {mark} {nm:<14} 成功{cnt:<5} {bar}")
    print(f"  └──────────────────────────────────────────┘")

    total_sold = 0
    for aid in activities:
        try:
            r = os.popen(f"redis-cli GET seckill:stock:{aid} 2>/dev/null").read().strip()
            if r: total_sold += (200 - int(r))
        except: pass

    print(f"\n  ┌──── 验算 ───────────────────────────────┐")
    print(f"  │ 库存总扣减:       {total_sold:>8}")
    print(f"  │ 业务成功数:       {biz_ok:>8}")
    match = "✅ 一致" if biz_ok == total_sold else f"❌ 差{abs(biz_ok-total_sold)}"
    print(f"  │ 校验:             {match}")
    print(f"  └──────────────────────────────────────────┘")

    if http_500 == 0 and covered == n_acts:
        print(f"\n  ✅ {total}单 · {covered}/{n_acts}品全覆盖 · 0错误 · 库存验算{match}")
    elif http_500 == 0:
        print(f"\n  ⚠️ 未全覆盖: {n_acts-covered}个品无人买")
    else:
        print(f"\n  ❌ {http_500}个500")


# ==================== 主流程 ====================
if __name__ == "__main__":
    print("=" * 70)
    print("  万人同场 · 实打实10000单（数据库直插版）")
    print(f"  {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)

    import requests as sync_requests
    
    def api(method, path, **kwargs):
        hdrs = kwargs.pop("headers", {})
        to = kwargs.pop("timeout", 10)
        if "token" in kwargs:
            hdrs["Authorization"] = f"Bearer {kwargs.pop('token')}"
        if method in ("POST","PUT","PATCH"):
            hdrs.setdefault("Content-Type","application/json")
        try:
            r = sync_requests.request(method, f"{BASE}{path}", headers=hdrs, timeout=to, **kwargs)
            return r.status_code, r.json() if r.text else {}
        except Exception as e:
            return 0, {"error": str(e)}

    # 1. Admin登录 + 准备活动
    c, d = api("POST", "/api/user/login", json={"username":"admin","password":"admin123"})
    admin_token = d["data"]["token"] if c == 200 else None
    if not admin_token: print("admin登录失败"); sys.exit(1)

    c, d = api("GET", "/api/product", params={"page":1,"size":200}, token=admin_token)
    all_products = d.get("data",{}).get("records",[])
    products = [(p["id"], p["name"]) for p in all_products[:50]]
    print(f"\n系统商品: {len(all_products)}种，取前{len(products)}种建活动")

    activities = []
    act_product = {}
    from datetime import datetime, timedelta
    now = datetime.now()
    for i, (pid, nm) in enumerate(products):
        c, d = api("POST", "/api/seckill/activity", json={
            "productId": pid, "seckillPrice": 9900, "totalStock": 200,
            "startTime": (now-timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%S"),
            "endTime": (now+timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S")
        }, token=admin_token)
        if c == 200 and d.get("code") == 0 and d.get("data"):
            aid = d["data"]
            activities.append(aid)
            act_product[aid] = pid
            api("PATCH", f"/api/seckill/activity/{aid}/status", json={"status":"PENDING"},
                token=admin_token, headers={"X-User-Role":"ADMIN"})
            api("POST", f"/api/seckill/activity/{aid}/warm-up", json={}, token=admin_token)
        if (i+1) % 10 == 0: print(f" 活动{i+1}", end=" ", flush=True)
    print(f"\n  {len(activities)}个活动预热OK")

    # 2. 直插数据库造用户
    t0 = time.time()
    ts_base = int(t0)  # 固定时间戳，插入和登录共用
    inserted = batch_insert_users(10000, ts_base)

    # 3. 批量登录
    n_logged = asyncio.run(batch_login(inserted, ts_base))
    if n_logged < 50:
        print(f"登录成功太少({n_logged})，中止"); sys.exit(1)

    # 4. 秒杀
    results = asyncio.run(run_flash(activities))

    # 5. 统计
    report(results, activities, act_product, products)
    print(f"\n  总耗时: {time.time()-t0:.1f}秒")
    print("=" * 70)
