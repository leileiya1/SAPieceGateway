package com.sapiece.nova.sapiecegateway.service;

import com.sapiece.nova.sapiecegateway.entity.SysMenu;
import reactor.core.publisher.Flux;

/**
 * 系统菜单权限Service接口
 * 提供菜单权限相关的业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-08
 */
public interface SysMenuService {

    /**
     * 根据用户ID查询该用户的所有菜单权限
     *
     * @param userId 用户ID
     * @return 菜单权限列表（响应式）
     */
    Flux<SysMenu> findMenusByUserId(Long userId);

    /**
     * 根据角色ID查询该角色的所有菜单权限
     *
     * @param roleId 角色ID
     * @return 菜单权限列表（响应式）
     */
    Flux<SysMenu> findMenusByRoleId(Long roleId);

    /**
     * 根据用户ID查询该用户的所有权限标识
     *
     * @param userId 用户ID
     * @return 权限标识列表（响应式）
     */
    Flux<String> findPermissionCodesByUserId(Long userId);
}
