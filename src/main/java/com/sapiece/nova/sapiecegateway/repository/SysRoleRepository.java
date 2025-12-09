package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysRole;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统角色Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Repository
public interface SysRoleRepository extends R2dbcRepository<SysRole, Long> {

    /**
     * 根据角色编码查询角色
     *
     * @param roleCode 角色编码
     * @return 角色信息（响应式）
     */
    Mono<SysRole> findByRoleCode(String roleCode);

    /**
     * 根据用户ID查询该用户的所有角色
     * 通过用户角色关联表查询
     *
     * @param userId 用户ID
     * @return 角色列表（响应式）
     */
    @Query("""
                        SELECT r.* FROM sys_role r\s
                        INNER JOIN sys_user_role ur ON r.id = ur.role_id\s
                        WHERE ur.user_id = :userId AND r.status = 1 AND r.del_flag = 0
            """)
    Flux<SysRole> findRolesByUserId(Long userId);

    /**
     * 根据角色编码和状态查询角色
     *
     * @param roleCode 角色编码
     * @param status   状态（1-启用）
     * @return 角色信息（响应式）
     */
    Mono<SysRole> findByRoleCodeAndStatus(String roleCode, Integer status);
}
