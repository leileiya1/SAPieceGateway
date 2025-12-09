package com.sapiece.nova.sapiecegateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 灰度发布规则配置实体
 * 支持按用户ID、用户标签、IP、Header、比例等多种灰度策略
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_gray_rule")
public class SysGrayRule {

    // ==================== 基础信息 ====================

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 规则名称
     */
    @Column("rule_name")
    private String ruleName;

    /**
     * 规则编码（唯一标识）
     */
    @Column("rule_code")
    private String ruleCode;

    /**
     * 规则描述
     */
    @Column("description")
    private String description;

    // ==================== 路由关联 ====================

    /**
     * 服务ID，关联 sys_gateway_route.route_id
     */
    @Column("service_id")
    private String serviceId;

    /**
     * 灰度目标URI（新版本服务地址）
     */
    @Column("target_uri")
    private String targetUri;

    /**
     * 稳定版本URI（不配置则使用路由默认URI）
     */
    @Column("stable_uri")
    private String stableUri;

    // ==================== 灰度策略 ====================

    /**
     * 策略类型
     * USER_ID-按用户ID、USER_TAG-按用户标签、IP-按IP、
     * HEADER-按请求头、WEIGHT-按比例、PARAM-按请求参数
     */
    @Column("strategy_type")
    private String strategyType;

    /**
     * 策略配置（JSON格式）
     * 不同策略类型对应不同结构
     */
    @Column("strategy_config")
    private String strategyConfig;

    // ==================== 优先级与状态 ====================

    /**
     * 优先级，数字越小优先级越高
     */
    @Column("priority")
    private Integer priority;

    /**
     * 状态：0-禁用 1-启用
     */
    @Column("status")
    private Integer status;

    // ==================== 生效时间 ====================

    /**
     * 生效开始时间（为空表示立即生效）
     */
    @Column("effective_start")
    private LocalDateTime effectiveStart;

    /**
     * 生效结束时间（为空表示永久有效）
     */
    @Column("effective_end")
    private LocalDateTime effectiveEnd;

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
     * 策略类型枚举
     */
    public static class StrategyType {
        public static final String USER_ID = "USER_ID";     // 按用户ID
        public static final String USER_TAG = "USER_TAG";   // 按用户标签
        public static final String IP = "IP";               // 按IP
        public static final String HEADER = "HEADER";       // 按请求头
        public static final String WEIGHT = "WEIGHT";       // 按比例
        public static final String PARAM = "PARAM";         // 按请求参数
    }

    /**
     * 状态枚举
     */
    public static class Status {
        public static final Integer DISABLED = 0;  // 禁用
        public static final Integer ENABLED = 1;   // 启用
    }

    // ==================== 便捷方法 ====================

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /**
     * 是否在有效期内
     */
    public boolean isInEffectivePeriod() {
        LocalDateTime now = LocalDateTime.now();

        // 检查开始时间
        if (effectiveStart != null && now.isBefore(effectiveStart)) {
            return false;
        }

        // 检查结束时间
        if (effectiveEnd != null && now.isAfter(effectiveEnd)) {
            return false;
        }

        return true;
    }

    /**
     * 是否有效（启用且在有效期内）
     */
    public boolean isValid() {
        return isEnabled() && isInEffectivePeriod();
    }
}
