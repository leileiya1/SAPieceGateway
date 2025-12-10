package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.service.SysMenuService;
import com.sapiece.nova.sapiecegateway.service.SysRoleService;
import com.sapiece.nova.sapiecegateway.service.UserPermissionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 用户权限缓存服务实现类
 * 使用Redis缓存用户的角色和权限信息
 *
 * Redis Key设计：
 * - user:roles:{userId} -> List<String> 角色列表
 * - user:permissions:{userId} -> List<String> 权限列表
 *
 * @author SAPiece
 * @since 2025-12-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionCacheServiceImpl implements UserPermissionCacheService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final SysRoleService roleService;
    private final SysMenuService menuService;

    /**
     * 用户角色缓存Key前缀
     */
    private static final String USER_ROLES_PREFIX = "user:roles:";

    /**
     * 用户权限缓存Key前缀
     */
    private static final String USER_PERMISSIONS_PREFIX = "user:permissions:";

    /**
     * 缓存过期时间（毫秒），默认7天，与Refresh Token有效期一致
     */
    @Value("${jwt.refresh-token-expiration:604800000}")
    private Long cacheExpiration;

    @Override
    public Mono<Boolean> cacheUserPermissions(Long userId, List<String> roles, List<String> permissions) {
        log.debug("缓存用户权限信息, userId: {}", userId);

        String rolesKey = USER_ROLES_PREFIX + userId;
        String permissionsKey = USER_PERMISSIONS_PREFIX + userId;
        Duration ttl = Duration.ofMillis(cacheExpiration);

        // 先删除旧数据，再添加新数据
        Mono<Boolean> cacheRoles = reactiveRedisTemplate.delete(rolesKey)
                .then(Mono.defer(() -> {
                    if (roles == null || roles.isEmpty()) {
                        // 如果角色为空，存储一个占位符
                        return reactiveRedisTemplate.opsForList()
                                .rightPush(rolesKey, "__EMPTY__")
                                .then(reactiveRedisTemplate.expire(rolesKey, ttl));
                    }
                    return reactiveRedisTemplate.opsForList()
                            .rightPushAll(rolesKey, roles)
                            .then(reactiveRedisTemplate.expire(rolesKey, ttl));
                }));

        Mono<Boolean> cachePermissions = reactiveRedisTemplate.delete(permissionsKey)
                .then(Mono.defer(() -> {
                    if (permissions == null || permissions.isEmpty()) {
                        // 如果权限为空，存储一个占位符
                        return reactiveRedisTemplate.opsForList()
                                .rightPush(permissionsKey, "__EMPTY__")
                                .then(reactiveRedisTemplate.expire(permissionsKey, ttl));
                    }
                    return reactiveRedisTemplate.opsForList()
                            .rightPushAll(permissionsKey, permissions)
                            .then(reactiveRedisTemplate.expire(permissionsKey, ttl));
                }));

        return Mono.zip(cacheRoles, cachePermissions)
                .map(tuple -> tuple.getT1() && tuple.getT2())
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("成功缓存用户权限信息, userId: {}, rolesCount: {}, permissionsCount: {}",
                                userId,
                                roles != null ? roles.size() : 0,
                                permissions != null ? permissions.size() : 0);
                    }
                })
                .onErrorResume(e -> {
                    log.error("缓存用户权限信息失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<List<String>> getUserRoles(Long userId) {
        log.debug("获取用户角色缓存, userId: {}", userId);

        String rolesKey = USER_ROLES_PREFIX + userId;

        return reactiveRedisTemplate.opsForList()
                .range(rolesKey, 0, -1)
                .collectList()
                .map(roles -> {
                    // 过滤掉占位符
                    if (roles.size() == 1 && "__EMPTY__".equals(roles.get(0))) {
                        return Collections.<String>emptyList();
                    }
                    return roles;
                })
                .doOnSuccess(roles -> log.debug("获取用户角色缓存成功, userId: {}, roles: {}", userId, roles))
                .onErrorResume(e -> {
                    log.error("获取用户角色缓存失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<List<String>> getUserPermissions(Long userId) {
        log.debug("获取用户权限缓存, userId: {}", userId);

        String permissionsKey = USER_PERMISSIONS_PREFIX + userId;

        return reactiveRedisTemplate.opsForList()
                .range(permissionsKey, 0, -1)
                .collectList()
                .map(permissions -> {
                    // 过滤掉占位符
                    if (permissions.size() == 1 && "__EMPTY__".equals(permissions.get(0))) {
                        return Collections.<String>emptyList();
                    }
                    return permissions;
                })
                .doOnSuccess(permissions -> log.debug("获取用户权限缓存成功, userId: {}, permissionsCount: {}",
                        userId, permissions.size()))
                .onErrorResume(e -> {
                    log.error("获取用户权限缓存失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<Boolean> removeUserPermissions(Long userId) {
        log.debug("删除用户权限缓存, userId: {}", userId);

        String rolesKey = USER_ROLES_PREFIX + userId;
        String permissionsKey = USER_PERMISSIONS_PREFIX + userId;

        return reactiveRedisTemplate.delete(rolesKey, permissionsKey)
                .map(count -> count > 0)
                .doOnSuccess(success -> log.info("删除用户权限缓存, userId: {}, success: {}", userId, success))
                .onErrorResume(e -> {
                    log.error("删除用户权限缓存失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Boolean> refreshUserPermissions(Long userId) {
        log.debug("刷新用户权限缓存, userId: {}", userId);

        // 从数据库重新加载角色和权限
        Mono<List<String>> rolesMono = roleService.findRoleCodesByUserId(userId).collectList();
        Mono<List<String>> permissionsMono = menuService.findPermissionCodesByUserId(userId).collectList();

        return Mono.zip(rolesMono, permissionsMono)
                .flatMap(tuple -> cacheUserPermissions(userId, tuple.getT1(), tuple.getT2()))
                .doOnSuccess(success -> log.info("刷新用户权限缓存完成, userId: {}, success: {}", userId, success))
                .onErrorResume(e -> {
                    log.error("刷新用户权限缓存失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Boolean> hasUserPermissions(Long userId) {
        String rolesKey = USER_ROLES_PREFIX + userId;

        return reactiveRedisTemplate.hasKey(rolesKey)
                .doOnSuccess(exists -> log.debug("检查用户权限缓存是否存在, userId: {}, exists: {}", userId, exists));
    }
}
