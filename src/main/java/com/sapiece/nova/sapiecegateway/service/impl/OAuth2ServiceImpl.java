package com.sapiece.nova.sapiecegateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.entity.SysOAuthConfig;
import com.sapiece.nova.sapiecegateway.entity.SysOAuthUser;
import com.sapiece.nova.sapiecegateway.repository.SysOAuthConfigRepository;
import com.sapiece.nova.sapiecegateway.repository.SysOAuthUserRepository;
import com.sapiece.nova.sapiecegateway.repository.SysUserRepository;
import com.sapiece.nova.sapiecegateway.service.AuditLogService;
import com.sapiece.nova.sapiecegateway.service.OAuth2Service;
import com.sapiece.nova.sapiecegateway.service.SysRoleService;
import com.sapiece.nova.sapiecegateway.service.SysMenuService;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * OAuth2登录Service实现类
 * 实现第三方登录相关功能
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ServiceImpl implements OAuth2Service {

    private final SysOAuthConfigRepository oauthConfigRepository;
    private final SysOAuthUserRepository oauthUserRepository;
    private final SysUserRepository userRepository;
    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final JwtUtil jwtUtil;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    // ==================== 授权流程方法 ====================

    /**
     * 获取OAuth授权URL
     */
    @Override
    public Mono<String> getAuthorizationUrl(String provider, String state) {
        log.info("获取OAuth授权URL, provider: {}, state: {}", provider, state);

        return getConfigByProvider(provider)
                .map(config -> {
                    String authUrl = buildAuthorizationUrl(config, state);
                    log.info("生成OAuth授权URL成功, provider: {}", provider);
                    return authUrl;
                })
                .switchIfEmpty(Mono.error(new RuntimeException("OAuth配置不存在或未启用: " + provider)));
    }

    /**
     * 构建授权URL
     */
    private String buildAuthorizationUrl(SysOAuthConfig config, String state) {
        StringBuilder url = new StringBuilder(config.getAuthorizationUri());
        url.append("?client_id=").append(config.getClientId());
        url.append("&redirect_uri=").append(URLEncoder.encode(config.getRedirectUri(), StandardCharsets.UTF_8));
        url.append("&response_type=code");
        url.append("&state=").append(state);

        if (config.getScopes() != null && !config.getScopes().isEmpty()) {
            url.append("&scope=").append(URLEncoder.encode(config.getScopes(), StandardCharsets.UTF_8));
        }

        return url.toString();
    }

    /**
     * 处理OAuth回调，获取用户信息
     */
    @Override
    public Mono<SysOAuthUser> handleCallback(String provider, String code, String state) {
        log.info("处理OAuth回调, provider: {}, code长度: {}", provider, code != null ? code.length() : 0);

        return getConfigByProvider(provider)
                .flatMap(config -> {
                    // 1. 用授权码换取AccessToken
                    return getAccessToken(config, code)
                            .flatMap(tokenResponse -> {
                                String accessToken = (String) tokenResponse.get("access_token");
                                String refreshToken = (String) tokenResponse.get("refresh_token");

                                log.info("获取AccessToken成功, provider: {}", provider);

                                // 2. 用AccessToken获取用户信息
                                return getUserInfo(config, accessToken)
                                        .flatMap(userInfo -> {
                                            log.info("获取用户信息成功, provider: {}", provider);

                                            // 3. 解析用户信息并保存/更新
                                            return saveOrUpdateOAuthUser(config, userInfo, accessToken, refreshToken);
                                        });
                            });
                })
                .doOnSuccess(oauthUser -> log.info("OAuth回调处理完成, provider: {}, oauthId: {}",
                        provider, oauthUser.getOauthId()))
                .doOnError(error -> log.error("OAuth回调处理失败, provider: {}, error: {}",
                        provider, error.getMessage()));
    }

    /**
     * 获取AccessToken
     */
    private Mono<Map<String, Object>> getAccessToken(SysOAuthConfig config, String code) {
        log.debug("获取AccessToken, provider: {}", config.getProvider());

        WebClient webClient = webClientBuilder.build();

        // 构建请求参数
        String body = "client_id=" + config.getClientId() +
                "&client_secret=" + config.getClientSecret() +
                "&code=" + code +
                "&redirect_uri=" + URLEncoder.encode(config.getRedirectUri(), StandardCharsets.UTF_8) +
                "&grant_type=authorization_code";

        return webClient.post()
                .uri(config.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Accept", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        Map<String, Object> result = objectMapper.readValue(response, new TypeReference<>() {});
                        return Mono.just(result);
                    } catch (Exception e) {
                        log.error("解析AccessToken响应失败: {}", e.getMessage());
                        return Mono.error(new RuntimeException("解析AccessToken失败"));
                    }
                });
    }

    /**
     * 获取用户信息
     */
    private Mono<Map<String, Object>> getUserInfo(SysOAuthConfig config, String accessToken) {
        log.debug("获取用户信息, provider: {}", config.getProvider());

        WebClient webClient = webClientBuilder.build();

        return webClient.get()
                .uri(config.getUserInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        Map<String, Object> result = objectMapper.readValue(response, new TypeReference<>() {});
                        return Mono.just(result);
                    } catch (Exception e) {
                        log.error("解析用户信息响应失败: {}", e.getMessage());
                        return Mono.error(new RuntimeException("解析用户信息失败"));
                    }
                });
    }

    /**
     * 保存或更新OAuth用户信息
     */
    private Mono<SysOAuthUser> saveOrUpdateOAuthUser(SysOAuthConfig config, Map<String, Object> userInfo,
                                                      String accessToken, String refreshToken) {
        String provider = config.getProvider();
        String oauthId = extractOAuthId(provider, userInfo);

        log.debug("保存/更新OAuth用户, provider: {}, oauthId: {}", provider, oauthId);

        return oauthUserRepository.findByProviderAndOauthId(provider, oauthId)
                .flatMap(existing -> {
                    // 更新已存在的OAuth用户
                    updateOAuthUserInfo(existing, provider, userInfo, accessToken, refreshToken);
                    existing.setLastLoginTime(LocalDateTime.now());
                    existing.setUpdateTime(LocalDateTime.now());
                    return oauthUserRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 创建新的OAuth用户
                    SysOAuthUser newUser = new SysOAuthUser();
                    newUser.setProvider(provider);
                    newUser.setOauthId(oauthId);
                    updateOAuthUserInfo(newUser, provider, userInfo, accessToken, refreshToken);
                    newUser.setCreateTime(LocalDateTime.now());
                    newUser.setLastLoginTime(LocalDateTime.now());
                    return oauthUserRepository.save(newUser);
                }));
    }

    /**
     * 从用户信息中提取OAuth ID
     */
    private String extractOAuthId(String provider, Map<String, Object> userInfo) {
        return switch (provider.toUpperCase()) {
            case "GITHUB" -> String.valueOf(userInfo.get("id"));
            case "GOOGLE" -> String.valueOf(userInfo.get("sub"));
            case "GITEE" -> String.valueOf(userInfo.get("id"));
            case "WECHAT" -> String.valueOf(userInfo.get("openid"));
            default -> String.valueOf(userInfo.get("id"));
        };
    }

    /**
     * 更新OAuth用户信息
     */
    private void updateOAuthUserInfo(SysOAuthUser oauthUser, String provider,
                                      Map<String, Object> userInfo,
                                      String accessToken, String refreshToken) {
        // 根据不同提供商解析用户信息
        switch (provider.toUpperCase()) {
            case "GITHUB" -> {
                oauthUser.setOauthName((String) userInfo.get("login"));
                oauthUser.setOauthNickname((String) userInfo.get("name"));
                oauthUser.setOauthAvatar((String) userInfo.get("avatar_url"));
                oauthUser.setOauthEmail((String) userInfo.get("email"));
            }
            case "GOOGLE" -> {
                oauthUser.setOauthName((String) userInfo.get("email"));
                oauthUser.setOauthNickname((String) userInfo.get("name"));
                oauthUser.setOauthAvatar((String) userInfo.get("picture"));
                oauthUser.setOauthEmail((String) userInfo.get("email"));
            }
            case "GITEE" -> {
                oauthUser.setOauthName((String) userInfo.get("login"));
                oauthUser.setOauthNickname((String) userInfo.get("name"));
                oauthUser.setOauthAvatar((String) userInfo.get("avatar_url"));
                oauthUser.setOauthEmail((String) userInfo.get("email"));
            }
            case "WECHAT" -> {
                oauthUser.setOauthName((String) userInfo.get("openid"));
                oauthUser.setOauthNickname((String) userInfo.get("nickname"));
                oauthUser.setOauthAvatar((String) userInfo.get("headimgurl"));
            }
            default -> {
                oauthUser.setOauthName(String.valueOf(userInfo.get("name")));
                oauthUser.setOauthNickname(String.valueOf(userInfo.get("nickname")));
            }
        }

        // 保存Token
        oauthUser.setAccessToken(accessToken);
        oauthUser.setRefreshToken(refreshToken);

        // 保存原始用户信息
        try {
            oauthUser.setRawUserInfo(objectMapper.writeValueAsString(userInfo));
        } catch (Exception e) {
            log.warn("序列化原始用户信息失败: {}", e.getMessage());
        }
    }

    /**
     * OAuth登录
     */
    @Override
    public Mono<Map<String, Object>> oauthLogin(String provider, String code, String state,
                                                 String clientIp, String userAgent) {
        log.info("OAuth登录, provider: {}", provider);

        return handleCallback(provider, code, state)
                .flatMap(oauthUser -> {
                    if (oauthUser.isBound()) {
                        // 已绑定系统用户，直接登录
                        return loginWithOAuthUser(oauthUser, clientIp, userAgent);
                    } else {
                        // 未绑定，返回OAuth用户信息供绑定
                        Map<String, Object> result = new HashMap<>();
                        result.put("needBind", true);
                        result.put("oauthUserId", oauthUser.getId());
                        result.put("provider", oauthUser.getProvider());
                        result.put("oauthName", oauthUser.getOauthName());
                        result.put("oauthNickname", oauthUser.getOauthNickname());
                        result.put("oauthAvatar", oauthUser.getOauthAvatar());
                        result.put("oauthEmail", oauthUser.getOauthEmail());

                        log.info("OAuth用户未绑定系统账号, provider: {}, oauthId: {}",
                                provider, oauthUser.getOauthId());

                        // 记录审计日志
                        auditLogService.recordOAuthLogin(null, oauthUser.getOauthName(),
                                provider, oauthUser.getOauthId(),
                                clientIp, userAgent, true, "OAuth登录成功，需要绑定账号")
                                .subscribe();

                        return Mono.just(result);
                    }
                })
                .onErrorResume(error -> {
                    log.error("OAuth登录失败, provider: {}, error: {}", provider, error.getMessage());

                    // 记录失败的审计日志
                    auditLogService.recordOAuthLogin(null, null, provider, null,
                            clientIp, userAgent, false, error.getMessage())
                            .subscribe();

                    return Mono.error(error);
                });
    }

    /**
     * 使用OAuth用户信息登录（已绑定系统用户）
     */
    private Mono<Map<String, Object>> loginWithOAuthUser(SysOAuthUser oauthUser,
                                                          String clientIp, String userAgent) {
        log.info("使用OAuth用户登录, userId: {}, provider: {}", oauthUser.getUserId(), oauthUser.getProvider());

        return userRepository.findById(oauthUser.getUserId())
                .flatMap(user -> {
                    // 检查用户状态
                    if (user.getStatus() != 1) {
                        return Mono.error(new RuntimeException("用户已被禁用"));
                    }
                    if (user.getDelFlag() != 0) {
                        return Mono.error(new RuntimeException("用户已被删除"));
                    }

                    // 获取角色和权限
                    return roleService.findRoleCodesByUserId(user.getId()).collectList()
                            .flatMap(roles -> menuService.findPermissionCodesByUserId(user.getId()).collectList()
                                    .map(permissions -> {
                                        // 生成JWT Token
                                        String token = jwtUtil.generateToken(user.getId(), user.getUserName());
                                        // 构建返回结果
                                        Map<String, Object> result = new HashMap<>();
                                        result.put("needBind", false);
                                        result.put("token", token);
                                        result.put("userId", user.getId());
                                        result.put("userName", user.getUserName());
                                        result.put("nickName", user.getNickName());
                                        result.put("avatar", user.getAvatar());
                                        result.put("roles", roles);
                                        result.put("permissions", permissions);
                                        result.put("oauthProvider", oauthUser.getProvider());
                                        log.info("OAuth登录成功, userId: {}, userName: {}, provider: {}",
                                                user.getId(), user.getUserName(), oauthUser.getProvider());
                                        // 记录审计日志
                                        auditLogService.recordOAuthLogin(user.getId(), user.getUserName(),
                                                oauthUser.getProvider(), oauthUser.getOauthId(),
                                                clientIp, userAgent, true, "OAuth登录成功")
                                                .subscribe();
                                        return result;
                                    }));
                })
                .switchIfEmpty(Mono.error(new RuntimeException("绑定的系统用户不存在")));
    }

    // ==================== 绑定管理方法 ====================

    /**
     * 绑定OAuth账号到现有系统用户
     */
    @Override
    public Mono<SysOAuthUser> bindOAuthAccount(Long userId, Long oauthUserId) {
        log.info("绑定OAuth账号, userId: {}, oauthUserId: {}", userId, oauthUserId);
        return oauthUserRepository.findById(oauthUserId)
                .flatMap(oauthUser -> {
                    if (oauthUser.isBound()) {
                        return Mono.error(new RuntimeException("该OAuth账号已绑定其他用户"));
                    }
                    // 检查用户是否已绑定同一提供商的账号
                    return oauthUserRepository.findByUserIdAndProvider(userId, oauthUser.getProvider())
                            .hasElement()
                            .flatMap(exists -> {
                                if (exists) {
                                    return Mono.error(new RuntimeException("您已绑定过该平台的账号"));
                                }
                                // 执行绑定
                                oauthUser.setUserId(userId);
                                oauthUser.setBindTime(LocalDateTime.now());
                                oauthUser.setUpdateTime(LocalDateTime.now());
                                return oauthUserRepository.save(oauthUser)
                                        .doOnSuccess(saved -> log.info("OAuth账号绑定成功, userId: {}, provider: {}",
                                                userId, saved.getProvider()));
                            });
                })
                .switchIfEmpty(Mono.error(new RuntimeException("OAuth账号信息不存在")));
    }

    /**
     * 解绑OAuth账号
     */
    @Override
    public Mono<Boolean> unbindOAuthAccount(Long userId, String provider) {
        log.info("解绑OAuth账号, userId: {}, provider: {}", userId, provider);
        return oauthUserRepository.deleteByUserIdAndProvider(userId, provider)
                .map(count -> {
                    boolean success = count > 0;
                    if (success) {
                        log.info("OAuth账号解绑成功, userId: {}, provider: {}", userId, provider);
                    } else {
                        log.warn("OAuth账号解绑失败（未找到绑定关系）, userId: {}, provider: {}", userId, provider);
                    }
                    return success;
                });
    }

    /**
     * 获取用户已绑定的OAuth账号列表
     */
    @Override
    public Flux<SysOAuthUser> getBoundAccounts(Long userId) {
        log.debug("获取用户绑定的OAuth账号, userId: {}", userId);
        return oauthUserRepository.findByUserId(userId);
    }

    // ==================== 配置管理方法 ====================

    /**
     * 获取所有启用的OAuth配置
     */
    @Override
    public Flux<SysOAuthConfig> getEnabledConfigs() {
        log.debug("获取所有启用的OAuth配置");
        return oauthConfigRepository.findAllEnabled();
    }

    /**
     * 根据提供商获取OAuth配置
     */
    @Override
    public Mono<SysOAuthConfig> getConfigByProvider(String provider) {
        log.debug("获取OAuth配置, provider: {}", provider);
        return oauthConfigRepository.findByProviderAndStatus(provider.toUpperCase(), SysOAuthConfig.Status.ENABLED);
    }
}
