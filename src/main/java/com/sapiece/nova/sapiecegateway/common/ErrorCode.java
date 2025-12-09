package com.sapiece.nova.sapiecegateway.common;

import lombok.Getter;

/**
 * 网关统一错误码
 * 错误码规范：
 * - 10xxx: 系统级错误
 * - 20xxx: 认证授权错误
 * - 30xxx: 流量控制错误（限流、熔断）
 * - 40xxx: 路由错误
 * - 50xxx: 参数验证错误
 * - 60xxx: 业务错误
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Getter
public enum ErrorCode {

    // ==================== 系统级错误 (10xxx) ====================
    SUCCESS(0, "success", "操作成功"),
    SYSTEM_ERROR(10001, "system.error", "系统内部错误"),
    SERVICE_UNAVAILABLE(10002, "service.unavailable", "服务暂时不可用"),
    REQUEST_TIMEOUT(10003, "request.timeout", "请求超时"),
    DATABASE_ERROR(10004, "database.error", "数据库错误"),
    REDIS_ERROR(10005, "redis.error", "缓存服务错误"),

    // ==================== 认证授权错误 (20xxx) ====================
    AUTH_TOKEN_MISSING(20001, "auth.token.missing", "缺少认证Token"),
    AUTH_TOKEN_INVALID(20002, "auth.token.invalid", "Token无效"),
    AUTH_TOKEN_EXPIRED(20003, "auth.token.expired", "Token已过期"),
    AUTH_TOKEN_BLACKLISTED(20004, "auth.token.blacklisted", "Token已被加入黑名单"),
    AUTH_USER_NOT_FOUND(20005, "auth.user.not.found", "用户不存在"),
    AUTH_USER_DISABLED(20006, "auth.user.disabled", "用户已被禁用"),
    AUTH_USER_BLACKLISTED(20007, "auth.user.blacklisted", "用户已被封禁"),
    AUTH_PASSWORD_ERROR(20008, "auth.password.error", "密码错误"),
    AUTH_PASSWORD_CHANGED(20009, "auth.password.changed", "密码已修改，请重新登录"),
    PERMISSION_DENIED(20010, "permission.denied", "权限不足"),
    API_PERMISSION_DENIED(20011, "api.permission.denied", "无权访问此API"),

    // ==================== 流量控制错误 (30xxx) ====================
    RATE_LIMIT_EXCEEDED(30001, "rate.limit.exceeded", "请求过于频繁，请稍后再试"),
    CIRCUIT_BREAKER_OPEN(30002, "circuit.breaker.open", "服务暂时不可用，请稍后再试"),
    REQUEST_RETRY_FAILED(30003, "request.retry.failed", "请求重试失败"),
    CONCURRENT_LIMIT_EXCEEDED(30004, "concurrent.limit.exceeded", "并发请求数超限"),

    // ==================== 路由错误 (40xxx) ====================
    ROUTE_NOT_FOUND(40001, "route.not.found", "路由不存在"),
    SERVICE_NOT_FOUND(40002, "service.not.found", "后端服务不存在"),
    SERVICE_CONNECT_ERROR(40003, "service.connect.error", "无法连接到后端服务"),
    SERVICE_RESPONSE_ERROR(40004, "service.response.error", "后端服务响应异常"),

    // ==================== 参数验证错误 (50xxx) ====================
    PARAM_INVALID(50001, "param.invalid", "参数错误"),
    PARAM_MISSING(50002, "param.missing", "缺少必需参数"),
    PARAM_TYPE_ERROR(50003, "param.type.error", "参数类型错误"),
    SIGNATURE_INVALID(50004, "signature.invalid", "签名验证失败"),
    SIGNATURE_EXPIRED(50005, "signature.expired", "签名已过期"),
    IDEMPOTENT_DUPLICATE(50006, "idempotent.duplicate", "请勿重复提交"),
    IP_BLOCKED(50007, "ip.blocked", "IP地址已被封禁"),
    IP_NOT_ALLOWED(50008, "ip.not.allowed", "IP地址不在白名单中"),

    // ==================== 业务错误 (60xxx) ====================
    BUSINESS_ERROR(60001, "business.error", "业务处理失败"),
    DATA_NOT_FOUND(60002, "data.not.found", "数据不存在"),
    DATA_ALREADY_EXISTS(60003, "data.already.exists", "数据已存在"),
    OPERATION_NOT_ALLOWED(60004, "operation.not.allowed", "操作不被允许"),
    ;

    /**
     * 错误码（数字）
     */
    private final int code;

    /**
     * 错误码（字符串，用于国际化）
     */
    private final String messageKey;

    /**
     * 默认错误消息（中文）
     */
    private final String defaultMessage;

    ErrorCode(int code, String messageKey, String defaultMessage) {
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }
}
