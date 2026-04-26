package com.sapiece.nova.sapiecegateway.service;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 认证服务接口
 * 处理用户登录、登出、Token刷新等业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-09
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param userName  用户名
     * @param password  密码
     * @param clientIp  客户端真实IP
     * @param userAgent User-Agent
     */
    Mono<Map<String, Object>> login(String userName, String password, String clientIp, String userAgent);

    /**
     * 用户登出，将Token加入黑名单
     *
     * @param token     JWT Token (含Bearer前缀)
     * @param clientIp  客户端IP（用于审计日志）
     * @param userAgent User-Agent（用于审计日志）
     */
    Mono<Boolean> logout(String token, String clientIp, String userAgent);

    /**
     * 刷新Token（旧方法，保留兼容性）
     *
     * @param token 旧Token
     * @return 新Token
     */
    Mono<String> refreshToken(String token);

    /**
     * 使用Refresh Token刷新Access Token（双Token模式）
     *
     * @param refreshToken Refresh Token
     * @return 新的Token对（accessToken + refreshToken）
     */
    Mono<Map<String, Object>> refreshAccessToken(String refreshToken);

    /**
     * 获取Token信息
     *
     * @param token JWT Token
     * @return Token中的用户信息
     */
    Mono<Map<String, Object>> getTokenInfo(String token);
}
