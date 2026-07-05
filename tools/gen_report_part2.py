#!/usr/bin/env python3
"""Part 2 - Ch5-11 + merge"""
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT

# Load part 1
doc = Document('/tmp/report_part1.docx')

def P(t, b=False, s=12, a='left', c=None):
    p = doc.add_paragraph()
    r = p.add_run(t); r.font.size=Pt(s); r.font.name='SimSun'
    if b: r.bold=True
    if c: r.font.color.rgb=c
    p.alignment={'center':WD_ALIGN_PARAGRAPH.CENTER,'right':WD_ALIGN_PARAGRAPH.RIGHT}.get(a,WD_ALIGN_PARAGRAPH.LEFT)
    return p

def B(t):
    p = doc.add_paragraph()
    r = p.add_run(t); r.font.size=Pt(12); r.font.name='SimSun'; r.bold=True; return p

def T(hdrs, rows):
    t = doc.add_table(rows=1+len(rows), cols=len(hdrs))
    t.style='Light Grid Accent 1'; t.alignment=WD_TABLE_ALIGNMENT.CENTER
    for i,h in enumerate(hdrs):
        c=t.rows[0].cells[i]; c.text=h
        for p2 in c.paragraphs:
            p2.alignment=WD_ALIGN_PARAGRAPH.CENTER
            for r in p2.runs: r.bold=True
    for ri,row in enumerate(rows):
        for ci,val in enumerate(row):
            t.rows[ri+1].cells[ci].text=str(val)

def C(t):
    p = doc.add_paragraph()
    r = p.add_run(t); r.font.name='Consolas'; r.font.size=Pt(9)
    p.paragraph_format.space_before=Pt(4); p.paragraph_format.space_after=Pt(4)

H = doc.add_heading

# ============ CH5 ============
doc.add_page_break()
H('五、AI/LLM 集成设计', level=1)
H('5.1 模型选型', level=2)
T(['配置','值'],[['模型','deepseek-chat / qwen-plus（可配置）'],['协议','OpenAI兼容 /v1/chat/completions'],['上下文','128K tokens'],['限流','约60 RPM']])
H('5.2 调用方式', level=2)
P('用户通过POST /api/ai/chat发送咨询，ai-service先从product-service获取商品列表拼接成system prompt，再调用LLM API获取回复。当前为同步调用（请求-响应模式）。')
C('请求: POST /api/ai/chat {"query":"推荐办公笔记本"}\n-> 获取商品列表拼入system prompt\n-> POST /v1/chat/completions\n-> 返回自然语言推荐')
H('5.3 生产优化方向', level=2)
[doc.add_paragraph(i, style='List Bullet') for i in [
    '请求排队：Redis队列控制并发不超过API限流（60 RPM）',
    '相似缓存：缓存重复问题回复（语义相似度>0.95）',
    '流式推送：改为SSE逐步返回',
    '降级：API不可用时返回预设FAQ']]

# ============ CH6 ============
doc.add_page_break()
H('六、实时通信设计（WebSocket）', level=1)
H('6.1 架构', level=2)
P('客户端连接 ws://localhost:8080/ws/seckill（经Gateway路由到order-service）。订单创建成功后SeckillOrderConsumer通过SeckillWebSocketHandler推送消息到对应用户。')
C('Client -> ws://:8080/ws/seckill -> Gateway -> OrderService(:8083)\n  -> SeckillWebSocketHandler.sessionMap<userId, session>\n  -> 订单创建成功后推送: {"type":"ORDER_CREATED","orderId":...,"status":"SUCCESS"}')
H('6.2 跨JVM问题', level=2)
P('SeckillWebSocketHandler使用静态ConcurrentHashMap存储session。开发中发现秒杀请求经gateway路由到seckill-service，而WebSocket连接在order-service，两个JVM的静态Map不共享，导致无法推送。')
P('解决方案：将Gateway的/ws/**路由从seckill-service改为order-service，同时添加白名单GET:/ws/。', b=True)

