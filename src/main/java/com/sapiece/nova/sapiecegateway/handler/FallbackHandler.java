package com.sapiece.nova.sapiecegateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级处理器
 * 当服务不可用时返回友好的降级响应
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackHandler {

    private final ObjectMapper objectMapper;

    /**
     * 默认降级响应
     */
    public Mono<ServerResponse> defaultFallback(ServerRequest request) {
        String path = request.path();
        log.warn("服务降级: path={}", path);

        Map<String, Object> data = new HashMap<>();
        data.put("path", path);
        data.put("message", "服务暂时不可用，请稍后重试");

        Result<Map<String, Object>> result = Result.error("服务降级：后端服务暂时不可用", data);

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }

    /**
     * 超时降级响应
     */
    public Mono<ServerResponse> timeoutFallback(ServerRequest request) {
        String path = request.path();
        log.warn("请求超时降级: path={}", path);

        Map<String, Object> data = new HashMap<>();
        data.put("path", path);
        data.put("message", "请求处理超时");

        Result<Map<String, Object>> result = Result.error("请求超时：服务响应时间过长", data);

        return ServerResponse.status(HttpStatus.GATEWAY_TIMEOUT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }

    /**
     * 熔断器打开时的降级响应
     */
    public Mono<ServerResponse> circuitBreakerFallback(ServerRequest request) {
        String path = request.path();
        log.warn("熔断器打开降级: path={}", path);

        Map<String, Object> data = new HashMap<>();
        data.put("path", path);
        data.put("message", "服务熔断，请稍后重试");
        data.put("tip", "服务正在恢复中，预计30秒后可用");

        Result<Map<String, Object>> result = Result.error("服务熔断：后端服务异常率过高", data);

        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }
}
