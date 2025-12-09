package com.sapiece.nova.sapiecegateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.common.ErrorCode;
import com.sapiece.nova.sapiecegateway.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 熔断降级全局过滤器
 * 捕获下游服务异常，提供统一的降级响应
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    /**
     * 过滤器优先级
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    /**
     * 过滤器核心逻辑
     * 捕获下游服务调用异常，返回降级响应
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        log.debug("熔断降级过滤器执行, path: {}", path);

        return chain.filter(exchange)
                .onErrorResume(throwable -> {
                    log.error("下游服务调用异常, path: {}, error: {}", path, throwable.getMessage());
                    return handleFallback(exchange, throwable);
                });
    }

    /**
     * 处理降级响应
     *
     * @param exchange  服务器Web交换对象
     * @param throwable 异常信息
     * @return Mono<Void>
     */
    private Mono<Void> handleFallback(ServerWebExchange exchange, Throwable throwable) {
        ServerHttpResponse response = exchange.getResponse();

        // 设置响应状态码
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构建降级响应
        Result<?> result;

        if (throwable instanceof java.util.concurrent.TimeoutException ||
                throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
            // 超时异常
            log.warn("服务调用超时, path: {}", exchange.getRequest().getPath().value());
            result = Result.error(ErrorCode.REQUEST_TIMEOUT);
        } else if (throwable instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            // 熔断器打开，拒绝调用
            log.warn("熔断器已打开，拒绝调用, path: {}", exchange.getRequest().getPath().value());
            result = Result.error(ErrorCode.CIRCUIT_BREAKER_OPEN);
        } else {
            // 其他异常
            log.error("服务调用异常, error: {}", throwable.getMessage());
            result = Result.error(ErrorCode.SERVICE_UNAVAILABLE, "服务异常：" + throwable.getMessage());
        }

        // 返回降级响应
        return writeResponse(response, result);
    }

    /**
     * 将响应结果写入Response
     *
     * @param response 响应对象
     * @param result   结果对象
     * @return Mono<Void>
     */
    private Mono<Void> writeResponse(ServerHttpResponse response, Result<?> result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            return response.writeWith(Mono.just(buffer))
                    .doOnError(error -> log.error("写入降级响应失败: {}", error.getMessage()));
        } catch (JsonProcessingException e) {
            log.error("序列化降级响应失败", e);
            return Mono.error(e);
        }
    }
}
