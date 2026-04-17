package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.route.RoutePermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 基于路由配置的响应式权限管理器
 * 从 sys_gateway_route 表读取权限配置，进行细粒度的权限验证
 *
 * 工作流程：
 * 1. 获取请求路径
 * 2. 匹配路由配置
 * 3. 根据路由配置判断：
 *    - require_auth = 0：公开接口，直接放行
 *    - require_auth = 1：需要认证，检查用户是否已登录
 *    - permission_code 不为空：需要特定权限
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteReactiveAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    private final RoutePermissionService routePermissionService;

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        ServerHttpRequest request = context.getExchange().getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        log.debug("路由权限验证: path={}, method={}", path, method);
        // 先匹配路由，看是否需要认证
        return routePermissionService.matchRoute(path, method)
                .flatMap(route -> {
                    // 匹配到路由配置
                    if (!route.isEnabled()) {
                        // 路由已禁用
                        log.warn("路由已禁用: path={}, routeId={}", path, route.getRouteId());
                        return Mono.just(new AuthorizationDecision(false));
                    }
                    if (!route.isRequireAuth()) {
                        // 路由不需要认证，直接放行
                        log.debug("路由不需要认证，直接放行: path={}, routeId={}", path, route.getRouteId());
                        return Mono.just(new AuthorizationDecision(true));
                    }

                    // 需要认证，检查用户权限
                    return authentication
                            .filter(Authentication::isAuthenticated)
                            .flatMap(auth -> checkUserPermission(auth, route, path))
                            .defaultIfEmpty(new AuthorizationDecision(false))
                            .doOnNext(decision -> {
                                if (!decision.isGranted()) {
                                    log.debug("用户未认证或权限不足: path={}", path);
                                }
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 未匹配到路由配置
                    log.debug("未匹配到路由配置: path={}", path);
                    // 默认策略：需要认证
                    return authentication
                            .filter(Authentication::isAuthenticated)
                            .map(auth -> {
                                log.debug("未匹配路由但用户已认证，放行: path={}, user={}", path, auth.getName());
                                return new AuthorizationDecision(true);
                            })
                            .defaultIfEmpty(new AuthorizationDecision(false));
                }))
                .onErrorResume(e -> {
                    log.error("权限验证异常: path={}, error={}", path, e.getMessage(), e);
                    return Mono.just(new AuthorizationDecision(false));
                });
    }

    /**
     * 检查用户是否有权限访问该路由
     */
    private Mono<AuthorizationResult> checkUserPermission(
            Authentication authentication,
            SysGatewayRoute route,
            String path) {

        return routePermissionService.hasPermission(authentication, route)
                .map(hasPermission -> {
                    if (hasPermission) {
                        log.debug("权限验证通过: user={}, path={}, routeId={}",
                                authentication.getName(), path, route.getRouteId());
                    } else {
                        log.warn("权限验证失败: user={}, path={}, routeId={}, requiredPermission={}",
                                authentication.getName(), path, route.getRouteId(), route.getPermissionCode());
                    }
                    return new AuthorizationDecision(hasPermission);
                });
    }
}
