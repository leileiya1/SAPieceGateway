package com.sapiece.nova.sapiecegateway.service;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 管理员服务接口
 * 处理IP黑白名单、Token黑名单、用户黑名单等管理功能
 *
 * @author SAPiece
 * @since 2025-11-09
 */
public interface AdminService {

    // ==================== IP黑名单管理 ====================

    /**
     * 获取IP黑名单列表
     *
     * @return IP黑名单
     */
    List<String> getIpBlacklist();

    /**
     * 添加IP到黑名单
     *
     * @param ip IP地址或CIDR
     */
    void addToIpBlacklist(String ip);

    /**
     * 从黑名单中移除IP
     *
     * @param ip IP地址或CIDR
     */
    void removeFromIpBlacklist(String ip);

    // ==================== IP白名单管理 ====================

    /**
     * 获取IP白名单列表
     *
     * @return IP白名单
     */
    List<String> getIpWhitelist();

    /**
     * 添加IP到白名单
     *
     * @param ip IP地址或CIDR
     */
    void addToIpWhitelist(String ip);

    /**
     * 从白名单中移除IP
     *
     * @param ip IP地址或CIDR
     */
    void removeFromIpWhitelist(String ip);

    // ==================== Token黑名单管理 ====================

    /**
     * 将Token加入黑名单
     *
     * @param token         JWT Token
     * @param durationHours 黑名单有效期（小时）
     * @return 是否成功
     */
    Mono<Boolean> addTokenToBlacklist(String token, Long durationHours);

    /**
     * 从黑名单中移除Token
     *
     * @param token JWT Token
     * @return 是否成功
     */
    Mono<Boolean> removeTokenFromBlacklist(String token);

    /**
     * 检查Token是否在黑名单中
     *
     * @param token JWT Token
     * @return 是否在黑名单中
     */
    Mono<Boolean> isTokenBlacklisted(String token);

    // ==================== 用户黑名单管理 ====================

    /**
     * 将用户加入黑名单（强制下线）
     *
     * @param userId        用户ID
     * @param durationHours 黑名单有效期（小时）
     * @return 是否成功
     */
    Mono<Boolean> addUserToBlacklist(Long userId, Long durationHours);

    /**
     * 检查用户是否在黑名单中
     *
     * @param userId 用户ID
     * @return 是否在黑名单中
     */
    Mono<Boolean> isUserBlacklisted(Long userId);
}
