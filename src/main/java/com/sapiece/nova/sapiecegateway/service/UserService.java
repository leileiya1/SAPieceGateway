package com.sapiece.nova.sapiecegateway.service;

import reactor.core.publisher.Mono;

/**
 * 用户服务接口
 * 处理用户相关的业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-24
 */
public interface UserService {

    /**
     * 修改密码
     * 修改成功后会更新 password_last_changed_at 字段，使所有旧Token失效
     *
     * @param username    用户名
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    Mono<Boolean> changePassword(String username, String oldPassword, String newPassword);
}
