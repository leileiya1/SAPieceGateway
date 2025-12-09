-- =====================================================
-- 网关动态路由配置表（增强版）
-- 整合路由配置 + 权限配置 + 限流配置 + 缓存配置
-- 一个路由 = 路由规则 + 权限控制 + 流量控制
-- =====================================================

DROP TABLE IF EXISTS `sys_gateway_route`;

CREATE TABLE `sys_gateway_route` (
    -- ==================== 基础信息 ====================
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `route_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '路由ID，如 user-service',
    `route_name` VARCHAR(100) COMMENT '路由名称',
    `uri` VARCHAR(255) NOT NULL COMMENT '目标URI，如 http://localhost:8081 或 lb://user-service',

    -- ==================== 路由配置 ====================
    `predicates` JSON COMMENT '断言配置，JSON数组',
    `filters` JSON COMMENT '过滤器配置，JSON数组',
    `metadata` JSON COMMENT '元数据，JSON对象',
    `order_num` INT DEFAULT 0 COMMENT '路由顺序，数字越小优先级越高',

    -- ==================== 权限配置 ====================
    `require_auth` TINYINT DEFAULT 1 COMMENT '是否需要认证：0-否(公开接口) 1-是',
    `permission_code` VARCHAR(500) COMMENT '所需权限标识，多个用逗号分隔，如 system:user:query,system:user:add',
    `permission_logic` VARCHAR(10) DEFAULT 'OR' COMMENT '多权限逻辑：AND(同时拥有) / OR(任一即可)',

    -- ==================== 限流配置 ====================
    `rate_limit_enabled` TINYINT DEFAULT 0 COMMENT '是否启用限流：0-否 1-是',
    `rate_limit_qps` INT DEFAULT 100 COMMENT '限流QPS（每秒请求数）',
    `rate_limit_strategy` VARCHAR(20) DEFAULT 'ip' COMMENT '限流策略：ip/route/user',

    -- ==================== 缓存配置 ====================
    `cache_enabled` TINYINT DEFAULT 0 COMMENT '是否启用响应缓存：0-否 1-是',
    `cache_ttl` INT DEFAULT 300 COMMENT '缓存过期时间（秒）',

    -- ==================== 其他配置 ====================
    `retry_enabled` TINYINT DEFAULT 0 COMMENT '是否启用重试：0-否 1-是',
    `retry_times` INT DEFAULT 3 COMMENT '重试次数',
    `timeout_ms` INT DEFAULT 30000 COMMENT '超时时间（毫秒）',

    -- ==================== 状态与描述 ====================
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `description` VARCHAR(500) COMMENT '路由描述',

    -- ==================== 审计字段 ====================
    `creator` VARCHAR(64) COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX `idx_status` (`status`),
    INDEX `idx_order` (`order_num`),
    INDEX `idx_require_auth` (`require_auth`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关动态路由配置表（整合路由+权限+限流+缓存）';

-- =====================================================
-- 初始化示例数据
-- =====================================================

INSERT INTO `sys_gateway_route` (
    `route_id`, `route_name`, `uri`,
    `predicates`, `filters`,
    `require_auth`, `permission_code`, `permission_logic`,
    `rate_limit_enabled`, `rate_limit_qps`,
    `order_num`, `status`, `description`, `creator`
) VALUES

-- 用户服务路由（需要认证，需要权限）
('user-service', '用户服务', 'http://localhost:8081',
 '[{"name": "Path", "args": {"pattern": "/api/user/**"}}]',
 '[{"name": "StripPrefix", "args": {"parts": "1"}}]',
 1, 'system:user:query', 'OR',
 1, 100,
 0, 1, '用户服务路由，需要登录和用户查询权限', 'system'),

-- 订单服务路由（需要认证，需要权限）
('order-service', '订单服务', 'http://localhost:8082',
 '[{"name": "Path", "args": {"pattern": "/api/order/**"}}]',
 '[{"name": "StripPrefix", "args": {"parts": "1"}}]',
 1, 'system:order:query', 'OR',
 1, 50,
 1, 1, '订单服务路由，需要登录和订单查询权限', 'system'),

-- 公开接口路由（不需要认证）
('public-service', '公开服务', 'http://localhost:8083',
 '[{"name": "Path", "args": {"pattern": "/api/public/**"}}]',
 '[{"name": "StripPrefix", "args": {"parts": "1"}}]',
 0, NULL, NULL,
 1, 200,
 2, 1, '公开接口，无需登录认证', 'system'),

-- 管理后台路由（需要管理员权限）
('admin-service', '管理后台', 'http://localhost:8084',
 '[{"name": "Path", "args": {"pattern": "/api/admin/**"}}]',
 '[{"name": "StripPrefix", "args": {"parts": "1"}}]',
 1, 'system:admin:manage,system:super:admin', 'OR',
 1, 30,
 3, 1, '管理后台路由，需要管理员权限', 'system');


-- =====================================================
-- predicates JSON 格式说明
-- =====================================================
-- [
--     {"name": "Path", "args": {"pattern": "/api/user/**"}},        # 路径匹配
--     {"name": "Method", "args": {"methods": "GET,POST"}},          # HTTP方法
--     {"name": "Header", "args": {"header": "X-Token", "regexp": ".*"}}, # 请求头
--     {"name": "Query", "args": {"param": "token", "regexp": ".*"}},     # 查询参数
--     {"name": "Host", "args": {"patterns": "**.example.com"}},     # 主机名
--     {"name": "RemoteAddr", "args": {"sources": "192.168.1.0/24"}}, # 客户端IP
--     {"name": "Weight", "args": {"group": "service", "weight": "8"}} # 权重(灰度)
-- ]

-- =====================================================
-- filters JSON 格式说明
-- =====================================================
-- [
--     {"name": "StripPrefix", "args": {"parts": "1"}},              # 去除前缀
--     {"name": "AddRequestHeader", "args": {"name": "X-Gateway", "value": "SAPiece"}},
--     {"name": "RewritePath", "args": {"regexp": "/api/(?<segment>.*)", "replacement": "/${segment}"}},
--     {"name": "Retry", "args": {"retries": "3", "statuses": "BAD_GATEWAY"}},
--     {"name": "CircuitBreaker", "args": {"name": "cb", "fallbackUri": "forward:/fallback"}}
-- ]
