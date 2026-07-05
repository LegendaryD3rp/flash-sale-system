#!/usr/bin/env python3
"""精确计时版 — 万人同场10000单"""
import asyncio, json, time, os, sys, random, bcrypt, subprocess
import aiohttp
from datetime import datetime, timedelta
import requests as sync_req

BASE = "http://localhost:8080"

def api(method, path, **kwargs):
    url = f"{BASE}{path}"
    hdrs = kwargs.pop("headers", {})
    to = kwargs.pop("timeout", 10)
    if "token" in kwargs: hdrs["Authorization"] = f"Bearer {kwargs.pop('token')}"
    if method in ("POST","PUT","PATCH"): hdrs.setdefault("Content-Type","application/json")
    try:
        r = sync_req.request(method, url, headers=hdrs, timeout=to, **kwargs)
        return r.status_code, r.json() if r.text else {}
    except: return 0, {}

T = {}  # timings
def mark(name):
    T[name] = time.time()

print("=" * 70)
print("  万人同场·10000单 · 精确计时版")
print(f"  {time.strftime('%Y-%m-%d %H:%M:%S')}")
print("=" * 70)

mark("start")

# === 1. 清数据 ===
print("\n[清理旧数据]...", end=" ", flush=True)
subprocess.run(["mysql","-u","root","flashsale","-e",
    "DELETE FROM user WHERE username LIKE 'bulk_%';"], capture_output=True)
subprocess.run(["mysql","-u","root","flashsale","-e",
    "DELETE FROM seckill_activity;"], capture_output=True)
print("OK", flush=True)

# === 2. 建活动 ===
mark("setup_start")
print("[1] 建50个秒杀活动...", end=" ", flush=True)
c, d = api("POST", "/api/user/login", json={"username":"admin","password":"admin123"})
admin_token = d["data"]["token"]

c, d = api("GET", "/api/product", params={"page":1,"size":200}, token=admin_token)
products = [(p["id"], p["name"]) for p in d.get("data",{}).get("records",[])[:50]]

