package com.sapiece.nova.sapiecegateway.route;

import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 动态路由管理服务接口
 *
 * @author SAPiece
 * @since 2025-11-26
 */
public interface DynamicRouteService {

    /**
     * 刷新所有路由（发布 RefreshRoutesEvent）
     *
     * @return Mono<Void>
     */
    Mono<Void> refreshRoutes();

    /**
     * 新增路由
     *
     * @param route 路由配置
     * @return 保存后的路由
     */
    Mono<SysGatewayRoute> addRoute(SysGatewayRoute route);

    /**
     * 修改路由
     *
     * @param id    路由主键ID
     * @param route 路由配置
     * @return 更新后的路由
     */
    Mono<SysGatewayRoute> updateRoute(Long id, SysGatewayRoute route);

    /**
     * 删除路由
     *
     * @param id 路由主键ID
     * @return Mono<Void>
     */
    Mono<Void> deleteRoute(Long id);

    /**
     * 根据路由ID删除
     *
     * @param routeId 路由ID
     * @return Mono<Void>
     */
    Mono<Void> deleteByRouteId(String routeId);

    /**
     * 启用/禁用路由
     *
     * @param id     路由主键ID
     * @param status 状态：0-禁用，1-启用
     * @return 更新后的路由
     */
    Mono<SysGatewayRoute> updateStatus(Long id, Integer status);

    /**
     * 根据主键ID获取路由
     *
     * @param id 主键ID
     * @return 路由配置
     */
    Mono<SysGatewayRoute> getById(Long id);

    /**
     * 根据路由ID获取路由
     *
     * @param routeId 路由ID
     * @return 路由配置
     */
    Mono<SysGatewayRoute> getByRouteId(String routeId);

    /**
     * 获取所有路由
     *
     * @return 所有路由列表
     */
    Flux<SysGatewayRoute> listAllRoutes();

    /**
     * 获取启用的路由
     *
     * @return 启用的路由列表
     */
    Flux<SysGatewayRoute> listEnabledRoutes();

    /**
     * 根据关键字搜索路由
     *
     * @param keyword 关键字
     * @return 匹配的路由列表
     */
    Flux<SysGatewayRoute> searchByKeyword(String keyword);

    /**
     * 统计启用的路由数量
     *
     * @return 启用的路由数量
     */
    Mono<Long> countEnabled();
}
