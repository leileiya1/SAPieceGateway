package com.sapiece.nova.sapiecegateway.ratelimit;

import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 基于IP的限流策略
 * 对每个IP地址设置统一的限流规则
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
public class IpBasedRateLimitStrategy implements RateLimitStrategy {

    @Value("${rate-limit.ip.qps:100}")
    private Integer defaultQps;

    @Value("${rate-limit.ip.capacity:200}")
    private Integer defaultCapacity;

    private static final String KEY_PREFIX = "rate_limit:ip:";

    @Override
    public String getRateLimitKey(ServerWebExchange exchange) {
        String clientIp = ResponseUtil.getClientIp(exchange);
        return KEY_PREFIX + clientIp;
    }

    @Override
    public Mono<Integer> getQpsLimit(ServerWebExchange exchange) {
        return Mono.just(defaultQps);
    }

    @Override
    public Mono<Integer> getCapacity(ServerWebExchange exchange) {
        return Mono.just(defaultCapacity);
    }

    @Override
    public String getStrategyName() {
        return "IP-Based";
    }
}
