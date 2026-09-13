-- Empty dedicated database only. No destructive statements or demo credentials.
SET NAMES utf8mb4;

CREATE TABLE `sys_user` (
    `id`                              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT   COMMENT '主键ID',
    `user_name`                       VARCHAR(50)         NOT NULL                  COMMENT '用户名（唯一）',
    `nick_name`                       VARCHAR(50)                                   COMMENT '用户昵称',
    `password`                        VARCHAR(100)        NOT NULL                  COMMENT '密码（BCrypt加密）',
    `email`                           VARCHAR(100)                                  COMMENT '邮箱',
    `mobile`                          VARCHAR(20)                                   COMMENT '手机号',
    `avatar`                          VARCHAR(255)                                  COMMENT '头像地址',
    `gender`                          TINYINT UNSIGNED    NOT NULL DEFAULT 0        COMMENT '性别：0-未知 1-男 2-女',
    `status`                          TINYINT UNSIGNED    NOT NULL DEFAULT 1        COMMENT '状态：0-禁用 1-启用',
    `private_flag`                    TINYINT             NOT NULL DEFAULT 0        COMMENT '是否私密账号：0-否 1-是',
    `del_flag`                        TINYINT UNSIGNED    NOT NULL DEFAULT 0        COMMENT '删除标志：0-正常 1-已删除',
    `login_ip`                        VARCHAR(50)                                   COMMENT '最后登录IP',
    `login_time`                      DATETIME                                      COMMENT '最后登录时间',
    `user_name_last_changed_at`       DATETIME            DEFAULT CURRENT_TIMESTAMP COMMENT '用户名最近修改时间',
    `nick_name_last_changed_at`       DATETIME            DEFAULT CURRENT_TIMESTAMP COMMENT '昵称最近修改时间',
    `password_last_changed_at`        DATETIME            DEFAULT CURRENT_TIMESTAMP COMMENT '密码最近修改时间',
    `deletion_requested_at`           DATETIME                                      COMMENT '申请注销时间',
    `scheduled_permanent_deletion_at` DATETIME                                      COMMENT '计划永久删除时间',
    `last_active_at`                  DATETIME                                      COMMENT '最近活跃时间',
    `remark`                          VARCHAR(500)                                  COMMENT '备注',
    `creator`                         VARCHAR(50)                                   COMMENT '创建人',
    `create_time`                     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`                         VARCHAR(50)                                   COMMENT '更新人',
    `update_time`                     DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_name`),
    KEY `idx_status`      (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '系统用户表';

CREATE TABLE `sys_role` (
    `id`          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_code`   VARCHAR(50)      NOT NULL                COMMENT '角色编码（如：ROLE_ADMIN）',
    `role_name`   VARCHAR(50)      NOT NULL                COMMENT '角色名称',
    `role_sort`   INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '显示顺序',
    `status`      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态：0-禁用 1-启用',
    `del_flag`    TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志：0-正常 1-已删除',
    `remark`      VARCHAR(500)                             COMMENT '备注',
    `creator`     VARCHAR(50)                              COMMENT '创建人',
    `create_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     VARCHAR(50)                              COMMENT '更新人',
    `update_time` DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_status`      (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '系统角色表';

CREATE TABLE `sys_menu` (
    `id`               BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `parent_id`        BIGINT UNSIGNED  NOT NULL DEFAULT 0      COMMENT '父菜单ID，顶级为0',
    `menu_name`        VARCHAR(50)      NOT NULL                COMMENT '菜单名称',
    `menu_type`        CHAR(1)          NOT NULL                COMMENT '菜单类型：M-目录 C-菜单 F-按钮 R-路由',
    `menu_sort`        INT UNSIGNED     NOT NULL DEFAULT 0      COMMENT '显示顺序',
    `permission_code`  VARCHAR(100)                             COMMENT '权限标识（如：system:user:list）',
    `path`             VARCHAR(200)                             COMMENT '路由地址',
    `component`        VARCHAR(255)                             COMMENT '组件路径',
    `icon`             VARCHAR(100)                             COMMENT '菜单图标',
    `target_uri`       VARCHAR(200)                             COMMENT '网关目标URI',
    `route_predicates` JSON                                     COMMENT '路由断言配置（JSON）',
    `route_filters`    JSON                                     COMMENT '路由过滤器配置（JSON）',
    `status`           TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '状态：0-禁用 1-启用',
    `del_flag`         TINYINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '删除标志：0-正常 1-已删除',
    `remark`           VARCHAR(500)                             COMMENT '备注',
    `creator`          VARCHAR(50)                              COMMENT '创建人',
    `create_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          VARCHAR(50)                              COMMENT '更新人',
    `update_time`      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status`    (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '系统菜单权限表';

CREATE TABLE `sys_user_role` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    `role_id`     BIGINT UNSIGNED NOT NULL                COMMENT '角色ID',
    `creator`     VARCHAR(50)                             COMMENT '创建人',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户与角色关联表';

CREATE TABLE `sys_role_menu` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id`     BIGINT UNSIGNED NOT NULL                COMMENT '角色ID',
    `menu_id`     BIGINT UNSIGNED NOT NULL                COMMENT '菜单ID',
    `creator`     VARCHAR(50)                             COMMENT '创建人',
    `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色与菜单关联表';

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
