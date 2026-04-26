package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.service.TokenBlacklistService;
import com.sapiece.nova.sapiecegateway.service.UserPermissionCacheService;
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

import java.util.List;

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
    private final UserPermissionCacheService userPermissionCacheService;

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
     * 热路径（Redis命中）：全程不查DB
     * 冷路径（Redis未命中）：降级查DB
     */
    private Mono<SecurityContext> authenticateToken(String token, String path) {
        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("【JWT认证】Token验证失败或已过期, path: {}", path);
                return Mono.empty();
            }
            if (!jwtUtil.isAccessToken(token)) {
                log.warn("【JWT认证】提供的不是Access Token, path: {}", path);
                return Mono.empty();
            }

            Long userId = jwtUtil.getUserIdFromToken(token);
            String userName = jwtUtil.getUserNameFromToken(token);
            long tokenPwdVer = jwtUtil.getPwdVerFromToken(token);

            return tokenBlacklistService.isBlacklisted(token)
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            log.warn("【JWT认证】Token已在黑名单中, path: {}", path);
                            return Mono.<SecurityContext>empty();
                        }
                        return tokenBlacklistService.isUserBlacklisted(userId);
                    })
                    .flatMap(userBlacklisted -> {
                        if (Boolean.TRUE.equals(userBlacklisted)) {
                            log.warn("【JWT认证】用户已在黑名单中, userId: {}, path: {}", userId, path);
                            return Mono.<SecurityContext>empty();
                        }
                        // 从Redis取pwdVer，不查DB
                        return userPermissionCacheService.getPwdVer(userId)
                                .flatMap(redisPwdVer -> {
                                    if (tokenPwdVer < redisPwdVer) {
                                        log.warn("【JWT认证】Token在密码修改前签发已失效, userId: {}, tokenPwdVer: {}, redisPwdVer: {}",
                                                userId, tokenPwdVer, redisPwdVer);
                                        return Mono.<SecurityContext>empty();
                                    }
                                    // 从Redis取权限，不查DB
                                    return Mono.zip(
                                            userPermissionCacheService.getUserRoles(userId),
                                            userPermissionCacheService.getUserPermissions(userId)
                                    ).flatMap(tuple -> {
                                        List<String> roles = tuple.getT1();
                                        List<String> permissions = tuple.getT2();
                                        if (roles.isEmpty() && permissions.isEmpty()) {
                                            // 缓存未命中，降级查DB
                                            log.debug("【JWT认证】权限缓存未命中，降级查DB, userId: {}", userId);
                                            return buildContextFromDb(userId, token, path);
                                        }
                                        return buildContextFromCache(userId, userName, roles, permissions, path);
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

    /** 热路径：从缓存数据直接构建SecurityContext，不查DB */
    private Mono<SecurityContext> buildContextFromCache(Long userId, String userName,
                                                        List<String> roles, List<String> permissions,
                                                        String path) {
        CustomUserDetails userDetails = new CustomUserDetails();
        userDetails.setUserId(userId);
        userDetails.setUsername(userName);
        userDetails.setPassword("");
        userDetails.setEnabled(true);
        userDetails.setAccountNonExpired(true);
        userDetails.setAccountNonLocked(true);
        userDetails.setCredentialsNonExpired(true);
        userDetails.setRoles(roles);
        userDetails.setPermissions(permissions);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        log.debug("【JWT认证】从缓存构建SecurityContext成功, userId: {}, path: {}", userId, path);
        return Mono.just(new SecurityContextImpl(auth));
    }

    /** 冷路径：缓存未命中时降级查DB（含pwdVer校验） */
    private Mono<SecurityContext> buildContextFromDb(Long userId, String token, String path) {
        return userDetailsService.findByUserId(userId)
                .flatMap(userDetails -> {
                    if (userDetails instanceof CustomUserDetails cd) {
                        if (jwtUtil.isTokenIssuedBeforePasswordChange(token, cd.getPasswordLastChangedAt())) {
                            log.warn("【JWT认证】(DB降级)Token在密码修改前签发，已失效, userId: {}", userId);
                            return Mono.<SecurityContext>empty();
                        }
                    }
                    Authentication auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    log.debug("【JWT认证】从DB构建SecurityContext成功, userId: {}, path: {}", userId, path);
                    return Mono.just((SecurityContext) new SecurityContextImpl(auth));
                });
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
