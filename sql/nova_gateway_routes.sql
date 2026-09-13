-- ============================================================
-- Nova-Project 微服务路由配置
-- 导入时间: 2026-05-02
-- URI 使用 lb://service-name，由 K3s Service DNS + LoadBalancer 解析
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `sys_gateway_route`
    (`route_id`, `route_name`, `uri`, `predicates`, `filters`, `order_num`,
     `require_auth`, `permission_code`, `permission_logic`,
     `rate_limit_enabled`, `rate_limit_qps`, `rate_limit_strategy`,
     `cache_enabled`, `cache_ttl`,
     `retry_enabled`, `retry_times`, `timeout_ms`,
     `status`, `description`, `creator`)
VALUES

-- ============================================================
-- 高优先级路由（解决 /v1/posts/** 路径冲突）
-- comment-service 和 reaction-service 的子路径必须在 post-service 前匹配
-- ============================================================

-- 帖子评论（优先于 /v1/posts/**）
(
    'nova-comment-on-post', '帖子评论路由', 'lb://comment-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/*/comments/**"}}]',
    '[]',
    1, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, '帖子评论路由，优先级高于 post-service', 'system'
),

-- 帖子点赞（优先于 /v1/posts/**）
(
    'nova-reaction-like', '点赞路由', 'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/*/like"}}]',
    '[]',
    2, 1, NULL, 'OR',
    1, 200, 'user',
    0, 300,
    1, 3, 30000,
    1, '帖子点赞路由，优先级高于 post-service', 'system'
),

-- 帖子收藏（优先于 /v1/posts/**）
(
    'nova-reaction-favorite', '收藏路由', 'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/*/favorite"}}]',
    '[]',
    3, 1, NULL, 'OR',
    1, 200, 'user',
    0, 300,
    1, 3, 30000,
    1, '帖子收藏路由，优先级高于 post-service', 'system'
),

-- ============================================================
-- 业务服务路由
-- ============================================================

-- 用户服务 (8082)
(
    'nova-user-service', '用户服务', 'lb://user-service',
    '[{"name":"Path","args":{"pattern":"/v1/users/**"}}]',
    '[]',
    10, 1, NULL, 'OR',
    1, 100, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova用户服务：用户信息、地址、关注、设备、标签等', 'system'
),

-- 通知服务 (8081)
(
    'nova-notification-service', '通知服务', 'lb://notification-service',
    '[{"name":"Path","args":{"pattern":"/v1/notification/**"}}]',
    '[]',
    11, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova通知服务：消息收件箱、未读数、偏好设置、验证码等', 'system'
),

-- 聊天服务 (8083)
(
    'nova-chat-service', '聊天服务', 'lb://chat-service',
    '[{"name":"Path","args":{"pattern":"/api/conversations/**,/api/messages/**,/api/files/**"}}]',
    '[]',
    12, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 60000,
    1, 'Nova聊天服务：会话、消息、文件上传下载', 'system'
),

-- 仪表盘服务 (8093)
(
    'nova-dashboard-service', '仪表盘服务', 'lb://dashboard-service',
    '[{"name":"Path","args":{"pattern":"/v1/dashboard/**"}}]',
    '[]',
    13, 1, NULL, 'OR',
    1, 50, 'user',
    1, 60,
    1, 3, 30000,
    1, 'Nova仪表盘服务：创作者看板、成长教练看板', 'system'
),

-- 事件服务 (8094)
(
    'nova-event-service', '事件追踪服务', 'lb://event-service',
    '[{"name":"Path","args":{"pattern":"/v1/events/**"}}]',
    '[]',
    14, 1, NULL, 'OR',
    1, 200, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova事件追踪服务：用户行为上报', 'system'
),

-- 任务服务 (8095)
(
    'nova-mission-service', '任务服务', 'lb://mission-service',
    '[{"name":"Path","args":{"pattern":"/v1/missions/**"}}]',
    '[]',
    15, 1, NULL, 'OR',
    1, 100, 'user',
    1, 60,
    1, 3, 30000,
    1, 'Nova任务服务：任务目录、进度、领奖等', 'system'
),

