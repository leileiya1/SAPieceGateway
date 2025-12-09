package com.sapiece.nova.sapiecegateway.ratelimit;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 限流策略接口
 * 定义限流策略的通用行为
 *
 * @author SAPiece
 * @since 2025-11-26
 */
public interface RateLimitStrategy {

    /**
     * 获取限流键
     *
     * @param exchange ServerWebExchange
     * @return 限流键
     */
    String getRateLimitKey(ServerWebExchange exchange);

    /**
     * 获取QPS限制
     *
     * @param exchange ServerWebExchange
     * @return QPS限制
     */
    Mono<Integer> getQpsLimit(ServerWebExchange exchange);

    /**
     * 获取令牌桶容量
     *
     * @param exchange ServerWebExchange
     * @return 令牌桶容量
     */
    Mono<Integer> getCapacity(ServerWebExchange exchange);

    /**
     * 策略名称
     *
     * @return 策略名称
     */
    String getStrategyName();
}
