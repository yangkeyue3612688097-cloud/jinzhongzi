-- AI Block Database Init Script
-- 全新环境建库用；已有库请执行 sql/upgrade_*.sql 增量脚本
CREATE DATABASE IF NOT EXISTS aigc_evidence DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aigc_evidence;

-- 用户
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password` VARCHAR(100) NOT NULL,
    `email` VARCHAR(100) DEFAULT NULL,
    `avatar` VARCHAR(255) DEFAULT NULL,
    `blockchain_address` VARCHAR(100) DEFAULT NULL,
    `public_key` TEXT DEFAULT NULL,
    `private_key_enc` TEXT DEFAULT NULL,
    `identity_signature` VARCHAR(512) DEFAULT NULL,
    `identity_attested_at` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 作品
CREATE TABLE IF NOT EXISTS `ai_work` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(200) NOT NULL,
    `description` TEXT DEFAULT NULL,
    `file_type` VARCHAR(50) DEFAULT NULL,
    `file_path` VARCHAR(500) DEFAULT NULL,
    `file_hash` VARCHAR(128) DEFAULT NULL,
    `blockchain_tx_hash` VARCHAR(128) DEFAULT NULL,
    `current_version_id` BIGINT DEFAULT NULL,
    `status` TINYINT DEFAULT 0 COMMENT '0=未存证, 1=已生成存证',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI 生成参数
CREATE TABLE IF NOT EXISTS `ai_work_generation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `work_id` BIGINT NOT NULL,
    `prompt` TEXT DEFAULT NULL,
    `negative_prompt` TEXT DEFAULT NULL,
    `model_name` VARCHAR(100) DEFAULT NULL,
    `model_version` VARCHAR(100) DEFAULT NULL,
    `seed` VARCHAR(64) DEFAULT NULL,
    `steps` INT DEFAULT NULL,
    `cfg_scale` DECIMAL(6,2) DEFAULT NULL,
    `width` INT DEFAULT NULL,
    `height` INT DEFAULT NULL,
    `sampler` VARCHAR(50) DEFAULT NULL,
    `extra_params` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_work_id` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 存证
CREATE TABLE IF NOT EXISTS `evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `work_id` BIGINT NOT NULL,
    `data_hash` VARCHAR(128) NOT NULL,
    `data_type` VARCHAR(50) DEFAULT NULL,
    `description` TEXT DEFAULT NULL,
    `blockchain_tx_hash` VARCHAR(128) DEFAULT NULL,
    `block_number` BIGINT DEFAULT NULL,
    `chain_status` TINYINT DEFAULT 0 COMMENT '0=待上链,1=已上链,2=失败',
    `chain_mode` VARCHAR(32) DEFAULT NULL,
    `canonical_version` INT DEFAULT 1,
    `content_hash` VARCHAR(128) DEFAULT NULL,
    `canonical_payload` MEDIUMTEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_work_id` (`work_id`),
    KEY `idx_data_hash` (`data_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 版本
