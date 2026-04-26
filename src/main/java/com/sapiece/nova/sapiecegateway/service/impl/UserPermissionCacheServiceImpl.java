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
        log.debug("缓存用户权限信息(Set), userId: {}", userId);

        String rolesKey = USER_ROLES_PREFIX + userId;
        String permissionsKey = USER_PERMISSIONS_PREFIX + userId;
        Duration ttl = Duration.ofMillis(cacheExpiration);

        // 使用Set：去重、语义清晰、不需要__EMPTY__占位符
        Mono<Boolean> cacheRoles = reactiveRedisTemplate.delete(rolesKey)
                .then(Mono.defer(() -> {
                    if (roles == null || roles.isEmpty()) {
                        // 空Set：用一个不可能的占位key + 立刻expire
                        return reactiveRedisTemplate.opsForSet()
                                .add(rolesKey + ":empty_flag", "1")
                                .then(reactiveRedisTemplate.opsForValue()
                                        .set(rolesKey + ":exists", "1", ttl))
                                .thenReturn(true);
                    }
                    return reactiveRedisTemplate.opsForSet()
                            .add(rolesKey, roles.toArray(String[]::new))
                            .then(reactiveRedisTemplate.expire(rolesKey, ttl))
                            .thenReturn(true);
                }));

        Mono<Boolean> cachePermissions = reactiveRedisTemplate.delete(permissionsKey)
                .then(Mono.defer(() -> {
                    if (permissions == null || permissions.isEmpty()) {
                        return reactiveRedisTemplate.opsForValue()
                                .set(permissionsKey + ":exists", "1", ttl)
                                .thenReturn(true);
                    }
                    return reactiveRedisTemplate.opsForSet()
                            .add(permissionsKey, permissions.toArray(String[]::new))
                            .then(reactiveRedisTemplate.expire(permissionsKey, ttl))
                            .thenReturn(true);
                }));

        return Mono.zip(cacheRoles, cachePermissions)
                .thenReturn(true)
                .doOnSuccess(ok -> log.info("成功缓存用户权限(Set), userId: {}, roles: {}, perms: {}",
                        userId,
                        roles != null ? roles.size() : 0,
                        permissions != null ? permissions.size() : 0))
                .onErrorResume(e -> {
                    log.error("缓存用户权限失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<List<String>> getUserRoles(Long userId) {
        String rolesKey = USER_ROLES_PREFIX + userId;
        return reactiveRedisTemplate.opsForSet()
                .members(rolesKey)
                .collectList()
                .doOnSuccess(r -> log.debug("获取用户角色(Set), userId: {}, count: {}", userId, r.size()))
                .onErrorResume(e -> {
                    log.error("获取用户角色缓存失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<List<String>> getUserPermissions(Long userId) {
        String permissionsKey = USER_PERMISSIONS_PREFIX + userId;
        return reactiveRedisTemplate.opsForSet()
                .members(permissionsKey)
                .collectList()
                .doOnSuccess(p -> log.debug("获取用户权限(Set), userId: {}, count: {}", userId, p.size()))
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
        // Set有数据时key存在；空Set情况用 rolesKey+":exists" 标记
        return reactiveRedisTemplate.hasKey(rolesKey)
                .flatMap(exists -> exists ? Mono.just(true)
                        : reactiveRedisTemplate.hasKey(rolesKey + ":exists"))
                .doOnSuccess(exists -> log.debug("检查用户权限缓存(Set)是否存在, userId: {}, exists: {}", userId, exists));
    }

    private static final String USER_PWD_VER_PREFIX = "user:pwdver:";

    @Override
    public Mono<Boolean> cachePwdVer(Long userId, long pwdVer) {
        String key = USER_PWD_VER_PREFIX + userId;
        Duration ttl = Duration.ofMillis(cacheExpiration);
        return reactiveRedisTemplate.opsForValue()
                .set(key, String.valueOf(pwdVer), ttl)
                .doOnSuccess(ok -> log.debug("缓存pwdVer, userId: {}, pwdVer: {}", userId, pwdVer))
                .onErrorResume(e -> {
                    log.warn("缓存pwdVer失败, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<Long> getPwdVer(Long userId) {
        String key = USER_PWD_VER_PREFIX + userId;
        return reactiveRedisTemplate.opsForValue()
                .get(key)
                .map(Long::parseLong)
                .defaultIfEmpty(0L)
                .onErrorReturn(0L);
    }
}
