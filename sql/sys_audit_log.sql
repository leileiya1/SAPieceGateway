-- ============================================================
-- 系统审计日志表
-- 功能：记录用户敏感操作，支持安全审计与问题追溯
-- 说明：此表为纯日志表，无需初始数据；建议定期归档清理
--        默认保留 90 天（可通过 audit.retain-days 配置调整）
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sys_audit_log`;
CREATE TABLE `sys_audit_log` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    -- 链路追踪
    `trace_id`        VARCHAR(64)                         COMMENT '链路追踪ID，关联同一请求的多条日志',
    -- 用户信息
    `user_id`         BIGINT                              COMMENT '操作用户ID',
    `user_name`       VARCHAR(64)                         COMMENT '操作用户名',
    `nick_name`       VARCHAR(64)                         COMMENT '用户昵称',
    -- 操作信息
    `module`          VARCHAR(50) NOT NULL                COMMENT '操作模块：AUTH/USER/ROLE/ROUTE/ADMIN/GRAY/OAUTH',
    `operation`       VARCHAR(50) NOT NULL                COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/QUERY/EXPORT等',
    `description`     VARCHAR(500)                        COMMENT '操作描述',
    -- 请求信息
    `request_method`  VARCHAR(10)                         COMMENT '请求方法：GET/POST/PUT/DELETE',
    `request_url`     VARCHAR(500)                        COMMENT '请求URL',
    `request_params`  TEXT                                COMMENT '请求参数（已脱敏）',
    `request_body`    TEXT                                COMMENT '请求体（已脱敏）',
    -- 响应信息
    `response_code`   INT                                 COMMENT '响应状态码',
    `response_msg`    VARCHAR(500)                        COMMENT '响应消息',
    `response_data`   TEXT                                COMMENT '响应数据（选填，敏感数据不采集）',
    -- 执行结果
    `status`          TINYINT     NOT NULL DEFAULT 1      COMMENT '操作状态：0-失败 1-成功',
    `error_msg`       TEXT                                COMMENT '错误信息（操作失败时记录）',
    `cost_time`       BIGINT                              COMMENT '执行耗时（毫秒）',
    -- 客户端信息
    `client_ip`       VARCHAR(50)                         COMMENT '客户端IP地址',
    `client_location` VARCHAR(100)                        COMMENT '客户端地理位置',
    `user_agent`      VARCHAR(500)                        COMMENT '用户代理（浏览器信息）',
    `device_type`     VARCHAR(20)                         COMMENT '设备类型：PC/Mobile/Tablet/Unknown',
    `browser`         VARCHAR(50)                         COMMENT '浏览器类型',
    `os`              VARCHAR(50)                         COMMENT '操作系统',
    -- OAuth2 扩展
    `oauth_provider`  VARCHAR(30)                         COMMENT 'OAuth2提供商：GITHUB/GOOGLE/WECHAT等',
    `oauth_user_id`   VARCHAR(100)                        COMMENT 'OAuth2用户ID',
    -- 时间
    `operate_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id`      (`user_id`),
    KEY `idx_user_name`    (`user_name`),
    KEY `idx_module`       (`module`),
    KEY `idx_operation`    (`operation`),
    KEY `idx_status`       (`status`),
    KEY `idx_operate_time` (`operate_time`),
    KEY `idx_client_ip`    (`client_ip`),
    KEY `idx_trace_id`     (`trace_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统审计日志表';

SET FOREIGN_KEY_CHECKS = 1;
