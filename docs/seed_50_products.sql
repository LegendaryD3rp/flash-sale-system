-- 清空旧测试数据
DELETE FROM `order` WHERE created_at >= '2026-06-16';
DELETE FROM cart;
DELETE FROM user_favorite;
DELETE FROM product_image;
DELETE FROM product_sku;
DELETE FROM product_review;
DELETE FROM user_coupon;
DELETE FROM coupon;
DELETE FROM seckill_activity;
DELETE FROM product;
DELETE FROM `user` WHERE username LIKE 'mix_%' OR username LIKE 'flow_test%' OR username LIKE 'stress_%';

ALTER TABLE product AUTO_INCREMENT = 1;

-- ========== 50种商品 ==========
-- 笔记本 (1-8)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('华为MateBook X Pro 2024','14.2寸 OLED i7-13700H 16GB+1TB',999900,200,'笔记本','/uploads/product_1.jpg','ON'),
('MacBook Air M3','13.6寸 M3芯片 8GB+256GB',1099900,150,'笔记本','/uploads/product_1.jpg','ON'),
('ThinkPad X1 Carbon Gen 11','14寸 i7-1360P 16GB+512GB',1299900,100,'笔记本','/uploads/product_1.jpg','ON'),
('ROG 幻16 2024','16寸 i9-13900H RTX4060 16GB+1TB',1599900,80,'笔记本','/uploads/product_1.jpg','ON'),
('RedmiBook Pro 15','15.6寸 i5-12450H 16GB+512GB',499900,300,'笔记本','/uploads/product_1.jpg','ON'),
('Surface Laptop 5','13.5寸 i5-1235U 8GB+256GB',899900,120,'笔记本','/uploads/product_1.jpg','ON'),
('荣耀MagicBook 14 Pro','14寸 i5-13500H 16GB+1TB',699900,180,'笔记本','/uploads/product_1.jpg','ON'),
('戴尔XPS 15 9530','15.6寸 i7-13700H RTX4060 32GB+1TB',1799900,60,'笔记本','/uploads/product_1.jpg','ON');

-- 手机 (9-16)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('iPhone 15 Pro Max','256GB A17 Pro 钛金属',999900,300,'手机','/uploads/product_2.jpg','ON'),
('iPhone 15','128GB A16 双摄',599900,400,'手机','/uploads/product_2.jpg','ON'),
('华为 Mate 60 Pro','512GB 麒麟9000S 卫星通话',799900,250,'手机','/uploads/product_2.jpg','ON'),
('小米14 Pro','256GB 骁龙8Gen3 徕卡光学',499900,350,'手机','/uploads/product_2.jpg','ON'),
('OPPO Find X7 Ultra','256GB 天玑9300 哈苏影像',599900,200,'手机','/uploads/product_2.jpg','ON'),
('vivo X100 Pro','256GB 天玑9300 蔡司镜头',499900,220,'手机','/uploads/product_2.jpg','ON'),
('三星 Galaxy S24 Ultra','256GB 骁龙8Gen3 S Pen',969900,150,'手机','/uploads/product_2.jpg','ON'),
('一加 12','256GB 骁龙8Gen3 哈苏影像',429900,280,'手机','/uploads/product_2.jpg','ON');

-- 耳机 (17-24)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('AirPods Pro 2','USB-C 主动降噪 H2芯片',189900,300,'耳机','/uploads/product_2.jpg','ON'),
('Sony WH-1000XM5','头戴式 降噪 30小时续航',299900,180,'耳机','/uploads/product_2.jpg','ON'),
('Bose QC Ultra Earbuds','真无线 降噪 CustomTune技术',249900,120,'耳机','/uploads/product_2.jpg','ON'),
('华为 FreeBuds Pro 3','麒麟A2芯片 超感知降噪',149900,250,'耳机','/uploads/product_2.jpg','ON'),
('小米 Buds 4 Pro','三麦克风降噪 哈曼调音',109900,300,'耳机','/uploads/product_2.jpg','ON'),
('漫步者 NeoBuds Pro 2','圈铁双单元 Hi-Res认证',59900,400,'耳机','/uploads/product_2.jpg','ON'),
('Samsung Galaxy Buds2 Pro','AKG调音 智能降噪',129900,200,'耳机','/uploads/product_2.jpg','ON'),
('Beats Fit Pro','耳挂式 空间音频 H1芯片',169900,150,'耳机','/uploads/product_2.jpg','ON');

