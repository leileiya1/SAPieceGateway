package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.exception.BusinessException;
import com.sapiece.nova.sapiecegateway.service.*;
import com.sapiece.nova.sapiecegateway.entity.SysAuditLog;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务实现类
 * 处理用户登录、登出、Token刷新等业务逻辑
 *
 * 精简版：
 * - JWT只存储userId和userName
 * - 权限信息存储在Redis缓存中
 * - 登录响应不再返回roles和permissions
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserPermissionCacheService userPermissionCacheService;
    private final AuditLogService auditLogService;

    @Override
    public Mono<Map<String, Object>> login(String userName, String password, String clientIp, String userAgent) {
        log.info("用户登录业务处理, userName: {}, ip: {}", userName, clientIp);

        return userService.findActiveUserByUserName(userName)
                .flatMap(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        log.warn("用户登录失败，密码错误, userName: {}", userName);
                        // 记录登录失败审计日志（异步，不阻塞响应）
                        auditLogService.recordLogin(null, userName, clientIp, userAgent, false, "密码错误")
                                .subscribe();
                        return Mono.error(new BusinessException(400, "用户名或密码错误"));
                    }

                    Mono<List<String>> rolesMono = roleService.findRoleCodesByUserId(user.getId()).collectList();
                    Mono<List<String>> permissionsMono = menuService.findPermissionCodesByUserId(user.getId()).collectList();

                    return Mono.zip(rolesMono, permissionsMono)
                            .flatMap(tuple -> {
                                List<String> roles = tuple.getT1();
                                List<String> permissions = tuple.getT2();

                                // 计算pwdVer（密码版本号）
                                LocalDateTime pwdChangedAt = user.getPasswordLastChangedAt();
                                long pwdVer = pwdChangedAt != null
                                        ? pwdChangedAt.toEpochSecond(ZoneOffset.UTC) : 0L;

                                Map<String, String> tokenPair = jwtUtil.generateTokenPair(
                                        user.getId(), user.getUserName(), pwdVer);

                                return userPermissionCacheService.cacheUserPermissions(user.getId(), roles, permissions)
                                        .then(userPermissionCacheService.cachePwdVer(user.getId(), pwdVer))
                                        .then(userService.updateLoginInfo(user.getId(), clientIp))
                                        .then(auditLogService.recordLogin(
                                                user.getId(), userName, clientIp, userAgent, true, "登录成功"))
                                        .thenReturn(buildLoginResult(tokenPair, user.getId(), user.getUserName(), user.getNickName()));
                            });
                })
                .switchIfEmpty(Mono.error(new BusinessException(400, "用户不存在或已被禁用")));
    }

    private Map<String, Object> buildLoginResult(Map<String, String> tokenPair, Long userId, String userName, String nickName) {
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", tokenPair.get("accessToken"));
        result.put("refreshToken", tokenPair.get("refreshToken"));
        result.put("userId", userId);
        result.put("userName", userName);
        result.put("nickName", nickName);
        log.info("用户登录成功, userId: {}, userName: {}", userId, userName);
        return result;
    }

    @Override
    public Mono<Boolean> logout(String token, String clientIp, String userAgent) {
        log.info("用户登出业务处理, ip: {}", clientIp);

        String actualToken = removeBearerPrefix(token);

        if (!jwtUtil.validateToken(actualToken)) {
            log.warn("登出失败，Token无效或已过期");
            return Mono.error(new BusinessException(400, "Token无效或已过期"));
        }

        Claims claims = jwtUtil.parseToken(actualToken);
        Date expiration = claims.getExpiration();
        long remainingTime = expiration.getTime() - System.currentTimeMillis();

        if (remainingTime <= 0) {
            return Mono.error(new BusinessException(400, "Token已过期"));
        }

        Duration duration = Duration.ofMillis(remainingTime);
        Long userId = jwtUtil.getUserIdFromToken(actualToken);
        String userName = jwtUtil.getUserNameFromToken(actualToken);

        return tokenBlacklistService.addToBlacklist(actualToken, duration)
                .flatMap(success -> userPermissionCacheService.removeUserPermissions(userId)
                        .onErrorResume(e -> {
                            log.warn("清除权限缓存失败，忽略, userId: {}", userId);
                            return Mono.just(false);
                        })
                        .thenReturn(true))
                .flatMap(ok -> auditLogService.recordLogout(userId, userName, clientIp, userAgent)
                        .onErrorResume(e -> Mono.empty())
                        .thenReturn(true))
                .doOnSuccess(s -> log.info("用户登出成功, userId: {}", userId));
    }

    @Override
    public Mono<String> refreshToken(String token) {
        log.info("Token刷新业务处理");

        // 移除Bearer前缀
        String actualToken = removeBearerPrefix(token);

        // 刷新Token
        try {
            String newToken = jwtUtil.refreshToken(actualToken);
            log.info("Token刷新成功");
            return Mono.just(newToken);
        } catch (Exception e) {
            log.error("Token刷新失败, error: {}", e.getMessage());
            return Mono.error(new BusinessException(400, "Token刷新失败：" + e.getMessage()));
        }
    }

    @Override
    public Mono<Map<String, Object>> refreshAccessToken(String refreshToken) {
        log.info("使用Refresh Token刷新Access Token");

        String actualToken = removeBearerPrefix(refreshToken);

        if (!jwtUtil.validateToken(actualToken)) {
            return Mono.error(new BusinessException(401, "Refresh Token无效或已过期，请重新登录"));
        }
        if (!jwtUtil.isRefreshToken(actualToken)) {
            return Mono.error(new BusinessException(400, "请提供有效的Refresh Token"));
        }

        Long userId = jwtUtil.getUserIdFromToken(actualToken);
        String userName = jwtUtil.getUserNameFromToken(actualToken);

        // 计算旧Refresh Token剩余TTL，用于撤销
        Claims oldClaims = jwtUtil.parseToken(actualToken);
        long oldTtlMs = oldClaims.getExpiration().getTime() - System.currentTimeMillis();

        return tokenBlacklistService.isBlacklisted(actualToken)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        return Mono.error(new BusinessException(401, "Refresh Token已失效，请重新登录"));
                    }
                    return tokenBlacklistService.isUserBlacklisted(userId);
                })
                .flatMap(isUserBlacklisted -> {
                    if (Boolean.TRUE.equals(isUserBlacklisted)) {
                        return Mono.error(new BusinessException(401, "用户已被禁用，请联系管理员"));
                    }

                    Mono<List<String>> rolesMono = roleService.findRoleCodesByUserId(userId).collectList();
                    Mono<List<String>> permsMono = menuService.findPermissionCodesByUserId(userId).collectList();

                    return Mono.zip(rolesMono, permsMono)
                            .flatMap(tuple -> {
                                List<String> roles = tuple.getT1();
                                List<String> permissions = tuple.getT2();

                                // 从Redis取pwdVer（不查DB）
                                return userPermissionCacheService.getPwdVer(userId)
                                        .flatMap(pwdVer -> {
                                            Map<String, String> tokenPair =
                                                    jwtUtil.generateTokenPair(userId, userName, pwdVer);

                                            // 撤销旧Refresh Token + 刷新权限缓存
                                            Mono<Boolean> revokeOld = oldTtlMs > 0
                                                    ? tokenBlacklistService.addToBlacklist(actualToken,
                                                    Duration.ofMillis(oldTtlMs))
                                                    : Mono.just(true);

                                            return revokeOld
                                                    .then(userPermissionCacheService.cacheUserPermissions(
                                                            userId, roles, permissions))
                                                    .thenReturn(tokenPair);
                                        });
                            })
                            .map(tokenPair -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("accessToken", tokenPair.get("accessToken"));
                                result.put("refreshToken", tokenPair.get("refreshToken"));
                                log.info("Access Token刷新成功, userId: {}", userId);
                                return result;
                            });
                });
    }

    @Override
    public Mono<Map<String, Object>> getTokenInfo(String token) {
        log.info("获取Token信息业务处理");

        String actualToken = removeBearerPrefix(token);

        if (!jwtUtil.validateToken(actualToken)) {
            return Mono.error(new BusinessException(401, "Token无效或已过期"));
        }

        Long userId = jwtUtil.getUserIdFromToken(actualToken);
        String userName = jwtUtil.getUserNameFromToken(actualToken);

        // 检查Token黑名单（登出后不允许查信息）
        return tokenBlacklistService.isBlacklisted(actualToken)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        return Mono.error(new BusinessException(401, "Token已失效，请重新登录"));
                    }
                    return loadTokenInfoData(userId, userName);
                });
    }

    private Mono<Map<String, Object>> loadTokenInfoData(Long userId, String userName) {

        // 从Redis缓存获取权限信息
        Mono<List<String>> rolesMono = userPermissionCacheService.getUserRoles(userId);
        Mono<List<String>> permissionsMono = userPermissionCacheService.getUserPermissions(userId);

        return Mono.zip(rolesMono, permissionsMono)
                .flatMap(tuple -> {
                    List<String> roles = tuple.getT1();
                    List<String> permissions = tuple.getT2();

                    // 如果缓存不存在，从数据库重新加载
                    if (roles.isEmpty() && permissions.isEmpty()) {
                        log.info("权限缓存不存在，从数据库重新加载, userId: {}", userId);
                        return userPermissionCacheService.refreshUserPermissions(userId)
                                .then(Mono.zip(
                                        userPermissionCacheService.getUserRoles(userId),
                                        userPermissionCacheService.getUserPermissions(userId)
                                ));
                    }
                    return Mono.just(tuple);
                })
                .map(tuple -> {
                    Map<String, Object> tokenInfo = new HashMap<>();
                    tokenInfo.put("userId", userId);
                    tokenInfo.put("userName", userName);
                    tokenInfo.put("roles", tuple.getT1());
                    tokenInfo.put("permissions", tuple.getT2());

                    log.info("获取Token信息成功, userId: {}", userId);
                    return tokenInfo;
                });
    }

    /**
     * 移除Bearer前缀
     *
     * @param token 原始token
     * @return 处理后的token
     */
    private String removeBearerPrefix(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
