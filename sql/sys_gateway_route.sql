-- ============================================================
-- 网关动态路由配置表
-- 功能：整合路由规则 + 权限控制 + 限流 + 缓存配置
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sys_gateway_route`;
CREATE TABLE `sys_gateway_route` (
    `id`                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `route_id`            VARCHAR(64)     NOT NULL                 COMMENT '路由ID（唯一标识），如：user-service',
    `route_name`          VARCHAR(100)                             COMMENT '路由名称（中文描述）',
    `uri`                 VARCHAR(255)    NOT NULL                 COMMENT '目标URI，支持 http://... 或 lb://服务名',
    `predicates`          JSON                                     COMMENT '断言配置（JSON数组），如：[{"name":"Path","args":{"pattern":"/api/**"}}]',
    `filters`             JSON                                     COMMENT '过滤器配置（JSON数组），如：[{"name":"StripPrefix","args":{"parts":"1"}}]',
    `metadata`            JSON                                     COMMENT '元数据（JSON对象），可存自定义扩展信息',
    `order_num`           INT             NOT NULL DEFAULT 0       COMMENT '路由顺序，数字越小优先级越高',
    -- 权限配置
    `require_auth`        TINYINT         NOT NULL DEFAULT 1       COMMENT '是否需要认证：0-否（公开接口） 1-是',
    `permission_code`     VARCHAR(500)                             COMMENT '所需权限标识，多个用英文逗号分隔，为空表示只需登录',
    `permission_logic`    VARCHAR(10)     NOT NULL DEFAULT 'OR'    COMMENT '多权限逻辑：AND-全部拥有 OR-拥有任一（默认）',
    -- 限流配置
    `rate_limit_enabled`  TINYINT         NOT NULL DEFAULT 0       COMMENT '是否启用限流：0-否 1-是',
    `rate_limit_qps`      INT             NOT NULL DEFAULT 100     COMMENT '限流QPS（每秒请求数）',
    `rate_limit_strategy` VARCHAR(20)     NOT NULL DEFAULT 'ip'    COMMENT '限流策略：ip / route / user',
    -- 缓存配置
    `cache_enabled`       TINYINT         NOT NULL DEFAULT 0       COMMENT '是否启用响应缓存：0-否 1-是',
    `cache_ttl`           INT             NOT NULL DEFAULT 300     COMMENT '缓存过期时间（秒）',
    -- 重试配置
    `retry_enabled`       TINYINT                  DEFAULT 0       COMMENT '是否启用重试：0-否 1-是',
    `retry_times`         INT                      DEFAULT 3       COMMENT '最大重试次数',
    `timeout_ms`          INT                      DEFAULT 30000   COMMENT '请求超时时间（毫秒）',
    -- 状态与描述
    `status`              TINYINT         NOT NULL DEFAULT 1       COMMENT '状态：0-禁用 1-启用',
    `description`         VARCHAR(500)                             COMMENT '路由描述',
    -- 审计字段
    `creator`             VARCHAR(64)                              COMMENT '创建人',
    `create_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             VARCHAR(64)                              COMMENT '更新人',
    `update_time`         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_route_id`       (`route_id`),
    KEY `idx_status`               (`status`),
    KEY `idx_order`                (`order_num`),
    KEY `idx_require_auth`         (`require_auth`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '网关动态路由配置表（路由+权限+限流+缓存）';

-- ============================================================
-- 初始路由数据
-- 说明：
--   uri 中 http://localhost:xxxx 仅用于本地开发环境，
--   生产环境应替换为实际服务地址或使用 lb://服务名（需注册中心）
-- ============================================================
INSERT INTO `sys_gateway_route`
    (`route_id`, `route_name`, `uri`, `predicates`, `filters`, `order_num`,
     `require_auth`, `permission_code`, `permission_logic`,
     `rate_limit_enabled`, `rate_limit_qps`, `rate_limit_strategy`,
     `cache_enabled`, `cache_ttl`,
     `retry_enabled`, `retry_times`, `timeout_ms`,
     `status`, `description`, `creator`)
VALUES
-- 公开服务：无需登录
(
    'public-service', '公开服务', 'http://localhost:8083',
    '[{"name":"Path","args":{"pattern":"/api/public/**"}}]',
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',
    0, 0, '', 'OR',
    1, 200, 'ip',
    1, 300,
    0, 3, 30000,
    1, '公开接口，无需登录认证', 'system'
),
-- 用户服务：需要登录
(
    'user-service', '用户服务', 'http://localhost:8081',
    '[{"name":"Path","args":{"pattern":"/api/user/**"}}]',
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',
    1, 1, 'system:user:query', 'OR',
    1, 100, 'ip',
    1, 300,
    0, 3, 30000,
    1, '用户服务路由，需要登录和用户查询权限', 'system'
),
-- 订单服务：需要订单查询权限
(
    'order-service', '订单服务', 'http://localhost:8082',
    '[{"name":"Path","args":{"pattern":"/api/order/**"}}]',
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',
    2, 1, 'system:order:query', 'OR',
    1, 50, 'ip',
    1, 300,
    0, 3, 30000,
    1, '订单服务路由，需要登录和订单查询权限', 'system'
),
-- 管理后台：需要管理员权限（任一即可）
(
    'admin-service', '管理后台', 'http://localhost:8084',
    '[{"name":"Path","args":{"pattern":"/api/admin/**"}}]',
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',
    3, 1, 'system:admin:manage,system:super:admin', 'OR',
    1, 30, 'ip',
    0, 300,
    0, 3, 30000,
    1, '管理后台路由，需要管理员权限（任一即可）', 'system'
),
-- 角色/系统服务：需要角色列表权限
(
    'role-service', '角色服务', 'http://localhost:8082',
    '[{"name":"Path","args":{"pattern":"/api/system/**"}}]',
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',
    4, 1, 'system:role:list', 'OR',
    1, 100, 'ip',
    1, 300,
    0, 3, 30000,
    1, '角色服务路由', 'system'
),
-- 测试服务：需要登录，无特定权限
(
    'test-service', '测试服务', 'http://localhost:9001',
    '[{"name":"Path","args":{"pattern":"/test/**"}}]',
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',
    99, 1, NULL, 'OR',
    0, 100, 'ip',
    0, 300,
    0, 3, 30000,
    1, '测试服务路由，需要登录', 'admin'
);

SET FOREIGN_KEY_CHECKS = 1;
