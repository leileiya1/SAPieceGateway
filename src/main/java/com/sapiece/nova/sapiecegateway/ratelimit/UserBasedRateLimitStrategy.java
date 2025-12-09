package com.sapiece.nova.sapiecegateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于用户的限流策略
 * 根据用户角色设置不同的限流规则
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
public class UserBasedRateLimitStrategy implements RateLimitStrategy {

    @Value("${rate-limit.user.default-qps:50}")
    private Integer defaultQps;

    @Value("${rate-limit.user.default-capacity:100}")
    private Integer defaultCapacity;

    private static final String KEY_PREFIX = "rate_limit:user:";

    /**
     * 角色级别的限流配置
     * 实际生产环境中应该从数据库读取
     */
    private final Map<String, RoleLimitConfig> roleConfigs = new HashMap<>();

    public UserBasedRateLimitStrategy() {
        // 示例配置：不同角色的限流规则
        roleConfigs.put("ROLE_ADMIN", new RoleLimitConfig(1000, 2000));      // 管理员：高限额
        roleConfigs.put("ROLE_VIP", new RoleLimitConfig(500, 1000));         // VIP用户：中高限额
        roleConfigs.put("ROLE_USER", new RoleLimitConfig(100, 200));         // 普通用户：中等限额
        roleConfigs.put("ROLE_GUEST", new RoleLimitConfig(20, 40));          // 访客：低限额
    }

    @Override
    public String getRateLimitKey(ServerWebExchange exchange) {
        // 尝试从属性中获取用户名（由认证过滤器设置）
        String username = exchange.getAttribute("username");
        if (username != null) {
            return KEY_PREFIX + username;
        }
        // 如果没有用户信息，使用IP作为后备
        return KEY_PREFIX + "anonymous:" + getClientIp(exchange);
    }

    @Override
    public Mono<Integer> getQpsLimit(ServerWebExchange exchange) {
        return getUserRoles(exchange)
                .map(this::getQpsForRoles)
                .defaultIfEmpty(defaultQps);
    }

    @Override
    public Mono<Integer> getCapacity(ServerWebExchange exchange) {
        return getUserRoles(exchange)
                .map(this::getCapacityForRoles)
                .defaultIfEmpty(defaultCapacity);
    }

    @Override
    public String getStrategyName() {
        return "User-Based";
    }

    /**
     * 获取用户角色
     *
     * @param exchange ServerWebExchange
     * @return 用户角色集合
     */
    private Mono<Collection<? extends GrantedAuthority>> getUserRoles(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getAuthorities)
                .doOnNext(authorities -> log.debug("获取到用户角色: {}", authorities));
    }

    /**
     * 根据角色获取QPS限制
     * 如果用户有多个角色，返回最高的限制
     *
     * @param authorities 用户权限
     * @return QPS限制
     */
    private Integer getQpsForRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(roleConfigs::get)
                .filter(config -> config != null)
                .mapToInt(config -> config.qps)
                .max()
                .orElse(defaultQps);
    }

    /**
     * 根据角色获取令牌桶容量
     * 如果用户有多个角色，返回最高的容量
     *
     * @param authorities 用户权限
     * @return 令牌桶容量
     */
    private Integer getCapacityForRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .map(roleConfigs::get)
                .filter(config -> config != null)
                .mapToInt(config -> config.capacity)
                .max()
                .orElse(defaultCapacity);
    }

    /**
     * 获取客户端IP（后备方案）
     *
     * @param exchange ServerWebExchange
     * @return 客户端IP
     */
    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    /**
     * 角色限流配置内部类
     */
    private static class RoleLimitConfig {
        final int qps;
        final int capacity;

        RoleLimitConfig(int qps, int capacity) {
            this.qps = qps;
            this.capacity = capacity;
        }
    }
}
