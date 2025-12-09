package com.sapiece.nova.sapiecegateway.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性注解
 * 标注在需要进行幂等性校验的方法上
 * 防止重复提交（如重复下单、重复支付等）
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等性键的前缀
     * 默认使用方法全限定名
     */
    String prefix() default "";

    /**
     * 幂等性有效期（默认30秒）
     * 在此时间内的重复请求会被拦截
     */
    long timeout() default 30;

    /**
     * 时间单位（默认秒）
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 提示信息
     */
    String message() default "请勿重复提交";

    /**
     * 幂等性键的来源
     * HEADER - 从请求头获取
     * PARAMETER - 从请求参数获取
     * AUTO - 自动生成（基于请求内容）
     */
    KeySource keySource() default KeySource.HEADER;

    /**
     * 幂等性键的字段名
     * 当keySource为HEADER或PARAMETER时有效
     */
    String keyField() default "Idempotent-Token";

    /**
     * 幂等性键来源枚举
     */
    enum KeySource {
        /**
         * 从请求头获取
         */
        HEADER,

        /**
         * 从请求参数获取
         */
        PARAMETER,

        /**
         * 自动生成（基于用户ID + 请求路径 + 请求参数）
         */
        AUTO
    }
}