-- 作品集服务 (8092)
(
    'nova-portfolio-service', '作品集服务', 'lb://portfolio-service',
    '[{"name":"Path","args":{"pattern":"/v1/portfolio/**"}}]',
    '[]',
    16, 1, NULL, 'OR',
    1, 100, 'user',
    1, 120,
    1, 3, 30000,
    1, 'Nova作品集服务：个人主页、作品管理', 'system'
),

-- 课程服务 (8087)
(
    'nova-course-service', '课程服务', 'lb://course-service',
    '[{"name":"Path","args":{"pattern":"/v1/courses/**,/v1/course-categories/**"}}]',
    '[]',
    17, 1, NULL, 'OR',
    1, 100, 'ip',
    1, 300,
    1, 3, 30000,
    1, 'Nova课程服务：课程列表、分类、章节', 'system'
),

-- 学习记录服务 (8090)
(
    'nova-learning-service', '学习服务', 'lb://learning-service',
    '[{"name":"Path","args":{"pattern":"/v1/learning/**"}}]',
    '[]',
    18, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova学习服务：学习进度、完成记录', 'system'
),

-- 会员服务 (8088)
(
    'nova-membership-service', '会员服务', 'lb://membership-service',
    '[{"name":"Path","args":{"pattern":"/v1/membership-plans/**,/v1/my/membership/**"}}]',
    '[]',
    19, 1, NULL, 'OR',
    1, 100, 'user',
    1, 60,
    1, 3, 30000,
    1, 'Nova会员服务：会员套餐、我的会员状态', 'system'
),

-- 订单服务 (8089)
(
    'nova-order-service', '订单服务', 'lb://order-service',
    '[{"name":"Path","args":{"pattern":"/v1/orders/**,/v1/payments/**"}}]',
    '[]',
    20, 1, NULL, 'OR',
    0, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova订单服务：订单管理、支付回调', 'system'
),

-- 项目服务 (8091)
(
    'nova-project-service', '项目服务', 'lb://project-service',
    '[{"name":"Path","args":{"pattern":"/v1/projects/**"}}]',
    '[]',
    21, 1, NULL, 'OR',
    1, 100, 'ip',
    1, 300,
    1, 3, 30000,
    1, 'Nova项目服务：项目列表、详情', 'system'
),

-- 帖子服务 (8083)
(
    'nova-post-service', '帖子服务', 'lb://post-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/**,/v1/tags/**"}}]',
    '[]',
    22, 1, NULL, 'OR',
    1, 100, 'user',
    1, 60,
    1, 3, 30000,
    1, 'Nova帖子服务：帖子CRUD、标签管理（低优先级，已被评论/点赞路由覆盖冲突路径）', 'system'
),

-- 评论服务 (8084)
(
    'nova-comment-service', '评论服务', 'lb://comment-service',
    '[{"name":"Path","args":{"pattern":"/v1/comments/**"}}]',
    '[]',
    23, 1, NULL, 'OR',
    1, 100, 'user',
    1, 60,
    1, 3, 30000,
    1, 'Nova评论服务：评论直接访问路径', 'system'
),

-- 反应服务 (8085)
(
    'nova-reaction-service', '点赞收藏服务', 'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/reactions/**"}}]',
    '[]',
    24, 1, NULL, 'OR',
    1, 200, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova反应服务：点赞收藏汇总接口', 'system'
),

-- 搜索服务 (8086)
(
    'nova-search-service', '搜索服务', 'lb://search-service',
    '[{"name":"Path","args":{"pattern":"/v1/search/**"}}]',
    '[]',
    25, 1, NULL, 'OR',
    1, 100, 'ip',
    1, 30,
    1, 3, 30000,
    1, 'Nova搜索服务：全文检索', 'system'
),

-- 媒体服务 (8087)
(
    'nova-media-service', '媒体服务', 'lb://media-service',
    '[{"name":"Path","args":{"pattern":"/v1/media/**"}}]',
    '[]',
    26, 1, NULL, 'OR',
    1, 50, 'user',
    0, 300,
    1, 3, 60000,
    1, 'Nova媒体服务：图片/视频上传与获取', 'system'
),

-- 举报审核服务 (8088)
(
    'nova-moderation-service', '审核服务', 'lb://moderation-service',
    '[{"name":"Path","args":{"pattern":"/v1/reports/**,/v1/blocks/**,/v1/penalties/**"}}]',
    '[]',
    27, 1, NULL, 'OR',
    1, 50, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova审核服务：用户举报、屏蔽、处罚查询', 'system'
),

