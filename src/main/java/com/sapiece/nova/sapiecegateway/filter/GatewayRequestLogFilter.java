package com.sapiece.nova.sapiecegateway.filter;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gateway全局请求日志过滤器
 * 使用Spring Cloud Gateway的GlobalFilter机制记录请求和响应信息
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
// @Component  // 禁用GlobalFilter，使用WebFilter版本的RequestLogFilter
public class GatewayRequestLogFilter implements GlobalFilter, Ordered {

    /**
     * 日期时间格式化器
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 请求ID的Header名称
     */
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    /**
     * 过滤器优先级（数值越小，优先级越高）
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 生成或获取请求ID
        String requestId = getOrGenerateRequestId(request);

        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        LocalDateTime requestTime = LocalDateTime.now();

        // 记录请求信息
        logRequest(requestId, request, requestTime);

        // 继续执行过滤器链
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    // 计算请求耗时
                    long duration = System.currentTimeMillis() - startTime;
                    // 记录响应信息
                    logResponse(requestId, request, response, duration);
                }));
    }

    /**
     * 获取或生成请求ID
     */
    private String getOrGenerateRequestId(ServerHttpRequest request) {
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = IdUtil.simpleUUID();
        }
        return requestId;
    }

    /**
     * 记录请求信息
     */
    private void logRequest(String requestId, ServerHttpRequest request, LocalDateTime requestTime) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        String query = request.getURI().getQuery();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);

        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n╔════════════════════════════ 请求开始 ════════════════════════════╗\n");
        logMessage.append(String.format("║ 请求ID      : %s\n", requestId));
        logMessage.append(String.format("║ 请求时间    : %s\n", requestTime.format(DATE_TIME_FORMATTER)));
        logMessage.append(String.format("║ 请求方法    : %s\n", method));
        logMessage.append(String.format("║ 请求路径    : %s\n", path));
        if (query != null && !query.isEmpty()) {
            logMessage.append(String.format("║ 请求参数    : %s\n", query));
        }
        logMessage.append(String.format("║ 客户端IP    : %s\n", clientIp));
        if (userAgent != null) {
            logMessage.append(String.format("║ User-Agent  : %s\n", userAgent));
        }
        logMessage.append("╚═══════════════════════════════════════════════════════════════╝");

        log.info(logMessage.toString());
    }

    /**
     * 记录响应信息
     */
    private void logResponse(String requestId, ServerHttpRequest request, ServerHttpResponse response, long duration) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;
        LocalDateTime responseTime = LocalDateTime.now();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n╔════════════════════════════ 请求结束 ════════════════════════════╗\n");
        logMessage.append(String.format("║ 请求ID      : %s\n", requestId));
        logMessage.append(String.format("║ 响应时间    : %s\n", responseTime.format(DATE_TIME_FORMATTER)));
        logMessage.append(String.format("║ 请求方法    : %s\n", method));
        logMessage.append(String.format("║ 请求路径    : %s\n", path));
        logMessage.append(String.format("║ 响应状态    : %d\n", statusCode));
        logMessage.append(String.format("║ 请求耗时    : %d ms\n", duration));

        if (statusCode >= 500) {
            logMessage.append("║ 请求状态    : 失败 ❌\n");
        } else if (statusCode >= 400) {
            logMessage.append("║ 请求状态    : 客户端错误 ⚠️\n");
        } else {
            logMessage.append("║ 请求状态    : 成功 ✓\n");
        }

        logMessage.append("╚═══════════════════════════════════════════════════════════════╝");

        // 根据状态码选择日志级别
        if (statusCode >= 500) {
            log.error(logMessage.toString());
        } else if (statusCode >= 400) {
            log.warn(logMessage.toString());
        } else {
            log.info(logMessage.toString());
        }

        // 如果请求耗时过长，额外记录警告日志
        if (duration > 3000) {
            log.warn("⚠️ 慢请求警告 - requestId: {}, path: {}, duration: {} ms", requestId, path, duration);
        }
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(ServerHttpRequest request) {
        // 尝试从X-Forwarded-For头获取
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // 尝试从X-Real-IP头获取
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 直接从RemoteAddress获取
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
