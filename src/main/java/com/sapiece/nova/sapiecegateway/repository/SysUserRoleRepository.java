package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysUserRole;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户角色关联Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Repository
public interface SysUserRoleRepository extends R2dbcRepository<SysUserRole, Long> {

    /**
     * 根据用户ID查询用户角色关联
     *
     * @param userId 用户ID
     * @return 用户角色关联列表（响应式）
     */
    Flux<SysUserRole> findByUserId(Long userId);

    /**
     * 根据角色ID查询用户角色关联
     *
     * @param roleId 角色ID
     * @return 用户角色关联列表（响应式）
     */
    Flux<SysUserRole> findByRoleId(Long roleId);

    /**
     * 根据用户ID和角色ID查询用户角色关联
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 用户角色关联（响应式）
     */
    Mono<SysUserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 根据用户ID删除用户角色关联
     *
     * @param userId 用户ID
     * @return 删除数量（响应式）
     */
    Mono<Long> deleteByUserId(Long userId);
}
