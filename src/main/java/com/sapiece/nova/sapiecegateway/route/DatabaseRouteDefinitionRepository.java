package com.sapiece.nova.sapiecegateway.route;

import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    /**
     * 获取所有启用的路由定义
     * Spring Cloud Gateway 会调用此方法加载路由配置
     *
     * @return 路由定义 Flux
     */
    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        log.debug("从数据库加载动态路由配置...");

        return routeRepository.findAllEnabled()
                .map(entity -> {
                    try {
                        RouteDefinition definition = converter.convert(entity);
                        log.debug("加载路由: id={}, uri={}", entity.getRouteId(), entity.getUri());
                        return definition;
                    } catch (Exception e) {
                        log.error("转换路由失败: routeId={}, error={}", entity.getRouteId(), e.getMessage());
                        return null;
                    }
                })
                .filter(definition -> definition != null)
                .doOnComplete(() -> log.info("动态路由加载完成"))
                .doOnError(error -> log.error("加载动态路由失败: {}", error.getMessage()));
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
