-- 协作协议、确认与真实结算状态升级
-- 执行方式：mysql -uroot -p -h127.0.0.1 -P3307 aigc_evidence < upgrade_20261030_collaboration_agreement.sql

USE aigc_evidence;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `collaboration` ADD COLUMN `current_agreement_id` BIGINT DEFAULT NULL',
    'SELECT ''current_agreement_id exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'collaboration' AND column_name = 'current_agreement_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `collaboration` ADD COLUMN `chain_status` TINYINT DEFAULT 0 COMMENT ''0=待上链,1=已上链,2=失败''',
    'SELECT ''chain_status exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'collaboration' AND column_name = 'chain_status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `collaboration` ADD COLUMN `chain_mode` VARCHAR(32) DEFAULT NULL',
    'SELECT ''chain_mode exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'collaboration' AND column_name = 'chain_mode');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `profit_record` ADD COLUMN `agreement_id` BIGINT DEFAULT NULL',
    'SELECT ''agreement_id exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'profit_record' AND column_name = 'agreement_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `profit_record` ADD COLUMN `chain_status` TINYINT DEFAULT 0 COMMENT ''0=待上链,1=已上链,2=失败''',
    'SELECT ''chain_status exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'profit_record' AND column_name = 'chain_status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `profit_record` ADD COLUMN `chain_mode` VARCHAR(32) DEFAULT NULL',
    'SELECT ''chain_mode exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'profit_record' AND column_name = 'chain_mode');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agreement_user` (`agreement_id`, `user_id`),
    KEY `idx_agreement_id` (`agreement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 清除历史占位哈希，改用作品真实 SHA-256。
UPDATE `collaboration` c
JOIN `ai_work` w ON w.id = c.work_id
SET c.work_hash = w.file_hash,
    c.blockchain_tx_hash = NULL,
    c.chain_status = 0,
    c.chain_mode = COALESCE(c.chain_mode, 'pending')
WHERE c.work_hash IS NULL OR c.work_hash LIKE 'work\_%' OR c.blockchain_tx_hash LIKE '0xcollab\_%';

-- 旧版本接受邀请时没有自动加入发起人，补齐协议所需的协作者记录。
INSERT INTO `collaborator` (`collaboration_id`, `user_id`, `share_ratio`, `contributed_amount`, `status`)
SELECT c.id, c.owner_id, 100, 0.00, 1
FROM `collaboration` c
WHERE NOT EXISTS (
    SELECT 1 FROM `collaborator` x
    WHERE x.collaboration_id = c.id AND x.user_id = c.owner_id
);
