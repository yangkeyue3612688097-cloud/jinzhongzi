-- 创作元数据 + 证据字段升级
-- 执行方式：mysql -uroot -p -h127.0.0.1 -P3307 aigc_evidence < upgrade_20261030_generation_evidence.sql

USE aigc_evidence;

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

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `evidence` ADD COLUMN `chain_status` TINYINT DEFAULT 0 COMMENT ''0=待上链,1=已上链,2=失败''',
    'SELECT ''chain_status exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'evidence' AND column_name = 'chain_status');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `evidence` ADD COLUMN `chain_mode` VARCHAR(32) DEFAULT NULL',
    'SELECT ''chain_mode exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'evidence' AND column_name = 'chain_mode');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `evidence` ADD COLUMN `canonical_version` INT DEFAULT 1',
    'SELECT ''canonical_version exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'evidence' AND column_name = 'canonical_version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `evidence` ADD COLUMN `content_hash` VARCHAR(128) DEFAULT NULL',
    'SELECT ''content_hash exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'evidence' AND column_name = 'content_hash');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `evidence` ADD COLUMN `canonical_payload` MEDIUMTEXT DEFAULT NULL',
    'SELECT ''canonical_payload exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'evidence' AND column_name = 'canonical_payload');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
