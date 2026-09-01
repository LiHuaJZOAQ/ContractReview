-- ContractReview Database Init Script
-- MySQL 8.0

CREATE DATABASE IF NOT EXISTS contract_review DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE contract_review;

-- 用户表：存储系统注册用户的基本信息、角色与审查配额
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名，唯一',
    `password_hash` VARCHAR(256) NOT NULL COMMENT '加密后的密码哈希值',
    `review_quota` INT NOT NULL DEFAULT 10 COMMENT '剩余审查次数配额',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色：USER / ADMIN',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户自定义API配置表：存储用户自行配置的大模型 API 接入信息
CREATE TABLE IF NOT EXISTS `user_api_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置主键ID',
    `user_id` BIGINT NOT NULL COMMENT '关联用户ID',
    `config_name` VARCHAR(100) NOT NULL DEFAULT '默认配置' COMMENT '配置名称',
    `api_url` VARCHAR(512) NOT NULL COMMENT '大模型 API 请求地址',
    `api_key` VARCHAR(512) NOT NULL COMMENT '大模型 API 密钥（加密存储）',
    `model` VARCHAR(100) NOT NULL COMMENT '使用的模型名称，如 gpt-4o',
    `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用 0-禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
    CONSTRAINT `fk_api_config_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户自定义API配置表';

-- 审查任务表：记录每次合同审查任务的全生命周期状态
CREATE TABLE IF NOT EXISTS `review_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务主键ID',
    `user_id` BIGINT NOT NULL COMMENT '发起任务的用户ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '上传的合同文件名',
    `file_size` BIGINT NOT NULL COMMENT '文件大小（字节）',
    `preview_text` MEDIUMTEXT NULL COMMENT '合同文本预览内容（前N字符）',
    `file_url` VARCHAR(1024) NULL COMMENT '文件存储路径或对象存储URL',
    `contract_type` VARCHAR(50) NULL COMMENT '合同类型，如：劳动合同、采购合同',
    `user_stance` VARCHAR(50) NULL COMMENT '用户立场，如：甲方 / 乙方',
    `status` ENUM('PENDING','PARSING','RETRIEVING','REVIEWING','SUMMARIZING','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING-待处理 PARSING-解析中 RETRIEVING-检索法规 REVIEWING-审查中 SUMMARIZING-汇总中 PROCESSING-处理中 SUCCESS-成功 FAILED-失败',
    `progress` INT NOT NULL DEFAULT 0 COMMENT '任务进度百分比（0-100）',
    `error_msg` MEDIUMTEXT NULL COMMENT '任务失败时的错误信息',
    `total_chunks` INT NULL COMMENT '合同文本分块总数',
    `reviewed_chunks` INT NULL DEFAULT 0 COMMENT '已完成审查的分块数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    `completed_at` DATETIME NULL COMMENT '任务完成时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
    KEY `idx_status` (`status`) COMMENT '任务状态索引',
    KEY `idx_created_at` (`created_at`) COMMENT '创建时间索引',
    KEY `idx_user_status_created` (`user_id`, `status`, `created_at`) COMMENT '用户+状态+时间联合索引，用于列表查询',
    CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审查任务表';

-- 风险项表：存储每个审查任务识别出的合同风险条款详情
CREATE TABLE IF NOT EXISTS `risk_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '风险项主键ID',
    `task_id` BIGINT NOT NULL COMMENT '关联的审查任务ID',
    `clause_index` INT NOT NULL COMMENT '风险条款在合同中的分块序号',
    `clause_content` MEDIUMTEXT NOT NULL COMMENT '风险条款的原文内容',
    `risk_level` ENUM('HIGH','MEDIUM','LOW') NOT NULL COMMENT '风险等级：HIGH-高 MEDIUM-中 LOW-低',
    `risk_type` VARCHAR(100) NOT NULL COMMENT '风险类型，如：付款条款、违约责任',
    `description` MEDIUMTEXT NOT NULL COMMENT '风险描述：说明存在的具体风险',
    `suggestion` MEDIUMTEXT NOT NULL COMMENT '修改建议：针对该风险的处理建议',
    `related_laws` JSON NULL COMMENT '相关法律法规引用列表（JSON数组）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_clause` (`task_id`, `clause_index`) COMMENT '同一任务内条款序号唯一',
    KEY `idx_task_id` (`task_id`) COMMENT '任务ID索引',
    KEY `idx_task_risk` (`task_id`, `risk_level`) COMMENT '任务+风险等级联合索引，用于按等级过滤',
    CONSTRAINT `fk_risk_task` FOREIGN KEY (`task_id`) REFERENCES `review_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风险项表';

-- 审查报告表：存储审查任务完成后生成的汇总报告
CREATE TABLE IF NOT EXISTS `review_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告主键ID',
    `task_id` BIGINT NOT NULL COMMENT '关联的审查任务ID',
    `summary` MEDIUMTEXT NOT NULL COMMENT '报告总结文本',
    `risk_count_high` INT NOT NULL DEFAULT 0 COMMENT '高风险项数量',
    `risk_count_medium` INT NOT NULL DEFAULT 0 COMMENT '中风险项数量',
    `risk_count_low` INT NOT NULL DEFAULT 0 COMMENT '低风险项数量',
    `report_json` JSON NOT NULL COMMENT '完整报告结构化数据（JSON格式）',
    `pdf_url` VARCHAR(1024) NULL COMMENT '导出的PDF报告存储路径或URL',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报告生成时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_id` (`task_id`) COMMENT '每个任务仅对应一份报告',
    CONSTRAINT `fk_report_task` FOREIGN KEY (`task_id`) REFERENCES `review_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审查报告表';

-- 审查过程日志表：记录各 Agent 在审查过程中产生的中间输出，供前端流式展示
CREATE TABLE IF NOT EXISTS `review_process_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志主键ID',
    `task_id` BIGINT NOT NULL COMMENT '关联的审查任务ID',
    `agent` VARCHAR(50) NOT NULL COMMENT 'Agent 名称，如：Agent-A 合同分类、Agent-B 风险识别',
    `content` MEDIUMTEXT NOT NULL COMMENT 'Agent 输出的日志内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志记录时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`) COMMENT '任务ID索引',
    CONSTRAINT `fk_log_task` FOREIGN KEY (`task_id`) REFERENCES `review_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审查过程日志表';

-- 操作日志表：记录用户在系统中的关键操作行为，用于审计与追踪
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志主键ID',
    `user_id` BIGINT NOT NULL COMMENT '操作用户ID',
    `action` VARCHAR(50) NOT NULL COMMENT '操作类型：REGISTER-注册 UPLOAD-上传 SUBMIT-提交审查 VIEW_REPORT-查看报告 RETRY-重试',
    `task_id` BIGINT NULL COMMENT '关联的任务ID（无任务关联时为空）',
    `detail` JSON NULL COMMENT '操作附加详情（JSON格式）',
    `ip_address` VARCHAR(45) NULL COMMENT '操作来源IP地址（支持IPv6）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`) COMMENT '用户ID索引',
    KEY `idx_action` (`action`) COMMENT '操作类型索引',
    KEY `idx_created_at` (`created_at`) COMMENT '操作时间索引',
    CONSTRAINT `fk_oplog_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- 法律法规表：存储系统内置或管理员维护的法律法规条文，供 RAG 检索使用
CREATE TABLE IF NOT EXISTS `law` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '法规主键ID',
    `title` VARCHAR(200) NOT NULL COMMENT '法律法规标题，唯一',
    `category` VARCHAR(50) NULL COMMENT '法律类别：民法、劳动法、知识产权等',
    `content` MEDIUMTEXT NOT NULL COMMENT '法律法规正文内容',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态：1-启用 0-禁用',
    `created_by` BIGINT NULL COMMENT '创建该条目的管理员用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标志：0-正常 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_title` (`title`) COMMENT '法规标题唯一索引',
    KEY `idx_category` (`category`) COMMENT '法律类别索引',
    KEY `idx_status` (`status`) COMMENT '启用状态索引',
    CONSTRAINT `fk_law_creator` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律法规表';

