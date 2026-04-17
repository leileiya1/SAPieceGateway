-- ============================================================
-- 系统核心表：用户 / 角色 / 菜单权限 / 关联关系
-- 数据库：product_test（或目标库名）
-- 字符集：utf8mb4
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. sys_user 系统用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
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

-- ----------------------------
-- 2. sys_role 系统角色表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
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

-- ----------------------------
-- 3. sys_menu 系统菜单权限表
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
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

-- ----------------------------
-- 4. sys_user_role 用户角色关联表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
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

-- ----------------------------
-- 5. sys_role_menu 角色菜单关联表
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
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

-- ============================================================
-- 初始化数据
-- ============================================================

-- 初始用户（密码统一为 123456，BCrypt 加密）
INSERT INTO `sys_user` (`id`, `user_name`, `nick_name`, `password`, `email`, `status`, `creator`, `create_time`, `update_time`)
VALUES
    (1, 'superadmin', '超级系统管理员', '$2a$10$I0Nod2ObY0dWzv31.XCOROK3nGYpfXBNeqcnYJI.jmRekPoOSbBi.', 'superadmin@sapiece.com', 1, 'system',  NOW(), NOW()),
    (2, 'admin',      '系统管理员',     '$2a$10$ksgEpV.7hyb2tJH5SoUyn.mrHHZng3LatHAt9XV7ks7KSuphZn9Ve', 'admin@sapiece.com',      1, 'superadmin', NOW(), NOW()),
    (3, 'sysadmin',   '系统运维',       '$2a$10$ksgEpV.7hyb2tJH5SoUyn.mrHHZng3LatHAt9XV7ks7KSuphZn9Ve', 'sysadmin@sapiece.com',   1, 'superadmin', NOW(), NOW());

-- 初始角色
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `role_sort`, `status`, `creator`, `create_time`, `update_time`)
VALUES
    (1,  'SUPER_ADMIN',    '超级管理员',   1,  1, 'system', NOW(), NOW()),
    (2,  'ADMIN',          '系统管理员',   2,  1, 'system', NOW(), NOW()),
    (3,  'SYS_OPS',        '系统运维',     3,  1, 'system', NOW(), NOW()),
    (10, 'GATEWAY_ADMIN',  '网关管理员',   10, 1, 'admin',  NOW(), NOW()),
    (11, 'ROUTE_ADMIN',    '路由管理员',   11, 1, 'admin',  NOW(), NOW()),
    (12, 'SECURITY_ADMIN', '安全管理员',   12, 1, 'admin',  NOW(), NOW());

-- 初始菜单权限（网关管理相关）
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `menu_sort`, `permission_code`, `status`, `creator`, `create_time`, `update_time`)
VALUES
    -- 顶级目录
    (1,  0, '系统管理', 'M', 1,  NULL,                   1, 'system', NOW(), NOW()),
    (2,  0, '网关管理', 'M', 2,  NULL,                   1, 'system', NOW(), NOW()),
    -- 系统管理子菜单
    (10, 1, '用户管理', 'C', 1,  'system:user:list',     1, 'system', NOW(), NOW()),
    (11, 1, '角色管理', 'C', 2,  'system:role:list',     1, 'system', NOW(), NOW()),
    (12, 1, '菜单管理', 'C', 3,  'system:menu:list',     1, 'system', NOW(), NOW()),
    -- 系统管理按钮权限
    (20, 10, '用户新增', 'F', 1, 'system:user:add',      1, 'system', NOW(), NOW()),
    (21, 10, '用户修改', 'F', 2, 'system:user:edit',     1, 'system', NOW(), NOW()),
    (22, 10, '用户删除', 'F', 3, 'system:user:delete',   1, 'system', NOW(), NOW()),
    (23, 10, '用户查询', 'F', 4, 'system:user:query',    1, 'system', NOW(), NOW()),
    (30, 11, '角色新增', 'F', 1, 'system:role:add',      1, 'system', NOW(), NOW()),
    (31, 11, '角色修改', 'F', 2, 'system:role:edit',     1, 'system', NOW(), NOW()),
    (32, 11, '角色删除', 'F', 3, 'system:role:delete',   1, 'system', NOW(), NOW()),
    -- 网关管理子菜单
    (50, 2, '路由管理',   'C', 1, 'gateway:route:list',  1, 'system', NOW(), NOW()),
    (51, 2, '灰度管理',   'C', 2, 'gateway:gray:list',   1, 'system', NOW(), NOW()),
    (52, 2, '审计日志',   'C', 3, 'gateway:audit:list',  1, 'system', NOW(), NOW()),
    -- 网关管理按钮权限
    (60, 50, '路由新增',  'F', 1, 'gateway:route:add',   1, 'system', NOW(), NOW()),
    (61, 50, '路由修改',  'F', 2, 'gateway:route:edit',  1, 'system', NOW(), NOW()),
    (62, 50, '路由删除',  'F', 3, 'gateway:route:delete',1, 'system', NOW(), NOW()),
    (63, 50, '路由刷新',  'F', 4, 'gateway:route:refresh',1,'system', NOW(), NOW()),
    (64, 50, '路由查询',  'F', 5, 'system:role:list',    1, 'system', NOW(), NOW()),
    -- 管理后台权限
    (70, 1, '管理操作',   'F', 99,'system:admin:manage', 1, 'system', NOW(), NOW()),
    (71, 1, '超级管理',   'F', 100,'system:super:admin', 1, 'system', NOW(), NOW()),
    (72, 10,'订单查询',   'F', 5, 'system:order:query',  1, 'system', NOW(), NOW());

-- 用户角色关联
INSERT INTO `sys_user_role` (`user_id`, `role_id`, `creator`, `create_time`)
VALUES
    (1, 1, 'system', NOW()),
    (2, 2, 'system', NOW()),
    (3, 3, 'system', NOW());

-- 角色菜单关联（超级管理员拥有所有权限）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`)
SELECT 1, `id`, 'system', NOW() FROM `sys_menu`;

-- 管理员拥有网关+系统管理权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`)
SELECT 2, `id`, 'system', NOW() FROM `sys_menu` WHERE `id` IN (1,2,10,11,12,20,21,22,23,30,31,32,50,51,52,60,61,62,63,64);

-- 网关管理员拥有路由管理权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`)
SELECT 10, `id`, 'admin', NOW() FROM `sys_menu` WHERE `id` IN (2,50,51,52,60,61,62,63,64);

SET FOREIGN_KEY_CHECKS = 1;