# ============ CH7 ============
doc.add_page_break()
H('七、技术选型依据与权衡', level=1)
H('7.1 Redis：String原子扣减 vs Lua脚本', level=2)
P('对于当前单key原子扣减场景，Redis DECR命令本身就是原子性的，无需Lua。但如果后续需要"扣库存+记录流水+检查限购"的多步操作，会改用Lua脚本保证原子性。String类型选择原因：库存是单值标量，无需ZSET/HASH的额外开销。')
H('7.2 RabbitMQ：削峰选型分析', level=2)
P('秒杀场景核心需求是削峰和可靠消费，而非大吞吐量流处理。RabbitMQ的prefetch机制（prefetch=10）天然适合控制消费者压力，避免订单服务被秒杀流量打垮。死信队列（DLQ）兜底重试失败的订单。Kafka的优势在大吞吐日志场景，对秒杀来说overkill。')
H('7.3 限流：Gateway vs Nginx', level=2)
P('本地原型阶段，Gateway在应用层限流可精确控制到接口级别（seckill接口 vs 普通接口），且与JWT Filter同一管道，配置集中。生产环境建议Nginx+Gateway两层限流。')
H('7.4 订单表：统一 vs 分离', level=2)
P('秒杀订单和普通订单结构高度相似（都有user_id、product_id、amount、status），区别仅在于seckill_activity_id字段是否为NULL。统一表简化查询逻辑，后续百万量级可考虑按月/按用户哈希分表。')
H('7.5 鉴权：JWT vs Session', level=2)
P('微服务架构下Session共享需要Redis Session Manager，复杂度高。JWT无状态，Gateway验证后直接透传X-User-Id Header到各服务，无需额外基础设施。权衡：JWT无法服务端主动失效，需依赖短有效期（7天）+注销Token黑名单机制。')

# ============ CH8 ============
doc.add_page_break()
H('八、测试', level=1)
H('8.1 测试体系', level=2)
T(['层次','工具','范围','数量'],[['单元测试','JUnit 5 + Mockito','Java后端核心逻辑','88个全部通过'],['E2E测试','Python + aiohttp','HTTP全链路','56个(53通过3跳过)'],['架构验证','Python + aiohttp','架构9概念','11/11通过'],['万人演示','Python + aiohttp','10000并发','7个脚本'],['Postman','Postman Collection','核心业务链','一键导入按顺序执行']])
H('8.2 Postman Collection', level=2)
P('Postman Collection文件位于 /docs/postman_collection.json。导入Postman后按文件夹顺序依次执行即可完成全流程验证，变量自动传递。')
H('8.3 关键压测结果', level=2)
T(['场景','请求','耗时','成功','错误'],[['单商品1万秒杀','10,000','7.4s','10,000','0'],['50品万人同场(DB直插)','10,000','19.8s','9,694','0'],['50品万人同场(HTTP全流程)','10,000x8','565s','80,000步','0'],['全流程E2E 8步','10,000人','565s','10,000人','0']])
H('8.4 测试环境', level=2)
T(['配置','值'],[['CPU','Intel Xeon 4核'],['内存','3.6GB（可用约850MB）'],['OS','Linux 6.8.0'],['网络','本地回环（无网络延迟）'],['并发框架','asyncio + aiohttp 200并发']])

