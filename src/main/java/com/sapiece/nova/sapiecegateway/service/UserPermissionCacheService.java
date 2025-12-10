package com.sapiece.nova.sapiecegateway.service;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 用户权限缓存服务接口
 * 用于在Redis中缓存用户的角色和权限信息
 *
 * 设计说明：
 * - JWT只存储userId，不再存储roles和permissions
 * - 权限信息存储在Redis中，通过userId查询
 * - 优点：JWT体积小、权限变更实时生效、敏感信息不暴露
 *
 * @author SAPiece
 * @since 2025-12-10
 */
public interface UserPermissionCacheService {

    /**
     * 缓存用户权限信息
     * 在用户登录或刷新Token时调用
     *
     * @param userId      用户ID
     * @param roles       角色列表
     * @param permissions 权限列表
     * @return 是否成功
     */
    Mono<Boolean> cacheUserPermissions(Long userId, List<String> roles, List<String> permissions);

    /**
     * 获取用户角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    Mono<List<String>> getUserRoles(Long userId);

    /**
     * 获取用户权限列表
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    Mono<List<String>> getUserPermissions(Long userId);

    /**
     * 删除用户权限缓存
     * 在用户登出或权限变更时调用
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    Mono<Boolean> removeUserPermissions(Long userId);

    /**
     * 刷新用户权限缓存
     * 重新从数据库加载权限并更新缓存
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    Mono<Boolean> refreshUserPermissions(Long userId);

    /**
     * 检查用户权限缓存是否存在
     *
     * @param userId 用户ID
     * @return 是否存在
     */
    Mono<Boolean> hasUserPermissions(Long userId);
}
