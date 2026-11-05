-- ============================================================
-- V3：角色基本信息改为「昵称 + 用户自定义类别」
--
-- 背景：原来 answer_sheet 里写死了 persona_age / persona_occupation 两列，
-- 现在内置字段只保留昵称，其余由用户自己定义类别（如 职业/年龄/兴趣/口头禅…）。
--
-- 迁移策略：老数据里的年龄、职业转成两条自定义字段，保证已有答卷不丢内容。
-- ============================================================

CREATE TABLE IF NOT EXISTS `persona_field` (
  `id`         bigint       NOT NULL AUTO_INCREMENT,
  `sheet_id`   bigint       NOT NULL,
  `label`      varchar(50)  NOT NULL COMMENT '类别名，如 职业 / 年龄 / 兴趣爱好',
  `field_value` varchar(500) NOT NULL COMMENT '该类别下的内容（value 在 H2 是保留字，故加前缀）',
  `sort_order` int          NOT NULL COMMENT '排序，决定生成提示词时的呈现顺序',
  PRIMARY KEY (`id`),
  KEY `idx_persona_sheet` (`sheet_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 老数据迁移：年龄 -> 自定义字段「年龄」
INSERT INTO persona_field (sheet_id, label, field_value, sort_order)
SELECT id, '年龄', CAST(persona_age AS CHAR), 0
FROM answer_sheet
WHERE persona_age IS NOT NULL;

-- 老数据迁移：职业 -> 自定义字段「职业」
INSERT INTO persona_field (sheet_id, label, field_value, sort_order)
SELECT id, '职业', persona_occupation, 1
FROM answer_sheet
WHERE persona_occupation IS NOT NULL AND persona_occupation <> '';

-- 迁移完成后删除写死的两列
ALTER TABLE answer_sheet
  DROP COLUMN persona_age,
  DROP COLUMN persona_occupation;
