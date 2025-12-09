package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.entity.SysUser;
import com.sapiece.nova.sapiecegateway.service.SysMenuService;
import com.sapiece.nova.sapiecegateway.service.SysRoleService;
import com.sapiece.nova.sapiecegateway.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 响应式用户详情服务
 * 实现Spring Security的ReactiveUserDetailsService接口
 * 用于加载用户信息、角色和权限
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

                    // 2. 查询用户的角色列表
                    Mono<List<String>> rolesMono = roleService.findRoleCodesByUserId(user.getId())
                            .collectList()
                            .doOnSuccess(roles -> log.debug("查询到用户角色, userId: {}, roles: {}", user.getId(), roles));

                    // 3. 查询用户的权限列表
                    Mono<List<String>> permissionsMono = menuService.findPermissionCodesByUserId(user.getId())
                            .collectList()
                            .doOnSuccess(permissions -> log.debug("查询到用户权限, userId: {}, permissions: {}", user.getId(), permissions));

                    // 4. 组装UserDetails对象
                    return Mono.zip(rolesMono, permissionsMono)
                            .map(tuple -> {
                                List<String> roles = tuple.getT1();
                                List<String> permissions = tuple.getT2();

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

                                log.info("成功加载用户详情, userId: {}, userName: {}, roles: {}, permissions: {}",
                                        user.getId(), user.getUserName(), roles, permissions);

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
                .flatMap(user -> findByUsername(user.getUserName()))
                .doOnError(error -> log.error("根据用户ID加载用户详情失败, userId: {}, error: {}", userId, error.getMessage()));
    }
}
