package com.sapiece.nova.sapiecegateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 网关监控指标服务
 * 提供自定义的 Prometheus 监控指标
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayMetricsService {

    private final MeterRegistry meterRegistry;

    // ==================== Counters (计数器) ====================

    /**
     * 请求总数计数器
     */
    private Counter requestTotalCounter;

    /**
     * 错误请求计数器
     */
    private Counter errorTotalCounter;

    /**
     * 认证失败计数器
     */
    private Counter authFailureCounter;

    /**
     * 权限拒绝计数器
     */
    private Counter permissionDeniedCounter;

    /**
     * 限流触发计数器
     */
    private Counter rateLimitCounter;

    /**
     * 熔断器打开计数器
     */
    private Counter circuitBreakerOpenCounter;

    /**
     * 重试请求计数器
     */
    private Counter retryCounter;

    // ==================== Timers (计时器) ====================

    /**
     * 请求处理时间计时器
     */
    private Timer requestDurationTimer;

    /**
     * 初始化指标
     */
    @PostConstruct
    public void init() {
        log.info("初始化 Gateway Metrics Service");

        // 请求总数
        requestTotalCounter = Counter.builder("gateway.requests.total")
                .description("网关请求总数")
                .register(meterRegistry);

        // 错误请求总数
        errorTotalCounter = Counter.builder("gateway.errors.total")
                .description("网关错误请求总数")
                .register(meterRegistry);

        // 认证失败总数
        authFailureCounter = Counter.builder("gateway.auth.failures")
                .description("认证失败次数")
                .register(meterRegistry);

        // 权限拒绝总数
        permissionDeniedCounter = Counter.builder("gateway.permission.denied")
                .description("权限拒绝次数")
                .register(meterRegistry);

        // 限流触发总数
        rateLimitCounter = Counter.builder("gateway.ratelimit.hits")
                .description("限流触发次数")
                .register(meterRegistry);

        // 熔断器打开总数
        circuitBreakerOpenCounter = Counter.builder("gateway.circuitbreaker.open")
                .description("熔断器打开次数")
                .register(meterRegistry);

        // 重试请求总数
        retryCounter = Counter.builder("gateway.retry.attempts")
                .description("请求重试次数")
                .register(meterRegistry);

        // 请求处理时间
        requestDurationTimer = Timer.builder("gateway.request.duration")
                .description("请求处理时长")
                .register(meterRegistry);

        log.info("Gateway Metrics 初始化完成");
    }

    // ==================== 请求相关指标 ====================

    /**
     * 记录请求（带路径和方法标签）
     *
     * @param path   请求路径
     * @param method 请求方法
     */
    public void recordRequest(String path, String method) {
        Counter.builder("gateway.requests.total")
                .tag("path", simplifyPath(path))
                .tag("method", method)
                .description("网关请求总数（按路径和方法分组）")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录请求处理时间
     *
     * @param path     请求路径
     * @param method   请求方法
     * @param duration 处理时长（毫秒）
     */
    public void recordRequestDuration(String path, String method, long duration) {
        Timer.builder("gateway.request.duration")
                .tag("path", simplifyPath(path))
                .tag("method", method)
                .description("请求处理时长（按路径和方法分组）")
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);
    }

    // ==================== 错误相关指标 ====================

    /**
     * 记录错误请求（带错误码标签）
     *
     * @param errorCode 错误码
     * @param path      请求路径
     */
    public void recordError(int errorCode, String path) {
        Counter.builder("gateway.errors.total")
                .tag("error_code", String.valueOf(errorCode))
                .tag("path", simplifyPath(path))
                .description("网关错误请求总数（按错误码和路径分组）")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录认证失败
     *
     * @param path 请求路径
     */
    public void recordAuthFailure(String path) {
        authFailureCounter.increment();
        Counter.builder("gateway.auth.failures")
                .tag("path", simplifyPath(path))
                .description("认证失败次数（按路径分组）")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录权限拒绝
     *
     * @param path 请求路径
     */
    public void recordPermissionDenied(String path) {
        permissionDeniedCounter.increment();
        Counter.builder("gateway.permission.denied")
                .tag("path", simplifyPath(path))
                .description("权限拒绝次数（按路径分组）")
                .register(meterRegistry)
                .increment();
    }

    // ==================== 限流熔断相关指标 ====================

    /**
     * 记录限流触发
     *
     * @param path 请求路径
     */
    public void recordRateLimitHit(String path) {
        rateLimitCounter.increment();
        Counter.builder("gateway.ratelimit.hits")
                .tag("path", simplifyPath(path))
                .description("限流触发次数（按路径分组）")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录熔断器打开
     *
     * @param serviceName 服务名称
     */
    public void recordCircuitBreakerOpen(String serviceName) {
        circuitBreakerOpenCounter.increment();
        Counter.builder("gateway.circuitbreaker.open")
                .tag("service", serviceName)
                .description("熔断器打开次数（按服务分组）")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录请求重试
     *
     * @param path         请求路径
     * @param attemptCount 重试次数
     */
    public void recordRetry(String path, int attemptCount) {
        retryCounter.increment();
        Counter.builder("gateway.retry.attempts")
                .tag("path", simplifyPath(path))
                .tag("attempts", String.valueOf(attemptCount))
                .description("请求重试次数（按路径和重试次数分组）")
                .register(meterRegistry)
                .increment();
    }

    // ==================== 工具方法 ====================

    /**
     * 简化路径（移除路径参数，避免标签数量爆炸）
     * 例如：/api/user/123 -> /api/user/{id}
     *
     * @param path 原始路径
     * @return 简化后的路径
     */
    private String simplifyPath(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }

        // 替换数字 ID 为 {id}
        path = path.replaceAll("/\\d+(/|$)", "/{id}$1");

        // 替换 UUID 为 {uuid}
        path = path.replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(/|$)", "/{uuid}$1");

        // 限制路径长度
        if (path.length() > 100) {
            path = path.substring(0, 100) + "...";
        }

        return path;
    }
}
