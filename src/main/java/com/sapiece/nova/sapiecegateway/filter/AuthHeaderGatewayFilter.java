package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.security.CustomUserDetails;
import com.sapiece.nova.sapiecegateway.util.GatewaySignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 认证信息透传 + 下游信任签名过滤器
 *
 * 功能：
 * 1. 剥离客户端伪造的 X-User-* / X-Gateway-* 请求头（防注入）
 * 2. JWT 验证通过后，注入真实用户信息 header（下游 Option A 模式可直接使用）
 * 3. 注入 HMAC-SHA256 签名 header（下游验证请求来自网关，防绕过）
 *
 * 下游 Option A（网关受信）：读 X-User-Id / X-User-Name / X-User-Roles
 * 下游 Option B（零信任）：验 Authorization Bearer + X-Gateway-Signature
 *
 * HMAC Header 格式：
 *   X-Gateway-Timestamp : 毫秒时间戳
 *   X-Gateway-Signature : HMAC-SHA256("timestamp={ts}&userId={uid}&path={path}")
 *
 * 下游服务只需共享 GATEWAY_DOWNSTREAM_SECRET 环境变量即可验证签名。
 *
 * @author SAPiece
 * @since 2026-04-26
 */
@Slf4j
@Component
public class AuthHeaderGatewayFilter implements GlobalFilter, Ordered {

    private static final int ORDER = -50;

    @Value("${gateway.downstream-sign.enabled:true}")
    private boolean signEnabled;

    @Value("${gateway.downstream-sign.secret:SAPiece-Gateway-Downstream-HMAC-Secret-2026}")
    private String signSecret;

    // 需要从客户端请求中剥离的 Header，防止伪造
    private static final List<String> PROTECTED_HEADERS = List.of(
            "X-User-Id",
            "X-User-Name",
            "X-User-Roles",
            "X-User-Permissions",
            "X-Gateway-Timestamp",
            "X-Gateway-Signature"
    );

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Step 1: 剥离客户端伪造的 Header
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> PROTECTED_HEADERS.forEach(headers::remove))
                .build();

        // Step 2: 读取 Spring Security 认证结果，注入用户信息和签名
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> isAuthenticatedUser(auth))
                .map(auth -> {
                    CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
                    return buildAuthenticatedRequest(stripped, user, path);
                })
                .defaultIfEmpty(buildUnauthenticatedRequest(stripped, path))
                .flatMap(req -> chain.filter(exchange.mutate().request(req).build()));
    }

    private boolean isAuthenticatedUser(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CustomUserDetails;
    }

    /** 已认证请求：注入用户信息 + HMAC 签名 */
    private ServerHttpRequest buildAuthenticatedRequest(ServerHttpRequest request,
                                                         CustomUserDetails user, String path) {
        String userId = String.valueOf(user.getUserId());
        String roles = user.getRoles() != null ? String.join(",", user.getRoles()) : "";
        String permissions = user.getPermissions() != null ? String.join(",", user.getPermissions()) : "";

        log.debug("注入认证 header, userId={}, roles={}, path={}", userId, roles, path);

        ServerHttpRequest.Builder builder = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Name", user.getUsername())
                .header("X-User-Roles", roles)
                .header("X-User-Permissions", permissions);

        if (signEnabled) {
            long ts = System.currentTimeMillis();
            String sig = GatewaySignatureUtil.sign(signSecret, ts, userId, path);
            builder.header("X-Gateway-Timestamp", String.valueOf(ts))
                   .header("X-Gateway-Signature", sig);
            log.debug("注入 HMAC 签名, ts={}, userId={}", ts, userId);
        }

        return builder.build();
    }

    /** 未认证请求（公开接口）：仅注入签名（userId=0），不注入用户信息 */
    private ServerHttpRequest buildUnauthenticatedRequest(ServerHttpRequest request, String path) {
        if (!signEnabled) return request;

        long ts = System.currentTimeMillis();
        String sig = GatewaySignatureUtil.sign(signSecret, ts, "0", path);
        return request.mutate()
                .header("X-Gateway-Timestamp", String.valueOf(ts))
                .header("X-Gateway-Signature", sig)
                .build();
    }
}
