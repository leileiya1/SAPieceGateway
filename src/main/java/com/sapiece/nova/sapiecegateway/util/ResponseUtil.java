package com.sapiece.nova.sapiecegateway.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.common.Constants;
import com.sapiece.nova.sapiecegateway.common.ErrorCode;
import com.sapiece.nova.sapiecegateway.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 响应工具类
 * 提供便捷的响应构建和返回方法，特别针对WebFlux响应式编程
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
public class ResponseUtil {

    /**
     * Jackson对象映射器（用于JSON序列化）
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ==================== 基础响应方法 ====================

    /**
     * 返回JSON响应
     * 将对象序列化为JSON并写入响应体
     *
     * @param exchange ServerWebExchange对象
     * @param data     要返回的数据对象
     * @return Mono<Void>
     */
    public static Mono<Void> writeJson(ServerWebExchange exchange, Object data) {
        return writeJson(exchange, HttpStatus.OK, data);
    }

    /**
     * 返回JSON响应（指定HTTP状态码）
     *
     * @param exchange   ServerWebExchange对象
     * @param httpStatus HTTP状态码
     * @param data       要返回的数据对象
     * @return Mono<Void>
     */
    public static Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus httpStatus, Object data) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            String json = OBJECT_MAPPER.writeValueAsString(data);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return Mono.error(e);
        }
    }

    /**
     * 返回文本响应
     *
     * @param exchange ServerWebExchange对象
     * @param text     要返回的文本
     * @return Mono<Void>
     */
    public static Mono<Void> writeText(ServerWebExchange exchange, String text) {
        return writeText(exchange, HttpStatus.OK, text);
    }

    /**
     * 返回文本响应（指定HTTP状态码）
     *
     * @param exchange   ServerWebExchange对象
     * @param httpStatus HTTP状态码
     * @param text       要返回的文本
     * @return Mono<Void>
     */
    public static Mono<Void> writeText(ServerWebExchange exchange, HttpStatus httpStatus, String text) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.TEXT_PLAIN);

        DataBuffer buffer = response.bufferFactory().wrap(text.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    // ==================== Result对象响应方法 ====================

    /**
     * 返回成功响应
     *
     * @param exchange ServerWebExchange对象
     * @param message  成功消息
     * @param data     返回的数据
     * @param <T>      数据类型
     * @return Mono<Void>
     */
    public static <T> Mono<Void> success(ServerWebExchange exchange, String message, T data) {
        Result<T> result = Result.success(message, data);
        return writeJson(exchange, HttpStatus.OK, result);
    }

    /**
     * 返回成功响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @param data     返回的数据
     * @param <T>      数据类型
     * @return Mono<Void>
     */
    public static <T> Mono<Void> success(ServerWebExchange exchange, T data) {
        return success(exchange, Constants.MSG_SUCCESS, data);
    }

    /**
     * 返回成功响应（无数据）
     *
     * @param exchange ServerWebExchange对象
     * @param message  成功消息
     * @return Mono<Void>
     */
    public static Mono<Void> success(ServerWebExchange exchange, String message) {
        return success(exchange, message, null);
    }

    /**
     * 返回成功响应（无数据，使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> success(ServerWebExchange exchange) {
        return success(exchange, Constants.MSG_SUCCESS, null);
    }

    // ==================== 错误响应方法 ====================

    /**
     * 返回错误响应
     *
     * @param exchange   ServerWebExchange对象
     * @param code       错误码
     * @param message    错误消息
     * @param httpStatus HTTP状态码
     * @return Mono<Void>
     */
    public static Mono<Void> error(ServerWebExchange exchange, int code, String message, HttpStatus httpStatus) {
        Result<?> result = Result.error(code, message);
        return writeJson(exchange, httpStatus, result);
    }

    /**
     * 返回错误响应（HTTP状态码与错误码相同）
     *
     * @param exchange ServerWebExchange对象
     * @param code     错误码
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> error(ServerWebExchange exchange, int code, String message) {
        HttpStatus httpStatus = HttpStatus.valueOf(code);
        return error(exchange, code, message, httpStatus);
    }

    /**
     * 返回错误响应（使用默认错误码500）
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> error(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.INTERNAL_SERVER_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 返回错误响应（使用ErrorCode枚举）
     *
     * @param exchange  ServerWebExchange对象
     * @param errorCode 错误码枚举
     * @return Mono<Void>
     */
    public static Mono<Void> error(ServerWebExchange exchange, ErrorCode errorCode) {
        Result<?> result = Result.error(errorCode);
        // 根据错误码确定HTTP状态码
        HttpStatus httpStatus = getHttpStatusFromErrorCode(errorCode);
        return writeJson(exchange, httpStatus, result);
    }

    /**
     * 返回错误响应（使用ErrorCode枚举，自定义消息）
     *
     * @param exchange  ServerWebExchange对象
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> error(ServerWebExchange exchange, ErrorCode errorCode, String message) {
        Result<?> result = Result.error(errorCode, message);
        HttpStatus httpStatus = getHttpStatusFromErrorCode(errorCode);
        return writeJson(exchange, httpStatus, result);
    }

    /**
     * 根据ErrorCode获取对应的HttpStatus
     *
     * @param errorCode 错误码枚举
     * @return HttpStatus
     */
    private static HttpStatus getHttpStatusFromErrorCode(ErrorCode errorCode) {
        int code = errorCode.getCode();
        // 20xxx 认证授权错误
        if (code >= 20001 && code <= 20009) {
            return HttpStatus.UNAUTHORIZED;
        } else if (code >= 20010 && code <= 20099) {
            return HttpStatus.FORBIDDEN;
        }
        // 30xxx 流量控制错误
        else if (code >= 30001 && code <= 30099) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        // 40xxx 路由错误
        else if (code >= 40001 && code <= 40099) {
            return HttpStatus.BAD_GATEWAY;
        }
        // 50xxx 参数验证错误
        else if (code >= 50001 && code <= 50099) {
            return HttpStatus.BAD_REQUEST;
        }
        // 60xxx 业务错误 和 其他错误
        else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    // ==================== 常见HTTP错误响应 ====================

    /**
     * 返回400 Bad Request响应
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> badRequest(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.BAD_REQUEST, message, HttpStatus.BAD_REQUEST);
    }

    /**
     * 返回400 Bad Request响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> badRequest(ServerWebExchange exchange) {
        return badRequest(exchange, Constants.MSG_BAD_REQUEST);
    }

    /**
     * 返回401 Unauthorized响应
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.UNAUTHORIZED, message, HttpStatus.UNAUTHORIZED);
    }

    /**
     * 返回401 Unauthorized响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> unauthorized(ServerWebExchange exchange) {
        return unauthorized(exchange, Constants.MSG_UNAUTHORIZED);
    }

    /**
     * 返回403 Forbidden响应
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.FORBIDDEN, message, HttpStatus.FORBIDDEN);
    }

    /**
     * 返回403 Forbidden响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> forbidden(ServerWebExchange exchange) {
        return forbidden(exchange, Constants.MSG_FORBIDDEN);
    }

    /**
     * 返回404 Not Found响应
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> notFound(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.NOT_FOUND, message, HttpStatus.NOT_FOUND);
    }

    /**
     * 返回404 Not Found响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> notFound(ServerWebExchange exchange) {
        return notFound(exchange, Constants.MSG_NOT_FOUND);
    }

    /**
     * 返回409 Conflict响应（如重复提交）
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> conflict(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.CONFLICT, message, HttpStatus.CONFLICT);
    }

    /**
     * 返回409 Conflict响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> conflict(ServerWebExchange exchange) {
        return conflict(exchange, Constants.MSG_IDEMPOTENT_FAILED);
    }

    /**
     * 返回429 Too Many Requests响应（限流）
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> tooManyRequests(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.TOO_MANY_REQUESTS, message, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * 返回429 Too Many Requests响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        return tooManyRequests(exchange, Constants.MSG_TOO_MANY_REQUESTS);
    }

    /**
     * 返回500 Internal Server Error响应
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> internalServerError(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.INTERNAL_SERVER_ERROR, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * 返回500 Internal Server Error响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> internalServerError(ServerWebExchange exchange) {
        return internalServerError(exchange, Constants.MSG_INTERNAL_SERVER_ERROR);
    }

    /**
     * 返回503 Service Unavailable响应（服务不可用）
     *
     * @param exchange ServerWebExchange对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    public static Mono<Void> serviceUnavailable(ServerWebExchange exchange, String message) {
        return error(exchange, Constants.SERVICE_UNAVAILABLE, message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * 返回503 Service Unavailable响应（使用默认消息）
     *
     * @param exchange ServerWebExchange对象
     * @return Mono<Void>
     */
    public static Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return serviceUnavailable(exchange, Constants.MSG_SERVICE_UNAVAILABLE);
    }

    // ==================== 自定义Header方法 ====================

    /**
     * 设置响应Header
     *
     * @param exchange ServerWebExchange对象
     * @param name     Header名称
     * @param value    Header值
     */
    public static void setHeader(ServerWebExchange exchange, String name, String value) {
        exchange.getResponse().getHeaders().set(name, value);
    }

    /**
     * 添加响应Header（支持多值）
     *
     * @param exchange ServerWebExchange对象
     * @param name     Header名称
     * @param value    Header值
     */
    public static void addHeader(ServerWebExchange exchange, String name, String value) {
        exchange.getResponse().getHeaders().add(name, value);
    }

    /**
     * 设置CORS响应头
     *
     * @param exchange      ServerWebExchange对象
     * @param allowOrigin   允许的源
     * @param allowMethods  允许的HTTP方法
     * @param allowHeaders  允许的请求头
     * @param exposeHeaders 暴露的响应头
     */
    public static void setCorsHeaders(ServerWebExchange exchange,
                                      String allowOrigin,
                                      String allowMethods,
                                      String allowHeaders,
                                      String exposeHeaders) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowMethods);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders);
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, exposeHeaders);
        headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
    }

    // ==================== 便捷方法 ====================

    /**
     * 判断响应是否已提交
     *
     * @param exchange ServerWebExchange对象
     * @return 是否已提交
     */
    public static boolean isCommitted(ServerWebExchange exchange) {
        return exchange.getResponse().isCommitted();
    }

    /**
     * 获取客户端真实IP
     *
     * @param exchange ServerWebExchange对象
     * @return 客户端IP地址
     */
    public static String getClientIp(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getRequest().getHeaders();

        // 优先从X-Real-IP获取
        String ip = headers.getFirst(Constants.REAL_IP_HEADER);
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // 其次从X-Forwarded-For获取
        ip = headers.getFirst(Constants.FORWARDED_FOR_HEADER);
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For可能包含多个IP，取第一个
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index);
            } else {
                return ip;
            }
        }

        // 最后从RemoteAddress获取
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    /**
     * 私有构造函数（防止实例化）
     */
    private ResponseUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
