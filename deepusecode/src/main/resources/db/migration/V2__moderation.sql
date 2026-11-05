-- ============================================================
-- V2：内容审核与下架能力
--
-- 1. 用户角色（USER / ADMIN）
-- 2. 公开内容的强制下架字段（与作者自己的"私密"是两个维度）
-- 3. 举报表
-- ============================================================

-- ---------- 1. 用户角色 ----------
ALTER TABLE app_user ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER' AFTER password;

-- ---------- 2. 下架字段 ----------
-- taken_down 与 visibility 是正交的两件事：
--   visibility=1 是作者主动公开；taken_down=1 是管理员强制下架。
--   只要 taken_down=1，无论作者怎么切 visibility，内容都不出现在广场。
ALTER TABLE question_set
  ADD COLUMN taken_down INT NOT NULL DEFAULT 0,
  ADD COLUMN takedown_reason VARCHAR(200) NULL,
  ADD COLUMN taken_down_at DATETIME(6) NULL;

ALTER TABLE ai_template
  ADD COLUMN taken_down INT NOT NULL DEFAULT 0,
  ADD COLUMN takedown_reason VARCHAR(200) NULL,
  ADD COLUMN taken_down_at DATETIME(6) NULL;

-- 公开列表会带上 taken_down 过滤，补进复合索引
ALTER TABLE question_set
  DROP INDEX idx_set_public,
  ADD KEY idx_set_public (visibility, taken_down, deleted, created_at);

ALTER TABLE ai_template
  DROP INDEX idx_tpl_public,
  ADD KEY idx_tpl_public (visibility, taken_down, deleted, created_at);

-- ---------- 3. 举报表 ----------
CREATE TABLE IF NOT EXISTS `content_report` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `reporter_id` bigint       NOT NULL,
  `target_type` varchar(20)  NOT NULL COMMENT 'SET / TEMPLATE',
  `target_id`   bigint       NOT NULL,
  `reason`      varchar(50)  NOT NULL COMMENT '违规分类',
  `detail`      varchar(500) DEFAULT NULL,
  `status`      varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / TAKEN_DOWN / REJECTED',
  `handled_by`  bigint       DEFAULT NULL,
  `handled_at`  datetime(6)  DEFAULT NULL,
  `handle_note` varchar(200) DEFAULT NULL,
  `created_at`  datetime(6)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  -- 同一个人对同一内容只能举报一次，防止刷举报
  UNIQUE KEY `uk_report_once` (`reporter_id`,`target_type`,`target_id`),
  KEY `idx_report_status` (`status`,`created_at`),
  KEY `idx_report_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