activities, act_product = [], {}
now = datetime.now()
for pid, nm in products:
    c, d = api("POST", "/api/seckill/activity", json={
        "productId":pid,"seckillPrice":9900,"totalStock":200,
        "startTime":(now-timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%S"),
        "endTime":(now+timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S")
    }, token=admin_token)
    if c == 200 and d.get("code") == 0 and d.get("data"):
        aid = d["data"]; activities.append(aid); act_product[aid] = pid
        api("PATCH", f"/api/seckill/activity/{aid}/status", json={"status":"PENDING"},
            token=admin_token, headers={"X-User-Role":"ADMIN"})
        api("POST", f"/api/seckill/activity/{aid}/warm-up", json={}, token=admin_token)
mark("setup_end")
print(f"{len(activities)}个 ✅ ({T['setup_end']-T['setup_start']:.1f}s)")

# === 3. 批量插入用户 ===
mark("insert_start")
print("[2] 批量插入10000用户...", end=" ", flush=True)
ts_base = int(time.time())
pwd_hash = bcrypt.hashpw(b"w123456", bcrypt.gensalt(rounds=4)).decode()
vals = [f"('bulk_{ts_base}_{i}',NULL,'{pwd_hash}','bulk_{ts_base}_{i}@x.com',NULL,'CUSTOMER','ACTIVE',NOW(),NOW())"
        for i in range(10000)]
for start in range(0, 10000, 500):
    batch = vals[start:start+500]
    subprocess.run(["mysql","-u","root","flashsale","-e",
        f"INSERT IGNORE INTO user (username,nickname,password,email,phone,role,status,created_at,updated_at) VALUES {','.join(batch)};"],
        capture_output=True, timeout=30)
mark("insert_end")
print(f"✅ ({T['insert_end']-T['insert_start']:.1f}s)")

# === 4. 批量登录 ===
mark("login_start")
print("[3] 批量登录10000用户拿token...", end=" ", flush=True)
async def login_all():
    sem = asyncio.Semaphore(200)
    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    users = []
    async with aiohttp.ClientSession(connector=connector) as session:
        for batch_start in range(0, 10000, 2000):
            tasks = []
            for i in range(2000):
                uname = f"bulk_{ts_base}_{batch_start + i}"
                tasks.append(asyncio.create_task(_login(session, sem, uname)))
            batch_users = await asyncio.gather(*tasks)
            users.extend([u for u in batch_users if u])
    return users

async def _login(session, sem, uname):
    async with sem:
        try:
            async with session.post(f"{BASE}/api/user/login",
                json={"username":uname,"password":"w123456"},
                timeout=aiohttp.ClientTimeout(total=15)) as r:
                j = await r.json()
                if r.status == 200 and j.get("code") == 0:
                    d = j.get("data",{}); tok = d.get("token",""); uid = d.get("userId",0)
                    return (tok,uid) if tok else None
        except: return None

users = asyncio.run(login_all())
mark("login_end")
print(f"{len(users)}人 ✅ ({T['login_end']-T['login_start']:.1f}s)")

if len(users) < 100: print("用户太少"); sys.exit(1)

# === 5. 秒杀冲击 ===
mark("attack_start")
print("[4] 秒杀冲击 — 10000人×每人1次...", end=" ", flush=True)
async def attack_all():
    sem = asyncio.Semaphore(200)
    connector = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    results = []
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = []
        for tok, uid in users:
            aid = random.choice(activities)
            headers = {"Authorization":f"Bearer {tok}","Content-Type":"application/json","X-User-Id":str(uid)}
            tasks.append(asyncio.create_task(_flash(session, sem, headers, aid)))
        for i in range(0, 10000, 2000):
            batch = await asyncio.gather(*tasks[i:i+2000])
            results.extend(batch)
    return results

async def _flash(session, sem, headers, aid):
    async with sem:
        try:
            async with session.post(f"{BASE}/api/seckill/flash", headers=headers,
                data=json.dumps({"activityId":aid}).encode(),
                timeout=aiohttp.ClientTimeout(total=30)) as r:
                status = r.status
                try: code = (await r.json()).get("code", -1)
                except: code = -1
                return {"status":status, "code":code}
        except: return {"status":0, "code":-999}

results = asyncio.run(attack_all())
mark("attack_end")
print(f"✅ ({T['attack_end']-T['attack_start']:.1f}s)")

# === 6. 统计 ===
mark("report_start")
total = len(results)
code0 = sum(1 for r in results if r.get("code") == 0)
s500 = sum(1 for r in results if r["status"] == 500)
s429 = sum(1 for r in results if r["status"] == 429)
s409 = sum(1 for r in results if r["status"] == 409)
t_out = sum(1 for r in results if r["status"] == 0)

# 库存验算
total_sold = 0
for aid in activities:
    try:
        r = os.popen(f"redis-cli GET seckill:stock:{aid} 2>/dev/null").read().strip()
        if r: total_sold += (200 - int(r))
    except: pass

total_time = time.time() - T["start"]
attack_time = T["attack_end"] - T["attack_start"]
print(f"\n{'='*60}")
print(f"  SITREP — 万人同场·实打实10000单")
print(f"{'='*60}")
print(f"  全流程总耗时:    {total_time:>6.1f} 秒")
print(f"  ├─ 清数据:       {T['setup_start']-T['start']:>6.1f} 秒")
print(f"  ├─ 建活动:       {T['setup_end']-T['setup_start']:>6.1f} 秒")
print(f"  ├─ 批量插用户:   {T['insert_end']-T['insert_start']:>6.1f} 秒")
print(f"  ├─ 批量登录:     {T['login_end']-T['login_start']:>6.1f} 秒")
print(f"  ├─ 秒杀并发:     {attack_time:>6.1f} 秒 ← 皇上问的这个")
print(f"  └─ 统计:         {time.time()-T['report_start']:>6.1f} 秒")
print(f"")
print(f"  秒杀并发详情:")
print(f"    请求总数:     {total:>6}")
print(f"    业务成功:     {code0:>6} 单")
print(f"    吞吐:         {total/max(attack_time,0.01):>6.0f} req/s")
print(f"    HTTP 500:     {s500:>6}")
print(f"    HTTP 429:     {s429:>6}")
print(f"    HTTP 409:     {s409:>6}")
print(f"    超时:         {t_out:>6}")
print(f"    库存验算:     {'✅ 一致' if code0 == total_sold else '❌ 不一致'}")
print(f"{'='*60}")
