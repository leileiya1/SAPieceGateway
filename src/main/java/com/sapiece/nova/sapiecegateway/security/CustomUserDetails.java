package com.sapiece.nova.sapiecegateway.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 自定义用户详情类
 * 实现Spring Security的UserDetails接口
 * 用于存储用户认证和授权信息
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 账号是否未过期
     */
    private boolean accountNonExpired;

    /**
     * 账号是否未锁定
     */
    private boolean accountNonLocked;

    /**
     * 凭证是否未过期
     */
    private boolean credentialsNonExpired;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 密码最后修改时间（用于Token失效检查）
     */
    private LocalDateTime passwordLastChangedAt;

    /**
     * 获取用户的所有权限（角色+权限）
     * Spring Security使用此方法进行权限验证
     *
     * @return 权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 将角色和权限合并，并转换为GrantedAuthority
        return Stream.concat(
                        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                        permissions.stream().map(SimpleGrantedAuthority::new)
                )
                .collect(Collectors.toList());
    }

    /**
     * 获取密码
     *
     * @return 密码
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 获取用户名
     *
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账号是否未过期
     *
     * @return true-未过期 false-已过期
     */
    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    /**
     * 账号是否未锁定
     *
     * @return true-未锁定 false-已锁定
     */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    /**
     * 凭证是否未过期
     *
     * @return true-未过期 false-已过期
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    /**
     * 账号是否启用
     *
     * @return true-启用 false-禁用
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
