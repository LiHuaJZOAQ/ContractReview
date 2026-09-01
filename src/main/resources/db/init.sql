-- ContractReview Database Init Script
-- MySQL 8.0

CREATE DATABASE IF NOT EXISTS contract_review DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE contract_review;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL,
    `password_hash` VARCHAR(256) NOT NULL,
    `review_quota` INT NOT NULL DEFAULT 10,
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
    `version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 用户自定义API配置表
CREATE TABLE IF NOT EXISTS `user_api_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `config_name` VARCHAR(100) NOT NULL DEFAULT '默认配置',
    `api_url` VARCHAR(512) NOT NULL,
    `api_key` VARCHAR(512) NOT NULL,
    `model` VARCHAR(100) NOT NULL,
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `fk_api_config_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审查任务表
CREATE TABLE IF NOT EXISTS `review_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `preview_text` MEDIUMTEXT NULL,
    `file_url` VARCHAR(1024) NULL,
    `contract_type` VARCHAR(50) NULL,
    `user_stance` VARCHAR(50) NULL,
    `status` ENUM('PENDING','PARSING','RETRIEVING','REVIEWING','SUMMARIZING','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
    `progress` INT NOT NULL DEFAULT 0,
    `error_msg` MEDIUMTEXT NULL,
    `total_chunks` INT NULL,
    `reviewed_chunks` INT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at` DATETIME NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_user_status_created` (`user_id`, `status`, `created_at`),
    CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 风险项表
CREATE TABLE IF NOT EXISTS `risk_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `clause_index` INT NOT NULL,
    `clause_content` MEDIUMTEXT NOT NULL,
    `risk_level` ENUM('HIGH','MEDIUM','LOW') NOT NULL,
    `risk_type` VARCHAR(100) NOT NULL,
    `description` MEDIUMTEXT NOT NULL,
    `suggestion` MEDIUMTEXT NOT NULL,
    `related_laws` JSON NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_clause` (`task_id`, `clause_index`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_task_risk` (`task_id`, `risk_level`),
    CONSTRAINT `fk_risk_task` FOREIGN KEY (`task_id`) REFERENCES `review_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审查报告表
CREATE TABLE IF NOT EXISTS `review_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `summary` MEDIUMTEXT NOT NULL,
    `risk_count_high` INT NOT NULL DEFAULT 0,
    `risk_count_medium` INT NOT NULL DEFAULT 0,
    `risk_count_low` INT NOT NULL DEFAULT 0,
    `report_json` JSON NOT NULL,
    `pdf_url` VARCHAR(1024) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`),
    CONSTRAINT `fk_report_task` FOREIGN KEY (`task_id`) REFERENCES `review_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审查过程日志表
CREATE TABLE IF NOT EXISTS `review_process_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `agent` VARCHAR(50) NOT NULL COMMENT 'Agent 名称（如 Agent-A 合同分类）',
    `content` MEDIUMTEXT NOT NULL COMMENT 'Agent 输出内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    CONSTRAINT `fk_log_task` FOREIGN KEY (`task_id`) REFERENCES `review_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `action` VARCHAR(50) NOT NULL COMMENT 'REGISTER / UPLOAD / SUBMIT / VIEW_REPORT / RETRY',
    `task_id` BIGINT NULL,
    `detail` JSON NULL,
    `ip_address` VARCHAR(45) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_created_at` (`created_at`),
    CONSTRAINT `fk_oplog_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 法律法规表
CREATE TABLE IF NOT EXISTS `law` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL,
    `category` VARCHAR(50) NULL COMMENT '法律类别：民法、劳动法、知识产权等',
    `content` MEDIUMTEXT NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1-启用 0-禁用',
    `created_by` BIGINT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_title` (`title`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_law_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
