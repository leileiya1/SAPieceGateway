package com.sapiece.nova.sapiecegateway.route;

import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
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
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 根据请求路径匹配路由配置
     *
     * @param requestPath 请求路径
     * @return 匹配的路由配置
     */
    public Mono<SysGatewayRoute> matchRoute(String requestPath) {
        return routeRepository.findAllEnabled()
                .filter(route -> {
                    // 从 predicates 中提取 Path 配置
                    String pathPattern = extractPathPattern(route.getPredicates());
                    if (pathPattern == null) {
                        return false;
                    }
                    return pathMatcher.match(pathPattern, requestPath);
                })
                .sort((a, b) -> {
                    // 按匹配精确度排序
                    String patternA = extractPathPattern(a.getPredicates());
                    String patternB = extractPathPattern(b.getPredicates());
                    return Integer.compare(
                            calculateMatchScore(patternB, requestPath),
                            calculateMatchScore(patternA, requestPath)
                    );
                })
                .next()
                .doOnNext(route -> log.debug("路由匹配成功: path={}, routeId={}", requestPath, route.getRouteId()));
    }

    /**
     * 从 predicates JSON 中提取 Path 配置
     *
     * @param predicates predicates JSON 字符串
     * @return 路径模式
     */
    private String extractPathPattern(String predicates) {
        if (predicates == null || predicates.isBlank()) {
            return null;
        }

        // 简单解析，查找 Path 断言的 pattern
        // 格式: [{"name": "Path", "args": {"pattern": "/api/user/**"}}]
        try {
            if (predicates.contains("\"name\"") && predicates.contains("Path")) {
                int patternIndex = predicates.indexOf("\"pattern\"");
                if (patternIndex > 0) {
                    int start = predicates.indexOf("\"", patternIndex + 10) + 1;
                    int end = predicates.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        return predicates.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析 predicates 失败: {}", e.getMessage());
        }

        return null;
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
    public Mono<PermissionCheckResult> checkPermission(Authentication authentication, String requestPath) {
        return matchRoute(requestPath)
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
