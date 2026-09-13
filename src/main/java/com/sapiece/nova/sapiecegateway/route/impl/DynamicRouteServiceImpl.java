package com.sapiece.nova.sapiecegateway.route.impl;

import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import com.sapiece.nova.sapiecegateway.route.DatabaseRouteDefinitionRepository;
import com.sapiece.nova.sapiecegateway.route.DynamicRouteService;
import com.sapiece.nova.sapiecegateway.route.RouteDefinitionConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 动态路由管理服务实现
 * 负责路由的 CRUD 操作，并在变更后自动刷新 Gateway 路由
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRouteServiceImpl implements DynamicRouteService {

    public static final String ROUTE_CHANGE_CHANNEL = "gateway:route:changed";

    private final SysGatewayRouteRepository routeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final DatabaseRouteDefinitionRepository routeDefinitionRepository;
    private final RouteDefinitionConverter routeDefinitionConverter;

    /**
     * 刷新所有路由：发布 RefreshRoutesEvent + Redis 广播（通知所有实例清空权限缓存）
     */
    @Override
    public Mono<Void> refreshRoutes() {
        log.info("发布路由刷新事件 RefreshRoutesEvent...");
        // 立即失效本实例的路由定义缓存
        routeDefinitionRepository.invalidateCache();
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        // 广播给所有实例
        return reactiveRedisTemplate.convertAndSend(ROUTE_CHANGE_CHANNEL, "updated")
                .doOnSuccess(n -> log.debug("路由变更广播成功, subscribers: {}", n))
                .onErrorResume(e -> {
                    log.warn("路由变更广播失败（Redis不可用），仅本实例刷新: {}", e.getMessage());
                    return Mono.just(0L);
                })
                .then();
    }

    /**
     * 新增路由
     */
    @Override
    public Mono<SysGatewayRoute> addRoute(SysGatewayRoute route) {
        // 设置创建时间
        route.setCreateTime(LocalDateTime.now());
        route.setUpdateTime(LocalDateTime.now());

        // 默认启用
        if (route.getStatus() == null) {
            route.setStatus(1);
        }

        // 默认排序
        if (route.getOrderNum() == null) {
            route.setOrderNum(0);
        }

        validateRoute(route);
        routeDefinitionConverter.convert(route);
        return routeRepository.existsByRouteId(route.getRouteId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("路由ID已存在: " + route.getRouteId()));
                    }
                    return routeRepository.save(route);
                })
                .flatMap(saved -> refreshRoutes().thenReturn(saved))
                .doOnSuccess(saved -> log.info("新增路由成功: routeId={}, uri={}", saved.getRouteId(), saved.getUri()))
                .doOnError(error -> log.error("新增路由失败: routeId={}, error={}",
                        route.getRouteId(), error.getMessage()));
    }

    /**
     * 修改路由
     */
    @Override
    public Mono<SysGatewayRoute> updateRoute(Long id, SysGatewayRoute route) {
        validateRoutePatch(route);
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("路由不存在: id=" + id)))
                .flatMap(existing -> {
                    // Merge non-null fields from request into existing entity
                    if (route.getRouteId() != null)          existing.setRouteId(route.getRouteId());
                    if (route.getRouteName() != null)         existing.setRouteName(route.getRouteName());
                    if (route.getUri() != null)               existing.setUri(route.getUri());
                    if (route.getPredicates() != null)        existing.setPredicates(route.getPredicates());
                    if (route.getFilters() != null)           existing.setFilters(route.getFilters());
                    if (route.getMetadata() != null)          existing.setMetadata(route.getMetadata());
                    if (route.getOrderNum() != null)          existing.setOrderNum(route.getOrderNum());
                    if (route.getRequireAuth() != null)       existing.setRequireAuth(route.getRequireAuth());
                    if (route.getPermissionCode() != null)    existing.setPermissionCode(route.getPermissionCode());
                    if (route.getPermissionLogic() != null)   existing.setPermissionLogic(route.getPermissionLogic());
                    if (route.getRateLimitEnabled() != null)  existing.setRateLimitEnabled(route.getRateLimitEnabled());
                    if (route.getRateLimitQps() != null)      existing.setRateLimitQps(route.getRateLimitQps());
                    if (route.getRateLimitStrategy() != null) existing.setRateLimitStrategy(route.getRateLimitStrategy());
                    if (route.getCacheEnabled() != null)      existing.setCacheEnabled(route.getCacheEnabled());
                    if (route.getCacheTtl() != null)          existing.setCacheTtl(route.getCacheTtl());
                    if (route.getRetryEnabled() != null)      existing.setRetryEnabled(route.getRetryEnabled());
                    if (route.getRetryTimes() != null)        existing.setRetryTimes(route.getRetryTimes());
                    if (route.getTimeoutMs() != null)         existing.setTimeoutMs(route.getTimeoutMs());
                    if (route.getStatus() != null)            existing.setStatus(route.getStatus());
                    if (route.getDescription() != null)       existing.setDescription(route.getDescription());
                    existing.setUpdater(route.getUpdater());
                    existing.setUpdateTime(LocalDateTime.now());

                    routeDefinitionConverter.convert(existing);
                    return routeRepository.save(existing);
                })
                .flatMap(updated -> refreshRoutes().thenReturn(updated))
                .doOnSuccess(updated -> log.info("更新路由成功: routeId={}, uri={}", updated.getRouteId(), updated.getUri()))
                .doOnError(error -> log.error("更新路由失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 删除路由
     */
    @Override
    public Mono<Void> deleteRoute(Long id) {
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("路由不存在: id=" + id)))
                .flatMap(existing -> routeRepository.deleteById(id)
                        .then(refreshRoutes())
                        .doOnSuccess(v -> log.info("删除路由成功: routeId={}", existing.getRouteId())))
                .then();
    }

    /**
     * 根据路由ID删除
     */
    @Override
    public Mono<Void> deleteByRouteId(String routeId) {
        return routeRepository.findByRouteId(routeId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("路由不存在: routeId=" + routeId)))
                .flatMap(existing -> routeRepository.deleteByRouteId(routeId)
                        .flatMap(count -> refreshRoutes().thenReturn(count))
                        .doOnSuccess(count -> log.info("删除路由成功: routeId={}, 删除数量={}", routeId, count)))
                .then();
    }

    /**
     * 启用/禁用路由
     */
    @Override
    public Mono<SysGatewayRoute> updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            return Mono.error(new IllegalArgumentException("路由状态只能是0或1"));
        }
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("路由不存在: id=" + id)))
                .flatMap(existing -> {
                    existing.setStatus(status);
                    existing.setUpdateTime(LocalDateTime.now());
                    return routeRepository.save(existing);
                })
                .flatMap(updated -> refreshRoutes().thenReturn(updated))
                .doOnSuccess(updated -> {
                    String statusText = status == 1 ? "启用" : "禁用";
                    log.info("路由状态更新: routeId={}, status={}", updated.getRouteId(), statusText);
                })
                .doOnError(error -> log.error("更新路由状态失败: id={}, error={}", id, error.getMessage()));
    }

    /**
     * 根据主键ID获取路由
     */
    @Override
    public Mono<SysGatewayRoute> getById(Long id) {
        return routeRepository.findById(id);
    }

    /**
     * 根据路由ID获取路由
     */
    @Override
    public Mono<SysGatewayRoute> getByRouteId(String routeId) {
        return routeRepository.findByRouteId(routeId);
    }

    /**
     * 获取所有路由
     */
    @Override
    public Flux<SysGatewayRoute> listAllRoutes() {
        return routeRepository.findAllOrderByOrderNum();
    }

    /**
     * 获取启用的路由
     */
    @Override
    public Flux<SysGatewayRoute> listEnabledRoutes() {
        return routeRepository.findAllEnabled();
    }

    /**
     * 根据关键字搜索路由
     */
    @Override
    public Flux<SysGatewayRoute> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listAllRoutes();
        }
        return routeRepository.findByKeyword(keyword);
    }

    /**
     * 统计启用的路由数量
     */
    @Override
    public Mono<Long> countEnabled() {
        return routeRepository.countEnabled();
    }

    private static void validateRoute(SysGatewayRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("路由配置不能为空");
        }
        if (route.getRouteId() == null || !route.getRouteId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("路由ID格式不正确");
        }
        validateUri(route.getUri());
        if (route.getPredicates() == null || route.getPredicates().isBlank()) {
            throw new IllegalArgumentException("路由断言不能为空");
        }
        validateLimits(route);
    }

    private static void validateRoutePatch(SysGatewayRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("路由配置不能为空");
        }
        if (route.getRouteId() != null && !route.getRouteId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("路由ID格式不正确");
        }
        if (route.getUri() != null) {
            validateUri(route.getUri());
        }
        if (route.getPredicates() != null && route.getPredicates().isBlank()) {
            throw new IllegalArgumentException("路由断言不能为空");
        }
        validateLimits(route);
    }

    private static void validateUri(String uri) {
        if (uri == null || !(uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("lb://"))) {
            throw new IllegalArgumentException("路由URI只允许http、https或lb协议");
        }
    }

    private static void validateLimits(SysGatewayRoute route) {
        if (route.getRateLimitQps() != null && route.getRateLimitQps() <= 0) {
            throw new IllegalArgumentException("限流QPS必须大于0");
        }
        if (route.getCacheTtl() != null && route.getCacheTtl() <= 0) {
            throw new IllegalArgumentException("缓存TTL必须大于0");
        }
        if (route.getRetryTimes() != null && (route.getRetryTimes() < 0 || route.getRetryTimes() > 10)) {
            throw new IllegalArgumentException("重试次数必须在0到10之间");
        }
        if (route.getTimeoutMs() != null && (route.getTimeoutMs() < 100 || route.getTimeoutMs() > 300_000)) {
            throw new IllegalArgumentException("超时时间必须在100到300000毫秒之间");
        }
    }
}
