package com.sapiece.nova.sapiecegateway.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结果类
 * 用于封装API接口的返回数据
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一响应结果")
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    @Schema(description = "响应码", example = "200")
    private Integer code;

    /**
     * 响应消息
     */
    @Schema(description = "响应消息", example = "操作成功")
    private String message;

    /**
     * 响应数据
     */
    @Schema(description = "响应数据")
    private T data;

    /**
     * 时间戳
     */
    @Schema(description = "时间戳", example = "1699999999999")
    private Long timestamp;

    /**
     * 成功响应码
     */
    public static final Integer SUCCESS_CODE = 200;

    /**
     * 失败响应码
     */
    public static final Integer ERROR_CODE = 500;

    /**
     * 未认证响应码
     */
    public static final Integer UNAUTHORIZED_CODE = 401;

    /**
     * 无权限响应码
     */
    public static final Integer FORBIDDEN_CODE = 403;

    /**
     * 成功响应（无数据）
     *
     * @return Result
     */
    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, "操作成功", null, System.currentTimeMillis());
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @return Result
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, "操作成功", data, System.currentTimeMillis());
    }

    /**
     * 成功响应（带消息和数据）
     *
     * @param message 响应消息
     * @param data    响应数据
     * @return Result
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(SUCCESS_CODE, message, data, System.currentTimeMillis());
    }

    /**
     * 失败响应
     *
     * @param message 错误消息
     * @return Result
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ERROR_CODE, message, null, System.currentTimeMillis());
    }

    /**
     * 失败响应（带数据）
     *
     * @param message 错误消息
     * @param data    响应数据
     * @return Result
     */
    public static <T> Result<T> error(String message, T data) {
        return new Result<>(ERROR_CODE, message, data, System.currentTimeMillis());
    }

    /**
     * 失败响应（带响应码）
     *
     * @param code    响应码
     * @param message 错误消息
     * @return Result
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }


    /**
     * 未认证响应
     *
     * @param message 错误消息
     * @return Result
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(UNAUTHORIZED_CODE, message, null, System.currentTimeMillis());
    }

    /**
     * 无权限响应
     *
     * @param message 错误消息
     * @return Result
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(FORBIDDEN_CODE, message, null, System.currentTimeMillis());
    }

    // ==================== 使用 ErrorCode 的方法 ====================

    /**
     * 失败响应（使用 ErrorCode）
     *
     * @param errorCode 错误码枚举
     * @return Result
     */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getDefaultMessage(), null, System.currentTimeMillis());
    }

    /**
     * 失败响应（使用 ErrorCode，带数据）
     *
     * @param errorCode 错误码枚举
     * @param data      响应数据
     * @return Result
     */
    public static <T> Result<T> error(ErrorCode errorCode, T data) {
        return new Result<>(errorCode.getCode(), errorCode.getDefaultMessage(), data, System.currentTimeMillis());
    }

    /**
     * 失败响应（使用 ErrorCode，自定义消息）
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     * @return Result
     */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null, System.currentTimeMillis());
    }

    /**
     * 判断响应是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(this.code);
    }
}
