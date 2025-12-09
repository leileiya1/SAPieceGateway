package com.sapiece.nova.sapiecegateway.service;

import com.sapiece.nova.sapiecegateway.entity.SysRole;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统角色Service接口
 * 提供角色相关的业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-08
 */
public interface SysRoleService {

    /**
     * 根据角色编码查询角色
     *
     * @param roleCode 角色编码
     * @return 角色信息（响应式）
     */
    Mono<SysRole> findByRoleCode(String roleCode);

    /**
     * 根据用户ID查询该用户的所有角色
     *
     * @param userId 用户ID
     * @return 角色列表（响应式）
     */
    Flux<SysRole> findRolesByUserId(Long userId);

    /**
     * 根据用户ID查询该用户的所有角色编码
     *
     * @param userId 用户ID
     * @return 角色编码列表（响应式）
     */
    Flux<String> findRoleCodesByUserId(Long userId);
}
