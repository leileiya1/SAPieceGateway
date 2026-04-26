package com.sapiece.nova.sapiecegateway.route;

import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于数据库的路由定义仓库
 * 实现 Spring Cloud Gateway 的 RouteDefinitionRepository 接口
 * 从数据库加载动态路由配置
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseRouteDefinitionRepository implements RouteDefinitionRepository {

    private final SysGatewayRouteRepository routeRepository;
    private final RouteDefinitionConverter converter;

    /** 本地缓存：避免每次路由匹配都打DB */
    private final AtomicReference<Flux<RouteDefinition>> routeCache = new AtomicReference<>();
    private final AtomicLong cacheTimestamp = new AtomicLong(0);
    private static final long CACHE_TTL_MS = 30_000;

    /**
     * 获取所有启用的路由定义（带TTL本地缓存）
     */
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        long now = System.currentTimeMillis();
        Flux<RouteDefinition> cached = routeCache.get();
        if (cached != null && now - cacheTimestamp.get() < CACHE_TTL_MS) {
            log.debug("从本地缓存返回路由定义");
            return cached;
        }
        return loadAndCache();
    }

    private Flux<RouteDefinition> loadAndCache() {
        log.debug("从数据库加载动态路由配置...");
        Flux<RouteDefinition> fresh = routeRepository.findAllEnabled()
                .map(entity -> {
                    try {
                        RouteDefinition def = converter.convert(entity);
                        log.debug("加载路由: id={}, uri={}", entity.getRouteId(), entity.getUri());
                        return def;
                    } catch (Exception e) {
                        log.error("转换路由失败: routeId={}, error={}", entity.getRouteId(), e.getMessage());
                        return null;
                    }
                })
                .filter(def -> def != null)
                .doOnComplete(() -> log.info("动态路由加载完成"))
                .doOnError(err -> log.error("加载动态路由失败: {}", err.getMessage()))
                .cache(); // 让多个并发订阅共享同一次DB查询
        routeCache.set(fresh);
        cacheTimestamp.set(System.currentTimeMillis());
        return fresh;
    }

    /** 路由变更时由 DynamicRouteServiceImpl 调用，立即失效缓存 */
    public void invalidateCache() {
        routeCache.set(null);
        cacheTimestamp.set(0);
        log.info("路由定义缓存已失效");
    }

    /**
     * 保存路由定义
     * 注意：本实现通过 DynamicRouteService 管理路由，此方法仅作兼容
     *
     * @param route 路由定义 Mono
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(definition -> {
            log.info("RouteDefinitionRepository.save() 被调用: routeId={}", definition.getId());
            // 实际的保存逻辑通过 DynamicRouteService 实现
            // 这里只记录日志，不做实际操作
            return Mono.empty();
        });
    }

    /**
     * 删除路由定义
     * 注意：本实现通过 DynamicRouteService 管理路由，此方法仅作兼容
     *
     * @param routeId 路由ID Mono
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId.flatMap(id -> {
            log.info("RouteDefinitionRepository.delete() 被调用: routeId={}", id);
            // 实际的删除逻辑通过 DynamicRouteService 实现
            // 这里只记录日志，不做实际操作
            return Mono.empty();
        });
    }
}
