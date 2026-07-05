╔══════════════════════════════════════════════════════════════════════╗
║             FlashSaleAI 架构重构方案                              ║
║         问题：秒杀服务越界落单 → 订单服务应负责                   ║
╚══════════════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────────┐
│ 一、现状（错的）                                                    │
└─────────────────────────────────────────────────────────────────────┘

             秒杀服务                          订单服务
  FlashSaleServiceImpl      SeckillOrderConsumer
    Redis扣库存 ──→ 发MQ ──→  自己收MQ
                                ↓
                             INSERT INTO order  ← ❌ 替别人写表

┌─────────────────────────────────────────────────────────────────────┐
│ 二、目标（对的）                                                    │
└─────────────────────────────────────────────────────────────────────┘

             秒杀服务                          订单服务
  FlashSaleServiceImpl                    SeckillOrderConsumer
    Redis扣库存 ──→ 发MQ ─────────────────→ 自己收MQ
                                ↓
                             OrderService.createOrder()
                                ↓
                             INSERT INTO order  ← ✅ 自己的表自己写
                                ↓
                             WebSocket推送用户

┌─────────────────────────────────────────────────────────────────────┐
│ 三、分步实施方案                                                    │
└─────────────────────────────────────────────────────────────────────┘

 Phase 1 — 共享层（flash-sale-common）
 ────────────────────────────────────────
 ① 新增 SeckillMessage.java     — 共用DTO（秒杀→订单的消息体）
 ② 新增 SeckillWebSocketHandler — 从seckill-service搬过来
 ③ 新增 WebSocketConfig.java    — 注册WS端点到 /ws/seckill

 Phase 2 — 订单服务（order-service）
 ────────────────────────────────────────
 ① pom.xml 添加 spring-boot-starter-amqp
 ② application.yml 添加 RabbitMQ + 消费限流配置
 ③ 新增 RabbitMQConfig.java     — 声明队列/交换机/绑定
 ④ 新增 SeckillOrderConsumer.java  — MQ消费，调OrderService落单
     √ prefetch=10 — 一次只拉10条
     √ concurrency=3-5 — 平滑消费
     √ 重试3次 → 死信队列兜底
     √ 幂等校验防重复
 ⑤ 修改 OrderService  — 新增 createSeckillOrder() 方法

 Phase 3 — 秒杀服务（seckill-service）清理
 ────────────────────────────────────────
 ① 删除 SeckillOrderConsumer.java     — 不再自己落单
 ② 删除 SeckillMessage.java           — 改用common的
 ③ 删除 SeckillWebSocketHandler.java  — 改用common的
 ④ 删除 WebSocketConfig.java          — 改用common的
 ⑤ FlashSaleServiceImpl 引用 common 的 SeckillMessage

 Phase 4 — 削峰效果验证
 ────────────────────────────────────────
 ① 启动全服务，测试秒杀走通
 ② 用stres_combo.py跑一次混合压测
 ③ 观察消费者日志确认节流生效

┌─────────────────────────────────────────────────────────────────────┐
│ 四、改动文件清单                                                    │
└─────────────────────────────────────────────────────────────────────┘

 新建  flash-sale-common/.../dto/SeckillMessage.java
 新建  flash-sale-common/.../websocket/SeckillWebSocketHandler.java
 新建  flash-sale-common/.../websocket/WebSocketConfig.java
 修改  order-service/pom.xml
 修改  order-service/src/main/resources/application.yml
 新建  order-service/.../config/RabbitMQConfig.java
 新建  order-service/.../consumer/SeckillOrderConsumer.java
 修改  order-service/.../service/OrderService.java
 删除  seckill-service/.../consumer/SeckillOrderConsumer.java
 删除  seckill-service/.../dto/SeckillMessage.java
 删除  seckill-service/.../websocket/SeckillWebSocketHandler.java

 共计：7新建 + 3修改 + 3删除 = 13个文件

┌─────────────────────────────────────────────────────────────────────┐
│ 五、预计工期                                                        │
└─────────────────────────────────────────────────────────────────────┘

 Phase 1 (共享层)     →  较快
 Phase 2 (订单服务)   →  核心工作
 Phase 3 (秒杀清理)   →  较快
 Phase 4 (验证)       →  较快

 合计：一次完成，不拆多轮
