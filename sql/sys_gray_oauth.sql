-- ============================================================
-- 灰度发布 & OAuth2 第三方登录相关表
--   1. sys_gray_rule    灰度发布规则
--   2. sys_oauth_config OAuth2 提供商配置
--   3. sys_oauth_user   OAuth2 用户绑定关系
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. sys_gray_rule 灰度发布规则表
-- ----------------------------
DROP TABLE IF EXISTS `sys_gray_rule`;
CREATE TABLE `sys_gray_rule` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `rule_name`       VARCHAR(100) NOT NULL               COMMENT '规则名称',
    `rule_code`       VARCHAR(64)  NOT NULL               COMMENT '规则编码（唯一标识）',
    `description`     VARCHAR(500)                        COMMENT '规则描述',
    -- 路由关联
    `service_id`      VARCHAR(64)  NOT NULL               COMMENT '关联服务ID（对应 sys_gateway_route.route_id）',
    `target_uri`      VARCHAR(255) NOT NULL               COMMENT '灰度目标URI（新版本服务地址）',
    `stable_uri`      VARCHAR(255)                        COMMENT '稳定版本URI（不填则使用路由默认URI）',
    -- 灰度策略
    `strategy_type`   VARCHAR(20)  NOT NULL               COMMENT '策略类型：USER_ID/USER_TAG/IP/HEADER/WEIGHT/PARAM/PERCENTAGE',
    `strategy_config` JSON         NOT NULL               COMMENT '策略配置（JSON），不同策略类型结构不同',
    -- 状态与优先级
    `priority`        INT          NOT NULL DEFAULT 0     COMMENT '优先级，数字越小优先级越高',
    `status`          TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：0-禁用 1-启用',
    -- 有效期
    `effective_start` DATETIME                            COMMENT '生效开始时间（空=立即生效）',
    `effective_end`   DATETIME                            COMMENT '生效结束时间（空=永久有效）',
    -- 审计字段
    `creator`         VARCHAR(64)                         COMMENT '创建人',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         VARCHAR(64)                         COMMENT '更新人',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code`          (`rule_code`),
    KEY `idx_service_id`               (`service_id`),
    KEY `idx_strategy_type`            (`strategy_type`),
    KEY `idx_status`                   (`status`),
    KEY `idx_priority`                 (`priority`),
    KEY `idx_effective_time`           (`effective_start`, `effective_end`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '灰度发布规则配置表';

-- ----------------------------
-- 2. sys_oauth_config OAuth2 提供商配置表
-- ----------------------------
DROP TABLE IF EXISTS `sys_oauth_config`;
CREATE TABLE `sys_oauth_config` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider`          VARCHAR(30)  NOT NULL               COMMENT 'OAuth提供商：GITHUB/GOOGLE/WECHAT/GITEE/QQ',
    `provider_name`     VARCHAR(50)                         COMMENT '提供商名称（显示用）',
    -- OAuth 凭证
    `client_id`         VARCHAR(200) NOT NULL               COMMENT '客户端ID（从第三方平台获取）',
    `client_secret`     VARCHAR(500) NOT NULL               COMMENT '客户端密钥（建议加密存储）',
    -- 端点地址
    `authorization_uri` VARCHAR(500)                        COMMENT '授权地址',
    `token_uri`         VARCHAR(500)                        COMMENT '获取Token地址',
    `user_info_uri`     VARCHAR(500)                        COMMENT '获取用户信息地址',
    `redirect_uri`      VARCHAR(500)                        COMMENT '回调地址',
    `scopes`            VARCHAR(200)                        COMMENT '授权范围，多个用英文逗号分隔',
    -- 状态
    `status`            TINYINT      NOT NULL DEFAULT 1     COMMENT '状态：0-禁用 1-启用',
    `sort_order`        INT          NOT NULL DEFAULT 0     COMMENT '排序顺序',
    -- 审计字段
    `creator`           VARCHAR(64)                         COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`           VARCHAR(64)                         COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider`  (`provider`),
    KEY `idx_status`          (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'OAuth2第三方登录配置表';

-- ----------------------------
-- 3. sys_oauth_user OAuth2 用户绑定关系表
-- ----------------------------
DROP TABLE IF EXISTS `sys_oauth_user`;
CREATE TABLE `sys_oauth_user` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    -- 关联
    `user_id`          BIGINT                               COMMENT '系统用户ID（关联 sys_user.id，NULL 表示未绑定）',
    `provider`         VARCHAR(30)   NOT NULL               COMMENT 'OAuth提供商：GITHUB/GOOGLE/WECHAT等',
    -- 第三方用户信息
    `oauth_id`         VARCHAR(100)  NOT NULL               COMMENT '第三方用户唯一标识（openid）',
    `oauth_name`       VARCHAR(100)                         COMMENT '第三方用户名',
    `oauth_nickname`   VARCHAR(100)                         COMMENT '第三方昵称',
    `oauth_avatar`     VARCHAR(500)                         COMMENT '第三方头像URL',
    `oauth_email`      VARCHAR(100)                         COMMENT '第三方邮箱',
    `oauth_mobile`     VARCHAR(20)                          COMMENT '第三方手机号',
    -- Token 信息（加密存储）
    `access_token`     VARCHAR(1000)                        COMMENT 'OAuth AccessToken（加密存储）',
    `refresh_token`    VARCHAR(1000)                        COMMENT 'OAuth RefreshToken（加密存储）',
    `token_expire_time` DATETIME                            COMMENT 'Token过期时间',
    -- 扩展
    `raw_user_info`    JSON                                 COMMENT '原始用户信息（JSON格式）',
    -- 时间
    `bind_time`        DATETIME                             COMMENT '绑定时间',
    `last_login_time`  DATETIME                             COMMENT '最后登录时间',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_oauth_id` (`provider`, `oauth_id`),
    KEY `idx_user_id`                 (`user_id`),
    KEY `idx_provider`                (`provider`),
    KEY `idx_oauth_id`                (`oauth_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'OAuth2用户绑定关系表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 灰度规则示例
INSERT INTO `sys_gray_rule`
    (`rule_name`, `rule_code`, `description`, `service_id`, `target_uri`, `stable_uri`,
     `strategy_type`, `strategy_config`, `priority`, `status`, `creator`)
VALUES
(
    'Beta标识灰度', 'beta-header-gray',
    '请求头包含 X-Gray-Tag: beta 则路由到灰度版本',
    'order-service', 'http://localhost:8082-v2', 'http://localhost:8082',
    'HEADER', '{"headerName":"X-Gray-Tag","headerValues":["beta","canary"]}',
    1, 0, 'system'
),
(
    '10%流量灰度', 'weight-10-gray',
    '10%的流量路由到灰度版本，用于新版本验证',
    'user-service', 'http://localhost:8081-v2', 'http://localhost:8081',
    'WEIGHT', '{"grayWeight":10}',
    2, 0, 'system'
),
(
    '内网IP灰度', 'internal-ip-gray',
    '公司内网IP段使用灰度版本',
    'user-service', 'http://localhost:8081-v2', 'http://localhost:8081',
    'IP', '{"ips":["192.168.0.0/16","10.0.0.0/8"]}',
    3, 0, 'system'
);

-- OAuth2 提供商配置示例（client_id / client_secret 需替换为真实值）
INSERT INTO `sys_oauth_config`
    (`provider`, `provider_name`, `client_id`, `client_secret`,
     `authorization_uri`, `token_uri`, `user_info_uri`, `redirect_uri`,
     `scopes`, `status`, `sort_order`, `creator`)
VALUES
(
    'GITHUB', 'GitHub',
    'your-github-client-id', 'your-github-client-secret',
    'https://github.com/login/oauth/authorize',
    'https://github.com/login/oauth/access_token',
    'https://api.github.com/user',
    'http://localhost:8080/auth/oauth2/callback',
    'read:user,user:email', 0, 1, 'system'
),
(
    'GITEE', 'Gitee码云',
    'your-gitee-client-id', 'your-gitee-client-secret',
    'https://gitee.com/oauth/authorize',
    'https://gitee.com/oauth/token',
    'https://gitee.com/api/v5/user',
    'http://localhost:8080/auth/oauth2/callback',
    'user_info', 0, 2, 'system'
);

SET FOREIGN_KEY_CHECKS = 1;
