-- =====================================================
-- 系统审计日志表
-- 记录用户敏感操作，用于安全审计和问题追溯
-- =====================================================

DROP TABLE IF EXISTS `sys_audit_log`;

CREATE TABLE `sys_audit_log` (
    -- ==================== 基础信息 ====================
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `trace_id` VARCHAR(64) COMMENT '链路追踪ID，用于关联同一请求的多条日志',

    -- ==================== 用户信息 ====================
    `user_id` BIGINT COMMENT '操作用户ID',
    `user_name` VARCHAR(64) COMMENT '操作用户名',
    `nick_name` VARCHAR(64) COMMENT '用户昵称',

    -- ==================== 操作信息 ====================
    `module` VARCHAR(50) NOT NULL COMMENT '操作模块：AUTH-认证、USER-用户、ROLE-角色、ROUTE-路由、ADMIN-管理',
    `operation` VARCHAR(50) NOT NULL COMMENT '操作类型：LOGIN-登录、LOGOUT-登出、PASSWORD_CHANGE-修改密码、CREATE-新增、UPDATE-修改、DELETE-删除、QUERY-查询、EXPORT-导出',
    `description` VARCHAR(500) COMMENT '操作描述',

    -- ==================== 请求信息 ====================
    `request_method` VARCHAR(10) COMMENT '请求方法：GET、POST、PUT、DELETE等',
    `request_url` VARCHAR(500) COMMENT '请求URL',
    `request_params` TEXT COMMENT '请求参数（脱敏后）',
    `request_body` TEXT COMMENT '请求体（脱敏后）',

    -- ==================== 响应信息 ====================
    `response_code` INT COMMENT '响应状态码',
    `response_msg` VARCHAR(500) COMMENT '响应消息',
    `response_data` TEXT COMMENT '响应数据（可选，敏感操作可记录）',

    -- ==================== 执行信息 ====================
    `status` TINYINT DEFAULT 1 COMMENT '操作状态：0-失败 1-成功',
    `error_msg` TEXT COMMENT '错误信息（操作失败时记录）',
    `cost_time` BIGINT COMMENT '执行耗时（毫秒）',

    -- ==================== 客户端信息 ====================
    `client_ip` VARCHAR(50) COMMENT '客户端IP地址',
    `client_location` VARCHAR(100) COMMENT '客户端地理位置',
    `user_agent` VARCHAR(500) COMMENT '用户代理（浏览器信息）',
    `device_type` VARCHAR(20) COMMENT '设备类型：PC、Mobile、Tablet、Unknown',
    `browser` VARCHAR(50) COMMENT '浏览器类型',
    `os` VARCHAR(50) COMMENT '操作系统',

    -- ==================== OAuth2相关 ====================
    `oauth_provider` VARCHAR(30) COMMENT 'OAuth2提供商：GITHUB、GOOGLE、WECHAT等',
    `oauth_user_id` VARCHAR(100) COMMENT 'OAuth2用户ID',

    -- ==================== 时间信息 ====================
    `operate_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    -- ==================== 索引 ====================
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_name` (`user_name`),
    INDEX `idx_module` (`module`),
    INDEX `idx_operation` (`operation`),
    INDEX `idx_status` (`status`),
    INDEX `idx_operate_time` (`operate_time`),
    INDEX `idx_client_ip` (`client_ip`),
    INDEX `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统审计日志表';


-- =====================================================
-- 操作模块枚举说明
-- =====================================================
-- AUTH    - 认证模块（登录、登出、Token刷新等）
-- USER    - 用户模块（用户增删改查、修改密码等）
-- ROLE    - 角色模块（角色增删改查、权限分配等）
-- MENU    - 菜单模块（菜单增删改查等）
-- ROUTE   - 路由模块（网关路由配置等）
-- ADMIN   - 管理模块（IP黑白名单、系统配置等）
-- GRAY    - 灰度模块（灰度发布配置等）
-- OAUTH   - OAuth2模块（第三方登录等）

-- =====================================================
-- 操作类型枚举说明
-- =====================================================
-- LOGIN           - 用户登录
-- LOGOUT          - 用户登出
-- LOGIN_FAILED    - 登录失败
-- PASSWORD_CHANGE - 修改密码
-- TOKEN_REFRESH   - Token刷新
-- CREATE          - 新增
-- UPDATE          - 修改
-- DELETE          - 删除
-- QUERY           - 查询
-- EXPORT          - 导出
-- IMPORT          - 导入
-- ENABLE          - 启用
-- DISABLE         - 禁用
-- OAUTH_LOGIN     - OAuth2登录
-- OAUTH_BINDΡ      - OAuth2绑定
-- OAUTH_UNBIND    - OAuth2解绑