CREATE TABLE IF NOT EXISTS `work_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `work_id` BIGINT NOT NULL,
    `version_number` INT NOT NULL,
    `creator_id` BIGINT NOT NULL,
    `file_path` VARCHAR(255) DEFAULT NULL,
    `file_hash` VARCHAR(128) DEFAULT NULL,
    `version_hash` VARCHAR(128) DEFAULT NULL,
    `parent_version_id` BIGINT DEFAULT NULL,
    `change_description` TEXT DEFAULT NULL,
    `blockchain_tx_hash` VARCHAR(128) DEFAULT NULL,
    `status` TINYINT DEFAULT 1 COMMENT '1=已提交,2=已确认',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_work_id` (`work_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 协作
CREATE TABLE IF NOT EXISTS `collaboration` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `work_id` BIGINT NOT NULL,
    `owner_id` BIGINT NOT NULL,
    `work_hash` VARCHAR(128) DEFAULT NULL,
    `blockchain_tx_hash` VARCHAR(128) DEFAULT NULL,
    `total_revenue` DECIMAL(18,2) DEFAULT 0.00,
    `distributed_amount` DECIMAL(18,2) DEFAULT 0.00,
    `current_version_id` BIGINT DEFAULT NULL,
    `final_version_id` BIGINT DEFAULT NULL,
    `invite_message` VARCHAR(500) DEFAULT NULL,
    `current_agreement_id` BIGINT DEFAULT NULL,
    `chain_status` TINYINT DEFAULT 0 COMMENT '0=待上链,1=已上链,2=失败',
    `chain_mode` VARCHAR(32) DEFAULT NULL,
    `status` TINYINT DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_work_id` (`work_id`),
    KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 协作邀请
CREATE TABLE IF NOT EXISTS `collaboration_invite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `work_id` BIGINT NOT NULL,
    `inviter_id` BIGINT NOT NULL,
    `invitee_id` BIGINT NOT NULL,
    `message` VARCHAR(500) DEFAULT NULL,
    `status` TINYINT DEFAULT 0 COMMENT '0=待处理,1=已接受,2=已拒绝',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_invitee_id` (`invitee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 协作者
CREATE TABLE IF NOT EXISTS `collaborator` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `collaboration_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `user_name` VARCHAR(50) DEFAULT NULL,
    `share_ratio` INT NOT NULL DEFAULT 0,
    `contributed_amount` DECIMAL(18,2) DEFAULT 0.00,
    `version_count` INT DEFAULT 0,
    `last_contribution_time` DATETIME DEFAULT NULL,
    `status` TINYINT DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_collaboration_id` (`collaboration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 协作协议快照与确认
CREATE TABLE IF NOT EXISTS `collaboration_agreement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `collaboration_id` BIGINT NOT NULL,
    `version` INT NOT NULL,
    `agreement_hash` VARCHAR(128) NOT NULL,
    `canonical_payload` MEDIUMTEXT NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待确认,1=已生效,2=已替代',
    `created_by` BIGINT NOT NULL,
    `effective_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collaboration_version` (`collaboration_id`, `version`),
    KEY `idx_collaboration_status` (`collaboration_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `collaboration_agreement_confirmation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `agreement_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `confirmation_hash` VARCHAR(128) NOT NULL,
    `signature` VARCHAR(512) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agreement_user` (`agreement_id`, `user_id`),
    KEY `idx_agreement_id` (`agreement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 贡献记录
CREATE TABLE IF NOT EXISTS `contribution` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `contribution_type` VARCHAR(50) DEFAULT NULL,
    `description` TEXT DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收益流水
CREATE TABLE IF NOT EXISTS `profit_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `collaboration_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `agreement_id` BIGINT DEFAULT NULL,
    `order_id` BIGINT DEFAULT NULL,
    `revenue_event_id` BIGINT DEFAULT NULL,
    `amount` DECIMAL(18,2) NOT NULL,
    `blockchain_tx_hash` VARCHAR(128) DEFAULT NULL,
    `chain_status` TINYINT DEFAULT 0 COMMENT '0=待上链,1=已上链,2=失败',
    `chain_mode` VARCHAR(32) DEFAULT NULL,
    `description` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_collaboration_id` (`collaboration_id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_revenue_event_user` (`revenue_event_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志
CREATE TABLE IF NOT EXISTS `license_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(64) NOT NULL,
    `work_id` BIGINT NOT NULL,
    `collaboration_id` BIGINT NOT NULL,
    `buyer_name` VARCHAR(100) DEFAULT NULL,
    `amount` DECIMAL(18,2) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=created,1=paid,2=settled,3=cancelled',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_collaboration` (`collaboration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `revenue_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL,
    `collaboration_id` BIGINT NOT NULL,
    `work_id` BIGINT NOT NULL,
    `agreement_id` BIGINT DEFAULT NULL,
    `agreement_hash` VARCHAR(128) DEFAULT NULL,
    `agreement_version` INT DEFAULT NULL,
    `event_hash` VARCHAR(128) DEFAULT NULL,
    `amount` DECIMAL(18,2) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=available,1=settled',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order` (`order_id`),
    KEY `idx_collaboration` (`collaboration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `settlement_receipt` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `receipt_no` VARCHAR(80) NOT NULL,
    `collaboration_id` BIGINT NOT NULL,
    `revenue_event_id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL,
    `agreement_id` BIGINT NOT NULL,
    `agreement_hash` VARCHAR(128) NOT NULL,
    `agreement_version` INT NOT NULL,
    `total_amount` DECIMAL(18,2) NOT NULL,
    `item_count` INT NOT NULL,
    `receipt_hash` VARCHAR(128) NOT NULL,
    `canonical_payload` MEDIUMTEXT NOT NULL,
    `chain_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending,1=anchored,2=failed',
    `blockchain_tx_hash` VARCHAR(128) DEFAULT NULL,
    `block_number` BIGINT DEFAULT NULL,
    `chain_mode` VARCHAR(32) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_receipt_no` (`receipt_no`),
    UNIQUE KEY `uk_receipt_revenue_event` (`revenue_event_id`),
    KEY `idx_receipt_collaboration` (`collaboration_id`),
    KEY `idx_receipt_agreement` (`agreement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `chain_anchor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `business_type` VARCHAR(32) NOT NULL,
    `business_id` BIGINT NOT NULL,
    `data_hash` VARCHAR(128) NOT NULL,
    `metadata_uri` VARCHAR(500) DEFAULT NULL,
    `chain_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending,1=anchored,2=failed',
    `blockchain_tx_hash` VARCHAR(128) DEFAULT NULL,
    `block_number` BIGINT DEFAULT NULL,
    `chain_mode` VARCHAR(32) DEFAULT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `error_message` VARCHAR(1000) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_anchor_business` (`business_type`, `business_id`, `data_hash`),
    KEY `idx_anchor_status` (`chain_status`, `create_time`),
    KEY `idx_anchor_business` (`business_type`, `business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `operation` VARCHAR(100) DEFAULT NULL,
    `detail` VARCHAR(500) DEFAULT NULL,
    `ip_address` VARCHAR(50) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
