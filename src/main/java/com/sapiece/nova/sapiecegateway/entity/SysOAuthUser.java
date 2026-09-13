package com.sapiece.nova.sapiecegateway.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * OAuth2用户绑定关系实体
 * 记录系统用户与第三方账号的绑定关系
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_oauth_user")
public class SysOAuthUser {

    // ==================== 基础信息 ====================

    /**
     * 主键ID
     */
    @Id
    private Long id;

    // ==================== 关联信息 ====================

    /**
     * 系统用户ID（关联sys_user.id，为空表示未绑定）
     */
    @Column("user_id")
    private Long userId;

    /**
     * OAuth提供商（GITHUB、GOOGLE、WECHAT等）
     */
    @Column("provider")
    private String provider;

    // ==================== 第三方用户信息 ====================

    /**
     * 第三方用户唯一标识（openid）
     */
    @Column("oauth_id")
    private String oauthId;

    /**
     * 第三方用户名
     */
    @Column("oauth_name")
    private String oauthName;

    /**
     * 第三方昵称
     */
    @Column("oauth_nickname")
    private String oauthNickname;

    /**
     * 第三方头像URL
     */
    @Column("oauth_avatar")
    private String oauthAvatar;

    /**
     * 第三方邮箱
     */
    @Column("oauth_email")
    private String oauthEmail;

    /**
     * 第三方手机号
     */
    @Column("oauth_mobile")
    private String oauthMobile;

    // ==================== Token信息 ====================

    /**
     * OAuth AccessToken（加密存储）
     */
    @Column("access_token")
    @JsonIgnore
    private String accessToken;

    /**
     * OAuth RefreshToken（加密存储）
     */
    @Column("refresh_token")
    @JsonIgnore
    private String refreshToken;

    /**
     * Token过期时间
     */
    @Column("token_expire_time")
    private LocalDateTime tokenExpireTime;

    // ==================== 扩展信息 ====================

    /**
     * 原始用户信息（JSON格式）
     */
    @Column("raw_user_info")
    @JsonIgnore
    private String rawUserInfo;

    // ==================== 时间信息 ====================

    /**
     * 绑定时间
     */
    @Column("bind_time")
    private LocalDateTime bindTime;

    /**
     * 最后登录时间
     */
    @Column("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    // ==================== 便捷方法 ====================

    /**
     * 是否已绑定系统用户
     */
    public boolean isBound() {
        return userId != null;
    }
}
