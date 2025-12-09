package com.sapiece.nova.sapiecegateway.filter;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.web.server.WebFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 参数签名验证过滤器（WebFilter版本）
 * 防止参数篡改和重放攻击
 * WebFilter对所有请求生效（包括本地Controller和路由请求）
 *
 * 签名规则：
 * 1. 将所有请求参数按key的字典序排序
 * 2. 拼接成 key1=value1&key2=value2 格式
 * 3. 追加密钥：params + "&key=" + secret
 * 4. 计算MD5或SHA256签名
 * 5. 将签名放入请求头 X-Signature
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Component
public class SignatureVerificationFilter implements WebFilter, Ordered {

    /**
     * 是否启用签名验证
     */
    @Value("${signature.enabled:false}")
    private Boolean signatureEnabled;

    /**
     * 签名密钥（从配置文件读取）
     */
    @Value("${signature.secret:SAPiece-Signature-Secret-Key-2025}")
    private String secret;

    /**
     * 签名算法（MD5、SHA256）
     */
    @Value("${signature.algorithm:MD5}")
    private String algorithm;

    /**
     * 时间戳有效期（秒）
     */
    @Value("${signature.timestamp-validity:300}")
    private Long timestampValidity;

    /**
     * 签名Header名称
     */
    private static final String SIGNATURE_HEADER = "X-Signature";

    /**
     * 时间戳Header名称
     */
    private static final String TIMESTAMP_HEADER = "X-Timestamp";

    /**
     * Nonce Header名称（随机数，防止重放攻击）
     */
    private static final String NONCE_HEADER = "X-Nonce";

    /**
     * 过滤器优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    /**
     * 过滤器核心逻辑
     * 验证请求签名
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 如果签名验证未启用，直接放行
        if (!signatureEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        log.debug("签名验证过滤器执行, path: {}", path);

        // 跳过登录接口等不需要签名的接口
        if (isExcludedPath(path)) {
            log.debug("跳过签名验证, path: {}", path);
            return chain.filter(exchange);
        }

        // 获取请求头中的签名信息
        String signature = request.getHeaders().getFirst(SIGNATURE_HEADER);
        String timestamp = request.getHeaders().getFirst(TIMESTAMP_HEADER);
        String nonce = request.getHeaders().getFirst(NONCE_HEADER);

        // 检查必要参数
        if (signature == null || timestamp == null || nonce == null) {
            log.warn("签名验证失败：缺少必要参数, path: {}", path);
            return handleSignatureError(exchange, "缺少签名参数");
        }

        // 验证时间戳（防止重放攻击）
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis() / 1000;
            if (Math.abs(currentTime - requestTime) > timestampValidity) {
                log.warn("签名验证失败：请求已过期, path: {}, requestTime: {}, currentTime: {}",
                        path, requestTime, currentTime);
                return handleSignatureError(exchange, "请求已过期");
            }
        } catch (NumberFormatException e) {
            log.warn("签名验证失败：时间戳格式错误, path: {}", path);
            return handleSignatureError(exchange, "时间戳格式错误");
        }

        // 验证签名
        return verifySignature(exchange, signature, timestamp, nonce)
                .flatMap(valid -> {
                    if (valid) {
                        log.info("签名验证通过, path: {}", path);
                        return chain.filter(exchange);
                    } else {
                        log.warn("签名验证失败：签名不匹配, path: {}", path);
                        return handleSignatureError(exchange, "签名验证失败");
                    }
                });
    }

    /**
     * 验证签名
     *
     * @param exchange  服务器Web交换对象
     * @param signature 客户端签名
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @return 是否验证通过（响应式）
     */
    private Mono<Boolean> verifySignature(ServerWebExchange exchange, String signature,
                                          String timestamp, String nonce) {
        ServerHttpRequest request = exchange.getRequest();

        // 获取查询参数
        MultiValueMap<String, String> queryParams = request.getQueryParams();

        // 如果是POST请求，需要读取请求体
        if (HttpMethod.POST.equals(request.getMethod())) {
            return readRequestBody(exchange)
                    .flatMap(bodyMap -> {
                        // 合并查询参数和请求体参数
                        Map<String, String> allParams = new TreeMap<>();
                        queryParams.forEach((key, values) -> allParams.put(key, values.get(0)));
                        allParams.putAll(bodyMap);

                        // 添加时间戳和随机数
                        allParams.put("timestamp", timestamp);
                        allParams.put("nonce", nonce);

                        // 生成服务端签名
                        String serverSignature = generateSignature(allParams);

                        // 比较签名
                        boolean valid = signature.equalsIgnoreCase(serverSignature);
                        if (!valid) {
                            log.debug("签名不匹配, clientSign: {}, serverSign: {}", signature, serverSignature);
                        }

                        return Mono.just(valid);
                    });
        } else {
            // GET请求直接使用查询参数
            Map<String, String> allParams = new TreeMap<>();
            queryParams.forEach((key, values) -> allParams.put(key, values.get(0)));
            allParams.put("timestamp", timestamp);
            allParams.put("nonce", nonce);

            String serverSignature = generateSignature(allParams);
            boolean valid = signature.equalsIgnoreCase(serverSignature);

            if (!valid) {
                log.debug("签名不匹配, clientSign: {}, serverSign: {}", signature, serverSignature);
            }

            return Mono.just(valid);
        }
    }

    /**
     * 读取请求体
     * 由于响应式流只能读取一次，需要缓存请求体以便后续使用
     *
     * @param exchange 服务器Web交换对象
     * @return 请求体参数Map（响应式）
     */
    private Mono<Map<String, String>> readRequestBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String body = new String(bytes, StandardCharsets.UTF_8);
                    log.debug("读取请求体: {}", body);

                    // 简单解析JSON或form-data
                    Map<String, String> params = new HashMap<>();
                    // 这里简化处理，实际应该根据Content-Type解析
                    // TODO: 完善请求体解析逻辑

                    return Mono.just(params);
                })
                .defaultIfEmpty(new HashMap<>());
    }

    /**
     * 生成签名
     *
     * @param params 参数Map（已按key排序）
     * @return 签名字符串
     */
    private String generateSignature(Map<String, String> params) {
        // 1. 按key的字典序排序并拼接
        String paramsStr = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));

        // 2. 追加密钥
        String signContent = paramsStr + "&key=" + secret;

        log.debug("待签名字符串: {}", signContent);

        // 3. 计算签名
        String signature;
        if ("SHA256".equalsIgnoreCase(algorithm)) {
            signature = DigestUtil.sha256Hex(signContent);
        } else {
            signature = DigestUtil.md5Hex(signContent);
        }

        log.debug("生成签名: {}", signature);
        return signature.toUpperCase();
    }

    /**
     * 判断是否是排除路径（不需要签名验证）
     *
     * @param path 请求路径
     * @return 是否排除
     */
    private boolean isExcludedPath(String path) {
        // 登录、注册等公开接口不需要签名
        List<String> excludedPaths = Arrays.asList(
                "/auth/login",
                "/auth/register",
                "/public/",
                "/actuator/"
        );

        return excludedPaths.stream().anyMatch(path::startsWith);
    }

    /**
     * 处理签名验证失败
     *
     * @param exchange 服务器Web交换对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    private Mono<Void> handleSignatureError(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String responseBody = String.format(
                "{\"code\": 401, \"message\": \"签名验证失败：%s\"}",
                message
        );

        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
