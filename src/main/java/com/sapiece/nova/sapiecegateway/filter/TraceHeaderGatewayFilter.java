package com.sapiece.nova.sapiecegateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 链路追踪 Header 透传过滤器
 *
 * 将当前 Span 的 traceId/spanId 注入下游请求头，实现跨服务链路串联：
 *   X-Trace-Id  : traceId（全链路唯一 ID）
 *   X-Span-Id   : spanId（当前跨度 ID）
 *   traceparent : W3C TraceContext 标准格式（下游 OTel SDK 自动识别）
 *
 * 下游服务配置了 OTel SDK 后，traceparent header 会自动被接管，
 * 无需额外代码即可在 Jaeger 中看到完整调用链。
 *
 * @author SAPiece
 * @since 2026-04-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TraceHeaderGatewayFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;

    @Override
    public int getOrder() {
        return -49; // 紧跟 AuthHeaderGatewayFilter(-50) 之后
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return chain.filter(exchange);
        }

        String traceId = currentSpan.context().traceId();
        String spanId  = currentSpan.context().spanId();

        // W3C traceparent 格式：version-traceId-spanId-flags
        String traceparent = "00-" + traceId + "-" + spanId + "-01";

        ServerHttpRequest enriched = exchange.getRequest().mutate()
                .header("X-Trace-Id",  traceId)
                .header("X-Span-Id",   spanId)
                .header("traceparent", traceparent)
                .build();

        log.debug("链路追踪 header 注入, traceId={}, spanId={}", traceId, spanId);

        return chain.filter(exchange.mutate().request(enriched).build());
    }
}
