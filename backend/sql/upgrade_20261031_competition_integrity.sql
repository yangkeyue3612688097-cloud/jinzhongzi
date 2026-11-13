-- Competition integrity upgrade:
-- 1. bind every revenue event to the agreement effective when revenue is recognized
-- 2. create one idempotent settlement receipt per revenue event
-- 3. add a business-anchor outbox for the chain adapter
-- 4. prevent duplicate profit rows for the same revenue event and collaborator
--
-- Existing installations:
-- mysql -uroot -p -h127.0.0.1 -P3307 aigc_evidence < upgrade_20261031_competition_integrity.sql
USE aigc_evidence;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `revenue_event` ADD COLUMN `agreement_id` BIGINT DEFAULT NULL',
    'SELECT ''agreement_id exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'revenue_event' AND column_name = 'agreement_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `revenue_event` ADD COLUMN `agreement_hash` VARCHAR(128) DEFAULT NULL',
    'SELECT ''agreement_hash exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'revenue_event' AND column_name = 'agreement_hash');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `revenue_event` ADD COLUMN `agreement_version` INT DEFAULT NULL',
    'SELECT ''agreement_version exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'revenue_event' AND column_name = 'agreement_version');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `revenue_event` ADD COLUMN `event_hash` VARCHAR(128) DEFAULT NULL',
    'SELECT ''event_hash exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'revenue_event' AND column_name = 'event_hash');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Bind legacy unbound revenue events only when a current effective agreement exists.
UPDATE `revenue_event` re
JOIN `collaboration` c ON c.id = re.collaboration_id
JOIN `collaboration_agreement` ca
  ON ca.id = c.current_agreement_id
 AND ca.collaboration_id = c.id
 AND ca.status = 1
SET re.agreement_id = ca.id,
    re.agreement_hash = ca.agreement_hash,
    re.agreement_version = ca.version
WHERE re.agreement_id IS NULL;

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

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `profit_record` ADD UNIQUE KEY `uk_revenue_event_user` (`revenue_event_id`, `user_id`)',
    'SELECT ''uk_revenue_event_user exists''')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'profit_record'
      AND index_name = 'uk_revenue_event_user');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
