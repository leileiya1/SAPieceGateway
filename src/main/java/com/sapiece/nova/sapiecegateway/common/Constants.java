package com.sapiece.nova.sapiecegateway.common;

/**
 * 系统常量类
 * 统一管理所有常量，避免魔法值
 *
 * @author SAPiece
 * @since 2025-11-08
 */
public class Constants {

    // ==================== 错误码常量 ====================

    /**
     * 操作成功
     */
    public static final int SUCCESS = 200;

    /**
     * 请求参数错误
     */
    public static final int BAD_REQUEST = 400;

    /**
     * 未认证（未登录）
     */
    public static final int UNAUTHORIZED = 401;

    /**
     * 无权限访问
     */
    public static final int FORBIDDEN = 403;

    /**
     * 资源不存在
     */
    public static final int NOT_FOUND = 404;

    /**
     * 请求冲突（如重复提交）
     */
    public static final int CONFLICT = 409;

    /**
     * 请求频率过高（限流）
     */
    public static final int TOO_MANY_REQUESTS = 429;

    /**
     * 系统内部错误
     */
    public static final int INTERNAL_SERVER_ERROR = 500;

    /**
     * 服务不可用（如熔断、降级）
     */
    public static final int SERVICE_UNAVAILABLE = 503;

    // ==================== Redis Key前缀 ====================

    /**
     * 限流Key前缀
     */
    public static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    /**
     * Token黑名单Key前缀
     */
    public static final String TOKEN_BLACKLIST_KEY_PREFIX = "token_blacklist:";

    /**
     * 用户黑名单Key前缀
     */
    public static final String USER_BLACKLIST_KEY_PREFIX = "user_blacklist:";

    /**
     * 幂等性Token Key前缀
     */
    public static final String IDEMPOTENT_TOKEN_KEY_PREFIX = "idempotent:token:";

    /**
     * 幂等性校验Key前缀
     */
    public static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";

    /**
     * 签名Nonce Key前缀（防重放）
     */
    public static final String SIGNATURE_NONCE_KEY_PREFIX = "signature:nonce:";

    /**
     * 用户登录次数Key前缀（防暴力破解）
     */
    public static final String LOGIN_ATTEMPT_KEY_PREFIX = "login:attempt:";

    // ==================== HTTP Header常量 ====================

    /**
     * 授权Header
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * JWT Token前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 幂等性Token Header
     */
    public static final String IDEMPOTENT_TOKEN_HEADER = "Idempotent-Token";

    /**
     * 签名Header
     */
    public static final String SIGNATURE_HEADER = "X-Signature";

    /**
     * 时间戳Header
     */
    public static final String TIMESTAMP_HEADER = "X-Timestamp";

    /**
     * 随机数Header
     */
    public static final String NONCE_HEADER = "X-Nonce";

    /**
     * 真实IP Header
     */
    public static final String REAL_IP_HEADER = "X-Real-IP";

    /**
     * 转发IP Header
     */
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    // ==================== JWT常量 ====================

    /**
     * JWT Claims - 用户ID
     */
    public static final String JWT_CLAIM_USER_ID = "userId";

    /**
     * JWT Claims - 角色列表
     */
    public static final String JWT_CLAIM_ROLES = "roles";

    /**
     * JWT Claims - 权限列表
     */
    public static final String JWT_CLAIM_PERMISSIONS = "permissions";

    // ==================== 用户状态常量 ====================

    /**
     * 用户状态 - 正常
     */
    public static final Integer USER_STATUS_NORMAL = 1;

    /**
     * 用户状态 - 禁用
     */
    public static final Integer USER_STATUS_DISABLED = 0;

    /**
     * 删除标志 - 未删除
     */
    public static final Integer DEL_FLAG_NORMAL = 0;

    /**
     * 删除标志 - 已删除
     */
    public static final Integer DEL_FLAG_DELETED = 1;

    // ==================== 角色相关常量 ====================

    /**
     * 角色前缀
     */
    public static final String ROLE_PREFIX = "ROLE_";

    /**
     * 管理员角色
     */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * 普通用户角色
     */
    public static final String ROLE_USER = "ROLE_USER";

    // ==================== 限流相关常量 ====================

    /**
     * 默认QPS（每秒请求数）
     */
    public static final int DEFAULT_QPS = 100;

    /**
     * 默认令牌桶容量
     */
    public static final int DEFAULT_CAPACITY = 200;

    /**
     * 限流窗口时间（秒）
     */
    public static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    // ==================== 幂等性相关常量 ====================

    /**
     * 幂等性Token默认有效期（秒）
     */
    public static final int IDEMPOTENT_TOKEN_TIMEOUT = 300;

    /**
     * 幂等性Token默认长度
     */
    public static final int IDEMPOTENT_TOKEN_LENGTH = 32;

    // ==================== 签名验证相关常量 ====================

    /**
     * 签名时间戳有效期（秒）
     */
    public static final int SIGNATURE_TIMESTAMP_VALIDITY = 300;

    /**
     * 签名算法 - MD5
     */
    public static final String SIGNATURE_ALGORITHM_MD5 = "MD5";

    /**
     * 签名算法 - SHA256
     */
    public static final String SIGNATURE_ALGORITHM_SHA256 = "SHA256";

    /**
     * 签名参数名 - key
     */
    public static final String SIGNATURE_PARAM_KEY = "key";

    // ==================== 熔断器相关常量 ====================

