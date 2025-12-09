package com.sapiece.nova.sapiecegateway.service;

import com.sapiece.nova.sapiecegateway.entity.SysUser;
import reactor.core.publisher.Mono;

/**
 * 系统用户Service接口
 * 提供用户相关的业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-08
 */
public interface SysUserService {

    /**
     * 根据用户名查询用户
     *
     * @param userName 用户名
     * @return 用户信息（响应式）
     */
    Mono<SysUser> findByUserName(String userName);

    /**
     * 根据用户名查询启用状态的用户
     *
     * @param userName 用户名
     * @return 用户信息（响应式）
     */
    Mono<SysUser> findActiveUserByUserName(String userName);

    /**
     * 根据用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户信息（响应式）
     */
    Mono<SysUser> findById(Long userId);

    /**
     * 更新用户最后登录信息
     *
     * @param userId   用户ID
     * @param loginIp  登录IP
     * @return 更新后的用户信息（响应式）
     */
    Mono<SysUser> updateLoginInfo(Long userId, String loginIp);

    /**
     * 验证用户密码
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配（响应式）
     */
    Mono<Boolean> verifyPassword(String rawPassword, String encodedPassword);
}
