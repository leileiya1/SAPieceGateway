package com.sapiece.nova.sapiecegateway.filter;

import cn.hutool.crypto.digest.DigestUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.common.FilterOrders;
import com.sapiece.nova.sapiecegateway.config.SignatureProperties;
import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
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

    private static final String CACHED_REQUEST_BODY_ATTR = "signatureFilter.cachedBody";

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

    private final ObjectMapper objectMapper;
    private final SignatureProperties signatureProperties;

    public SignatureVerificationFilter(ObjectMapper objectMapper, SignatureProperties signatureProperties) {
        this.objectMapper = objectMapper;
        this.signatureProperties = signatureProperties;
    }

    /**
     * 过滤器优先级
     */
    @Override
    public int getOrder() {
        return FilterOrders.SIGNATURE_VERIFICATION;
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
        if (!signatureProperties.isEnabled()) {
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

        Mono<ServerWebExchange> readyExchangeMono = requiresBodyCaching(request)
                ? cacheRequestBody(exchange)
                : Mono.just(exchange);

        return readyExchangeMono.flatMap(readyExchange -> {
            ServerHttpRequest readyRequest = readyExchange.getRequest();
            HttpHeaders headers = readyRequest.getHeaders();

            String signature = headers.getFirst(SIGNATURE_HEADER);
            String timestamp = headers.getFirst(TIMESTAMP_HEADER);
            String nonce = headers.getFirst(NONCE_HEADER);

            if (signature == null || timestamp == null || nonce == null) {
                log.warn("签名验证失败：缺少必要参数, path: {}", path);
                return handleSignatureError(readyExchange, "缺少签名参数");
            }

            try {
                long requestTime = Long.parseLong(timestamp);
                long currentTime = System.currentTimeMillis() / 1000;
                if (Math.abs(currentTime - requestTime) > signatureProperties.getTimestampValidity()) {
                    log.warn("签名验证失败：请求已过期, path: {}, requestTime: {}, currentTime: {}",
                            path, requestTime, currentTime);
                    return handleSignatureError(readyExchange, "请求已过期");
                }
            } catch (NumberFormatException e) {
                log.warn("签名验证失败：时间戳格式错误, path: {}", path);
                return handleSignatureError(readyExchange, "时间戳格式错误");
            }

            return verifySignature(readyExchange, signature, timestamp, nonce)
                    .flatMap(valid -> {
                        if (valid) {
                            log.info("签名验证通过, path: {}", path);
                            return chain.filter(readyExchange);
                        } else {
                            log.warn("签名验证失败：签名不匹配, path: {}", path);
                            return handleSignatureError(readyExchange, "签名验证失败");
                        }
                    });
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

        boolean hasBody = requiresBodyCaching(request);

        if (hasBody) {
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
        byte[] cachedBody = exchange.getAttribute(CACHED_REQUEST_BODY_ATTR);
        if (cachedBody == null || cachedBody.length == 0) {
            return Mono.just(new HashMap<>());
        }

        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        String body = new String(cachedBody, StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return Mono.just(new HashMap<>());
        }

        Map<String, String> params = parseBodyByContentType(body, contentType);
        return Mono.just(params);
    }

    private boolean requiresBodyCaching(ServerHttpRequest request) {
        HttpMethod method = request.getMethod();
        return HttpMethod.POST.equals(method)
                || HttpMethod.PUT.equals(method)
                || HttpMethod.PATCH.equals(method);
    }

    private Mono<ServerWebExchange> cacheRequestBody(ServerWebExchange exchange) {
        byte[] cached = exchange.getAttribute(CACHED_REQUEST_BODY_ATTR);
        if (cached != null) {
            return Mono.just(exchange);
        }

        return DataBufferUtils.join(exchange.getRequest().getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    exchange.getAttributes().put(CACHED_REQUEST_BODY_ATTR, bytes);

                    DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                    ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.defer(() -> {
                                DataBuffer buffer = bufferFactory.wrap(bytes);
                                return Mono.just(buffer);
                            });
                        }
                    };

                    ServerWebExchange mutated = exchange.mutate().request(decoratedRequest).build();
                    mutated.getAttributes().put(CACHED_REQUEST_BODY_ATTR, bytes);
                    return Mono.just(mutated);
                });
    }

    private Map<String, String> parseBodyByContentType(String body, MediaType contentType) {
        Map<String, String> params = new HashMap<>();
        try {
            if (isJsonContent(contentType) || looksLikeJson(body)) {
                params.putAll(parseJsonBody(body));
            } else if (contentType != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
                params.putAll(parseFormBody(body));
            } else if (contentType != null && MediaType.MULTIPART_FORM_DATA.isCompatibleWith(contentType)) {
                log.warn("暂不支持 multipart/form-data 签名解析，请改用JSON或表单参数. contentType={}", contentType);
            } else {
                params.putAll(parseFormBody(body));
            }
        } catch (Exception e) {
            log.warn("解析请求体失败: {}", e.getMessage());
        }
        return params;
    }

    private Map<String, String> parseJsonBody(String body) throws java.io.IOException {
        Map<String, String> params = new LinkedHashMap<>();
        JsonNode root = objectMapper.readTree(body);
        flattenJsonNode(root, "", params);
        return params;
    }

    private Map<String, String> parseFormBody(String body) {
        Map<String, String> params = new HashMap<>();
        try {
            MultiValueMap<String, String> formData = UriComponentsBuilder.newInstance()
                    .query(body)
                    .build()
                    .getQueryParams();
            formData.forEach((key, values) -> {
                if (values != null && !values.isEmpty()) {
                    params.put(key, values.get(0));
                }
            });
        } catch (Exception e) {
            log.warn("解析表单请求体失败: {}", e.getMessage());
        }
        return params;
    }

    private void flattenJsonNode(JsonNode node, String path, Map<String, String> target) {
        if (node == null) {
            return;
        }
        if (node.isValueNode() || node.isNull()) {
            String key = path.isEmpty() ? "body" : path;
            target.put(key, node.isNull() ? "" : node.asText(""));
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                String childPath = path.isEmpty() ? "[" + index + "]" : path + "[" + index + "]";
                flattenJsonNode(child, childPath, target);
                index++;
            }
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            flattenJsonNode(entry.getValue(), childPath, target);
        }
    }

    private boolean isJsonContent(MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        return MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || (contentType.getSubtype() != null && contentType.getSubtype().contains("json"));
    }

    private boolean looksLikeJson(String body) {
        String trimmed = body.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
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
        String signContent = paramsStr + "&key=" + signatureProperties.getSecret();

        log.debug("待签名字符串: {}", signContent);

        // 3. 计算签名
        String signature;
        if ("SHA256".equalsIgnoreCase(signatureProperties.getAlgorithm())) {
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
        // 从配置获取排除路径
        List<String> excludedPaths = signatureProperties.getExcludedPaths();
        if (excludedPaths == null || excludedPaths.isEmpty()) {
            return false;
        }
        return excludedPaths.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> handleSignatureError(ServerWebExchange exchange, String message) {
        return ResponseUtil.unauthorized(exchange, "签名验证失败：" + message);
    }
}
