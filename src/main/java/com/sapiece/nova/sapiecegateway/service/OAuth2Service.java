package com.sapiece.nova.sapiecegateway.service;

import com.sapiece.nova.sapiecegateway.entity.SysOAuthConfig;
import com.sapiece.nova.sapiecegateway.entity.SysOAuthUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * OAuth2登录Service接口
 * 提供第三方登录相关功能
 *
 * @author SAPiece
 * @since 2025-12-07
 */
public interface OAuth2Service {

    // ==================== 授权流程方法 ====================

    /**
     * 获取OAuth授权URL
     *
     * @param provider 提供商（GITHUB、GOOGLE等）
     * @param state    状态参数（用于防CSRF攻击）
     * @return 授权URL
     */
    Mono<String> getAuthorizationUrl(String provider, String state);

    /**
     * 处理OAuth回调，获取用户信息
     *
     * @param provider 提供商
     * @param code     授权码
     * @param state    状态参数
     * @return OAuth用户信息
     */
    Mono<SysOAuthUser> handleCallback(String provider, String code, String state);

    /**
     * OAuth登录（如果已绑定系统用户则直接登录，否则返回OAuth用户信息供绑定）
     *
     * @param provider  提供商
     * @param code      授权码
     * @param state     状态参数
     * @param clientIp  客户端IP
     * @param userAgent 用户代理
     * @return 登录结果（包含Token或需要绑定的信息）
     */
    Mono<Map<String, Object>> oauthLogin(String provider, String code, String state,
                                          String clientIp, String userAgent);

    // ==================== 绑定管理方法 ====================

    /**
     * 绑定OAuth账号到现有系统用户
     *
     * @param userId      系统用户ID
     * @param oauthUserId OAuth用户表ID
     * @return 绑定后的OAuth用户
     */
    Mono<SysOAuthUser> bindOAuthAccount(Long userId, Long oauthUserId);

    /**
     * 解绑OAuth账号
     *
     * @param userId   系统用户ID
     * @param provider 提供商
     * @return 是否成功
     */
    Mono<Boolean> unbindOAuthAccount(Long userId, String provider);

    /**
     * 获取用户已绑定的OAuth账号列表
     *
     * @param userId 系统用户ID
     * @return OAuth账号列表
     */
    Flux<SysOAuthUser> getBoundAccounts(Long userId);

    // ==================== 配置管理方法 ====================

    /**
     * 获取所有启用的OAuth配置
     *
     * @return OAuth配置列表
     */
    Flux<SysOAuthConfig> getEnabledConfigs();

    /**
     * 根据提供商获取OAuth配置
     *
     * @param provider 提供商
     * @return OAuth配置
     */
    Mono<SysOAuthConfig> getConfigByProvider(String provider);
}
