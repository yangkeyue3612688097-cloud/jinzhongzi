-- 比赛闭环升级：用户托管签名身份、订单/收入事件、版本证据哈希、证据与协议签名
-- 执行方式：mysql -uroot -p -h127.0.0.1 -P3307 aigc_evidence < upgrade_20261030_competition.sql
USE aigc_evidence;

-- user：本地托管签名身份
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `public_key` TEXT DEFAULT NULL',
    'SELECT ''public_key exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'public_key');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `private_key_enc` TEXT DEFAULT NULL',
    'SELECT ''private_key_enc exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'private_key_enc');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `identity_signature` VARCHAR(512) DEFAULT NULL',
    'SELECT ''identity_signature exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'identity_signature');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `user` ADD COLUMN `identity_attested_at` DATETIME DEFAULT NULL',
    'SELECT ''identity_attested_at exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'identity_attested_at');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- profit_record：追溯订单与收入事件
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `profit_record` ADD COLUMN `order_id` BIGINT DEFAULT NULL',
    'SELECT ''order_id exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'profit_record' AND column_name = 'order_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `profit_record` ADD COLUMN `revenue_event_id` BIGINT DEFAULT NULL',
    'SELECT ''revenue_event_id exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'profit_record' AND column_name = 'revenue_event_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- work_version：每个版本独立的证据哈希
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `work_version` ADD COLUMN `version_hash` VARCHAR(128) DEFAULT NULL',
    'SELECT ''version_hash exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'work_version' AND column_name = 'version_hash');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- evidence：存证创建者签名
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `evidence` ADD COLUMN `creator_signature` VARCHAR(512) DEFAULT NULL',
    'SELECT ''creator_signature exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'evidence' AND column_name = 'creator_signature');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- agreement confirmation：协议确认签名
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `collaboration_agreement_confirmation` ADD COLUMN `signature` VARCHAR(512) DEFAULT NULL',
    'SELECT ''signature exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'collaboration_agreement_confirmation' AND column_name = 'signature');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
