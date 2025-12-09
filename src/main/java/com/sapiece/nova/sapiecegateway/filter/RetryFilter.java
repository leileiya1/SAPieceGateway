package com.sapiece.nova.sapiecegateway.filter;

import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 请求重试过滤器（重构版）
 *
 * 主要改进：
 * 1. 只对真正的失败（网络错误、超时、5xx 错误）重试
 * 2. 不对成功响应（2xx）或客户端错误（4xx）重试
 * 3. 只对幂等请求（GET、PUT、DELETE）重试
 * 4. 只对路由请求（通过网关转发）重试，本地 Controller 请求不重试
 * 5. 使用更精确的错误判断，避免不必要的重试
 *
 * 重试条件：
 * - 网络连接错误（ConnectException）
 * - 请求超时（TimeoutException）
 * - 服务端错误（5xx）
 * - IO 异常
 *
 * 不重试的情况：
 * - 客户端错误（4xx）：认证失败、参数错误等
 * - 成功响应（2xx、3xx）
 * - 非幂等请求（POST）
 * - 本地 Controller 请求
 *
 * @author SAPiece
 * @since 2025-11-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetryFilter implements WebFilter, Ordered {

    private final Retry defaultRetry;

    /**
     * 是否启用重试
     */
    @Value("${retry.enabled:true}")
    private Boolean retryEnabled;

    /**
     * 幂等HTTP方法（可以安全重试）
     */
    private static final List<HttpMethod> IDEMPOTENT_METHODS = Arrays.asList(
            HttpMethod.GET,
            HttpMethod.PUT,
            HttpMethod.DELETE,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS
    );

    /**
     * 排除路径（不需要重试的接口）
     */
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/actuator/",
            "/auth/login",
            "/auth/register"
    );

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果重试未启用，直接放行
        if (!retryEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();

        log.debug("重试过滤器执行, method: {}, path: {}", method, path);

        // 1. 检查是否是排除路径（本地 Controller 请求）
        if (isLocalControllerPath(path)) {
            log.debug("本地 Controller 请求，跳过重试, path: {}", path);
            return chain.filter(exchange);
        }

        // 2. 检查是否是排除路径
        if (isExcludedPath(path)) {
            log.debug("排除路径，跳过重试, path: {}", path);
            return chain.filter(exchange);
        }

        // 3. 只对幂等请求应用重试策略
        if (!isIdempotent(method)) {
            log.debug("非幂等方法，跳过重试, method: {}", method);
            return chain.filter(exchange);
        }

        // 4. 应用重试策略，只对可重试的错误进行重试
        log.debug("应用重试策略, method: {}, path: {}", method, path);

        return chain.filter(exchange)
                .transformDeferred(RetryOperator.of(defaultRetry))
                .doOnSuccess(v -> log.debug("请求成功, path: {}", path))
                .onErrorResume(error -> {
                    // 只对可重试的错误记录日志
                    if (isRetryableError(error)) {
                        log.warn("请求失败（已重试），path: {}, error: {}", path, error.getMessage());
                    } else {
                        log.debug("请求失败（不可重试），path: {}, error: {}", path, error.getMessage());
                    }
                    // 将错误传递给下游处理
                    return Mono.error(error);
                });
    }

    /**
     * 判断HTTP方法是否幂等
     *
     * @param method HTTP方法
     * @return 是否幂等
     */
    private boolean isIdempotent(HttpMethod method) {
        return IDEMPOTENT_METHODS.contains(method);
    }

    /**
     * 判断是否是排除路径
     *
     * @param path 请求路径
     * @return 是否排除
     */
    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 判断是否是本地 Controller 请求
     * 本地 Controller 请求不需要重试（因为不涉及网络调用）
     *
     * @param path 请求路径
     * @return 是否是本地 Controller 请求
     */
    private boolean isLocalControllerPath(String path) {
        // 本地 Controller 的路径特征
        // 1. 以 /admin/ 开头的管理接口
        // 2. 以 /auth/ 开头的认证接口
        // 3. 以 /gateway/ 开头的网关管理接口
        return path.startsWith("/admin/") ||
                path.startsWith("/auth/") ||
                path.startsWith("/gateway/") ||
                path.startsWith("/actuator/");
    }

    /**
     * 判断错误是否可重试
     * 只有以下错误才应该重试：
     * - 网络连接错误（ConnectException）
     * - 请求超时（TimeoutException）
     * - IO 异常（IOException）
     * - 5xx 服务端错误（ResponseStatusException with 5xx status）
     *
     * 不应该重试的错误：
     * - 4xx 客户端错误（认证失败、参数错误等）
     * - 业务异常
     *
     * @param error 异常
     * @return 是否可重试
     */
    private boolean isRetryableError(Throwable error) {
        // 网络连接错误 - 可重试
        if (error instanceof ConnectException) {
            return true;
        }

        // 超时错误 - 可重试
        if (error instanceof TimeoutException) {
            return true;
        }

        // IO 异常 - 可重试
        if (error instanceof IOException) {
            return true;
        }

        // ResponseStatusException - 只有 5xx 错误可重试
        if (error instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) error;
            HttpStatus status = (HttpStatus) rse.getStatusCode();
            return status.is5xxServerError();
        }

        // 其他异常 - 不重试
        return false;
    }
}
