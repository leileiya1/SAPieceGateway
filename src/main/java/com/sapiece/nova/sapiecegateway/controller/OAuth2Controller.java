package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.entity.SysOAuthConfig;
import com.sapiece.nova.sapiecegateway.entity.SysOAuthUser;
import com.sapiece.nova.sapiecegateway.service.OAuth2Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth2第三方登录控制器
 * 提供第三方登录、绑定、解绑等接口
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Slf4j
@RestController
@RequestMapping("/auth/oauth2")
@RequiredArgsConstructor
@Tag(name = "OAuth2第三方登录", description = "第三方登录相关接口（GitHub、Google、Gitee等）")
public class OAuth2Controller {

    private final OAuth2Service oAuth2Service;

    /**
     * 获取所有启用的OAuth提供商列表
     * 前端用于显示可用的第三方登录按钮
     *
     * @return OAuth配置列表
     */
    @GetMapping("/providers")
    @Operation(summary = "获取OAuth提供商列表", description = "获取所有启用的第三方登录提供商")
    public Mono<Result<List<Map<String, String>>>> getProviders() {
        log.info("获取OAuth提供商列表");

        return oAuth2Service.getEnabledConfigs()
                .map(config -> {
                    Map<String, String> provider = new HashMap<>();
                    provider.put("provider", config.getProvider());
                    provider.put("name", config.getProviderName());
                    return provider;
                })
                .collectList()
                .map(providers -> {
                    log.info("查询到OAuth提供商数量: {}", providers.size());
                    return Result.success("获取成功", providers);
                })
                .onErrorResume(e -> {
                    log.error("获取OAuth提供商列表失败: {}", e.getMessage());
                    return Mono.just(Result.error("获取OAuth提供商列表失败"));
                });
    }