-- ============================================================
-- 管理员路由（/admin/v1/** 和 /v1/admin/**）
-- ============================================================

-- 支付宝回调（无需登录，支付宝服务器主动 POST）
(
    'nova-payment-callback', '支付回调接口', 'lb://order-service',
    '[{"name":"Path","args":{"pattern":"/v1/payments/alipay/notify"}}]',
    '[]',
    8, 0, NULL, 'OR',
    0, 100, 'ip',
    0, 300,
    1, 3, 30000,
    1, '支付宝异步回调，由支付宝服务器主动调用，无需 JWT 认证', 'system'
),

-- 验证码接口（无需登录，注册/找回密码前调用）
(
    'nova-notification-captcha', '验证码接口', 'lb://notification-service',
    '[{"name":"Path","args":{"pattern":"/v1/notification/captcha,/v1/notification/captcha/verify"}}]',
    '[]',
    9, 0, NULL, 'OR',
    1, 10, 'ip',
    0, 300,
    0, 3, 10000,
    1, '验证码发送/验证，注册前调用无需登录；IP限流防刷', 'system'
),

-- 事件管理（事件服务 admin，排除 /outbox，outbox 属于各业务服务需直连）
(
    'nova-admin-event', '事件管理', 'lb://event-service',
    '[{"name":"Path","args":{"pattern":"/admin/v1/events,/admin/v1/events/dead-letters/**,/admin/v1/events/summaries/**"}}]',
    '[]',
    40, 1, 'system:admin:manage', 'OR',
    0, 50, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova事件服务管理端（不含 outbox，outbox 各服务需直连）', 'system'
),

-- 任务管理（任务服务 admin）
(
    'nova-admin-mission', '任务管理', 'lb://mission-service',
    '[{"name":"Path","args":{"pattern":"/admin/v1/missions/**"}}]',
    '[]',
    41, 1, 'system:admin:manage', 'OR',
    0, 50, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova任务服务管理端', 'system'
),

-- 作品集管理（作品集服务 admin）
(
    'nova-admin-portfolio', '作品集管理', 'lb://portfolio-service',
    '[{"name":"Path","args":{"pattern":"/admin/v1/portfolio/**"}}]',
    '[]',
    42, 1, 'system:admin:manage', 'OR',
    0, 50, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova作品集服务管理端', 'system'
),

-- 项目管理（项目服务 admin）
(
    'nova-admin-project', '项目管理', 'lb://project-service',
    '[{"name":"Path","args":{"pattern":"/admin/v1/projects/**"}}]',
    '[]',
    43, 1, 'system:admin:manage', 'OR',
    0, 50, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova项目服务管理端', 'system'
),

-- 搜索管理
(
    'nova-admin-search', '搜索管理', 'lb://search-service',
    '[{"name":"Path","args":{"pattern":"/admin/search/**"}}]',
    '[]',
    44, 1, 'system:admin:manage', 'OR',
    0, 30, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova搜索服务管理端：索引管理', 'system'
),

-- 课程后台管理
(
    'nova-admin-course', '课程后台管理', 'lb://course-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/courses/**,/v1/admin/course-categories/**"}}]',
    '[]',
    45, 1, 'system:admin:manage', 'OR',
    0, 50, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova课程服务管理端：课程、章节、分类管理', 'system'
),

-- 会员后台管理
(
    'nova-admin-membership', '会员后台管理', 'lb://membership-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/membership-plans/**,/v1/admin/memberships/**,/v1/admin/course-access/**"}}]',
    '[]',
    46, 1, 'system:admin:manage', 'OR',
    0, 50, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova会员服务管理端：套餐管理、激活、课程权限', 'system'
),

-- 审核后台管理
(
    'nova-admin-moderation', '审核后台管理', 'lb://moderation-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/reports/**,/v1/admin/reviews/**,/v1/admin/penalties/**"}}]',
    '[]',
    47, 1, 'system:admin:manage', 'OR',
    0, 30, 'ip',
    0, 300,
    1, 3, 30000,
    1, 'Nova审核服务管理端：举报审核、违规处理', 'system'
);
