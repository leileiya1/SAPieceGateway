package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysMenu;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统菜单权限Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Repository
public interface SysMenuRepository extends R2dbcRepository<SysMenu, Long> {

    /**
     * 根据用户ID查询该用户的所有菜单权限
     * 通过用户->角色->菜单的关联查询
     *
     * @param userId 用户ID
     * @return 菜单权限列表（响应式）
     */
    @Query("""
             SELECT DISTINCT m.*
             FROM sys_menu m
             INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
             INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id
             WHERE ur.user_id = :userId AND m.status = 1 AND m.del_flag = 0
            """)
    Flux<SysMenu> findMenusByUserId(Long userId);

    /**
     * 根据角色ID查询该角色的所有菜单权限
     *
     * @param roleId 角色ID
     * @return 菜单权限列表（响应式）
     */
    @Query("""
            SELECT m.*
            FROM sys_menu m
            INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
            WHERE rm.role_id = :roleId AND m.status = 1 AND m.del_flag = 0
            """)
    Flux<SysMenu> findMenusByRoleId(Long roleId);

    /**
     * 根据用户ID查询该用户的所有权限标识
     * 用于权限验证
     *
     * @param userId 用户ID
     * @return 权限标识列表（响应式）
     */
    @Query("""
                SELECT DISTINCT m.permission_code FROM sys_menu m\s
                INNER JOIN sys_role_menu rm ON m.id = rm.menu_id\s
                INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id\s
                WHERE ur.user_id = :userId AND m.status = 1 AND m.del_flag = 0\s
                AND m.permission_code IS NOT NULL AND m.permission_code != ''
            """)
    Flux<String> findPermissionCodesByUserId(Long userId);

    /**
     * 根据父菜单ID查询子菜单
     * 注意：避免查询JSON字段，使用CAST转换
     *
     * @param parentId 父菜单ID
     * @param status 状态
     * @param delFlag 删除标志
     * @return 子菜单列表（响应式）
     */
    @Query("""
            SELECT *
            FROM sys_menu
            WHERE parent_id = :parentId AND status = :status AND del_flag = :delFlag
            ORDER BY menu_sort
            """)
    Flux<SysMenu> findByParentIdAndStatusAndDelFlag(Long parentId, Integer status, Integer delFlag);

    /**
     * 根据ID查询菜单（用于路由配置）
     * 将JSON字段显式转换为CHAR，避免R2DBC的JSON类型转换问题
     *
     * @param id 菜单ID
     * @return 菜单信息（响应式）
     */
    @Query("SELECT * FROM sys_menu WHERE id = :id")
    Mono<SysMenu> findMenuById(Long id);

    /**
     * 查询所有路由配置（menu_type='R'）
     * 包含启用和禁用的路由，用于管理界面展示
     * 注意：需要将JSON字段显式转换为CHAR，避免R2DBC的JSON类型转换问题
     *
     * @return 路由配置列表（响应式）
     */
    @Query("SELECT * FROM sys_menu WHERE menu_type = 'R' AND del_flag = 0 ORDER BY menu_sort")
    Flux<SysMenu> findAllRouteConfigs();

    /**
     * 查询所有启用的路由配置
     * 用于网关路由加载
     * 注意：需要将JSON字段显式转换为CHAR，避免R2DBC的JSON类型转换问题
     *
     * @return 启用的路由配置列表（响应式）
     */
    @Query("""
            SELECT * FROM sys_menu
            WHERE menu_type = 'R' AND status = 1 AND del_flag = 0
            AND target_uri IS NOT NULL AND target_uri != ''
            AND route_predicates IS NOT NULL
            ORDER BY menu_sort
            """)
    Flux<SysMenu> findEnabledRouteConfigs();
}
