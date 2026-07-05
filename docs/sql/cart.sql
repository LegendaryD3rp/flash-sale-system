-- 购物车表
CREATE TABLE IF NOT EXISTS `cart` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `user_id`    BIGINT       NOT NULL                 COMMENT '用户ID',
    `product_id` BIGINT       NOT NULL                 COMMENT '商品ID',
    `quantity`   INT          NOT NULL DEFAULT 1       COMMENT '数量',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';
