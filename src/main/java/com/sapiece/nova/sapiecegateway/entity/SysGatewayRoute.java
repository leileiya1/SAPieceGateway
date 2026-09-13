package com.sapiece.nova.sapiecegateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 网关动态路由配置实体（增强版）
 * 整合路由配置 + 权限配置 + 限流配置 + 缓存配置
 * 一个路由 = 路由规则 + 权限控制 + 流量控制
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_gateway_route")
public class SysGatewayRoute {

    // ==================== 基础信息 ====================

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 路由ID（唯一标识）
     * 如：user-service, order-service
     */
    @Column("route_id")
    private String routeId;

    /**
     * 路由名称（中文描述）
     */
    @Column("route_name")
    private String routeName;

    /**
     * 目标URI
     * 支持两种格式：
     * 1. http://localhost:8081 - 直接转发到指定地址
     * 2. lb://user-service - 通过 Kubernetes Service DNS 动态负载均衡
     */
    @Column("uri")
    private String uri;

    // ==================== 路由配置 ====================

    /**
     * 断言配置（JSON数组）
     * 格式：[{"name": "Path", "args": {"pattern": "/api/user/**"}}]
     */
    @Column("predicates")
    private String predicates;

    /**
     * 过滤器配置（JSON数组）
     * 格式：[{"name": "StripPrefix", "args": {"parts": "1"}}]
     */
    @Column("filters")
    private String filters;

    /**
     * 元数据（JSON对象）
     * 可存储自定义扩展信息
     */
    @Column("metadata")
    private String metadata;

    /**
     * 路由顺序
     * 数字越小优先级越高
     */
    @Column("order_num")
    private Integer orderNum;

    // ==================== 权限配置 ====================

    /**
     * 是否需要认证
     * 0-否（公开接口，不需要登录）
     * 1-是（需要登录认证）
     */
    @Column("require_auth")
    private Integer requireAuth;

    /**
     * 所需权限标识
     * 多个权限用逗号分隔，如：system:user:query,system:user:add
     * 为空表示只需要登录，不需要特定权限
     */
    @Column("permission_code")
    private String permissionCode;

    /**
     * 多权限逻辑
     * AND：用户必须同时拥有所有权限
     * OR：用户拥有任一权限即可（默认）
     */
    @Column("permission_logic")
    private String permissionLogic;

    // ==================== 限流配置 ====================

    /**
     * 是否启用限流
     * 0-否，1-是
     */
    @Column("rate_limit_enabled")
    private Integer rateLimitEnabled;

    /**
     * 限流QPS（每秒请求数）
     */
    @Column("rate_limit_qps")
    private Integer rateLimitQps;

    /**
     * 限流策略
     * ip - 按IP限流
     * route - 按路由限流
     * user - 按用户限流
     */
    @Column("rate_limit_strategy")
    private String rateLimitStrategy;

    // ==================== 缓存配置 ====================

    /**
     * 是否启用响应缓存
     * 0-否，1-是
     */
    @Column("cache_enabled")
    private Integer cacheEnabled;

    /**
     * 缓存过期时间（秒）
     */
    @Column("cache_ttl")
    private Integer cacheTtl;

    // ==================== 其他配置 ====================

    /**
     * 是否启用重试
     * 0-否，1-是
     */
    @Column("retry_enabled")
    private Integer retryEnabled;

    /**
     * 重试次数
     */
    @Column("retry_times")
    private Integer retryTimes;

    /**
     * 超时时间（毫秒）
     */
    @Column("timeout_ms")
    private Integer timeoutMs;

    // ==================== 状态与描述 ====================

    /**
     * 状态
     * 0-禁用，1-启用
     */
    @Column("status")
    private Integer status;

    /**
     * 路由描述
     */
    @Column("description")
    private String description;

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

    // ==================== 便捷方法 ====================

    /**
     * 是否需要认证
     */
    public boolean isRequireAuth() {
        return requireAuth != null && requireAuth == 1;
    }

    /**
     * 是否启用限流
     */
    public boolean isRateLimitEnabled() {
        return rateLimitEnabled != null && rateLimitEnabled == 1;
    }

    /**
     * 是否启用缓存
     */
    public boolean isCacheEnabled() {
        return cacheEnabled != null && cacheEnabled == 1;
    }

    /**
     * 是否启用重试
     */
    public boolean isRetryEnabled() {
        return retryEnabled != null && retryEnabled == 1;
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /**
     * 是否有权限要求
     */
    public boolean hasPermissionRequirement() {
        return permissionCode != null && !permissionCode.isBlank();
    }

    /**
     * 是否使用 AND 逻辑
     */
    public boolean isAndLogic() {
        return "AND".equalsIgnoreCase(permissionLogic);
    }
}