-- 键盘 (25-30)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('Keychron K8 Pro','87键 蓝牙双模 热插拔',59900,200,'键盘','/uploads/product_3.jpg','ON'),
('Keychron Q1 Pro','75% 铝坨坨 Gasket结构',129900,100,'键盘','/uploads/product_3.jpg','ON'),
('罗技 MX Mechanical','全尺寸 矮轴 多设备切换',109900,150,'键盘','/uploads/product_3.jpg','ON'),
('雷蛇 黑寡妇 V4','全尺寸 绿轴 RGB 腕托',129900,120,'键盘','/uploads/product_3.jpg','ON'),
('宁芝 静电容 35g','35g静电容 87键 办公神器',89900,80,'键盘','/uploads/product_3.jpg','ON'),
('京东京造 JZ990','98配列 Gasket 热插拔',39900,300,'键盘','/uploads/product_3.jpg','ON');

-- 书籍 (31-36)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('深入理解Java虚拟机','第3版 周志明 经典JVM',9900,500,'书籍','/uploads/product_4.jpg','ON'),
('Java并发编程的艺术','方腾飞 并发实战经典',7900,400,'书籍','/uploads/product_4.jpg','ON'),
('Spring实战 第6版','Craig Walls 微服务入门',11900,350,'书籍','/uploads/product_4.jpg','ON'),
('系统设计与实现','DDIA 中文版 分布式理论',15900,250,'书籍','/uploads/product_4.jpg','ON'),
('算法导论 第4版','CLRS 算法圣经',12900,300,'书籍','/uploads/product_4.jpg','ON'),
('CSAPP 深入理解计算机系统','Randal E. Bryant 经典',14900,200,'书籍','/uploads/product_4.jpg','ON');

-- 游戏 (37-42)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('Nintendo Switch OLED','白色 7寸OLED Joy-Con',259900,120,'游戏','/uploads/product_5.jpg','ON'),
('PS5 光驱版','Slim款 825GB 4K游戏',359900,80,'游戏','/uploads/product_5.jpg','ON'),
('Xbox Series X','1TB 快速架构 真4K',379900,60,'游戏','/uploads/product_5.jpg','ON'),
('Steam Deck OLED','512GB 掌上3A游戏',459900,50,'游戏','/uploads/product_5.jpg','ON'),
('Switch 健身环大冒险','Ring-Con + 游戏卡带',59900,200,'游戏','/uploads/product_5.jpg','ON'),
('PS5 DualSense 手柄','白色 自适应扳机 触觉反馈',52900,300,'游戏','/uploads/product_5.jpg','ON');

-- 显示器 (43-47)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('戴尔 U2723QE','27寸 4K IPS Black Type-C 90W',499900,60,'显示器','/uploads/product_1.jpg','ON'),
('LG 27GP95R','27寸 4K 144Hz Nano IPS',399900,80,'显示器','/uploads/product_1.jpg','ON'),
('小米 34寸带鱼屏','3440x1440 144Hz 1500R',259900,100,'显示器','/uploads/product_1.jpg','ON'),
('华硕 ROG PG27UQ','27寸 4K 144Hz HDR1000',899900,30,'显示器','/uploads/product_1.jpg','ON'),
('AOC 24G2SP','24寸 1080p 165Hz 电竞',94900,200,'显示器','/uploads/product_1.jpg','ON');

-- 路由器 (48-50)
INSERT INTO product (name, description, price, stock, category, image_url, status) VALUES
('小米 AX9000','三频 9000Mbps 高通四核',129900,150,'路由器','/uploads/product_3.jpg','ON'),
('华硕 AX86U Pro','AX5700 博通四核 电竞',179900,100,'路由器','/uploads/product_3.jpg','ON'),
('TP-Link XDR6088','AX6000 双2.5G 高性价比',59900,250,'路由器','/uploads/product_3.jpg','ON');

-- ========== 秒杀活动 ==========
INSERT INTO seckill_activity (product_id, seckill_price, total_stock, available_stock, start_time, end_time, status) VALUES
(1, 899900, 20, 20, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(2, 989900, 15, 15, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(9, 799900, 30, 30, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(10, 499900, 40, 40, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(11, 659900, 20, 20, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(17, 149900, 50, 50, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(19, 199900, 25, 25, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(29, 45900, 60, 60, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(32, 6900,  100, 100, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE'),
(40, 99900, 30, 30, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 'ACTIVE');

SELECT CONCAT('商品: ', COUNT(*)) AS result FROM product;
SELECT CONCAT('秒杀活动: ', COUNT(*)) AS result FROM seckill_activity;
