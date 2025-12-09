package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysUser;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 系统用户Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Repository
public interface SysUserRepository extends R2dbcRepository<SysUser, Long> {

    /**
     * 根据用户名查询用户
     * 用于用户登录认证
     *
     * @param userName 用户名
     * @return 用户信息（响应式）
     */
    Mono<SysUser> findByUserName(String userName);

    /**
     * 根据用户名和状态查询用户
     * 确保查询的是启用状态的用户
     *
     * @param userName 用户名
     * @param status   状态（1-启用）
     * @return 用户信息（响应式）
     */
    Mono<SysUser> findByUserNameAndStatus(String userName, Integer status);

    /**
     * 根据用户名、状态和删除标志查询用户
     * 确保查询的是未删除的启用状态用户
     *
     * @param userName 用户名
     * @param status   状态（1-启用）
     * @param delFlag  删除标志（0-正常）
     * @return 用户信息（响应式）
     */
    @Query("SELECT * FROM sys_user WHERE user_name = :userName AND status = :status AND del_flag = :delFlag")
    Mono<SysUser> findActiveUser(String userName, Integer status, Integer delFlag);

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户信息（响应式）
     */
    Mono<SysUser> findByEmail(String email);

    /**
     * 根据手机号查询用户
     *
     * @param mobile 手机号
     * @return 用户信息（响应式）
     */
    Mono<SysUser> findByMobile(String mobile);
}
