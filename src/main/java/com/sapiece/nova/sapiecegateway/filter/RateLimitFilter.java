package com.sapiece.nova.sapiecegateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.web.server.WebFilterChain;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Redis限流过滤器（WebFilter版本）
 * 基于令牌桶算法实现接口限流
 * 使用Redis存储限流信息，支持分布式场景
 * WebFilter对所有请求生效（包括本地Controller和路由请求）
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
// @Component  // 暂时禁用
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * 限流开关（从配置文件读取）
     */
    @Value("${rate-limit.enabled:true}")
    private Boolean rateLimitEnabled;

    /**
     * 每个IP的QPS限制（每秒请求数）
     */
    @Value("${rate-limit.qps:10}")
    private Integer qps;

    /**
     * 令牌桶容量（突发流量容量）
     */
    @Value("${rate-limit.capacity:20}")
    private Integer capacity;

    /**
     * 限流键前缀
     */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    /**
     * 限流Lua脚本
     * 使用令牌桶算法实现限流
     * 返回值：1表示通过，0表示被限流
     */
    private static final String RATE_LIMIT_LUA_SCRIPT =
            "local key = KEYS[1]\n" +
            "local capacity = tonumber(ARGV[1])\n" +
            "local qps = tonumber(ARGV[2])\n" +
            "local timestamp = tonumber(ARGV[3])\n" +
            "\n" +
            "local info = redis.call('hmget', key, 'last_time', 'tokens')\n" +
            "local last_time = tonumber(info[1])\n" +
            "local tokens = tonumber(info[2])\n" +
            "\n" +
            "if last_time == nil then\n" +
            "  last_time = timestamp\n" +
            "  tokens = capacity\n" +
            "end\n" +
            "\n" +
            "local delta = math.max(0, timestamp - last_time)\n" +
            "local new_tokens = math.min(capacity, tokens + delta * qps)\n" +
            "\n" +
            "if new_tokens >= 1 then\n" +
            "  new_tokens = new_tokens - 1\n" +
            "  redis.call('hmset', key, 'last_time', timestamp, 'tokens', new_tokens)\n" +
            "  redis.call('expire', key, 10)\n" +
            "  return 1\n" +
            "else\n" +
            "  return 0\n" +
            "end";

    /**
     * 过滤器优先级
     */
    @Override
    public int getOrder() {
        return -100;  // 限流过滤器优先级较高
    }

    /**
     * 过滤器核心逻辑
     * 对每个请求进行限流检查
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果限流功能未启用，直接放行
        if (!rateLimitEnabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        String clientIp = getClientIp(exchange);

        log.debug("限流检查开始, path: {}, clientIp: {}", path, clientIp);

        // 生成限流键（基于IP地址）
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + clientIp;

        // 当前时间戳（秒）
        long timestamp = System.currentTimeMillis() / 1000;

        // 执行Lua脚本进行限流判断
        Mono<Boolean> rateLimitDecision = executeLuaScript(rateLimitKey, timestamp)
                .map(result -> {
                    if (result == 1) {
                        log.debug("限流检查通过, path: {}, clientIp: {}", path, clientIp);
                        return true;
                    }

                    log.warn("触发限流限制, path: {}, clientIp: {}, qps: {}", path, clientIp, qps);
                    return false;
                })
                .onErrorResume(error -> {
                    log.error("限流检查异常, 放行请求, path: {}, clientIp: {}, error: {}", path, clientIp, error.getMessage());
                    return Mono.just(true);
                });

        return rateLimitDecision.flatMap(allowed ->
                allowed ? chain.filter(exchange) : handleRateLimitExceeded(exchange));
    }

    /**
     * 执行Lua脚本进行限流判断
     *
     * @param key       限流键
     * @param timestamp 当前时间戳
     * @return 限流结果（1-通过，0-限流）
     */
    private Mono<Long> executeLuaScript(String key, long timestamp) {
        RedisScript<Long> script = RedisScript.of(RATE_LIMIT_LUA_SCRIPT, Long.class);

        return reactiveRedisTemplate.execute(
                script,
                List.of(key),
                List.of(String.valueOf(capacity), String.valueOf(qps), String.valueOf(timestamp))
        ).next().switchIfEmpty(Mono.error(new IllegalStateException("Redis限流脚本未返回结果")));
    }

    /**
     * 处理限流响应
     * 返回429状态码（Too Many Requests）
     *
     * @param exchange 服务器Web交换对象
     * @return Mono<Void>
     */
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String responseBody = String.format(
                "{\"code\": 429, \"message\": \"请求过于频繁，请稍后再试\", \"qps\": %d}",
                qps
        );

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))
        );
    }

    /**
     * 获取客户端真实IP
     * 考虑代理和负载均衡的情况
     *
     * @param exchange 服务器Web交换对象
     * @return 客户端IP
     */
    private String getClientIp(ServerWebExchange exchange) {
        // 尝试从X-Forwarded-For头获取（经过代理的情况）
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For可能包含多个IP，取第一个
            String ip = xForwardedFor.split(",")[0].trim();
            log.debug("从X-Forwarded-For获取客户端IP: {}", ip);
            return ip;
        }

        // 尝试从X-Real-IP头获取
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            log.debug("从X-Real-IP获取客户端IP: {}", xRealIp);
            return xRealIp;
        }

        // 直接从RemoteAddress获取
        String remoteAddress = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        log.debug("从RemoteAddress获取客户端IP: {}", remoteAddress);
        return remoteAddress;
    }

    /**
     * 检查指定IP是否被限流
     * 可用于监控和统计
     *
     * @param clientIp 客户端IP
     * @return 是否被限流
     */
    public Mono<Boolean> isRateLimited(String clientIp) {
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + clientIp;
        long timestamp = System.currentTimeMillis() / 1000;

        return executeLuaScript(rateLimitKey, timestamp)
                .map(result -> result == 0)
                .defaultIfEmpty(false);
    }

    /**
     * 清除指定IP的限流记录
     * 可用于管理员手动解除限流
     *
     * @param clientIp 客户端IP
     * @return 是否成功
     */
    public Mono<Boolean> clearRateLimit(String clientIp) {
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + clientIp;
        log.info("清除限流记录, clientIp: {}", clientIp);

        return reactiveRedisTemplate.delete(rateLimitKey)
                .map(count -> count > 0)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("成功清除限流记录, clientIp: {}", clientIp);
                    } else {
                        log.warn("限流记录不存在, clientIp: {}", clientIp);
                    }
                })
                .doOnError(error -> log.error("清除限流记录失败, clientIp: {}, error: {}", clientIp, error.getMessage()));
    }
}
