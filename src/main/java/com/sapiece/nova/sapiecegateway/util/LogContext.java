package com.sapiece.nova.sapiecegateway.util;

import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 日志上下文工具类
 * 用于设置结构化日志的上下文信息（MDC）
 *
 * @author SAPiece
 * @since 2025-11-26
 */
public class LogContext {

    // MDC键名常量
    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String USERNAME = "username";
    public static final String CLIENT_IP = "clientIp";
    public static final String REQUEST_PATH = "requestPath";
    public static final String REQUEST_METHOD = "requestMethod";
    public static final String STATUS_CODE = "statusCode";
    public static final String DURATION = "duration";

    /**
     * 生成并设置 TraceId
     *
     * @return 生成的 TraceId
     */
    public static String generateTraceId() {
        String traceId = fetchSkyWalkingTraceId();
        if (traceId == null || traceId.isBlank() || "Ignored_Trace".equalsIgnoreCase(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        return traceId;
    }

    /**
     * 设置 TraceId
     *
     * @param traceId TraceId
     */
    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            traceId = fetchSkyWalkingTraceId();
        }
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID, traceId);
        }
    }

    /**
     * 获取 TraceId
     *
     * @return TraceId
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public static void setUserId(String userId) {
        if (userId != null && !userId.isEmpty()) {
            MDC.put(USER_ID, userId);
        }
    }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        if (username != null && !username.isEmpty()) {
            MDC.put(USERNAME, username);
        }
    }

    /**
     * 设置客户端IP
     *
     * @param clientIp 客户端IP
     */
    public static void setClientIp(String clientIp) {
        if (clientIp != null && !clientIp.isEmpty()) {
            MDC.put(CLIENT_IP, clientIp);
        }
    }

    /**
     * 设置请求路径
     *
     * @param requestPath 请求路径
     */
    public static void setRequestPath(String requestPath) {
        if (requestPath != null && !requestPath.isEmpty()) {
            MDC.put(REQUEST_PATH, requestPath);
        }
    }

    /**
     * 设置请求方法
     *
     * @param requestMethod 请求方法
     */
    public static void setRequestMethod(String requestMethod) {
        if (requestMethod != null && !requestMethod.isEmpty()) {
            MDC.put(REQUEST_METHOD, requestMethod);
        }
    }

    /**
     * 设置响应状态码
     *
     * @param statusCode 状态码
     */
    public static void setStatusCode(int statusCode) {
        MDC.put(STATUS_CODE, String.valueOf(statusCode));
    }

    /**
     * 设置请求处理时长
     *
     * @param duration 时长（毫秒）
     */
    public static void setDuration(long duration) {
        MDC.put(DURATION, String.valueOf(duration));
    }

    /**
     * 清除所有MDC上下文
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 清除指定的MDC键
     *
     * @param key MDC键
     */
    public static void remove(String key) {
        MDC.remove(key);
    }

    /**
     * 尝试从 SkyWalking TraceContext 中获取 TraceId
     */
    private static String fetchSkyWalkingTraceId() {
        try {
            Class<?> traceContextClass = Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
            Method traceIdMethod = traceContextClass.getMethod("traceId");
            Object result = traceIdMethod.invoke(null);
            if (result instanceof String traceId && !traceId.isBlank()) {
                return traceId;
            }
        } catch (ClassNotFoundException ignored) {
            // SkyWalking toolkit 未启用
        } catch (Exception ignored) {
            // 反射调用失败时忽略，使用本地生成的TraceId
        }
        return null;
    }

    /**
     * 私有构造函数（防止实例化）
     */
    private LogContext() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
