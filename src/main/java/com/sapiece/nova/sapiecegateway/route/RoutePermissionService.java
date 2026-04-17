package com.sapiece.nova.sapiecegateway.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 路由权限验证服务
 * 从 sys_gateway_route 表读取权限配置，进行权限验证
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutePermissionService {

    private final SysGatewayRouteRepository routeRepository;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final AtomicReference<List<RoutePredicateHolder>> routeCache =
            new AtomicReference<>(Collections.emptyList());
    private final AtomicLong lastRouteCacheRefresh = new AtomicLong(0L);
    private static final Duration ROUTE_CACHE_TTL = Duration.ofSeconds(30);

    /**
     * 根据请求路径和方法匹配路由配置
     */
    public Mono<SysGatewayRoute> matchRoute(String requestPath, String method) {
        String normalizedPath = (requestPath == null || requestPath.isBlank()) ? "/" : requestPath;
        String normalizedMethod = method != null ? method.toUpperCase(java.util.Locale.ROOT) : null;

        return loadRouteCache()
                .flatMapMany(Flux::fromIterable)
                .filter(holder -> holder.matches(normalizedPath, normalizedMethod, pathMatcher))
                .sort((h1, h2) -> {
                    int scoreCompare = Integer.compare(
                            h2.bestMatchScore(normalizedPath),
                            h1.bestMatchScore(normalizedPath)
                    );
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return Integer.compare(h1.getOrder(), h2.getOrder());
                })
                .map(RoutePredicateHolder::getRoute)
                .next()
                .doOnNext(route -> log.debug("路由匹配成功: path={}, method={}, routeId={}",
                        normalizedPath, normalizedMethod, route.getRouteId()));
    }

    /**
     * 计算路径匹配精确度分数
     */
    private int calculateMatchScore(String pattern, String requestPath) {
        if (pattern == null) return 0;

        // 精确匹配
        if (pattern.equals(requestPath)) {
            return 100;
        }
        // 路径变量
        if (pattern.contains("{") && pattern.contains("}")) {
            return 80;
        }
        // 单层通配符
        if (pattern.contains("*") && !pattern.contains("**")) {
            return 60;
        }
        // 多层通配符
        if (pattern.contains("**")) {
            return 40;
        }
        return 20;
    }

    private Mono<List<RoutePredicateHolder>> loadRouteCache() {
        long now = System.currentTimeMillis();
        if (now - lastRouteCacheRefresh.get() < ROUTE_CACHE_TTL.toMillis()) {
            return Mono.just(routeCache.get());
        }

        return routeRepository.findAllEnabled()
                .map(this::buildPredicateHolder)
                .collectList()
                .doOnNext(list -> {
                    routeCache.set(list);
                    lastRouteCacheRefresh.set(now);
                    log.debug("刷新路由权限缓存, size={}", list.size());
                });
    }

    private RoutePredicateHolder buildPredicateHolder(SysGatewayRoute route) {
        List<String> paths = new ArrayList<>();
        Set<String> methods = new HashSet<>();

        if (route.getPredicates() != null && !route.getPredicates().isBlank()) {
            try {
                List<Map<String, Object>> predicateList = objectMapper.readValue(
                        route.getPredicates(), new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> predicate : predicateList) {
                    String name = String.valueOf(predicate.get("name"));
                    Object args = predicate.get("args");
                    Map<String, Object> argsMap = args instanceof Map ? (Map<String, Object>) args : Collections.emptyMap();
                    if ("Path".equalsIgnoreCase(name)) {
                        extractPredicateValues(argsMap).forEach(paths::add);
                    } else if ("Method".equalsIgnoreCase(name)) {
                        extractPredicateValues(argsMap).forEach(value -> methods.add(value.toUpperCase(Locale.ROOT)));
                    }
                }
            } catch (Exception e) {
                log.warn("解析路由断言失败: routeId={}, error={}", route.getRouteId(), e.getMessage());
            }
        }

        return new RoutePredicateHolder(route, paths, methods);
    }

    private List<String> extractPredicateValues(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return Collections.emptyList();
        }
        return args.values().stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    private class RoutePredicateHolder {
        private final SysGatewayRoute route;
        private final List<String> pathPatterns;
        private final Set<String> methods;

        RoutePredicateHolder(SysGatewayRoute route, List<String> pathPatterns, Set<String> methods) {
            this.route = route;
            this.pathPatterns = pathPatterns != null ? pathPatterns : Collections.emptyList();
            this.methods = methods != null ? methods : Collections.emptySet();
        }

        boolean matches(String requestPath, String httpMethod, AntPathMatcher pathMatcher) {
            boolean pathMatched = pathPatterns.isEmpty()
                    || pathPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
            boolean methodMatched = methods.isEmpty()
                    || (httpMethod != null && methods.contains(httpMethod));
            return pathMatched && methodMatched;
        }

        int bestMatchScore(String requestPath) {
            if (pathPatterns.isEmpty()) {
                return 0;
            }
            return pathPatterns.stream()
                    .mapToInt(pattern -> calculateMatchScore(pattern, requestPath))
                    .max()
                    .orElse(0);
        }

        int getOrder() {
            Integer order = route.getOrderNum();
            return order != null ? order : Integer.MAX_VALUE;
        }

        SysGatewayRoute getRoute() {
            return route;
        }
    }

    /**
     * 验证用户是否有权限访问指定路由
     *
     * @param authentication 用户认证信息
     * @param route          路由配置
     * @return 是否有权限
     */
    public Mono<Boolean> hasPermission(Authentication authentication, SysGatewayRoute route) {
        // 1. 检查路由是否需要认证
        if (!route.isRequireAuth()) {
            log.debug("路由不需要认证: routeId={}", route.getRouteId());
            return Mono.just(true);
        }

        // 2. 检查用户是否已认证
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("用户未认证");
            return Mono.just(false);
        }

        // 3. 检查是否有权限要求
        if (!route.hasPermissionRequirement()) {
            // 只需要登录，不需要特定权限
            log.debug("路由只需要登录，不需要特定权限: routeId={}", route.getRouteId());
            return Mono.just(true);
        }

        // 4. 检查是否是超级管理员
        if (isSuperAdmin(authentication)) {
            log.debug("用户是超级管理员，跳过权限检查: user={}", authentication.getName());
            return Mono.just(true);
        }

        // 5. 验证具体权限
        return checkPermissions(authentication, route);
    }

    /**
     * 检查用户是否是超级管理员
     */
    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> "ROLE_SUPER_ADMIN".equals(auth) || "ROLE_ADMIN".equals(auth));
    }

    /**
     * 检查用户是否拥有所需权限
     */
    private Mono<Boolean> checkPermissions(Authentication authentication, SysGatewayRoute route) {
        // 获取用户权限列表
        Set<String> userPermissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // 解析所需权限
        List<String> requiredPermissions = Arrays.stream(route.getPermissionCode().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (requiredPermissions.isEmpty()) {
            return Mono.just(true);
        }

        boolean hasPermission;
        if (route.isAndLogic()) {
            // AND 逻辑：必须拥有所有权限
            hasPermission = userPermissions.containsAll(requiredPermissions);
            log.debug("权限验证(AND): user={}, required={}, userHas={}, result={}",
                    authentication.getName(), requiredPermissions, userPermissions, hasPermission);
        } else {
            // OR 逻辑：拥有任一权限即可
            hasPermission = requiredPermissions.stream().anyMatch(userPermissions::contains);
            log.debug("权限验证(OR): user={}, required={}, userHas={}, result={}",
                    authentication.getName(), requiredPermissions, userPermissions, hasPermission);
        }

        return Mono.just(hasPermission);
    }

    /**
     * 综合验证：匹配路由 + 验证权限
     *
     * @param authentication 用户认证信息
     * @param requestPath    请求路径
     * @return 验证结果
     */
    public Mono<PermissionCheckResult> checkPermission(Authentication authentication, String requestPath, String method) {
        return matchRoute(requestPath, method)
                .flatMap(route -> {
                    // 检查路由是否禁用
                    if (!route.isEnabled()) {
                        log.warn("路由已禁用: routeId={}", route.getRouteId());
                        return Mono.just(PermissionCheckResult.routeDisabled(route));
                    }

                    // 验证权限
                    return hasPermission(authentication, route)
                            .map(hasPermission -> {
                                if (hasPermission) {
                                    return PermissionCheckResult.allowed(route);
                                } else {
                                    return PermissionCheckResult.denied(route, "权限不足");
                                }
                            });
                })
                .defaultIfEmpty(PermissionCheckResult.noRouteMatched());
    }

    /**
     * 权限检查结果
     */
    public static class PermissionCheckResult {
        private final boolean allowed;
        private final boolean routeMatched;
        private final SysGatewayRoute route;
        private final String message;

        private PermissionCheckResult(boolean allowed, boolean routeMatched, SysGatewayRoute route, String message) {
            this.allowed = allowed;
            this.routeMatched = routeMatched;
            this.route = route;
            this.message = message;
        }

        public static PermissionCheckResult allowed(SysGatewayRoute route) {
            return new PermissionCheckResult(true, true, route, "允许访问");
        }

        public static PermissionCheckResult denied(SysGatewayRoute route, String message) {
            return new PermissionCheckResult(false, true, route, message);
        }

        public static PermissionCheckResult routeDisabled(SysGatewayRoute route) {
            return new PermissionCheckResult(false, true, route, "路由已禁用");
        }

        public static PermissionCheckResult noRouteMatched() {
            return new PermissionCheckResult(true, false, null, "未匹配到路由配置，默认放行");
        }

        public boolean isAllowed() {
            return allowed;
        }

        public boolean isRouteMatched() {
            return routeMatched;
        }

        public SysGatewayRoute getRoute() {
            return route;
        }

        public String getMessage() {
            return message;
        }
    }
}