    /**
     * 默认熔断器名称
     */
    public static final String DEFAULT_CIRCUIT_BREAKER = "default";

    /**
     * 熔断器失败率阈值（百分比）
     */
    public static final float CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD = 50.0f;

    /**
     * 慢调用时间阈值（毫秒）
     */
    public static final long CIRCUIT_BREAKER_SLOW_CALL_DURATION = 2000;

    /**
     * 熔断器打开后等待时间（秒）
     */
    public static final int CIRCUIT_BREAKER_WAIT_DURATION = 10;

    // ==================== 登录相关常量 ====================

    /**
     * 最大登录失败次数
     */
    public static final int MAX_LOGIN_ATTEMPTS = 5;

    /**
     * 登录失败锁定时间（秒）
     */
    public static final int LOGIN_LOCK_DURATION = 1800;

    /**
     * 登录失败计数器过期时间（秒）
     */
    public static final int LOGIN_ATTEMPT_EXPIRE = 3600;

    // ==================== 响应消息常量 ====================

    /**
     * 操作成功消息
     */
    public static final String MSG_SUCCESS = "操作成功";

    /**
     * 操作失败消息
     */
    public static final String MSG_ERROR = "操作失败";

    /**
     * 参数错误消息
     */
    public static final String MSG_BAD_REQUEST = "请求参数错误";

    /**
     * 未登录消息
     */
    public static final String MSG_UNAUTHORIZED = "未登录或登录已过期";

    /**
     * 无权限消息
     */
    public static final String MSG_FORBIDDEN = "权限不足，拒绝访问";

    /**
     * 资源不存在消息
     */
    public static final String MSG_NOT_FOUND = "请求的资源不存在";

    /**
     * 请求频率过高消息
     */
    public static final String MSG_TOO_MANY_REQUESTS = "请求频率过高，请稍后重试";

    /**
     * 服务不可用消息
     */
    public static final String MSG_SERVICE_UNAVAILABLE = "服务暂时不可用，请稍后重试";

    /**
     * 系统错误消息
     */
    public static final String MSG_INTERNAL_SERVER_ERROR = "系统内部错误";

    /**
     * Token无效消息
     */
    public static final String MSG_INVALID_TOKEN = "Token无效或已过期";

    /**
     * Token已失效消息
     */
    public static final String MSG_TOKEN_BLACKLISTED = "Token已失效，请重新登录";

    /**
     * 用户名或密码错误消息
     */
    public static final String MSG_BAD_CREDENTIALS = "用户名或密码错误";

    /**
     * 用户已被禁用消息
     */
    public static final String MSG_USER_DISABLED = "账号已被禁用，请联系管理员";

    /**
     * 用户已被锁定消息
     */
    public static final String MSG_USER_LOCKED = "账号已被锁定，请稍后再试";

    /**
     * 签名验证失败消息
     */
    public static final String MSG_SIGNATURE_FAILED = "签名验证失败";

    /**
     * 幂等性校验失败消息
     */
    public static final String MSG_IDEMPOTENT_FAILED = "请勿重复提交";

    /**
     * IP被禁止消息
     */
    public static final String MSG_IP_BLOCKED = "您的IP地址已被禁止访问";

    /**
     * IP不在白名单消息
     */
    public static final String MSG_IP_NOT_ALLOWED = "您的IP地址没有访问权限";

    // ==================== 时间相关常量 ====================

    /**
     * 1秒的毫秒数
     */
    public static final long ONE_SECOND_MS = 1000L;

    /**
     * 1分钟的毫秒数
     */
    public static final long ONE_MINUTE_MS = 60 * ONE_SECOND_MS;

    /**
     * 1小时的毫秒数
     */
    public static final long ONE_HOUR_MS = 60 * ONE_MINUTE_MS;

    /**
     * 1天的毫秒数
     */
    public static final long ONE_DAY_MS = 24 * ONE_HOUR_MS;

    /**
     * 7天的毫秒数
     */
    public static final long ONE_WEEK_MS = 7 * ONE_DAY_MS;

    // ==================== 编码相关常量 ====================

    /**
     * UTF-8编码
     */
    public static final String CHARSET_UTF8 = "UTF-8";

    /**
     * JSON内容类型
     */
    public static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * JSON内容类型（带UTF-8编码）
     */
    public static final String CONTENT_TYPE_JSON_UTF8 = "application/json;charset=UTF-8";

    // ==================== 正则表达式常量 ====================

    /**
     * 用户名正则（字母、数字、下划线，4-16位）
     */
    public static final String REGEX_USERNAME = "^[a-zA-Z0-9_]{4,16}$";

    /**
     * 密码正则（至少包含字母、数字，6-20位）
     */
    public static final String REGEX_PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d@$!%*#?&]{6,20}$";

    /**
     * 邮箱正则
     */
    public static final String REGEX_EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /**
     * 手机号正则（中国大陆）
     */
    public static final String REGEX_MOBILE = "^1[3-9]\\d{9}$";

    /**
     * IP地址正则
     */
    public static final String REGEX_IP = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$";

    // ==================== 系统配置常量 ====================

    /**
     * 系统名称
     */
    public static final String SYSTEM_NAME = "SAPiece Gateway";

    /**
     * 系统版本
     */
    public static final String SYSTEM_VERSION = "1.0.0";

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    /**
     * 默认每页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页大小
     */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 私有构造函数（防止实例化） ====================

    private Constants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
