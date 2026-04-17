package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.service.SysMenuService;
import com.sapiece.nova.sapiecegateway.service.SysRoleService;
import com.sapiece.nova.sapiecegateway.service.SysUserService;
import com.sapiece.nova.sapiecegateway.service.UserPermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

/**
 * 响应式用户详情服务
 * 实现Spring Security的ReactiveUserDetailsService接口
 * 用于加载用户信息、角色和权限
 *
 * 优化版：
 * - 优先从Redis缓存获取权限信息
 * - 缓存不存在时再查询数据库
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomReactiveUserDetailsService implements ReactiveUserDetailsService {

    private final SysUserService userService;
    private final SysRoleService roleService;
    private final SysMenuService menuService;
    private final UserPermissionCacheService userPermissionCacheService;

    /**
     * 根据用户名加载用户详情
     * Spring Security会调用此方法进行用户认证
     *
     * @param username 用户名
     * @return 用户详情（响应式）
     */
    @Override
    public Mono<UserDetails> findByUsername(String username) {
        log.debug("加载用户详情, username: {}", username);

        // 1. 查询用户基本信息
        return userService.findActiveUserByUserName(username)
                .flatMap(user -> {
                    log.debug("查询到用户信息, userId: {}, userName: {}", user.getId(), user.getUserName());

                    // 2. 优先从Redis缓存获取角色和权限
                    return loadPermissionsWithCache(user.getId())
                            .map(tuple -> {
                                List<String> roles = tuple.roles();
                                List<String> permissions = tuple.permissions();

                                CustomUserDetails userDetails = new CustomUserDetails();
                                userDetails.setUserId(user.getId());
                                userDetails.setUsername(user.getUserName());
                                userDetails.setPassword(user.getPassword());
                                userDetails.setEnabled(user.getStatus() == 1); // 1-启用 0-禁用
                                userDetails.setAccountNonExpired(true);
                                userDetails.setAccountNonLocked(true);
                                userDetails.setCredentialsNonExpired(true);
                                userDetails.setRoles(roles);
                                userDetails.setPermissions(permissions);
                                userDetails.setPasswordLastChangedAt(user.getPasswordLastChangedAt()); // 密码修改时间

                                log.info("成功加载用户详情, userId: {}, userName: {}, rolesCount: {}, permissionsCount: {}",
                                        user.getId(), user.getUserName(), roles.size(), permissions.size());

                                return userDetails;
                            });
                })
                .cast(UserDetails.class)
                .doOnError(error -> log.error("加载用户详情失败, username: {}, error: {}", username, error.getMessage()));
    }

    /**
     * 根据用户ID加载用户详情
     * 用于从Token中解析用户信息后重新加载用户详情
     *
     * @param userId 用户ID
     * @return 用户详情（响应式）
     */
    public Mono<UserDetails> findByUserId(Long userId) {
        log.debug("根据用户ID加载用户详情, userId: {}", userId);

        return userService.findById(userId)
                .flatMap(user -> {
                    // 优先从Redis缓存获取角色和权限
                    return loadPermissionsWithCache(userId)
                            .map(tuple -> {
                                List<String> roles = tuple.roles();
                                List<String> permissions = tuple.permissions();

                                CustomUserDetails userDetails = new CustomUserDetails();
                                userDetails.setUserId(user.getId());
                                userDetails.setUsername(user.getUserName());
                                userDetails.setPassword(user.getPassword());
                                userDetails.setEnabled(user.getStatus() == 1);
                                userDetails.setAccountNonExpired(true);
                                userDetails.setAccountNonLocked(true);
                                userDetails.setCredentialsNonExpired(true);
                                userDetails.setRoles(roles);
                                userDetails.setPermissions(permissions);
                                userDetails.setPasswordLastChangedAt(user.getPasswordLastChangedAt());

                                log.debug("根据userId成功加载用户详情, userId: {}", userId);
                                return (UserDetails) userDetails;
                            });
                })
                .doOnError(error -> log.error("根据用户ID加载用户详情失败, userId: {}, error: {}", userId, error.getMessage()));
    }

    /**
     * 从缓存或数据库加载权限信息
     * 优先从Redis缓存获取，缓存不存在时查询数据库并写入缓存
     *
     * @param userId 用户ID
     * @return 角色和权限的元组
     */
    private Mono<PermissionTuple> loadPermissionsWithCache(Long userId) {
        // 先检查缓存是否存在
        return userPermissionCacheService.hasUserPermissions(userId)
                .flatMap(hasCache -> {
                    if (hasCache) {
                        // 从缓存获取
                        log.debug("从Redis缓存获取权限, userId: {}", userId);
                        return Mono.zip(
                                userPermissionCacheService.getUserRoles(userId),
                                userPermissionCacheService.getUserPermissions(userId)
                        ).map(tuple -> new PermissionTuple(tuple.getT1(), tuple.getT2()))
                                .onErrorResume(e -> {
                                    log.warn("Redis缓存读取失败，降级从数据库查询权限, userId: {}", userId);
                                    return loadPermissionsFromDatabase(userId);
                                });
                    } else {
                        // 缓存不存在，从数据库查询
                        log.debug("缓存不存在，从数据库查询权限, userId: {}", userId);
                        return loadPermissionsFromDatabase(userId);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Redis连接失败，降级从数据库查询权限, userId: {}", userId);
                    return loadPermissionsFromDatabase(userId);
                });
    }

    /**
     * 从数据库加载权限并写入缓存
     *
     * @param userId 用户ID
     * @return 角色和权限的元组
     */
    private Mono<PermissionTuple> loadPermissionsFromDatabase(Long userId) {
        Mono<List<String>> rolesMono = roleService.findRoleCodesByUserId(userId)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        Mono<List<String>> permissionsMono = menuService.findPermissionCodesByUserId(userId)
                .collectList()
                .defaultIfEmpty(Collections.emptyList());

        return Mono.zip(rolesMono, permissionsMono)
                .flatMap(tuple -> {
                    List<String> roles = tuple.getT1();
                    List<String> permissions = tuple.getT2();

                    // 写入缓存（Redis不可用时跳过）
                    return userPermissionCacheService.cacheUserPermissions(userId, roles, permissions)
                            .thenReturn(new PermissionTuple(roles, permissions))
                            .onErrorResume(e -> {
                                log.warn("Redis缓存写入失败，跳过缓存, userId: {}", userId);
                                return Mono.just(new PermissionTuple(roles, permissions));
                            });
                });
    }

    /**
     * 权限元组记录类
     */
    private record PermissionTuple(List<String> roles, List<String> permissions) {
    }
}
