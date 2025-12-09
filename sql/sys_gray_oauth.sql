-- =====================================================
-- 灰度发布规则配置表
-- 支持按用户ID、用户标签、IP、Header、比例等多种灰度策略
-- =====================================================

DROP TABLE IF EXISTS `sys_gray_rule`;

CREATE TABLE `sys_gray_rule` (
    -- ==================== 基础信息 ====================
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
    `rule_code` VARCHAR(64) NOT NULL UNIQUE COMMENT '规则编码（唯一标识）',
    `description` VARCHAR(500) COMMENT '规则描述',

    -- ==================== 路由关联 ====================
    `service_id` VARCHAR(64) NOT NULL COMMENT '服务ID，关联 sys_gateway_route.route_id',
    `target_uri` VARCHAR(255) NOT NULL COMMENT '灰度目标URI（新版本服务地址）',
    `stable_uri` VARCHAR(255) COMMENT '稳定版本URI（不配置则使用路由默认URI）',

    -- ==================== 灰度策略 ====================
    `strategy_type` VARCHAR(20) NOT NULL COMMENT '策略类型：USER_ID-按用户ID、USER_TAG-按用户标签、IP-按IP、HEADER-按请求头、WEIGHT-按比例、PARAM-按请求参数',

    -- ==================== 策略配置（JSON格式） ====================
    `strategy_config` JSON NOT NULL COMMENT '策略配置，不同策略类型对应不同结构',
    -- USER_ID: {"userIds": [1,2,3,4,5]}
    -- USER_TAG: {"tags": ["beta_user", "vip"]}
    -- IP: {"ips": ["192.168.1.100", "10.0.0.0/8"]}
    -- HEADER: {"headerName": "X-Gray-Tag", "headerValues": ["beta", "canary"]}
    -- WEIGHT: {"grayWeight": 10} -- 10%流量走灰度
    -- PARAM: {"paramName": "version", "paramValues": ["v2", "beta"]}

    -- ==================== 优先级与状态 ====================
    `priority` INT DEFAULT 0 COMMENT '优先级，数字越小优先级越高',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',

    -- ==================== 生效时间（可选） ====================
    `effective_start` DATETIME COMMENT '生效开始时间（为空表示立即生效）',
    `effective_end` DATETIME COMMENT '生效结束时间（为空表示永久有效）',

    -- ==================== 审计字段 ====================
    `creator` VARCHAR(64) COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- ==================== 索引 ====================
    INDEX `idx_service_id` (`service_id`),
    INDEX `idx_strategy_type` (`strategy_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_priority` (`priority`),
    INDEX `idx_effective_time` (`effective_start`, `effective_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='灰度发布规则配置表';


-- =====================================================
-- OAuth2第三方登录配置表
-- 支持GitHub、Google、微信等第三方登录
-- =====================================================

DROP TABLE IF EXISTS `sys_oauth_config`;

CREATE TABLE `sys_oauth_config` (
    -- ==================== 基础信息 ====================
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `provider` VARCHAR(30) NOT NULL UNIQUE COMMENT 'OAuth提供商：GITHUB、GOOGLE、WECHAT、GITEE、QQ等',
    `provider_name` VARCHAR(50) COMMENT '提供商名称（显示用）',

    -- ==================== OAuth配置 ====================
    `client_id` VARCHAR(200) NOT NULL COMMENT '客户端ID（从第三方平台获取）',
    `client_secret` VARCHAR(500) NOT NULL COMMENT '客户端密钥（加密存储）',
    `authorization_uri` VARCHAR(500) COMMENT '授权地址',
    `token_uri` VARCHAR(500) COMMENT '获取Token地址',
    `user_info_uri` VARCHAR(500) COMMENT '获取用户信息地址',
    `redirect_uri` VARCHAR(500) COMMENT '回调地址',
    `scopes` VARCHAR(200) COMMENT '授权范围，多个用逗号分隔',

    -- ==================== 状态配置 ====================
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',

    -- ==================== 审计字段 ====================
    `creator` VARCHAR(64) COMMENT '创建人',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) COMMENT '更新人',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2第三方登录配置表';


-- =====================================================
-- OAuth2用户绑定关系表
-- 记录系统用户与第三方账号的绑定关系
-- =====================================================

DROP TABLE IF EXISTS `sys_oauth_user`;

CREATE TABLE `sys_oauth_user` (
    -- ==================== 基础信息 ====================
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',

    -- ==================== 关联信息 ====================
    `user_id` BIGINT COMMENT '系统用户ID（关联sys_user.id，为空表示未绑定）',
    `provider` VARCHAR(30) NOT NULL COMMENT 'OAuth提供商：GITHUB、GOOGLE、WECHAT等',

    -- ==================== 第三方用户信息 ====================
    `oauth_id` VARCHAR(100) NOT NULL COMMENT '第三方用户唯一标识（openid）',
    `oauth_name` VARCHAR(100) COMMENT '第三方用户名',
    `oauth_nickname` VARCHAR(100) COMMENT '第三方昵称',
    `oauth_avatar` VARCHAR(500) COMMENT '第三方头像URL',
    `oauth_email` VARCHAR(100) COMMENT '第三方邮箱',
    `oauth_mobile` VARCHAR(20) COMMENT '第三方手机号',

    -- ==================== Token信息 ====================
    `access_token` VARCHAR(1000) COMMENT 'OAuth AccessToken（加密存储）',
    `refresh_token` VARCHAR(1000) COMMENT 'OAuth RefreshToken（加密存储）',
    `token_expire_time` DATETIME COMMENT 'Token过期时间',

    -- ==================== 扩展信息 ====================
    `raw_user_info` JSON COMMENT '原始用户信息（JSON格式，用于扩展）',

    -- ==================== 时间信息 ====================
    `bind_time` DATETIME COMMENT '绑定时间',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- ==================== 唯一约束 ====================
    UNIQUE KEY `uk_provider_oauth_id` (`provider`, `oauth_id`),

    -- ==================== 索引 ====================
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_provider` (`provider`),
    INDEX `idx_oauth_id` (`oauth_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='OAuth2用户绑定关系表';


-- =====================================================
-- 初始化OAuth配置示例数据
-- =====================================================

INSERT INTO `sys_oauth_config` (
    `provider`, `provider_name`,
    `client_id`, `client_secret`,
    `authorization_uri`, `token_uri`, `user_info_uri`, `redirect_uri`,
    `scopes`, `status`, `sort_order`, `creator`
) VALUES
-- GitHub OAuth配置
('GITHUB', 'GitHub',
 'your-github-client-id', 'your-github-client-secret',
 'https://github.com/login/oauth/authorize',
 'https://github.com/login/oauth/access_token',
 'https://api.github.com/user',
 'http://localhost:8080/auth/oauth2/callback/github',
 'read:user,user:email', 0, 1, 'system'),

-- Gitee OAuth配置
('GITEE', 'Gitee码云',
 'your-gitee-client-id', 'your-gitee-client-secret',
 'https://gitee.com/oauth/authorize',
 'https://gitee.com/oauth/token',
 'https://gitee.com/api/v5/user',
 'http://localhost:8080/auth/oauth2/callback/gitee',
 'user_info,emails', 0, 2, 'system'),

-- Google OAuth配置
('GOOGLE', 'Google',
 'your-google-client-id', 'your-google-client-secret',
 'https://accounts.google.com/o/oauth2/v2/auth',
 'https://oauth2.googleapis.com/token',
 'https://www.googleapis.com/oauth2/v3/userinfo',
 'http://localhost:8080/auth/oauth2/callback/google',
 'openid,profile,email', 0, 3, 'system');


-- =====================================================
-- 初始化灰度规则示例数据
-- =====================================================

INSERT INTO `sys_gray_rule` (
    `rule_name`, `rule_code`, `description`,
    `service_id`, `target_uri`, `stable_uri`,
    `strategy_type`, `strategy_config`,
    `priority`, `status`, `creator`
) VALUES

-- 按用户ID灰度：指定用户走灰度版本
('VIP用户灰度测试', 'vip-user-gray',
 '指定VIP用户优先体验新版本功能',
 'user-service', 'http://localhost:8081-v2', 'http://localhost:8081',
 'USER_ID', '{"userIds": [1, 2, 3, 100, 101]}',
 0, 0, 'system'),

-- 按请求头灰度：带特定Header走灰度版本
('Beta标识灰度', 'beta-header-gray',
 '请求头带有X-Gray-Tag: beta的请求走灰度版本',
 'order-service', 'http://localhost:8082-v2', 'http://localhost:8082',
 'HEADER', '{"headerName": "X-Gray-Tag", "headerValues": ["beta", "canary"]}',
 1, 0, 'system'),

-- 按比例灰度：10%流量走灰度版本
('10%流量灰度', 'weight-10-gray',
 '10%的流量走灰度版本，用于新版本验证',
 'user-service', 'http://localhost:8081-v2', 'http://localhost:8081',
 'WEIGHT', '{"grayWeight": 10}',
 2, 0, 'system'),

-- 按IP灰度：指定IP走灰度版本（内部测试用）
('内网测试灰度', 'internal-ip-gray',
 '公司内网IP优先使用灰度版本',
 'user-service', 'http://localhost:8081-v2', 'http://localhost:8081',
 'IP', '{"ips": ["192.168.0.0/16", "10.0.0.0/8"]}',
 3, 0, 'system');


-- =====================================================
-- 策略配置JSON格式说明
-- =====================================================
-- USER_ID（按用户ID）:
-- {"userIds": [1, 2, 3, 4, 5]}
--
-- USER_TAG（按用户标签）:
-- {"tags": ["beta_user", "vip", "internal"]}
--
-- IP（按IP地址）:
-- {"ips": ["192.168.1.100", "10.0.0.0/8", "172.16.0.0/12"]}
--
-- HEADER（按请求头）:
-- {"headerName": "X-Gray-Tag", "headerValues": ["beta", "canary", "v2"]}
--
-- WEIGHT（按比例）:
-- {"grayWeight": 10}  -- 10表示10%的流量走灰度
--
-- PARAM（按请求参数）:
-- {"paramName": "version", "paramValues": ["v2", "beta", "test"]}
