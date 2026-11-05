-- ============================================================
-- V1 基线：角色工坊全部业务表
--
-- 历史上这些表是由 Hibernate ddl-auto=update 自动生成的，
-- 现在冻结成迁移脚本，从此 schema 由 Flyway 单一管理。
--
-- 对已有库：spring.flyway.baseline-on-migrate + baseline-version=1
--   会把本文件记为"已执行"（不真正跑），从而与旧库对齐。
-- 对全新库：直接执行本文件。
--
-- 注意：早期版本的 character_template / chat_history / questionnaire_answer
-- 三张表已废弃，不再纳入基线；老库可以手工 DROP。
-- ============================================================

CREATE TABLE IF NOT EXISTS `app_user` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `account`     varchar(150) NOT NULL,
  `create_time` datetime(6)  DEFAULT NULL,
  `email`       varchar(150) DEFAULT NULL,
  `password`    varchar(100) NOT NULL,
  `phone`       varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_email` (`email`),
  UNIQUE KEY `uk_app_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `question_set` (
  `id`             bigint       NOT NULL AUTO_INCREMENT,
  `answer_count`   int          NOT NULL,
  `created_at`     datetime(6)  DEFAULT NULL,
  `deleted`        int          NOT NULL,
  `description`    varchar(500) DEFAULT NULL,
  `import_count`   int          NOT NULL,
  `owner_id`       bigint       NOT NULL,
  `question_count` int          NOT NULL,
  `title`          varchar(120) NOT NULL,
  `updated_at`     datetime(6)  DEFAULT NULL,
  `visibility`     int          NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_set_owner`  (`owner_id`,`deleted`),
  KEY `idx_set_public` (`visibility`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `question` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `content`    varchar(500) NOT NULL,
  `set_id`     bigint       NOT NULL,
  `sort_order` int          NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_question_set` (`set_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `answer_sheet` (
  `id`                 bigint        NOT NULL AUTO_INCREMENT,
  `created_at`         datetime(6)   DEFAULT NULL,
  `label`              varchar(60)   DEFAULT NULL,
  `note`               varchar(1000) DEFAULT NULL,
  `persona_age`        int           DEFAULT NULL,
  `persona_name`       varchar(50)   DEFAULT NULL,
  `persona_occupation` varchar(50)   DEFAULT NULL,
  `set_id`             bigint        NOT NULL,
  `updated_at`         datetime(6)   DEFAULT NULL,
  `user_id`            bigint        NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sheet_set_user` (`set_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `answer` (
  `id`          bigint        NOT NULL AUTO_INCREMENT,
  `content`     varchar(2000) DEFAULT NULL,
  `question_id` bigint        NOT NULL,
  `sheet_id`    bigint        NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_answer_sheet_question` (`sheet_id`,`question_id`),
  KEY `idx_answer_sheet` (`sheet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ai_template` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `content`     mediumtext   NOT NULL,
  `created_at`  datetime(6)  DEFAULT NULL,
  `deleted`     int          NOT NULL,
  `gen_error`   varchar(500) DEFAULT NULL,
  `gen_model`   varchar(60)  DEFAULT NULL,
  `gen_status`  int          NOT NULL,
  `name`        varchar(80)  NOT NULL,
  `owner_id`    bigint       NOT NULL,
  `raw_json`    mediumtext,
  `set_id`      bigint       DEFAULT NULL,
  `sheet_id`    bigint       DEFAULT NULL,
  `updated_at`  datetime(6)  DEFAULT NULL,
  `use_count`   int          NOT NULL,
  `visibility`  int          NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tpl_owner`  (`owner_id`,`deleted`),
  KEY `idx_tpl_public` (`visibility`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `conversation` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `created_at`  datetime(6)  DEFAULT NULL,
  `deleted`     int          NOT NULL,
  `template_id` bigint       NOT NULL,
  `title`       varchar(120) DEFAULT NULL,
  `updated_at`  datetime(6)  DEFAULT NULL,
  `user_id`     bigint       NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_conv_user` (`user_id`,`deleted`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `message` (
  `id`              bigint     NOT NULL AUTO_INCREMENT,
  `content`         mediumtext NOT NULL,
  `conversation_id` bigint     NOT NULL,
  `created_at`      datetime(6) DEFAULT NULL,
  `role`            varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_msg_conv` (`conversation_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
