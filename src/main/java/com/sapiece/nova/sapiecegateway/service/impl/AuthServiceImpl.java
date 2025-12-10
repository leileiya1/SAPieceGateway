package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.exception.BusinessException;
import com.sapiece.nova.sapiecegateway.service.*;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
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

    @Override
    public Mono<Map<String, Object>> login(String userName, String password) {
        log.info("用户登录业务处理, userName: {}", userName);

        // 1. 查询用户信息
        return userService.findActiveUserByUserName(userName)
                .flatMap(user -> {
                    // 2. 验证密码
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        log.warn("用户登录失败，密码错误, userName: {}", userName);
                        return Mono.error(new BusinessException(400, "用户名或密码错误"));
                    }

                    // 3. 查询用户角色
                    Mono<List<String>> rolesMono = roleService.findRoleCodesByUserId(user.getId())
                            .collectList();

                    // 4. 查询用户权限
                    Mono<List<String>> permissionsMono = menuService.findPermissionCodesByUserId(user.getId())
                            .collectList();

                    // 5. 生成双Token并缓存权限到Redis
                    return Mono.zip(rolesMono, permissionsMono)
                            .flatMap(tuple -> {
                                List<String> roles = tuple.getT1();
                                List<String> permissions = tuple.getT2();

                                // 生成精简版双Token（不再包含roles和permissions）
                                Map<String, String> tokenPair = jwtUtil.generateTokenPair(
                                        user.getId(),
                                        user.getUserName()
                                );

                                // 缓存权限到Redis
                                return userPermissionCacheService.cacheUserPermissions(user.getId(), roles, permissions)
                                        .then(userService.updateLoginInfo(user.getId(), "127.0.0.1"))
                                        .then(Mono.fromCallable(() -> {
                                            Map<String, Object> resultData = new HashMap<>();
                                            // 精简版响应：只返回Token和基本用户信息
                                            resultData.put("accessToken", tokenPair.get("accessToken"));
                                            resultData.put("refreshToken", tokenPair.get("refreshToken"));
                                            resultData.put("userId", user.getId());
                                            resultData.put("userName", user.getUserName());
                                            resultData.put("nickName", user.getNickName());

                                            log.info("用户登录成功（精简版双Token模式）, userId: {}, userName: {}",
                                                    user.getId(), user.getUserName());
                                            return resultData;
                                        }));
                            });
                })
                .switchIfEmpty(Mono.error(new BusinessException(400, "用户不存在或已被禁用")));
    }

    @Override
    public Mono<Boolean> logout(String token) {
        log.info("用户登出业务处理");

        // 移除Bearer前缀
        String actualToken = removeBearerPrefix(token);

        // 验证Token是否有效
        if (!jwtUtil.validateToken(actualToken)) {
            log.warn("登出失败，Token无效或已过期");
            return Mono.error(new BusinessException(400, "Token无效或已过期"));
        }

        // 计算Token的剩余有效期
        Claims claims = jwtUtil.parseToken(actualToken);
        Date expiration = claims.getExpiration();
        long remainingTime = expiration.getTime() - System.currentTimeMillis();

        if (remainingTime <= 0) {
            log.warn("登出失败，Token已过期");
            return Mono.error(new BusinessException(400, "Token已过期"));
        }

        // 将Token加入黑名单，有效期为Token的剩余有效期
        Duration duration = Duration.ofMillis(remainingTime);
        Long userId = jwtUtil.getUserIdFromToken(actualToken);

        // 同时清除用户的权限缓存
        return tokenBlacklistService.addToBlacklist(actualToken, duration)
                .flatMap(success -> {
                    if (success) {
                        // 清除权限缓存
                        return userPermissionCacheService.removeUserPermissions(userId)
                                .thenReturn(true);
                    }
                    return Mono.just(false);
                })
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("用户登出成功, userId: {}", userId);
                    } else {
                        log.error("Token加入黑名单失败");
                    }
                });
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

        // 移除Bearer前缀
        String actualToken = removeBearerPrefix(refreshToken);

        // 1. 验证Refresh Token是否有效
        if (!jwtUtil.validateToken(actualToken)) {
            log.warn("Refresh Token无效或已过期");
            return Mono.error(new BusinessException(401, "Refresh Token无效或已过期，请重新登录"));
        }

        // 2. 验证是否为Refresh Token类型
        if (!jwtUtil.isRefreshToken(actualToken)) {
            log.warn("提供的Token不是Refresh Token类型");
            return Mono.error(new BusinessException(400, "请提供有效的Refresh Token"));
        }

        // 3. 检查Refresh Token是否在黑名单中
        return tokenBlacklistService.isBlacklisted(actualToken)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("Refresh Token已在黑名单中");
                        return Mono.error(new BusinessException(401, "Refresh Token已失效，请重新登录"));
                    }

                    // 4. 从Refresh Token中获取用户信息
                    Long userId = jwtUtil.getUserIdFromToken(actualToken);
                    String userName = jwtUtil.getUserNameFromToken(actualToken);

                    // 5. 检查用户是否在黑名单中
                    return tokenBlacklistService.isUserBlacklisted(userId)
                            .flatMap(isUserBlacklisted -> {
                                if (isUserBlacklisted) {
                                    log.warn("用户已在黑名单中, userId: {}", userId);
                                    return Mono.error(new BusinessException(401, "用户已被禁用，请联系管理员"));
                                }

                                // 6. 查询用户最新的角色和权限并刷新缓存
                                Mono<List<String>> rolesMono = roleService.findRoleCodesByUserId(userId)
                                        .collectList();
                                Mono<List<String>> permissionsMono = menuService.findPermissionCodesByUserId(userId)
                                        .collectList();

                                // 7. 生成新的双Token并更新权限缓存
                                return Mono.zip(rolesMono, permissionsMono)
                                        .flatMap(tuple -> {
                                            List<String> roles = tuple.getT1();
                                            List<String> permissions = tuple.getT2();

                                            // 生成精简版双Token
                                            Map<String, String> tokenPair = jwtUtil.generateTokenPair(userId, userName);

                                            // 刷新权限缓存
                                            return userPermissionCacheService.cacheUserPermissions(userId, roles, permissions)
                                                    .thenReturn(tokenPair);
                                        })
                                        .map(tokenPair -> {
                                            Map<String, Object> resultData = new HashMap<>();
                                            resultData.put("accessToken", tokenPair.get("accessToken"));
                                            resultData.put("refreshToken", tokenPair.get("refreshToken"));

                                            log.info("Access Token刷新成功, userId: {}, userName: {}", userId, userName);
                                            return resultData;
                                        });
                            });
                });
    }

    @Override
    public Mono<Map<String, Object>> getTokenInfo(String token) {
        log.info("获取Token信息业务处理");

        // 移除Bearer前缀
        String actualToken = removeBearerPrefix(token);

        // 验证Token
        if (!jwtUtil.validateToken(actualToken)) {
            return Mono.error(new BusinessException(401, "Token无效或已过期"));
        }

        // 从Token中获取基本信息
        Long userId = jwtUtil.getUserIdFromToken(actualToken);
        String userName = jwtUtil.getUserNameFromToken(actualToken);

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
