package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.common.ErrorCode;
import com.sapiece.nova.sapiecegateway.ratelimit.RateLimitStrategy;
import com.sapiece.nova.sapiecegateway.service.GatewayMetricsService;
import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 增强型限流过滤器
 * 支持多种限流策略（IP、路由、用户）
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnhancedRateLimitFilter implements WebFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final List<RateLimitStrategy> rateLimitStrategies;
    private final GatewayMetricsService metricsService;

    @Value("${rate-limit.enabled:true}")
    private Boolean rateLimitEnabled;

    @Value("${rate-limit.strategy:route}")
    private String strategyName;

    /**
     * 限流Lua脚本（令牌桶算法）
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;  // 在认证之后执行
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果限流功能未启用，直接放行
        if (!rateLimitEnabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        log.debug("限流检查开始, path: {}, strategy: {}", path, strategyName);

        // 选择限流策略
        RateLimitStrategy strategy = selectStrategy();
        if (strategy == null) {
            log.warn("未找到限流策略: {}, 跳过限流", strategyName);
            return chain.filter(exchange);
        }

        // 获取限流配置
        String rateLimitKey = strategy.getRateLimitKey(exchange);
        Mono<Integer> qpsMono = strategy.getQpsLimit(exchange);
        Mono<Integer> capacityMono = strategy.getCapacity(exchange);

        return Mono.zip(qpsMono, capacityMono)
                .flatMap(tuple -> {
                    Integer qps = tuple.getT1();
                    Integer capacity = tuple.getT2();
                    long timestamp = System.currentTimeMillis() / 1000;

                    log.debug("限流策略: {}, key: {}, qps: {}, capacity: {}",
                            strategy.getStrategyName(), rateLimitKey, qps, capacity);

                    // 执行限流检查
                    return executeLuaScript(rateLimitKey, capacity, qps, timestamp)
                            .flatMap(result -> {
                                if (result == 1) {
                                    // 限流通过
                                    log.debug("限流检查通过, path: {}", path);
                                    return chain.filter(exchange);
                                } else {
                                    // 触发限流
                                    log.warn("触发限流限制, strategy: {}, path: {}, qps: {}",
                                            strategy.getStrategyName(), path, qps);
                                    // 记录限流指标
                                    metricsService.recordRateLimitHit(path);
                                    return handleRateLimitExceeded(exchange);
                                }
                            });
                })
                .onErrorResume(error -> {
                    // Redis异常时，放行请求（避免因Redis故障导致服务不可用）
                    log.error("限流检查异常, 放行请求, path: {}, error: {}", path, error.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 选择限流策略
     *
     * @return 限流策略
     */
    private RateLimitStrategy selectStrategy() {
        return rateLimitStrategies.stream()
                .filter(strategy -> {
                    String name = strategy.getStrategyName().toLowerCase().replace("-based", "");
                    return strategyName.toLowerCase().equals(name);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 执行Lua脚本进行限流判断
     *
     * @param key       限流键
     * @param capacity  令牌桶容量
     * @param qps       QPS限制
     * @param timestamp 当前时间戳
     * @return 限流结果（1-通过，0-限流）
     */
    private Mono<Long> executeLuaScript(String key, Integer capacity, Integer qps, long timestamp) {
        RedisScript<Long> script = RedisScript.of(RATE_LIMIT_LUA_SCRIPT, Long.class);

        return reactiveRedisTemplate.execute(
                script,
                List.of(key),
                List.of(String.valueOf(capacity), String.valueOf(qps), String.valueOf(timestamp))
        ).next();
    }

    /**
     * 处理限流响应
     *
     * @param exchange ServerWebExchange
     * @return Mono<Void>
     */
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return ResponseUtil.error(exchange, ErrorCode.RATE_LIMIT_EXCEEDED);
    }
}
