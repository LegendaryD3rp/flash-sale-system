#!/usr/bin/env python3
"""
e2e_ten_thousand_full.py — 全流程万人同场·真E2E（优化版）

优化点：
  ① 注册阶段用低并发（50），避免BCrypt压爆4核CPU
  ② 登录后阶段切200并发跑剩余步骤
  ③ 每个阶段单独计时，精准定位瓶颈

流程（100% HTTP，不绕数据库）:
  注册 → 登录 → 建地址 → 加购物车 → 普通下单 → 支付 → 秒杀 → 轮询
"""

import asyncio, json, time, sys, random, subprocess
import aiohttp
from datetime import datetime, timedelta
import requests as sync_req

BASE = "http://localhost:8080"

# ─── 工具 ──────────────────────────────────────────────────

def sync_api(method, path, **kw):
    url = f"{BASE}{path}"
    hdrs = kw.pop("headers", {})
    if "token" in kw: hdrs["Authorization"] = f"Bearer {kw.pop('token')}"
    uid = kw.pop("uid", None)
    if uid is not None: hdrs["X-User-Id"] = str(uid)
    role = kw.pop("role", None)
    if role: hdrs["X-User-Role"] = role
    hdrs.setdefault("Content-Type", "application/json")
    try:
        r = sync_req.request(method, url, headers=hdrs, timeout=kw.pop("timeout",15), **kw)
        return r.status_code, r.json() if r.text else {}, r.text
    except: return 0, {}, ""

# ─── 定时 ──────────────────────────────────────────────────

T = {}
def mark(name): T[name] = time.time()
def elapsed(name): return T.get(name, time.time()) - T.get(f"{name}_start", T.get("start", time.time()))

# ═══════════════════════════════════════════════════════════
#  主流程
# ═══════════════════════════════════════════════════════════

