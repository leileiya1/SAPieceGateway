package com.sapiece.nova.sapiecegateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.common.ErrorCode;
import com.sapiece.nova.sapiecegateway.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理器
 * 统一处理所有未捕获的异常，返回标准格式的错误响应
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Order(-2)  // 优先级高于默认异常处理器
@Component
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    /**
     * 处理异常
     *
     * @param exchange 服务器Web交换对象
     * @param ex       异常
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 如果响应已经提交，则不再处理
        if (response.isCommitted()) {
            log.warn("响应已提交，无法处理异常: {}", ex.getMessage());
            return Mono.error(ex);
        }

        // 设置响应头
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 根据异常类型返回不同的错误响应
        Result<?> result;
        HttpStatus httpStatus;

        if (ex instanceof BusinessException) {
            // 业务异常
            result = handleBusinessException((BusinessException) ex);
            httpStatus = HttpStatus.OK; // 业务异常返回200，通过code区分
            log.warn("业务异常: code={}, message={}", ((BusinessException) ex).getCode(), ex.getMessage());
        } else if (ex instanceof AccessDeniedException) {
            // 权限不足异常
            result = handleAccessDeniedException((AccessDeniedException) ex);
            httpStatus = HttpStatus.FORBIDDEN;
            log.warn("权限不足异常: {}", ex.getMessage());
        } else if (ex instanceof BadCredentialsException) {
            // 认证失败异常
            result = handleBadCredentialsException((BadCredentialsException) ex);
            httpStatus = HttpStatus.UNAUTHORIZED;
            log.warn("认证失败异常: {}", ex.getMessage());
        } else if (ex instanceof ResponseStatusException) {
            // 响应状态异常
            result = handleResponseStatusException((ResponseStatusException) ex);
            httpStatus = HttpStatus.valueOf(((ResponseStatusException) ex).getStatusCode().value());
            log.warn("响应状态异常: status={}, message={}", httpStatus, ex.getMessage());
        } else if (ex instanceof IllegalArgumentException) {
            // 非法参数异常
            result = handleIllegalArgumentException((IllegalArgumentException) ex);
            httpStatus = HttpStatus.BAD_REQUEST;
            log.warn("非法参数异常: {}", ex.getMessage());
        } else if (ex instanceof NullPointerException) {
            // 空指针异常
            result = handleNullPointerException((NullPointerException) ex);
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("空指针异常", ex);
        } else {
            // 其他未知异常
            result = handleUnknownException(ex);
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            log.error("系统异常: {}", ex.getMessage(), ex);
        }

        // 设置HTTP状态码
        response.setStatusCode(httpStatus);

        // 返回错误响应
        return writeResponse(response, result);
    }

    /**
     * 处理业务异常
     */
    private Result<?> handleBusinessException(BusinessException ex) {
        log.debug("处理业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return Result.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 处理权限不足异常
     */
    private Result<?> handleAccessDeniedException(AccessDeniedException ex) {
        log.debug("处理权限不足异常: {}", ex.getMessage());
        return Result.error(ErrorCode.PERMISSION_DENIED);
    }

    /**
     * 处理认证失败异常
     */
    private Result<?> handleBadCredentialsException(BadCredentialsException ex) {
        log.debug("处理认证失败异常: {}", ex.getMessage());
        return Result.error(ErrorCode.AUTH_TOKEN_INVALID);
    }

    /**
     * 处理响应状态异常
     */
    private Result<?> handleResponseStatusException(ResponseStatusException ex) {
        log.debug("处理响应状态异常: status={}, reason={}", ex.getStatusCode(), ex.getReason());
        String message = ex.getReason() != null ? ex.getReason() : "请求处理失败";
        return Result.error(ex.getStatusCode().value(), message);
    }

    /**
     * 处理非法参数异常
     */
    private Result<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.debug("处理非法参数异常: {}", ex.getMessage());
        return Result.error(ErrorCode.PARAM_INVALID, "参数错误: " + ex.getMessage());
    }

    /**
     * 处理空指针异常
     */
    private Result<?> handleNullPointerException(NullPointerException ex) {
        log.debug("处理空指针异常: {}", ex.getMessage());
        // 记录堆栈信息用于排查问题
        StackTraceElement[] stackTrace = ex.getStackTrace();
        if (stackTrace.length > 0) {
            StackTraceElement element = stackTrace[0];
            log.error("空指针异常位置: {}:{}", element.getClassName(), element.getLineNumber());
        }
        return Result.error(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 处理未知异常
     */
    private Result<?> handleUnknownException(Throwable ex) {
        log.debug("处理未知异常: type={}, message={}", ex.getClass().getName(), ex.getMessage());
        return Result.error(ErrorCode.SYSTEM_ERROR, "系统异常: " + ex.getMessage());
    }

    /**
     * 将响应结果写入到Response
     *
     * @param response 响应对象
     * @param result   结果对象
     * @return Mono<Void>
     */
    private Mono<Void> writeResponse(ServerHttpResponse response, Result<?> result) {
        try {
            // 将结果对象序列化为JSON
            String json = objectMapper.writeValueAsString(result);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            // 创建DataBuffer
            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            // 写入响应
            return response.writeWith(Mono.just(buffer))
                    .doOnError(error -> log.error("写入响应失败: {}", error.getMessage()));
        } catch (JsonProcessingException e) {
            log.error("序列化响应结果失败", e);
            return Mono.error(e);
        }
    }
}
