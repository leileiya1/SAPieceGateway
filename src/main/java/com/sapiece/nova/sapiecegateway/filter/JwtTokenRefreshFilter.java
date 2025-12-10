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
 * JWT Token 自动刷新过滤器（双Token模式）
 *
 * 功能：
 * 1. 检查请求中的 Access Token 是否即将过期
 * 2. 如果即将过期（剩余时间 < 5分钟），通过响应头通知客户端需要刷新
 * 3. 客户端收到通知后，使用 Refresh Token 调用 /auth/refresh/token 接口获取新Token
 *
 * 双Token模式说明：
 * - Access Token: 短期有效（30分钟），用于API访问认证
 * - Refresh Token: 长期有效（7天），用于刷新Access Token
 * - 当Access Token即将过期时，通过响应头 X-Token-Expired-Soon: true 通知客户端
 * - 客户端收到通知后，应使用Refresh Token调用刷新接口
 *
 * 客户端处理：
 * - 客户端收到响应后，检查响应头中的 X-Token-Expired-Soon
 * - 如果为 true，使用 Refresh Token 调用 POST /auth/refresh/token 获取新Token
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
     * Token即将过期通知响应头
     */
    private static final String TOKEN_EXPIRED_SOON_HEADER = "X-Token-Expired-Soon";

    /**
     * Access Token剩余有效期响应头（秒）
     */
    private static final String TOKEN_EXPIRES_IN_HEADER = "X-Token-Expires-In";

    /**
     * Token即将过期的阈值（毫秒）默认5分钟
     */
    private static final long TOKEN_EXPIRE_SOON_THRESHOLD = 5 * 60 * 1000;

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
     * 检查 Access Token 是否即将过期，如果即将过期则通知客户端刷新
     */
    private Mono<Void> refreshTokenIfNeeded(String token, ServerHttpResponse response, String path) {
        try {
            // 1. 检查 Token 是否仍然有效
            if (!jwtUtil.validateToken(token)) {
                log.debug("Token 无效或已过期, path: {}", path);
                return Mono.empty();
            }

            // 2. 只处理 Access Token
            if (!jwtUtil.isAccessToken(token)) {
                log.debug("不是 Access Token，跳过刷新检查, path: {}", path);
                return Mono.empty();
            }

            // 3. 计算 Token 剩余有效期
            io.jsonwebtoken.Claims claims = jwtUtil.parseToken(token);
            long expirationTime = claims.getExpiration().getTime();
            long currentTime = System.currentTimeMillis();
            long timeLeft = expirationTime - currentTime;

            // 4. 如果剩余时间小于阈值（5分钟），通知客户端刷新
            if (timeLeft < TOKEN_EXPIRE_SOON_THRESHOLD && timeLeft > 0) {
                response.getHeaders().set(TOKEN_EXPIRED_SOON_HEADER, "true");
                response.getHeaders().set(TOKEN_EXPIRES_IN_HEADER, String.valueOf(timeLeft / 1000));
                log.info("Access Token 即将过期, path: {}, 剩余时间: {}秒, 已通知客户端刷新",
                        path, timeLeft / 1000);
            }

        } catch (Exception e) {
            log.error("Token 刷新检查失败, path: {}, error: {}", path, e.getMessage());
        }

        return Mono.empty();
    }
}
