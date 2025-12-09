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

                    // 5. 生成Token
                    return Mono.zip(rolesMono, permissionsMono)
                            .flatMap(tuple -> {
                                List<String> roles = tuple.getT1();
                                List<String> permissions = tuple.getT2();

                                // 生成JWT Token
                                String token = jwtUtil.generateToken(
                                        user.getId(),
                                        user.getUserName(),
                                        roles,
                                        permissions
                                );

                                // 更新用户登录信息
                                return userService.updateLoginInfo(user.getId(), "127.0.0.1")
                                        .then(Mono.fromCallable(() -> {
                                            Map<String, Object> resultData = new HashMap<>();
                                            resultData.put("token", token);
                                            resultData.put("userId", user.getId());
                                            resultData.put("userName", user.getUserName());
                                            resultData.put("nickName", user.getNickName());
                                            resultData.put("roles", roles);
                                            resultData.put("permissions", permissions);

                                            log.info("用户登录成功, userId: {}, userName: {}",
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

        return tokenBlacklistService.addToBlacklist(actualToken, duration)
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
    public Mono<Map<String, Object>> getTokenInfo(String token) {
        log.info("获取Token信息业务处理");

        // 移除Bearer前缀
        String actualToken = removeBearerPrefix(token);

        // 验证Token
        if (!jwtUtil.validateToken(actualToken)) {
            return Mono.error(new BusinessException(401, "Token无效或已过期"));
        }

        // 从Token中获取用户信息
        Map<String, Object> tokenInfo = jwtUtil.getTokenInfo(actualToken);
        log.info("获取Token信息成功");
        return Mono.just(tokenInfo);
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
