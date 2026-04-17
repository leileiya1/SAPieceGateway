package com.sapiece.nova.sapiecegateway.common;

import org.springframework.core.Ordered;

/**
 * 过滤器排序常量类
 * 统一管理所有过滤器的执行顺序
 * 数值越小优先级越高
 *
 * @author SAPiece
 * @since 2025
 */
public final class FilterOrders {

    private FilterOrders() {
    }

    /**
     * IP黑白名单过滤器 - 最高优先级
     * 最先过滤非法IP
     */
    public static final int IP_BLACK_WHITE_LIST = Ordered.HIGHEST_PRECEDENCE;

    /**
     * 日志过滤器 - 最先记录请求进入时间
     */
    public static final int REQUEST_LOG = Ordered.HIGHEST_PRECEDENCE + 1;

    /**
     * 签名验证过滤器 - 在日志之后，认证之前
     * 需要提前验证请求完整性
     */
    public static final int SIGNATURE_VERIFICATION = Ordered.HIGHEST_PRECEDENCE + 2;

    /**
     * 重试过滤器 - 在签名验证之后
     */
    public static final int RETRY = Ordered.HIGHEST_PRECEDENCE + 3;

    /**
     * JWT认证过滤器 - 在签名验证之后
     */
    public static final int JWT_AUTHENTICATION = Ordered.HIGHEST_PRECEDENCE + 4;

    /**
     * 增强限流过滤器 - 在认证之后
     */
    public static final int ENHANCED_RATE_LIMIT = Ordered.HIGHEST_PRECEDENCE + 5;

    /**
     * 传统限流过滤器
     */
    public static final int RATE_LIMIT = Ordered.HIGHEST_PRECEDENCE + 6;

    /**
     * 灰度发布过滤器
     */
    public static final int GRAY_RELEASE = Ordered.HIGHEST_PRECEDENCE + 7;

    /**
     * 指标收集过滤器
     */
    public static final int METRICS = Ordered.HIGHEST_PRECEDENCE + 8;

    /**
     * 响应缓存过滤器
     */
    public static final int RESPONSE_CACHE = Ordered.HIGHEST_PRECEDENCE + 10;

    /**
     * 熔断器过滤器 - 在响应写入前最后捕获异常
     */
    public static final int CIRCUIT_BREAKER = Ordered.LOWEST_PRECEDENCE - 2;

    /**
     * JWT令牌刷新过滤器 - 在熔断之后，响应提交前通知客户端
     */
    public static final int JWT_TOKEN_REFRESH = Ordered.LOWEST_PRECEDENCE - 1;

    /**
     * 请求日志（网关全局版本，已禁用，由 RequestLogFilter 替代）
     */
    public static final int GATEWAY_REQUEST_LOG = Ordered.HIGHEST_PRECEDENCE;
}