# ============ CH9 ============
doc.add_page_break()
H('九、生产环境演进方案', level=1)
H('9.1 高可用（HA）设计', level=2)
B('微服务多实例：')
[doc.add_paragraph(i, style='List Bullet') for i in ['每个服务至少2实例，通过Nacos注册发现与负载均衡','Gateway多实例+Nginx前端分发','服务无状态，水平扩展简单']]
B('MySQL高可用：')
[doc.add_paragraph(i, style='List Bullet') for i in ['主从复制(1主2从)，读写分离','MHA/Orchestrator自动主从切换','ShardingSphere按user_id hash分16库，按月分表']]
B('Redis高可用：')
[doc.add_paragraph(i, style='List Bullet') for i in ['哨兵模式(1主2从3哨兵)自动切换','或Redis Cluster(3主3从)数据分片','秒杀库存用主库读写保证一致性']]
B('RabbitMQ高可用：')
[doc.add_paragraph(i, style='List Bullet') for i in ['镜像队列(3节点)或Quorum Queue','手动ACK+重试机制','死信队列兜底']]
H('9.2 高并发应对策略', level=2)
B('动态扩容：')
[doc.add_paragraph(i, style='List Bullet') for i in ['K8s + HPA基于CPU/Memory/QPS自动扩缩','秒杀前15分钟预扩至3-5倍','扩容后Gateway自动分发']]
B('MQ集群优化：')
[doc.add_paragraph(i, style='List Bullet') for i in ['Sharding插件按activity_id hash分片','单队列约5000 msg/s，8片达40000 msg/s','消费端动态线程池']]
B('限流与降级：')
[doc.add_paragraph(i, style='List Bullet') for i in ['Nginx层：limit_req_zone按IP限流','Gateway层：RateLimitFilter + CircuitBreaker','服务层：Sentinel流控','降级：秒杀不可用->"活动火爆"，AI不可用->FAQ']]
H('9.3 大模型调用优化', level=2)
B('请求排队与限流：')
[doc.add_paragraph(i, style='List Bullet') for i in ['Redis有界优先级队列控制并发不超过API限流','Nginx对/api/ai/chat单独限流(10 QPS/IP)','服务层Semaphore隔离，最多5个并发LLM']]
B('缓存策略：')
[doc.add_paragraph(i, style='List Bullet') for i in ['热点问答缓存(Redis TTL=3600s)','语义缓存：向量化query+余弦相似度>0.95命中','商品信息预热到本地Caffeine缓存']]
B('流式推送与降级：')
[doc.add_paragraph(i, style='List Bullet') for i in ['SSE流式逐字输出','异步：接收请求后立即返回，后台处理后推送','LLM超时(>5s)降级为"正在思考…"']]

# ============ CH10 ============
doc.add_page_break()
H('十、AI 辅助设计开发总结', level=1)
H('10.1 人机协作模式', level=2)
P('本系统的开发采用了"需求定义+AI实现+人工审阅"的协作模式。具体表现为：')
[doc.add_paragraph(i, style='List Bullet') for i in [
    '架构决策由人类主导（微服务拆分、技术选型、数据库设计）',
    '代码实现由AI辅助（Controller、Service、Mapper层代码生成）',
    '测试脚本全程由AI编写并验证（Python E2E测试、并发测试脚本）',
    'Bug修复由AI定位+人类确认（收藏NPE、评价端点缺失、WebSocket跨JVM等）',
    '报告文档由AI整理成稿，人类补充细节']]
H('10.2 关键经验', level=2)
[doc.add_paragraph(i, style='List Bullet') for i in [
    '并发瓶颈分析：BCrypt是CPU密集操作，4核机上限42 ops/s，远低于HTTP请求处理能力',
    'WebSocket跨JVM问题：静态Map不共享，需确保连接和推送在同一JVM',
    'Gateway限流阈值调整：500太低(拦了太多秒杀请求)，2000更适合演示场景',
    '测试脚本迭代：从v1(标准模糊)到v2(明确成功定义)大幅提升测试有效性',
    '数据库直插vs HTTP注册：性能差几十倍，但E2E测试必须走HTTP才能验证全链路']]
H('10.3 个人反思', level=2)
P('通过本项目，深刻理解了微服务架构在高并发场景下的设计取舍。'
  'Gateway限流不是越严越好，需要根据实际压测数据调整参数；'
  'Redis原子操作防超卖是成熟方案，但MQ异步削峰会引入最终一致性的复杂度；'
  'WebSocket实时推送在多服务架构下需要统一规划连接归属。'
  'AI辅助开发大幅提升了编码效率，但架构设计和技术决策仍需人类判断。'
  '尤其在测试领域，AI可以快速生成测试脚本，但测试的有效性设计（成功标准定义、边界覆盖）'
  '仍需要人类经验。')

# ============ SAVE ============
doc.save('/tmp/report_complete.docx')
print('OK: /tmp/report_complete.docx')