    /**
     * 获取OAuth授权URL
     * 前端重定向到此URL进行第三方授权
     *
     * @param provider 提供商（github、google、gitee等）
     * @return 授权URL
     */
    @GetMapping("/authorize/{provider}")
    @Operation(summary = "获取OAuth授权URL", description = "获取第三方登录授权URL，前端重定向到此URL")
    @Parameter(name = "provider", description = "OAuth提供商", required = true, example = "github")
    public Mono<Result<Map<String, String>>> getAuthorizationUrl(@PathVariable String provider) {
        log.info("获取OAuth授权URL, provider: {}", provider);

        // 生成随机state参数，用于防CSRF攻击
        String state = UUID.randomUUID().toString().replace("-", "");

        return oAuth2Service.getAuthorizationUrl(provider, state)
                .map(authUrl -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("authUrl", authUrl);
                    result.put("state", state);

                    log.info("OAuth授权URL生成成功, provider: {}", provider);
                    return Result.success("获取成功", result);
                })
                .onErrorResume(e -> {
                    log.error("获取OAuth授权URL失败, provider: {}, error: {}", provider, e.getMessage());
                    return Mono.just(Result.error("获取授权URL失败: " + e.getMessage()));
                });
    }

    /**
     * OAuth回调接口
     * 第三方平台授权后回调此接口
     *
     * @param provider 提供商
     * @param code     授权码
     * @param state    状态参数
     * @param request  请求对象
     * @return 登录结果
     */
    @GetMapping("/callback/{provider}")
    @Operation(summary = "OAuth回调", description = "第三方授权成功后的回调接口")
    public Mono<Result<Map<String, Object>>> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false) String state,
            ServerHttpRequest request) {

        log.info("OAuth回调, provider: {}, code长度: {}", provider, code != null ? code.length() : 0);

        // 获取客户端信息
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);

        return oAuth2Service.oauthLogin(provider, code, state, clientIp, userAgent)
                .map(result -> {
                    boolean needBind = (boolean) result.get("needBind");
                    if (needBind) {
                        log.info("OAuth登录成功，需要绑定账号, provider: {}", provider);
                        return Result.success("OAuth认证成功，请绑定账号", result);
                    } else {
                        log.info("OAuth登录成功, provider: {}, userName: {}", provider, result.get("userName"));
                        return Result.success("登录成功", result);
                    }
                })
                .onErrorResume(e -> {
                    log.error("OAuth回调处理失败, provider: {}, error: {}", provider, e.getMessage());
                    return Mono.just(Result.error("OAuth登录失败: " + e.getMessage()));
                });
    }

    /**
     * 绑定OAuth账号
     * 将OAuth账号绑定到当前登录用户
     *
     * @param request 绑定请求
     * @return 绑定结果
     */
    @PostMapping("/bind")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "绑定OAuth账号", description = "将OAuth账号绑定到当前登录的系统用户")
    public Mono<Result<SysOAuthUser>> bindAccount(@RequestBody BindOAuthRequest request) {
        log.info("绑定OAuth账号, oauthUserId: {}", request.getOauthUserId());

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    // 从认证信息中获取用户ID
                    // 这里假设CustomUserDetails实现了获取userId的方法
                    Object principal = auth.getPrincipal();
                    Long userId = extractUserId(principal);

                    if (userId == null) {
                        return Mono.just(Result.<SysOAuthUser>error("无法获取当前用户信息"));
                    }

                    return oAuth2Service.bindOAuthAccount(userId, request.getOauthUserId())
                            .map(oauthUser -> {
                                log.info("OAuth账号绑定成功, userId: {}, provider: {}",
                                        userId, oauthUser.getProvider());
                                return Result.success("绑定成功", oauthUser);
                            });
                })
                .onErrorResume(e -> {
                    log.error("绑定OAuth账号失败: {}", e.getMessage());
                    return Mono.just(Result.error("绑定失败: " + e.getMessage()));
                });
    }

    /**
     * 解绑OAuth账号
     *
     * @param provider 提供商
     * @return 操作结果
     */
    @DeleteMapping("/unbind/{provider}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "解绑OAuth账号", description = "解除当前用户与指定OAuth提供商的绑定")
    @Parameter(name = "provider", description = "OAuth提供商", required = true, example = "github")
    public Mono<Result<Void>> unbindAccount(@PathVariable String provider) {
        log.info("解绑OAuth账号, provider: {}", provider);

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    Long userId = extractUserId(auth.getPrincipal());

                    if (userId == null) {
                        return Mono.just(Result.<Void>error("无法获取当前用户信息"));
                    }

                    return oAuth2Service.unbindOAuthAccount(userId, provider)
                            .map(success -> {
                                if (success) {
                                    log.info("OAuth账号解绑成功, userId: {}, provider: {}", userId, provider);
                                    return Result.<Void>success("解绑成功", null);
                                } else {
                                    log.warn("OAuth账号解绑失败, userId: {}, provider: {}", userId, provider);
                                    return Result.<Void>error("未找到绑定关系");
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("解绑OAuth账号失败, provider: {}, error: {}", provider, e.getMessage());
                    return Mono.just(Result.error("解绑失败: " + e.getMessage()));
                });
    }

    /**
     * 获取当前用户已绑定的OAuth账号列表
     *
     * @return OAuth账号列表
     */
    @GetMapping("/bound-accounts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取已绑定账号", description = "获取当前用户已绑定的所有OAuth账号")
    public Mono<Result<List<Map<String, Object>>>> getBoundAccounts() {
        log.info("获取用户已绑定的OAuth账号");

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    Long userId = extractUserId(auth.getPrincipal());

                    if (userId == null) {
                        return Mono.just(Result.<List<Map<String, Object>>>error("无法获取当前用户信息"));
                    }

                    return oAuth2Service.getBoundAccounts(userId)
                            .map(oauthUser -> {
                                // 返回脱敏后的信息
                                Map<String, Object> info = new HashMap<>();
                                info.put("id", oauthUser.getId());
                                info.put("provider", oauthUser.getProvider());
                                info.put("oauthName", oauthUser.getOauthName());
                                info.put("oauthNickname", oauthUser.getOauthNickname());
                                info.put("oauthAvatar", oauthUser.getOauthAvatar());
                                info.put("bindTime", oauthUser.getBindTime());
                                info.put("lastLoginTime", oauthUser.getLastLoginTime());
                                return info;
                            })
                            .collectList()
                            .map(accounts -> {
                                log.info("查询到用户已绑定OAuth账号数量: {}", accounts.size());
                                return Result.success("获取成功", accounts);
                            });
                })
                .onErrorResume(e -> {
                    log.error("获取已绑定OAuth账号失败: {}", e.getMessage());
                    return Mono.just(Result.error("获取失败: " + e.getMessage()));
                });
    }

    // ==================== 私有方法 ====================

    /**
     * 从请求中提取客户端IP
     */
    private String extractClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");

        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int index = ip.indexOf(',');
            if (index > 0) {
                ip = ip.substring(0, index);
            }
            return ip.trim();
        }

        ip = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 从Principal中提取用户ID
     */
    private Long extractUserId(Object principal) {
        if (principal instanceof com.sapiece.nova.sapiecegateway.security.CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }

    // ==================== 请求DTO ====================

    /**
     * 绑定OAuth账号请求
     */
    @Data
    public static class BindOAuthRequest {
        /**
         * OAuth用户表ID
         */
        private Long oauthUserId;
    }
}