async def main():
    print("=" * 70)
    print("  全流程万人同场 · 真E2E（优化版）")
    print(f"  {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    mark("start")

    # ─── 管理员登录 ──────────────────────
    c, d, _ = sync_api("POST", "/api/user/login",
        json={"username":"admin","password":"admin123"})
    if c != 200 or d.get("code") != 0: print("❌ 管理员登录失败"); sys.exit(1)
    ADMIN_TOKEN, ADMIN_UID = d["data"]["token"], d["data"]["userId"]

    # ─── 清理 ────────────────────────────
    subprocess.run(["mysql","-u","root","flashsale","-e",
        "DELETE FROM user WHERE username LIKE 'e2efull_%';"], capture_output=True)
    subprocess.run(["mysql","-u","root","flashsale","-e",
        "DELETE FROM seckill_activity;"], capture_output=True)
    subprocess.run(["mysql","-u","root","flashsale","-e",
        "DELETE FROM `order`;"], capture_output=True)
    subprocess.run(["mysql","-u","root","flashsale","-e",
        "DELETE FROM cart;"], capture_output=True)
    subprocess.run(["mysql","-u","root","flashsale","-e",
        "DELETE FROM address;"], capture_output=True)
    for pid in range(1, 51):
        subprocess.run(["mysql","-u","root","flashsale","-e",
            f"UPDATE product SET stock=450 WHERE id={pid};"], capture_output=True)

    # ─── 获取商品 ────────────────────────
    c, d, _ = sync_api("GET", "/api/product", params={"page":1,"size":200}, token=ADMIN_TOKEN)
    products = d.get("data",{}).get("records",[])[:50]
    if len(products) < 50:
        out = subprocess.run(["mysql","-u","root","flashsale","-N","-e",
            "SELECT id,name FROM product WHERE id<=50 ORDER BY id;"],
            capture_output=True,text=True)
        products = [{"id":int(p),"name":n} for p,n in
                    [l.split("\t") for l in out.stdout.strip().split("\n") if l]]
    print(f"[商品] {len(products)}个 ✅")

    # ─── 建秒杀活动 ──────────────────────
    now = datetime.now()
    activities, act_map = [], {}
    for p in products:
        c, d, _ = sync_api("POST", "/api/seckill/activity", json={
            "productId":p["id"],"seckillPrice":9900,"totalStock":200,
            "startTime":(now-timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%S"),
            "endTime":(now+timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%S")}, token=ADMIN_TOKEN)
        if c==200 and d.get("code")==0 and d.get("data"):
            aid = d["data"]; activities.append(aid); act_map[aid] = p["id"]
            sync_api("PATCH", f"/api/seckill/activity/{aid}/status",
                json={"status":"PENDING"}, token=ADMIN_TOKEN,
                headers={"X-User-Role":"ADMIN"})
            sync_api("POST", f"/api/seckill/activity/{aid}/warm-up",
                json={}, token=ADMIN_TOKEN)
    print(f"[活动] {len(activities)}个 ✅")

    # ═══════════════════════════════════════════════════════
    #  阶段一：注册 + 登录（低并发50）
    # ═══════════════════════════════════════════════════════

    TS = int(time.time())
    REG_CONCURRENCY = 50
    mark("reg_login_start")
    auth_results = [None] * 10000

    async def register_and_login(session, sem, idx):
        async with sem:
            username = f"e2efull_{TS}_{idx}"
            password = "w123456"
            try:
                async with session.post(f"{BASE}/api/user/register",
                    json={"username":username,"password":password,"role":"CUSTOMER"},
                    timeout=aiohttp.ClientTimeout(total=30)) as r:
                    if r.status!=200 or (await r.json()).get("code")!=0:
                        return idx, None
            except: return idx, None
            try:
                async with session.post(f"{BASE}/api/user/login",
                    json={"username":username,"password":password},
                    timeout=aiohttp.ClientTimeout(total=15)) as r:
                    if r.status==200:
                        j = await r.json()
                        if j.get("code")==0:
                            return idx, (j["data"]["token"], j["data"]["userId"], username)
            except: pass
            return idx, None

    connector_p1 = aiohttp.TCPConnector(limit=REG_CONCURRENCY, limit_per_host=REG_CONCURRENCY)
    sem_p1 = asyncio.Semaphore(REG_CONCURRENCY)
    async with aiohttp.ClientSession(connector=connector_p1) as session:
        tasks = [asyncio.create_task(register_and_login(session, sem_p1, i))
                 for i in range(10000)]
        for batch_start in range(0, 10000, 2000):
            batch = await asyncio.gather(*tasks[batch_start:batch_start+2000])
            for idx, val in batch:
                auth_results[idx] = val
            done = min(batch_start+2000, 10000)
            ok = sum(1 for r in auth_results[:done] if r is not None)
            print(f"  注册登录: {done}/10000 ({elapsed('reg_login_start'):.1f}s, {ok}人成功)")

    mark("reg_login_end")
    auth_ok = [r for r in auth_results if r is not None]
    print(f"[阶段一] 注册+登录: {len(auth_ok)}/10000 ✅ ({elapsed('reg_login_end'):.1f}s)")

    if len(auth_ok) < 9000:
        print(f"❌ 注册成功不足: {len(auth_ok)}")
        sys.exit(1)

    # ═══════════════════════════════════════════════════════
    #  阶段二：建地址→加购→下单→支付→秒杀→轮询（200并发）
    # ═══════════════════════════════════════════════════════

    mark("flow_start")
    stats = {"total":0,"address":0,"cart":0,"order":0,"pay":0,
             "flash":0,"poll":0,"paid":0,"flash_ok":0,
             "err_addr":0,"err_cart":0,"err_order":0,"err_pay":0,
             "err_flash":0,"err_poll":0,"http500":0,"http429":0,"timeout":0}

    async def full_flow(session, sem, token, uid, username, idx, product, activity_id):
        nonlocal stats
        async with sem:
            headers = {"Authorization":f"Bearer {token}","Content-Type":"application/json",
                       "X-User-Id":str(uid)}
            to = aiohttp.ClientTimeout(total=30)

            # 建地址
            addr_body = {"receiverName":"测试","receiverPhone":"13800138000",
                         "province":"北京","city":"北京","district":"朝阳",
                         "detailAddress":f"街{idx}号","isDefault":1}
            address_id = None
            try:
                async with session.post(f"{BASE}/api/address",
                    json=addr_body, headers=headers, timeout=to) as r:
                    if r.status==200:
                        j=await r.json()
                        if j.get("code")==0:
                            d=j.get("data",{})
                            address_id=d.get("id") if isinstance(d,dict) else d
                            stats["address"]+=1
                    elif r.status>=500: stats["http500"]+=1; stats["err_addr"]+=1
                    else: stats["err_addr"]+=1
            except asyncio.TimeoutError: stats["timeout"]+=1; stats["err_addr"]+=1
            except: stats["err_addr"]+=1
            if not address_id: return

            # 加购
            cart_id = None
            try:
                async with session.post(f"{BASE}/api/cart/add",
                    json={"productId":product["id"],"quantity":1},
                    headers=headers, timeout=to) as r:
                    if r.status==200:
                        j=await r.json()
                        if j.get("code")==0:
                            d=j.get("data",{})
                            cart_id=d.get("id") if isinstance(d,dict) else d
                            stats["cart"]+=1
                    elif r.status>=500: stats["http500"]+=1; stats["err_cart"]+=1
                    else: stats["err_cart"]+=1
            except asyncio.TimeoutError: stats["timeout"]+=1; stats["err_cart"]+=1
            except: stats["err_cart"]+=1
            if not cart_id: return

            # 下单
            order_id = None
            try:
                async with session.post(f"{BASE}/api/order/create",
                    json={"cartIds":[cart_id],"addressId":address_id},
                    headers=headers, timeout=to) as r:
                    if r.status==200:
                        j=await r.json()
                        if j.get("code")==0:
                            ids=j.get("data",[])
                            order_id=ids[0] if isinstance(ids,list) and ids else ids
                            stats["order"]+=1
                    elif r.status>=500: stats["http500"]+=1; stats["err_order"]+=1
                    else: stats["err_order"]+=1
            except asyncio.TimeoutError: stats["timeout"]+=1; stats["err_order"]+=1
            except: stats["err_order"]+=1
            if not order_id: return

            # 支付
            try:
                async with session.post(f"{BASE}/api/order/{order_id}/pay",
                    headers=headers, timeout=to) as r:
                    if r.status==200:
                        j=await r.json()
                        if j.get("code")==0:
                            stats["pay"]+=1; stats["paid"]+=1
                    elif r.status>=500: stats["http500"]+=1; stats["err_pay"]+=1
                    else: stats["err_pay"]+=1
            except asyncio.TimeoutError: stats["timeout"]+=1; stats["err_pay"]+=1
            except: stats["err_pay"]+=1

            # 秒杀
            try:
                async with session.post(f"{BASE}/api/seckill/flash",
                    json={"activityId":activity_id},
                    headers=headers, timeout=to) as r:
                    if r.status==200:
                        j=await r.json()
                        if j.get("code")==0: stats["flash_ok"]+=1
                        stats["flash"]+=1
                    elif r.status==429: stats["http429"]+=1
                    elif r.status>=500: stats["http500"]+=1; stats["err_flash"]+=1
                    else: stats["err_flash"]+=1
            except asyncio.TimeoutError: stats["timeout"]+=1; stats["err_flash"]+=1
            except: stats["err_flash"]+=1

            # 轮询秒杀订单
            try:
                for _ in range(20):
                    await asyncio.sleep(0.3)
                    async with session.get(f"{BASE}/api/order/list",
                        params={"page":1,"size":50},
                        headers=headers, timeout=to) as r:
                        if r.status==200:
                            j=await r.json()
                            if j.get("code")==0:
                                records=j.get("data",{}).get("records",[])
                                if any(o.get("seckillActivityId")==activity_id for o in records):
                                    stats["poll"]+=1
                                    break
            except: pass
            stats["total"]+=1

    connector_p2 = aiohttp.TCPConnector(limit=200, limit_per_host=200)
    sem_p2 = asyncio.Semaphore(200)
    async with aiohttp.ClientSession(connector=connector_p2) as session:
        tasks = []
        for idx, (token, uid, username) in enumerate(auth_ok):
            p = products[idx % 50]
            act = activities[idx % 50]
            tasks.append(asyncio.create_task(
                full_flow(session, sem_p2, token, uid, username, idx, p, act)))
        for batch_start in range(0, len(auth_ok), 2000):
            await asyncio.gather(*tasks[batch_start:batch_start+2000])
            done = min(batch_start+2000, len(auth_ok))
            print(f"  后续流程: {done}/{len(auth_ok)} ({elapsed('flow_start'):.1f}s, 支付{stats['paid']})")

    mark("flow_end")

    # ═══════════════════════════════════════════════════════
    #  库存验算
    # ═══════════════════════════════════════════════════════
    total_flash_sold = 0
    for aid in activities:
        try:
            r = subprocess.run(["redis-cli","GET",f"seckill:stock:{aid}"],
                               capture_output=True,text=True,timeout=5)
            if r.stdout.strip():
                total_flash_sold += (200 - int(r.stdout.strip()))
        except: pass

    # ═══════════════════════════════════════════════════════
    #  报告
    # ═══════════════════════════════════════════════════════
    total_time = time.time()-T["start"]
    reg_time = elapsed("reg_login_end")
    flow_time = elapsed("flow_end")

    print(f"\n{'='*70}")
    print(f"  SITREP — 全流程万人同场 · 真E2E（优化版）")
    print(f"{'='*70}")
    print(f"  总用户数:       {len(auth_ok):>6,}")
    print(f"  总耗时:         {total_time:>6.1f} 秒")
    print(f"  ├─ 注册+登录:   {reg_time:>6.1f} 秒  (并发{REG_CONCURRENCY})")
    print(f"  └─ 后续流程:    {flow_time:>6.1f} 秒  (并发200)")
    print(f"")
    print(f"  步骤统计:")
    print(f"    ① 注册+登录:  {len(auth_ok):>6,} / {len(auth_ok):>6,}")
    print(f"    ② 建地址:     {stats['address']:>6,} / {stats['total']:>6,}")
    print(f"    ③ 加购物车:   {stats['cart']:>6,} / {stats['total']:>6,}")
    print(f"    ④ 下单:       {stats['order']:>6,} / {stats['total']:>6,}")
    print(f"    ⑤ 支付:       {stats['pay']:>6,} / {stats['total']:>6,}")
    print(f"    ⑥ 秒杀请求:   {stats['flash']:>6,} / {stats['total']:>6,}")
    print(f"    ⑦ 轮询到订单: {stats['poll']:>6,} / {stats['total']:>6,}")
    print(f"")
    print(f"  核心指标:")
    print(f"    HTTP 500:     {stats['http500']:>6}")
    print(f"    HTTP 429:     {stats['http429']:>6}")
    print(f"    超时:         {stats['timeout']:>6}")
    print(f"    支付成功:     {stats['paid']:>6,} 单")
    print(f"    秒杀成功:     {stats['flash_ok']:>6,} 单")
    print(f"    秒杀库存:     {total_flash_sold:>6,}")
    print(f"    库存验算:     {'✅ 一致' if stats['flash_ok']==total_flash_sold else '❌ 不一致'}")
    print(f"{'='*70}")

    with open("/tmp/e2e_full_report.json","w") as f:
        json.dump({"timestamp":time.strftime("%Y-%m-%d %H:%M:%S"),
                   "total_time_s":round(total_time,1),
                   "reg_time_s":round(reg_time,1),
                   "flow_time_s":round(flow_time,1),
                   "users":len(auth_ok),"paid":stats['paid'],
                   "flash_ok":stats['flash_ok'],
                   "flash_stock_sold":total_flash_sold,
                   "http500":stats['http500'],"http429":stats['http429'],
                   "timeout":stats['timeout']}, f, indent=2)
    print(f"报告: /tmp/e2e_full_report.json")

asyncio.run(main())
