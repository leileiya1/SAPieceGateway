package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 网关动态路由配置 Repository
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Repository
public interface SysGatewayRouteRepository extends R2dbcRepository<SysGatewayRoute, Long> {

    /**
     * 根据路由ID查询
     *
     * @param routeId 路由ID
     * @return 路由配置
     */
    Mono<SysGatewayRoute> findByRouteId(String routeId);

    /**
     * 查询所有启用的路由（按顺序排序）
     *
     * @return 启用的路由列表
     */
    @Query("SELECT * FROM sys_gateway_route WHERE status = 1 ORDER BY order_num ASC")
    Flux<SysGatewayRoute> findAllEnabled();

    /**
     * 查询所有路由（按顺序排序）
     *
     * @return 所有路由列表
     */
    @Query("SELECT * FROM sys_gateway_route ORDER BY order_num ASC")
    Flux<SysGatewayRoute> findAllOrderByOrderNum();

    /**
     * 根据路由ID删除
     *
     * @param routeId 路由ID
     * @return 删除的记录数
     */
    Mono<Long> deleteByRouteId(String routeId);

    /**
     * 检查路由ID是否存在
     *
     * @param routeId 路由ID
     * @return 是否存在
     */
    Mono<Boolean> existsByRouteId(String routeId);

    /**
     * 统计启用的路由数量
     *
     * @return 启用的路由数量
     */
    @Query("SELECT COUNT(*) FROM sys_gateway_route WHERE status = 1")
    Mono<Long> countEnabled();

    /**
     * 根据URI模糊查询
     *
     * @param uri URI关键字
     * @return 匹配的路由列表
     */
    @Query("SELECT * FROM sys_gateway_route WHERE uri LIKE CONCAT('%', :uri, '%') ORDER BY order_num ASC")
    Flux<SysGatewayRoute> findByUriContaining(String uri);

    /**
     * 根据路由名称模糊查询
     *
     * @param keyword 关键字
     * @return 匹配的路由列表
     */
    @Query("SELECT * FROM sys_gateway_route WHERE route_name LIKE CONCAT('%', :keyword, '%') OR route_id LIKE CONCAT('%', :keyword, '%') ORDER BY order_num ASC")
    Flux<SysGatewayRoute> findByKeyword(String keyword);
}
