package com.sapiece.nova.sapiecegateway.route.impl;

import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import com.sapiece.nova.sapiecegateway.route.DynamicRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
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

    private final SysGatewayRouteRepository routeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 刷新所有路由
     * 发布 RefreshRoutesEvent 事件，触发 Spring Cloud Gateway 重新加载路由
     */
    @Override
    public Mono<Void> refreshRoutes() {
        log.info("发布路由刷新事件 RefreshRoutesEvent...");
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        return Mono.empty();
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

        return routeRepository.existsByRouteId(route.getRouteId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("路由ID已存在: " + route.getRouteId()));
                    }
                    return routeRepository.save(route);
                })
                .doOnSuccess(saved -> {
                    log.info("新增路由成功: routeId={}, uri={}", saved.getRouteId(), saved.getUri());
                    // 异步刷新路由
                    refreshRoutes().subscribe();
                })
                .doOnError(error -> log.error("新增路由失败: routeId={}, error={}",
                        route.getRouteId(), error.getMessage()));
    }

    /**
     * 修改路由
     */
    @Override
    public Mono<SysGatewayRoute> updateRoute(Long id, SysGatewayRoute route) {
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("路由不存在: id=" + id)))
                .flatMap(existing -> {
                    // 保留原有的创建信息
                    route.setId(id);
                    route.setCreateTime(existing.getCreateTime());
                    route.setCreator(existing.getCreator());
                    route.setUpdateTime(LocalDateTime.now());

                    // 如果路由ID变更，需要检查新ID是否已存在
                    if (!existing.getRouteId().equals(route.getRouteId())) {
                        return routeRepository.existsByRouteId(route.getRouteId())
                                .flatMap(exists -> {
                                    if (exists) {
                                        return Mono.error(new IllegalArgumentException(
                                                "路由ID已存在: " + route.getRouteId()));
                                    }
                                    return routeRepository.save(route);
                                });
                    }

                    return routeRepository.save(route);
                })
                .doOnSuccess(updated -> {
                    log.info("更新路由成功: routeId={}, uri={}", updated.getRouteId(), updated.getUri());
                    refreshRoutes().subscribe();
                })
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
                        .doOnSuccess(v -> {
                            log.info("删除路由成功: routeId={}", existing.getRouteId());
                            refreshRoutes().subscribe();
                        }))
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
                        .doOnSuccess(count -> {
                            log.info("删除路由成功: routeId={}, 删除数量={}", routeId, count);
                            refreshRoutes().subscribe();
                        }))
                .then();
    }

    /**
     * 启用/禁用路由
     */
    @Override
    public Mono<SysGatewayRoute> updateStatus(Long id, Integer status) {
        return routeRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("路由不存在: id=" + id)))
                .flatMap(existing -> {
                    existing.setStatus(status);
                    existing.setUpdateTime(LocalDateTime.now());
                    return routeRepository.save(existing);
                })
                .doOnSuccess(updated -> {
                    String statusText = status == 1 ? "启用" : "禁用";
                    log.info("路由状态更新: routeId={}, status={}", updated.getRouteId(), statusText);
                    refreshRoutes().subscribe();
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
}
