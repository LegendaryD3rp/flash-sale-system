-- 收货地址表（user-service）
CREATE TABLE IF NOT EXISTS `address` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `user_id`        BIGINT       NOT NULL                 COMMENT '用户ID',
    `receiver_name`  VARCHAR(50)  NOT NULL                 COMMENT '收件人姓名',
    `receiver_phone` VARCHAR(20)  NOT NULL                 COMMENT '收件人电话',
    `province`       VARCHAR(20)  DEFAULT NULL             COMMENT '省',
    `city`           VARCHAR(20)  DEFAULT NULL             COMMENT '市',
    `district`       VARCHAR(20)  DEFAULT NULL             COMMENT '区',
    `detail_address` VARCHAR(200) NOT NULL                 COMMENT '详细地址',
    `is_default`     TINYINT      DEFAULT 0                COMMENT '是否默认地址 0-否 1-是',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址表';
