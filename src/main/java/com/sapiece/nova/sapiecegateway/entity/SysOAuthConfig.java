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
 * OAuth2第三方登录配置实体
 * 存储各个OAuth2提供商的配置信息
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_oauth_config")
public class SysOAuthConfig {

    // ==================== 基础信息 ====================

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * OAuth提供商（GITHUB、GOOGLE、WECHAT、GITEE等）
     */
    @Column("provider")
    private String provider;

    /**
     * 提供商名称（显示用）
     */
    @Column("provider_name")
    private String providerName;

    // ==================== OAuth配置 ====================

    /**
     * 客户端ID（从第三方平台获取）
     */
    @Column("client_id")
    private String clientId;

    /**
     * 客户端密钥（加密存储）
     */
    @Column("client_secret")
    @JsonIgnore
    private String clientSecret;

    /**
     * 授权地址
     */
    @Column("authorization_uri")
    private String authorizationUri;

    /**
     * 获取Token地址
     */
    @Column("token_uri")
    private String tokenUri;

    /**
     * 获取用户信息地址
     */
    @Column("user_info_uri")
    private String userInfoUri;

    /**
     * 回调地址
     */
    @Column("redirect_uri")
    private String redirectUri;

    /**
     * 授权范围，多个用逗号分隔
     */
    @Column("scopes")
    private String scopes;

    // ==================== 状态配置 ====================

    /**
     * 状态：0-禁用 1-启用
     */
    @Column("status")
    private Integer status;

    /**
     * 排序顺序
     */
    @Column("sort_order")
    private Integer sortOrder;

    // ==================== 审计字段 ====================

    /**
     * 创建人
     */
    @Column("creator")
    private String creator;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    @Column("updater")
    private String updater;

    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;

    // ==================== 常量定义 ====================

    /**
     * OAuth提供商枚举
     */
    public static class Provider {
        public static final String GITHUB = "GITHUB";
        public static final String GOOGLE = "GOOGLE";
        public static final String GITEE = "GITEE";
        public static final String WECHAT = "WECHAT";
        public static final String QQ = "QQ";
    }

    /**
     * 状态枚举
     */
    public static class Status {
        public static final Integer DISABLED = 0;
        public static final Integer ENABLED = 1;
    }

    // ==================== 便捷方法 ====================

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
