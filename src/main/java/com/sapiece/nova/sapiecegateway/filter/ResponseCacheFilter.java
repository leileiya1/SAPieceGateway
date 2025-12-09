package com.sapiece.nova.sapiecegateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 响应缓存过滤器
 * 对GET请求的响应进行缓存，提升性能
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
// @Component  // 暂时禁用
@RequiredArgsConstructor
public class ResponseCacheFilter implements WebFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * 是否启用响应缓存
     */
    @Value("${response-cache.enabled:true}")
    private Boolean cacheEnabled;

    /**
     * 缓存过期时间（秒）
     */
    @Value("${response-cache.ttl:300}")
    private Long cacheTtl;

    /**
     * 缓存键前缀
     */
    private static final String CACHE_KEY_PREFIX = "response_cache:";

    /**
     * 需要缓存的路径前缀
     */
    private static final List<String> CACHE_PATH_PATTERNS = Arrays.asList(
            "/api/user/info",
            "/api/config/",
            "/api/dict/"
    );

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果缓存未启用，直接放行
        if (!cacheEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 只缓存GET请求
        if (!HttpMethod.GET.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 检查是否是需要缓存的路径
        if (!shouldCache(path)) {
            return chain.filter(exchange);
        }

        // 生成缓存键
        String cacheKey = generateCacheKey(request);

        log.debug("检查响应缓存, path: {}, cacheKey: {}", path, cacheKey);

        // 尝试从缓存获取
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedResponse -> {
                    log.info("命中响应缓存, path: {}", path);
                    // 返回缓存的响应
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.OK);
                    response.getHeaders().add("X-Cache", "HIT");
                    return response.writeWith(
                            Mono.just(response.bufferFactory().wrap(cachedResponse.getBytes()))
                    );
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("未命中响应缓存, path: {}", path);
                    // 缓存未命中，继续处理请求
                    // 注意：实际的响应缓存需要包装Response，这里简化处理
                    exchange.getResponse().getHeaders().add("X-Cache", "MISS");
                    return chain.filter(exchange);
                }))
                .onErrorResume(error -> {
                    log.error("缓存读取失败, path: {}, error: {}", path, error.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 判断路径是否需要缓存
     */
    private boolean shouldCache(String path) {
        return CACHE_PATH_PATTERNS.stream()
                .anyMatch(path::startsWith);
    }

    /**
     * 生成缓存键
     * 格式: response_cache:GET:/api/user/info?id=123
     */
    private String generateCacheKey(ServerHttpRequest request) {
        String method = request.getMethod().name();
        String path = request.getPath().value();
        String query = request.getURI().getQuery();

        StringBuilder key = new StringBuilder(CACHE_KEY_PREFIX);
        key.append(method).append(":").append(path);

        if (query != null && !query.isEmpty()) {
            key.append("?").append(query);
        }

        return key.toString();
    }

    /**
     * 将响应存入缓存
     * 注意：这个方法需要在实际的响应写入后调用
     */
    public Mono<Boolean> cacheResponse(String cacheKey, String responseBody) {
        return reactiveRedisTemplate.opsForValue()
                .set(cacheKey, responseBody, Duration.ofSeconds(cacheTtl))
                .doOnSuccess(success -> {
                    if (success) {
                        log.debug("响应已缓存, cacheKey: {}, ttl: {}s", cacheKey, cacheTtl);
                    }
                })
                .doOnError(error -> log.error("缓存响应失败, cacheKey: {}, error: {}",
                        cacheKey, error.getMessage()));
    }

    /**
     * 清除指定路径的缓存
     */
    public Mono<Long> clearCache(String pathPattern) {
        String pattern = CACHE_KEY_PREFIX + "*" + pathPattern + "*";
        return reactiveRedisTemplate.keys(pattern)
                .flatMap(reactiveRedisTemplate::delete)
                .reduce(0L, Long::sum)
                .doOnSuccess(count -> log.info("清除缓存成功, pattern: {}, count: {}", pattern, count));
    }
}
