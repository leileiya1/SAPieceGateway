package com.sapiece.nova.sapiecegateway.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * 标注在Controller方法上，自动记录操作日志
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作模块
     * 参考 SysAuditLog.Module 常量
     */
    String module();

    /**
     * 操作类型
     * 参考 SysAuditLog.Operation 常量
     */
    String operation();

    /**
     * 操作描述
     * 支持SpEL表达式，如 "修改用户信息，用户ID: #{#userId}"
     */
    String description() default "";

    /**
     * 是否记录请求参数
     * 默认记录
     */
    boolean recordParams() default true;

    /**
     * 是否记录响应结果
     * 默认不记录（敏感操作可开启）
     */
    boolean recordResult() default false;

    /**
     * 需要脱敏的参数名
     * 这些参数的值会被替换为 ***
     */
    String[] maskParams() default {"password", "oldPassword", "newPassword", "secret", "token"};
}
