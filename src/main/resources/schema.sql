-- 创建数据库（如未存在）
CREATE DATABASE IF NOT EXISTS `web_tracing` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `web_tracing`;

-- 埋点事件表：保存前端上报的每条事件的原始 JSON 及类型
CREATE TABLE IF NOT EXISTS `trace_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_type` VARCHAR(64) NULL COMMENT '事件类型，如 PV/CLICK/ERROR/HTTP 等',
  `app_code` VARCHAR(128) NULL COMMENT '应用标识 appCode',
  `app_name` VARCHAR(256) NULL COMMENT '应用名称 appName',
  `session_id` VARCHAR(128) NULL COMMENT '会话ID',
  `payload` LONGTEXT NULL COMMENT '事件原始JSON载荷，保留完整结构用于分析',
  `created_at` DATETIME NULL COMMENT '事件入库时间（服务端接收时间）',
  PRIMARY KEY (`id`),
  KEY `idx_event_type_created_at` (`event_type`, `created_at`),
  KEY `idx_appcode_created_at` (`app_code`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前端埋点事件记录';

-- 已包含结构化列，无需二次 ALTER

-- 基线信息表：保存每次上报的基础环境信息的原始 JSON
CREATE TABLE IF NOT EXISTS `base_info_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `payload` LONGTEXT NULL COMMENT '基线信息原始JSON载荷',
  `created_at` DATETIME NULL COMMENT '上报入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基线环境信息记录';
-- 用户表：持久化注册用户
CREATE TABLE IF NOT EXISTS `user_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(128) NOT NULL,
  `password_hash` VARCHAR(256) NOT NULL,
  `salt` VARCHAR(64) NOT NULL,
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER',
  `created_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台用户账户';
-- 应用信息表
CREATE TABLE IF NOT EXISTS `application_info` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `app_name` VARCHAR(64) NOT NULL,
  `app_code` VARCHAR(32) NOT NULL,
  `app_code_prefix` VARCHAR(64) NOT NULL,
  `app_desc` VARCHAR(1000) NULL,
  `app_managers` LONGTEXT NULL COMMENT 'JSON数组，应用管理员用户名列表',
  `created_at` DATETIME NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用信息管理';

-- 已在建表语句中包含 app_managers 字段，移除重复 ALTER 以避免启动失败
