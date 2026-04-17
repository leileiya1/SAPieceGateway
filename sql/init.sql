-- ============================================================
-- SAPiece Gateway 数据库初始化脚本（总入口）
-- 执行顺序：核心表 → 路由表 → 日志表 → 灰度&OAuth表
--
-- 使用方式：
--   mysql -h <host> -u <user> -p<password> <database> < sql/init.sql
--
-- 注意：
--   1. 执行前请确认目标数据库已存在
--   2. 所有 DROP TABLE IF EXISTS 会清空现有数据，请谨慎在生产环境执行
--   3. OAuth 提供商的 client_id / client_secret 需替换为真实值
-- ============================================================

SET NAMES utf8mb4;

SOURCE sys_user_role_menu.sql;
SOURCE sys_gateway_route.sql;
SOURCE sys_audit_log.sql;
SOURCE sys_gray_oauth.sql;
