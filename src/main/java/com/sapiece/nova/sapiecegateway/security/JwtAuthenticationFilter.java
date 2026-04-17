package com.sapiece.nova.sapiecegateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT认证过滤器（已迁移）
 * 认证逻辑已重构到 JwtSecurityContextRepository，这是 Spring Security WebFlux 的标准做法。
 * 保留此类仅用于兼容已注册的 FilterOrders.JWT_AUTHENTICATION 顺序常量。
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return com.sapiece.nova.sapiecegateway.common.FilterOrders.JWT_AUTHENTICATION;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange);
    }
}
