package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.service.TokenBlacklistService;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT安全上下文仓储（重构版）
 *
 * Spring Security WebFlux 标准实现：
 * 在 load() 方法中完成所有 JWT 认证逻辑，包括：
 * 1. 从请求头提取 JWT Token
 * 2. 验证 Token 有效性
 * 3. 检查 Token 和用户黑名单
 * 4. 加载用户详情
 * 5. 创建并返回 SecurityContext
 *
 * 优势：
 * - 符合 Spring Security 设计模式
 * - 不需要额外的 WebFilter
 * - 避免过滤器顺序问题
 * - 避免重复执行
 *
 * @author SAPiece
 * @since 2025-11-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecurityContextRepository implements ServerSecurityContextRepository {

    private final JwtUtil jwtUtil;
    private final CustomReactiveUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 从请求中加载 SecurityContext
     * 这是 Spring Security 认证的入口，会在每次请求时被调用
     *
     * @param exchange ServerWebExchange
     * @return Mono<SecurityContext>
     */
    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        log.debug("【JWT认证】开始加载SecurityContext, path: {}", path);

        // 1. 从请求头提取 Token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            log.debug("【JWT认证】请求中未包含Token, path: {}", path);
            return Mono.empty();
        }

        // 2. 验证并创建 SecurityContext
        return authenticateToken(token, path)
                .doOnSuccess(context -> {
                    if (context != null) {
                        log.info("【JWT认证】认证成功, user: {}, path: {}",
                                context.getAuthentication().getName(), path);
                    }
                })
                .doOnError(e -> log.error("【JWT认证】认证失败, path: {}, error: {}",
                        path, e.getMessage()));
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
     * 验证 Token 并创建 SecurityContext
     */
    private Mono<SecurityContext> authenticateToken(String token, String path) {
        try {
            // 1. 验证 Token 是否有效
            if (!jwtUtil.validateToken(token)) {
                log.warn("【JWT认证】Token验证失败或已过期, path: {}", path);
                return Mono.empty();
            }

            // 2. 验证是否为 Access Token（双Token模式下，只有Access Token可用于API认证）
            if (!jwtUtil.isAccessToken(token)) {
                log.warn("【JWT认证】提供的不是Access Token, path: {}", path);
                return Mono.empty();
            }

            // 3. 检查 Token 是否在黑名单中
            return tokenBlacklistService.isBlacklisted(token)
                    .flatMap(isBlacklisted -> {
                        if (isBlacklisted) {
                            log.warn("【JWT认证】Token已在黑名单中, path: {}", path);
                            return Mono.empty();
                        }

                        // 4. 从 Token 中提取用户 ID
                        Long userId = jwtUtil.getUserIdFromToken(token);

                        // 5. 检查用户是否在黑名单中
                        return tokenBlacklistService.isUserBlacklisted(userId)
                                .flatMap(isUserBlacklisted -> {
                                    if (isUserBlacklisted) {
                                        log.warn("【JWT认证】用户已在黑名单中, userId: {}, path: {}", userId, path);
                                        return Mono.empty();
                                    }

                                    // 6. 加载用户详情
                                    return userDetailsService.findByUserId(userId)
                                            .flatMap(userDetails -> {
                                                // 7. 检查 Token 是否在密码修改之前签发
                                                if (userDetails instanceof CustomUserDetails customUserDetails) {
                                                    if (jwtUtil.isTokenIssuedBeforePasswordChange(token,
                                                            customUserDetails.getPasswordLastChangedAt())) {
                                                        log.warn("【JWT认证】Token在密码修改前签发，已失效, userId: {}", userId);
                                                        return Mono.empty();
                                                    }
                                                }

                                                // 8. 创建认证对象
                                                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                                        userDetails,
                                                        null,
                                                        userDetails.getAuthorities()
                                                );

                                                // 9. 创建并返回 SecurityContext
                                                SecurityContext context = new SecurityContextImpl(authentication);
                                                return Mono.just(context);
                                            });
                                });
                    })
                    .onErrorResume(e -> {
                        log.error("【JWT认证】认证过程异常, path: {}, error: {}", path, e.getMessage());
                        return Mono.empty();
                    });

        } catch (Exception e) {
            log.error("【JWT认证】Token解析异常, path: {}, error: {}", path, e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * 保存 SecurityContext
     * JWT 是无状态的，不需要保存 SecurityContext
     *
     * @param exchange ServerWebExchange
     * @param context SecurityContext
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        // JWT 无状态认证，不需要保存 SecurityContext
        return Mono.empty();
    }
}
