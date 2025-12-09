package com.sapiece.nova.sapiecegateway.service;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Token黑名单Service接口
 * 用于管理被禁用的Token（如用户登出、强制下线等场景）
 *
 * @author SAPiece
 * @since 2025-11-08
 */
public interface TokenBlacklistService {

    /**
     * 将Token加入黑名单
     * 通常在用户登出时调用
     *
     * @param token    JWT Token
     * @param duration Token在黑名单中的有效期（通常设置为Token的剩余有效期）
     * @return 是否成功（响应式）
     */
    Mono<Boolean> addToBlacklist(String token, Duration duration);

    /**
     * 检查Token是否在黑名单中
     *
     * @param token JWT Token
     * @return 是否在黑名单中（响应式）
     */
    Mono<Boolean> isBlacklisted(String token);

    /**
     * 从黑名单中移除Token
     * 通常用于管理员解除限制
     *
     * @param token JWT Token
     * @return 是否成功（响应式）
     */
    Mono<Boolean> removeFromBlacklist(String token);

    /**
     * 根据用户ID将该用户的所有Token加入黑名单
     * 用于强制用户下线
     *
     * @param userId   用户ID
     * @param duration 黑名单有效期
     * @return 是否成功（响应式）
     */
    Mono<Boolean> addUserToBlacklist(Long userId, Duration duration);

    /**
     * 检查用户是否在黑名单中
     *
     * @param userId 用户ID
     * @return 是否在黑名单中（响应式）
     */
    Mono<Boolean> isUserBlacklisted(Long userId);

    /**
     * 清除过期的黑名单记录
     * 由于使用Redis的过期机制，通常不需要手动清除
     *
     * @return 清除的数量（响应式）
     */
    Mono<Long> clearExpiredTokens();
}
