package com.sapiece.nova.sapiecegateway.ratelimit;

import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于路由的限流策略
 * 对不同的API路径设置不同的限流规则
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
public class RouteBasedRateLimitStrategy implements RateLimitStrategy {

    @Value("${rate-limit.route.default-qps:50}")
    private Integer defaultQps;

    @Value("${rate-limit.route.default-capacity:100}")
    private Integer defaultCapacity;

    private static final String KEY_PREFIX = "rate_limit:route:";

    /**
     * 路由级别的限流配置
     * 实际生产环境中应该从数据库读取
     */
    private final Map<String, RateLimitConfig> routeConfigs = new HashMap<>();

    public RouteBasedRateLimitStrategy() {
        // 示例配置：不同路径的限流规则
        routeConfigs.put("/admin/**", new RateLimitConfig(10, 20));
        routeConfigs.put("/api/user/**", new RateLimitConfig(100, 200));
        routeConfigs.put("/api/order/**", new RateLimitConfig(50, 100));
        routeConfigs.put("/api/product/**", new RateLimitConfig(200, 400));
    }

    @Override
    public String getRateLimitKey(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String clientIp = ResponseUtil.getClientIp(exchange);
        // 组合路径和IP，实现针对每个IP在特定路径上的限流
        return KEY_PREFIX + simplifyPath(path) + ":" + clientIp;
    }

    @Override
    public Mono<Integer> getQpsLimit(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        RateLimitConfig config = findMatchingConfig(path);
        return Mono.just(config != null ? config.qps : defaultQps);
    }

    @Override
    public Mono<Integer> getCapacity(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        RateLimitConfig config = findMatchingConfig(path);
        return Mono.just(config != null ? config.capacity : defaultCapacity);
    }

    @Override
    public String getStrategyName() {
        return "Route-Based";
    }

    /**
     * 查找匹配的路由配置
     *
     * @param path 请求路径
     * @return 限流配置
     */
    private RateLimitConfig findMatchingConfig(String path) {
        for (Map.Entry<String, RateLimitConfig> entry : routeConfigs.entrySet()) {
            String pattern = entry.getKey();
            if (pathMatches(path, pattern)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 路径匹配（支持通配符）
     *
     * @param path    实际路径
     * @param pattern 匹配模式
     * @return 是否匹配
     */
    private boolean pathMatches(String path, String pattern) {
        // 简单的通配符匹配实现
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        return path.equals(pattern);
    }

    /**
     * 简化路径（移除路径参数）
     *
     * @param path 原始路径
     * @return 简化后的路径
     */
    private String simplifyPath(String path) {
        // 替换数字 ID 为 {id}
        path = path.replaceAll("/\\d+(/|$)", "/{id}$1");
        // 替换 UUID 为 {uuid}
        path = path.replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(/|$)", "/{uuid}$1");
        return path;
    }

    /**
     * 限流配置内部类
     */
    private static class RateLimitConfig {
        final int qps;
        final int capacity;

        RateLimitConfig(int qps, int capacity) {
            this.qps = qps;
            this.capacity = capacity;
        }
    }
}
