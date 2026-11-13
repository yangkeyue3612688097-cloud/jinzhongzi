-- ComfyUI provenance fields + order/revenue-event closed loop
USE aigc_evidence;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_work_generation ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT ''manual'' COMMENT ''manual/comfyui/cloud''',
    'SELECT ''source_type exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_work_generation' AND column_name = 'source_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_work_generation ADD COLUMN workflow_hash VARCHAR(128) DEFAULT NULL',
    'SELECT ''workflow_hash exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_work_generation' AND column_name = 'workflow_hash');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_work_generation ADD COLUMN request_id VARCHAR(128) DEFAULT NULL',
    'SELECT ''request_id exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_work_generation' AND column_name = 'request_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_work_generation ADD COLUMN scheduler VARCHAR(64) DEFAULT NULL',
    'SELECT ''scheduler exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_work_generation' AND column_name = 'scheduler');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE ai_work_generation ADD COLUMN generated_at DATETIME DEFAULT NULL',
    'SELECT ''generated_at exists''')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_work_generation' AND column_name = 'generated_at');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS license_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    work_id BIGINT NOT NULL,
    collaboration_id BIGINT NOT NULL,
    buyer_name VARCHAR(100) DEFAULT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=created,1=paid,2=settled,3=cancelled',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_collaboration (collaboration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS revenue_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    collaboration_id BIGINT NOT NULL,
    work_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=available,1=settled',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order (order_id),
    KEY idx_collaboration (collaboration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
