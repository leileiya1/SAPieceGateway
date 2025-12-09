package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * JWT Token 自动刷新过滤器
 *
 * 功能：
 * 1. 检查请求中的 JWT Token 是否即将过期
 * 2. 如果即将过期且仍然有效，生成新的 Token
 * 3. 将新 Token 添加到响应头中返回给客户端
 *
 * 实现原理：
 * - 在响应写入前检查并刷新 Token
 * - 使用 ServerHttpResponse.beforeCommit() 确保在响应提交前执行
 * - 不干扰认证流程，只负责 Token 刷新
 *
 * 客户端处理：
 * - 客户端收到响应后，检查响应头中的 X-New-Token
 * - 如果存在新 Token，替换本地存储的旧 Token
 *
 * @author SAPiece
 * @since 2025-11-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenRefreshFilter implements WebFilter, Ordered {

    private final JwtUtil jwtUtil;

    /**
     * 是否启用 Token 自动刷新
     */
    @Value("${jwt.refresh.enabled:true}")
    private Boolean refreshEnabled;

    /**
     * Token 前缀
     */
    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 新 Token 响应头名称
     */
    private static final String NEW_TOKEN_HEADER = "X-New-Token";

    /**
     * 过滤器优先级
     * 需要在认证之后、响应写入之前执行
     */
    @Override
    public int getOrder() {
        // 在 SecurityWebFilterChain 之后执行
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果刷新未启用，直接放行
        if (!refreshEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        // 1. 从请求头提取 Token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            // 没有 Token，直接放行
            return chain.filter(exchange);
        }

        // 2. 在响应提交前检查并刷新 Token
        response.beforeCommit(() -> {
            return refreshTokenIfNeeded(token, response, request.getPath().value());
        });

        // 3. 继续执行过滤器链
        return chain.filter(exchange);
    }

    /**
     * 从请求头提取 JWT Token
     */
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }

        return null;
    }

    /**
     * 检查 Token 是否需要刷新，如果需要则生成新 Token
     */
    private Mono<Void> refreshTokenIfNeeded(String token, ServerHttpResponse response, String path) {
        try {
            // 1. 检查 Token 是否仍然有效
            if (!jwtUtil.validateToken(token)) {
                log.debug("Token 无效或已过期，不刷新, path: {}", path);
                return Mono.empty();
            }

            // 2. 检查 Token 是否需要刷新
            if (!jwtUtil.shouldRefresh(token)) {
                log.debug("Token 不需要刷新, path: {}", path);
                return Mono.empty();
            }

            // 3. 生成新 Token
            String newToken = jwtUtil.refreshToken(token);

            // 4. 将新 Token 添加到响应头
            response.getHeaders().set(NEW_TOKEN_HEADER, newToken);

            log.info("Token 已刷新, path: {}, 新 Token 已添加到响应头 {}", path, NEW_TOKEN_HEADER);

        } catch (Exception e) {
            log.error("Token 刷新失败, path: {}, error: {}", path, e.getMessage());
        }

        return Mono.empty();
    }
}
