-- ============================================================
-- Nova Project 微服务路由导入 SQL
-- 适用于 SAPieceGateway 动态路由表：sys_gateway_route
--
-- Nova Project 共 13 个微服务：
--   user-service, notification-service, chat-service,
--   post-service, comment-service, reaction-service, media-service,
--   search-service, moderation-service,
--   course-service, order-service, learning-service, membership-service
--
-- ★ 使用说明 ★
--   1. 所有 lb://service-name 依赖 Nacos 服务注册与发现。
--      执行前请确认各服务已注册到 Nacos（namespace: f054896c-1483-40ce-bda2-d3d71619099e）。
--   2. chat-service 和 notification-service 代码中未配置 Nacos，
--      若使用 lb:// 无法解析，请将对应 URI 修改为直接地址，如：
--        lb://chat-service  →  http://10.70.239.17:8086
--        lb://notification-service  →  http://10.70.239.17:8081
--   3. order_num 越小，路由优先级越高（先匹配先返回）。
--      路由冲突通过 order_num 分层解决，详见下方注释。
--   4. Internal/* 路径为服务间调用，不对外暴露，已排除。
--   5. 支付回调路径（PaymentCallbackController）请根据实际配置
--      的第三方支付平台 URL 单独添加 require_auth=0 的路由。
--   6. 本脚本使用 INSERT IGNORE，可重复执行不报错。
--      如需更新已有路由，使用 UPDATE 或先 DELETE 再执行。
-- ============================================================

SET NAMES utf8mb4;

-- ============================================================
-- 路由优先级（order_num）设计说明
-- ============================================================
--  order  5 : /v1/admin/reports|reviews|penalties|sensitive-words  → moderation-service（最高优先）
--  order  6 : /v1/admin/search|courses                             → search/course-service
--  order  7 : /v1/admin/membership*|course-access                  → membership-service
--  order 18 : /v1/users/me/reactions                               → reaction-service（先于 user-service）
--  order 19 : /v1/users/*/comments/**                              → comment-service（先于 user-service）
--  order 20 : /v1/users/**                                         → user-service（兜底）
--  order 40 : /v1/posts/*/comments/**                              → comment-service（先于 post-service）
--  order 41 : /v1/posts/*/like|favorite|reactions                  → reaction-service（先于 post-service）
--  order 50 : /v1/posts/**                                         → post-service（兜底）
--  order 51+ : 各服务独立路径，无冲突，顺序编排
-- ============================================================

INSERT IGNORE INTO `sys_gateway_route`
    (`route_id`, `route_name`, `uri`, `predicates`, `filters`, `order_num`,
     `require_auth`, `permission_code`, `permission_logic`,
     `rate_limit_enabled`, `rate_limit_qps`, `rate_limit_strategy`,
     `cache_enabled`, `cache_ttl`,
     `retry_enabled`, `retry_times`, `timeout_ms`,
     `status`, `description`, `creator`)
VALUES

-- ============================================================
-- [优先级 order=5] 内容治理服务管理员接口 - moderation-service
--   路径：/v1/admin/reports/**, /v1/admin/reviews/**
--         /v1/admin/penalties/**, /v1/admin/sensitive-words/**
--   ���须最高优先（5），防止被其他 /v1/admin/** 路由截获
-- ============================================================
(
    'nova-admin-reports',
    '内容治理-举报工单管理',
    'lb://moderation-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/reports/**"}}]',
    '[]',
    5, 1, 'nova:admin:moderation', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 内容治理服务：举报工单查询、处理（管理员，最高优先级）', 'system'
),
(
    'nova-admin-reviews',
    '内容治理-内容审核管理',
    'lb://moderation-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/reviews/**"}}]',
    '[]',
    5, 1, 'nova:admin:moderation', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 内容治理服务：内容审核管理（管���员，最高优先级）', 'system'
),
(
    'nova-admin-penalties',
    '内容治理-处罚管理',
    'lb://moderation-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/penalties/**"}}]',
    '[]',
    5, 1, 'nova:admin:moderation', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 内容治理服务：用户禁言/封禁处罚管理（管理员���最高优先级）', 'system'
),
(
    'nova-admin-sensitive-words',
    '内容治理-敏感词管理',
    'lb://moderation-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/sensitive-words/**"}}]',
    '[]',
    5, 1, 'nova:admin:moderation', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 内容治理服���：敏感词增删改查、缓存刷新���管理员，最高优先级）', 'system'
),

-- ============================================================
-- [优先级 order=6] 搜索服务管理员接口 + 课程服务管理员接口
--   路径：/v1/admin/search/**, /v1/admin/courses/**
-- ============================================================
(
    'nova-admin-search',
    '搜索服务-管理员索引管理',
    'lb://search-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/search/**"}}]',
    '[]',
    6, 1, 'nova:admin:search', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 搜索服务：ES 索引管理��管理员接口）', 'system'
),
(
    'nova-admin-courses',
    '课程服���-管理员课程管理',
    'lb://course-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/courses/**"}}]',
    '[]',
    6, 1, 'nova:admin:course', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 课程服务：课程发布、章节管理（管理员接口）', 'system'
),

-- ============================================================
-- [优先级 order=7] 会员服��管理员接口
--   路径：/v1/admin/membership-plans/**
--         /v1/admin/memberships/**
--         /v1/admin/course-access/**
-- ============================================================
(
    'nova-admin-membership-plans',
    '会员服务-套餐管理',
    'lb://membership-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/membership-plans/**"}}]',
    '[]',
    7, 1, 'nova:admin:membership', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 会员服务：会员套餐创建管理（管理员接口）', 'system'
),
(
    'nova-admin-memberships',
    '会员服务-用户会员激活',
    'lb://membership-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/memberships/**"}}]',
    '[]',
    7, 1, 'nova:admin:membership', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 会员服务：手动激活用户会员（管理员接口）', 'system'
),
(
    'nova-admin-course-access',
    '会员服务-课程访问授权',
    'lb://membership-service',
    '[{"name":"Path","args":{"pattern":"/v1/admin/course-access/**"}}]',
    '[]',
    7, 1, 'nova:admin:membership', 'OR',
    1, 30, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 会员服务：授予用户课程访问权限（管理员接口）', 'system'
),

-- ============================================================
-- [优先级 order=18] 互动服务 - 我的互动记��
--   路径：/v1/users/me/reactions
--   必须早于 order=20 的 user-service 路由匹配
-- ============================================================
(
    'nova-user-reactions-me',
    '互动服务-我的互动记录',
    'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/users/me/reactions"}}]',
    '[]',
    18, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 10000,
    1, 'Nova 互动服��：当前用户点赞/收藏记录（优先于 user-service，order=18）', 'system'
),

-- ============================================================
-- [优先级 order=19] 评论服务 - 用户的评论列表
--   路径：/v1/users/{userId}/comments/**
--   必须早于 order=20 的 user-service 路由匹配
-- ============================================================
(
    'nova-user-comments',
    '评论服务-用户评论列表',
    'lb://comment-service',
    '[{"name":"Path","args":{"pattern":"/v1/users/*/comments/**"}}]',
    '[]',
    19, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova 评论服务：指定用户发表的评论列表��优先于 user-service，order=19）', 'system'
),

-- ============================================================
-- [优先级 order=20] 用户服务 - user-service（兜底）
--   路径：/v1/users/**
--   注册/检查接口理论上无需 Auth，但网关白名单可在
--   SecurityConfig 中单独放行 /v1/users/register、/v1/users/check/**
-- ============================================================
(
    'nova-user-service',
    '用户服务',
    'lb://user-service',
    '[{"name":"Path","args":{"pattern":"/v1/users/**"}}]',
    '[]',
    20, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova 用户服务：注册、资料查询/更新、关注、收货地址、设备、标签、实名认证等', 'system'
),

-- ============================================================
-- [优先级 order=30~32] 聊天服务 - chat-service
--   ★ chat-service 未配置 Nacos，��� lb:// 解析失败请改为直接 IP：
--     uri: 'http://10.70.239.17:8086'  （请确认实际端口）
-- ============================================================
(
    'nova-chat-conversations',
    '聊天服务-会话管理',
    'lb://chat-service',
    '[{"name":"Path","args":{"pattern":"/api/conversations/**"}}]',
    '[]',
    30, 1, NULL, 'OR',
    1, 50, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 聊天服务：创建单聊/群聊、获取会话列表与详情', 'system'
),
(
    'nova-chat-messages',
    '聊天服务-消息管理',
    'lb://chat-service',
    '[{"name":"Path","args":{"pattern":"/api/messages/**"}}]',
    '[]',
    31, 1, NULL, 'OR',
    1, 200, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 聊天服务：发送消息、获取历史消息、撤回消息', 'system'
),
(
    'nova-chat-files',
    '聊天服务-文件上传',
    'lb://chat-service',
    '[{"name":"Path","args":{"pattern":"/api/files/**"}}]',
    '[]',
    32, 1, NULL, 'OR',
    1, 20, 'user',
    0, 300,
    0, 3, 60000,
    1, 'Nova 聊天服务：聊天文件上传（MinIO）', 'system'
),

-- ============================================================
-- [优先级 order=40] 评论服务 - 帖子下的��论
--   路径：/v1/posts/{postId}/comments/**
--   必须早于 order=50 的 post-service 路由匹配
-- ============================================================
(
    'nova-comment-in-post',
    '评论服务-帖子评论',
    'lb://comment-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/*/comments/**"}}]',
    '[]',
    40, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova 评���服务：帖子评论列表/创建/统计（优先于 post-service，order=40）', 'system'
),

-- ============================================================
-- [优先级 order=41] 互动服务 - 帖子点赞/收藏/统计
--   路径：/v1/posts/{id}/like
--         /v1/posts/{id}/favorite
--         /v1/posts/{id}/reactions
--   必须早于 order=50 的 post-service 路由匹配
-- ============================================================
(
    'nova-post-like',
    '互动服务-帖子点赞',
    'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/*/like"}}]',
    '[]',
    41, 1, NULL, 'OR',
    1, 200, 'user',
    0, 300,
    1, 3, 10000,
    1, 'Nova 互动服务：帖子点赞/取���点赞（优先于 post-service，order=41）', 'system'
),
(
    'nova-post-favorite',
    '互动服务-帖子收藏',
    'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/*/favorite"}}]',
    '[]',
    41, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 10000,
    1, 'Nova 互动��务：帖子收藏/���消收藏��优先于 post-service，order=41）', 'system'
),
(
    'nova-post-reactions-count',
    '互动服务-帖子互动计数',
    'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/*/reactions"}}]',
    '[]',
    41, 1, NULL, 'OR',
    1, 200, 'user',
    0, 300,
    1, 3, 10000,
    1, 'Nova 互动服务：帖子点赞数/收藏数统计（优先于 post-service，order=41）', 'system'
),

