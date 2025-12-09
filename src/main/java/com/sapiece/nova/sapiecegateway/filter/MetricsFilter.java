package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.service.GatewayMetricsService;
import com.sapiece.nova.sapiecegateway.util.LogContext;
import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 监控指标收集过滤器
 * 自动收集所有请求的监控指标
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsFilter implements GlobalFilter, Ordered {

    private final GatewayMetricsService metricsService;

    /**
     * 过滤器优先级 - 最高，确保能够记录所有请求
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();

        // 记录请求开始时间
        long startTime = System.currentTimeMillis();

        // 设置结构化日志上下文
        LogContext.generateTraceId();
        LogContext.setRequestPath(path);
        LogContext.setRequestMethod(method);
        LogContext.setClientIp(ResponseUtil.getClientIp(exchange));

        // 记录请求
        metricsService.recordRequest(path, method);

        return chain.filter(exchange)
                .doOnSuccess(unused -> {
                    // 请求成功完成
                    long duration = System.currentTimeMillis() - startTime;
                    ServerHttpResponse response = exchange.getResponse();
                    HttpStatus statusCode = (HttpStatus) response.getStatusCode();

                    // 设置状态码和处理时长到日志上下文
                    if (statusCode != null) {
                        LogContext.setStatusCode(statusCode.value());
                    }
                    LogContext.setDuration(duration);

                    // 记录请求处理时间
                    metricsService.recordRequestDuration(path, method, duration);

                    // 记录错误响应
                    if (statusCode != null && statusCode.isError()) {
                        metricsService.recordError(statusCode.value(), path);

                        // 记录特定类型的错误
                        if (statusCode == HttpStatus.UNAUTHORIZED) {
                            metricsService.recordAuthFailure(path);
                        } else if (statusCode == HttpStatus.FORBIDDEN) {
                            metricsService.recordPermissionDenied(path);
                        } else if (statusCode == HttpStatus.TOO_MANY_REQUESTS) {
                            metricsService.recordRateLimitHit(path);
                        }
                    }

                    log.debug("请求完成 [{}] {} - {} - {}ms",
                            method, path, statusCode != null ? statusCode.value() : "unknown", duration);
                })
                .doOnError(error -> {
                    // 请求处理出错
                    long duration = System.currentTimeMillis() - startTime;

                    // 设置错误状态码和处理时长到日志上下文
                    LogContext.setStatusCode(500);
                    LogContext.setDuration(duration);

                    // 记录处理时间
                    metricsService.recordRequestDuration(path, method, duration);

                    // 记录错误
                    metricsService.recordError(500, path);

                    log.error("请求失败 [{}] {} - {}ms - error: {}",
                            method, path, duration, error.getMessage());
                })
                .doFinally(signalType -> {
                    // 清除日志上下文（避免内存泄漏）
                    LogContext.clear();
                });
    }
}
