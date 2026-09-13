package com.sapiece.nova.sapiecegateway.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 网关下游信任签名工具
 *
 * 签名消息格式：timestamp={ts}&userId={uid}&method={method}&path={path}&requestId={requestId}
 * 算法：HMAC-SHA256，结果转十六进制字符串
 */
public final class GatewaySignatureUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private GatewaySignatureUtil() {}

    /**
     * 生成下游信任签名
     *
     * @param secret  共享密钥
     * @param timestamp 毫秒时间戳
     * @param userId  用户 ID，未认证请求传 "0"
     * @param path    请求路径
     * @return HMAC-SHA256 十六进制签名
     */
    public static String sign(String secret, long timestamp, String userId, String method,
                              String path, String requestId) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("下游签名密钥不能为空");
        }
        String message = canonicalMessage(timestamp, userId, method, path, requestId);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名失败", e);
        }
    }

    /**
     * 验证下游信任签名（下游服务调用）
     */
    public static boolean verify(String secret, long timestamp, String userId, String method,
                                 String path, String requestId, String signature) {
        if (signature == null) {
            return false;
        }
        byte[] expected = sign(secret, timestamp, userId, method, path, requestId)
                .getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private static String canonicalMessage(long timestamp, String userId, String method,
                                           String path, String requestId) {
        return "timestamp=" + timestamp
                + "&userId=" + valueOrEmpty(userId)
                + "&method=" + valueOrEmpty(method).toUpperCase()
                + "&path=" + valueOrEmpty(path)
                + "&requestId=" + valueOrEmpty(requestId);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
