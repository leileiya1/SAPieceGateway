package com.sapiece.nova.sapiecegateway.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.List;

/**
 * IP提取工具类
 * 只有请求来自可信代理IP时，才信任X-Forwarded-For头；
 * 否则直接使用RemoteAddress，避免客户端伪造XFF绕过黑名单。
 */
@Slf4j
public final class IpUtil {

    private IpUtil() {}

    /**
     * 提取客户端真实IP
     *
     * @param request       HTTP请求
     * @param trustedProxies 可信代理IP列表（来自这些IP的请求才信任XFF）；空列表=不信任XFF
     */
    public static String extractClientIp(ServerHttpRequest request, List<String> trustedProxies) {
        String remoteIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        // 没有配置可信代理，直接返回直连IP，防止伪造
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            return remoteIp;
        }

        // 只信任来自可信代理的XFF
        if (trustedProxies.contains(remoteIp)) {
            String xff = request.getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
                String ip = xff.split(",")[0].trim();
                log.debug("从可信代理{}的XFF提取客户端IP: {}", remoteIp, ip);
                return ip;
            }
            String xri = request.getHeaders().getFirst("X-Real-IP");
            if (xri != null && !xri.isBlank() && !"unknown".equalsIgnoreCase(xri)) {
                return xri.trim();
            }
        } else {
            log.debug("非可信代理请求({})，忽略XFF头，使用RemoteAddress", remoteIp);
        }

        return remoteIp;
    }
}
