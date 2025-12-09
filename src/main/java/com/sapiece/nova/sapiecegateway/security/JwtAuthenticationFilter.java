package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.service.TokenBlacklistService;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.server.WebFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * JWT认证过滤器（WebFilter版本）
 * 用于解析请求中的JWT Token并进行身份验证
 * WebFilter对所有请求生效（包括本地Controller和路由请求）
 * 增加了Token黑名单检查功能
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final CustomReactiveUserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Token前缀
     */
    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 过滤器优先级
     * 需要在日志过滤器之后执行
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 【已禁用】JWT 认证逻辑已重构到 JwtSecurityContextRepository 中
        // 这是 Spring Security WebFlux 的标准做法，不再需要自定义 WebFilter
        // 直接放行，让 Spring Security 通过 JwtSecurityContextRepository 处理认证
        log.debug("JwtAuthenticationFilter 已禁用，认证逻辑由 JwtSecurityContextRepository 处理");
        return chain.filter(exchange);

        /* 原有认证逻辑已迁移到 JwtSecurityContextRepository
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        log.info("【JWT过滤器】开始执行, path: {}", path);

        // 从请求头中提取Token
        String token = extractToken(request);

        // 如果Token不存在，直接放行（让Spring Security处理）
        if (!StringUtils.hasText(token)) {
            log.warn("【JWT过滤器】请求中未包含JWT Token, path: {}", path);
            return chain.filter(exchange);
        }

        log.info("【JWT过滤器】提取到Token, path: {}", path);

        // 验证Token并设置认证信息
        return validateAndSetAuthentication(token, exchange, chain)
                .onErrorResume(e -> {
                    log.error("【JWT过滤器】JWT认证失败, path: {}, error: {}", path, e.getMessage(), e);
                    // 认证失败，清除认证信息，继续执行过滤器链
                    return chain.filter(exchange);
                });
        */
    }

    /**
     * 从请求头中提取JWT Token
     *
     * @param request 服务器HTTP请求
     * @return JWT Token
     */
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
            String token = bearerToken.substring(TOKEN_PREFIX.length());
            log.debug("提取到JWT Token");
            return token;
        }

        log.debug("请求头中未找到有效的JWT Token");
        return null;
    }

    /**
     * 验证Token并设置认证信息
     *
     * @param token    JWT Token
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    private Mono<Void> validateAndSetAuthentication(String token, ServerWebExchange exchange, WebFilterChain chain) {
        try {
            // 1. 验证Token是否有效
            if (!jwtUtil.validateToken(token)) {
                log.warn("JWT Token验证失败或已过期");
                return chain.filter(exchange);
            }

            // 2. 检查Token是否在黑名单中
            return tokenBlacklistService.isBlacklisted(token)
                    .flatMap(isBlacklisted -> {
                        if (isBlacklisted) {
                            log.warn("JWT Token已在黑名单中，拒绝访问");
                            return chain.filter(exchange);
                        }

                        // 3. 从Token中提取用户ID
                        Long userId = jwtUtil.getUserIdFromToken(token);
                        log.debug("从Token中解析到用户ID: {}", userId);

                        // 4. 检查用户是否在黑名单中
                        return tokenBlacklistService.isUserBlacklisted(userId)
                                .flatMap(isUserBlacklisted -> {
                                    if (isUserBlacklisted) {
                                        log.warn("用户已在黑名单中，拒绝访问, userId: {}", userId);
                                        return chain.filter(exchange);
                                    }

                                    // 5. 加载用户详情
                                    return userDetailsService.findByUserId(userId)
                            .flatMap(userDetails -> {
                                        // 6. 检查Token是否在密码修改之前签发
                                        // 将 UserDetails 转换为 CustomUserDetails 以获取密码修改时间
                                        if (userDetails instanceof CustomUserDetails customUserDetails) {
                                            if (jwtUtil.isTokenIssuedBeforePasswordChange(token,
                                                    customUserDetails.getPasswordLastChangedAt())) {
                                                log.warn("Token在密码修改前签发，已失效, userId: {}", userId);
                                                return chain.filter(exchange);
                                            }
                                        }

                                        log.info("【JWT过滤器】JWT认证成功, userId: {}, userName: {}", userId, userDetails.getUsername());

                                        // 创建认证对象
                                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                                userDetails,
                                                null,
                                                userDetails.getAuthorities()
                                        );

                                        // 将认证信息存储到ServerWebExchange的attributes中
                                        // 这样JwtSecurityContextRepository就可以读取它
                                        exchange.getAttributes().put("SPRING_SECURITY_AUTHENTICATION", authentication);
                                        log.info("【JWT过滤器】认证信息已存入attributes, userName: {}", userDetails.getUsername());

                                        // 继续执行过滤器链
                                        return chain.filter(exchange);
                                    })
                                    .switchIfEmpty(Mono.defer(() -> {
                                        log.warn("用户不存在或已被禁用, userId: {}", userId);
                                        return chain.filter(exchange);
                                    }));
                                });
                    });
        } catch (Exception e) {
            log.error("JWT认证异常, error: {}", e.getMessage());
            return chain.filter(exchange);
        }
    }

}
