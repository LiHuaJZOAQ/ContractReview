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
    `version` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
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
    `error_msg` TEXT NULL,
    `total_chunks` INT NULL,
    `reviewed_chunks` INT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at` DATETIME NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 风险项表
CREATE TABLE IF NOT EXISTS `risk_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `clause_index` INT NOT NULL,
    `clause_content` TEXT NOT NULL,
    `risk_level` ENUM('HIGH','MEDIUM','LOW') NOT NULL,
    `risk_type` VARCHAR(100) NOT NULL,
    `description` TEXT NOT NULL,
    `suggestion` TEXT NOT NULL,
    `related_laws` JSON NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_task_risk` (`task_id`, `risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 审查报告表
CREATE TABLE IF NOT EXISTS `review_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL,
    `summary` TEXT NOT NULL,
    `risk_count_high` INT NOT NULL DEFAULT 0,
    `risk_count_medium` INT NOT NULL DEFAULT 0,
    `risk_count_low` INT NOT NULL DEFAULT 0,
    `report_json` JSON NOT NULL,
    `pdf_url` VARCHAR(1024) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`)
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
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_action` (`action`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
