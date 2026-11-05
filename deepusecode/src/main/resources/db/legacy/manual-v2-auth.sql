-- ============================================================
-- 邮箱 / 手机号注册功能所需的表结构变更
--
-- 背景：Hibernate 的 ddl-auto=update 只会「新增表 / 新增列」，
--       不会给**已存在的表**补索引或唯一约束。
--       所以老库升级到本版本时，必须手工执行下面两条 ALTER。
--
-- 全新部署（空库）不需要执行，JPA 建表时会自带唯一约束。
-- ============================================================

-- 执行前先确认没有重复值，否则 ALTER 会失败：
--   SELECT email, COUNT(*) c FROM app_user WHERE email IS NOT NULL GROUP BY email HAVING c > 1;
--   SELECT phone, COUNT(*) c FROM app_user WHERE phone IS NOT NULL GROUP BY phone HAVING c > 1;

ALTER TABLE app_user ADD UNIQUE KEY uk_app_user_email (email);
ALTER TABLE app_user ADD UNIQUE KEY uk_app_user_phone (phone);

-- 说明：MySQL 的唯一索引允许多个 NULL，
-- 所以「只有手机号没有邮箱」和「只有邮箱没有手机号」的用户可以共存。