-- ============================================================
-- [优先级 order=50] ��子服务 - post-service（兜��）
--   路径：/v1/posts/**
--   此路由兜底，评论/互动的具体路径已由 order=40/41 路由截获
-- ============================================================
(
    'nova-post-service',
    '帖子服务',
    'lb://post-service',
    '[{"name":"Path","args":{"pattern":"/v1/posts/**"}}]',
    '[]',
    50, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova ��子服务：草稿创建/更新/发布/隐藏/删除/详情/列表/搜索代理（兜底路由）', 'system'
),

-- ============================================================
-- [order=51] 评论服务 - 独立评论路径
--   路径：/v1/comments/**（评论详情、回复列表、点赞）
-- ============================================================
(
    'nova-comment-service',
    '评论服务',
    'lb://comment-service',
    '[{"name":"Path","args":{"pattern":"/v1/comments/**"}}]',
    '[]',
    51, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova 评论服务：评论详情/回复列表/评论点赞（直接操作）', 'system'
),

-- ============================================================
-- [order=52] 互动服务 - 通用互动路径
--   路径：/v1/reactions/**（通用PUT接口、批量查询）
-- ============================================================
(
    'nova-reaction-service',
    '互动��务',
    'lb://reaction-service',
    '[{"name":"Path","args":{"pattern":"/v1/reactions/**"}}]',
    '[]',
    52, 1, NULL, 'OR',
    1, 200, 'user',
    0, 300,
    1, 3, 10000,
    1, 'Nova 互动服务：通用互动接口、批量状态查询、批量计数', 'system'
),

-- ============================================================
-- [order=53] 媒体服务 - media-service
--   路径：/v1/media/**
-- ============================================================
(
    'nova-media-service',
    '媒体服务',
    'lb://media-service',
    '[{"name":"Path","args":{"pattern":"/v1/media/**"}}]',
    '[]',
    53, 1, NULL, 'OR',
    1, 50, 'user',
    0, 300,
    0, 3, 60000,
    1, 'Nova 媒体服务：上传凭证申请、分片上传、上传完成回调、媒体访问/删除', 'system'
),

-- ============================================================
-- [order=54] 搜索服务 - search-service（无需登录）
--   路径��/v1/search/**
--   缓存命中率高（搜索结果），开启 60s 缓存
-- ============================================================
(
    'nova-search-service',
    '搜��服务',
    'lb://search-service',
    '[{"name":"Path","args":{"pattern":"/v1/search/**"}}]',
    '[]',
    54, 0, NULL, 'OR',
    1, 200, 'ip',
    1, 60,
    1, 3, 30000,
    1, 'Nova 搜索服务：帖子搜索、用户搜索、搜索建议（无需登录，60s 缓存）', 'system'
),

-- ============================================================
-- [order=55] 通知服务 - notification-service（无需登录）
--   路径：/v1/notification/**
--   ★ 若 notification-service 未注册 Nacos，请将 URI 改为直接地址
-- ============================================================
(
    'nova-notification-service',
    '通��服务（验证码）',
    'lb://notification-service',
    '[{"name":"Path","args":{"pattern":"/v1/notification/**"}}]',
    '[]',
    55, 0, NULL, 'OR',
    1, 30, 'ip',
    0, 300,
    0, 3, 30000,
    1, 'Nova 通知服务：验证码发送（邮件/短信）与校验（无需登录，严格限流）', 'system'
),

-- ============================================================
-- [order=56] 内容治理服务 - 用户侧
--   路径：/v1/moderation/**（用户举报、申诉等）
-- ============================================================
(
    'nova-moderation-user',
    '内容治理服务-用户侧',
    'lb://moderation-service',
    '[{"name":"Path","args":{"pattern":"/v1/moderation/**"}}]',
    '[]',
    56, 1, NULL, 'OR',
    1, 50, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 内容治理服务：用户侧举报提交、处罚状态查询', 'system'
),

-- ============================================================
-- [order=57~58] 课程服务 - course-service（前台无需登录）
--   路径：/v1/course-categories/**（分类列表）
--         /v1/courses/**（课程列表/详情/目录）
--   课时付费内容由下游 course-service 内部鉴权
-- ============================================================
(
    'nova-course-categories',
    '课程服务-分类列表',
    'lb://course-service',
    '[{"name":"Path","args":{"pattern":"/v1/course-categories/**"}}]',
    '[]',
    57, 0, NULL, 'OR',
    1, 500, 'ip',
    1, 300,
    1, 3, 30000,
    1, 'Nova 课程服务：课程分类列表（无需登录，可缓存）', 'system'
),
(
    'nova-course-service',
    '课程服务',
    'lb://course-service',
    '[{"name":"Path","args":{"pattern":"/v1/courses/**"}}]',
    '[]',
    58, 0, NULL, 'OR',
    1, 300, 'ip',
    1, 120,
    1, 3, 30000,
    1, 'Nova 课程服务：课程列表/详情/目录（前台无需登录；付费内容由服务内部鉴权）', 'system'
),

-- ============================================================
-- [order=59] 订单服务 - order-service
--   路径：/v1/orders/**
--   ★ 支付回调接口（PaymentCallbackController）来自第三方支付平台，
--     无需 JWT 认证。请根据实际回调 URL 另外添加一条 require_auth=0
--     的路由，例如：/v1/payment/callback/** 或 /v1/orders/notify/**
-- ============================================================
(
    'nova-order-service',
    '订单服务',
    'lb://order-service',
    '[{"name":"Path","args":{"pattern":"/v1/orders/**"}}]',
    '[]',
    59, 1, NULL, 'OR',
    1, 50, 'user',
    0, 300,
    0, 3, 30000,
    1, 'Nova 订单服务：创建订单、查询订单、取消订单、支付预参数', 'system'
),

-- ============================================================
-- [order=60] 学��服务 - learning-service
--   路径：/v1/learning/**
-- ============================================================
(
    'nova-learning-service',
    '学习服��',
    'lb://learning-service',
    '[{"name":"Path","args":{"pattern":"/v1/learning/**"}}]',
    '[]',
    60, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova 学习服务：学习中心首页、我的课程、学习进度上报、继续学习', 'system'
),

-- ============================================================
-- [order=61~62] 会员服务 - membership-service
--   路径：/v1/membership-plans/**（套餐列表，无需登录）
--         /v1/my/**（我的会员状态，需要登录）
-- ============================================================
(
    'nova-membership-plans',
    '会员服务-套餐��表',
    'lb://membership-service',
    '[{"name":"Path","args":{"pattern":"/v1/membership-plans/**"}}]',
    '[]',
    61, 0, NULL, 'OR',
    1, 300, 'ip',
    1, 300,
    1, 3, 30000,
    1, 'Nova 会员服务：可用套餐列表/套餐购买状态（无��登录）', 'system'
),
(
    'nova-my-membership',
    '会员服务-我的会员',
    'lb://membership-service',
    '[{"name":"Path","args":{"pattern":"/v1/my/**"}}]',
    '[]',
    62, 1, NULL, 'OR',
    1, 100, 'user',
    0, 300,
    1, 3, 30000,
    1, 'Nova 会员服务：当前用户会员状态/有效期查询', 'system'
),

-- ============================================================
-- [order=63] 标签服务 - post-service 内的 TagController
--   路径：/v1/tags/**
-- ============================================================
(
    'nova-tag-service',
    '标签服务',
    'lb://post-service',
    '[{"name":"Path","args":{"pattern":"/v1/tags/**"}}]',
    '[]',
    63, 1, NULL, 'OR',
    1, 100, 'user',
    1, 300,
    1, 3, 30000,
    1, 'Nova 标签服务（post-service 内）：标签 CRUD、热门标签查询', 'system'
);

-- ============================================================
-- 验证查询（执行完毕后可用以下 SQL 确认导入结果）
-- ============================================================
-- SELECT route_id, route_name, order_num, require_auth, status
-- FROM sys_gateway_route
-- WHERE route_id LIKE 'nova-%'
-- ORDER BY order_num ASC;
