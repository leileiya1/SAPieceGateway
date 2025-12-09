package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysRoleMenu;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 角色菜单关联Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Repository
public interface SysRoleMenuRepository extends R2dbcRepository<SysRoleMenu, Long> {

    /**
     * 根据角色ID查询角色菜单关联
     *
     * @param roleId 角色ID
     * @return 角色菜单关联列表（响应式）
     */
    Flux<SysRoleMenu> findByRoleId(Long roleId);

    /**
     * 根据菜单ID查询角色菜单关联
     *
     * @param menuId 菜单ID
     * @return 角色菜单关联列表（响应式）
     */
    Flux<SysRoleMenu> findByMenuId(Long menuId);

    /**
     * 根据角色ID和菜单ID查询角色菜单关联
     *
     * @param roleId 角色ID
     * @param menuId 菜单ID
     * @return 角色菜单关联（响应式）
     */
    Mono<SysRoleMenu> findByRoleIdAndMenuId(Long roleId, Long menuId);

    /**
     * 根据角色ID删除角色菜单关联
     *
     * @param roleId 角色ID
     * @return 删除数量（响应式）
     */
    Mono<Long> deleteByRoleId(Long roleId);
}
