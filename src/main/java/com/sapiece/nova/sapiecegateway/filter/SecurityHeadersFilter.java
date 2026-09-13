package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.common.FilterOrders;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 安全响应头过滤器
 *
 * 为所有响应注入浏览器安全头，防范常见 Web 攻击：
 *  - HSTS            : 强制 HTTPS（仅 HTTPS 部署时有效）
 *  - CSP             : 内容安全策略，防 XSS / 数据注入
 *  - X-Frame-Options : 防点击劫持（Clickjacking）
 *  - X-Content-Type  : 防 MIME 嗅探攻击
 *  - Referrer-Policy : 控制 Referer 信息泄露
 *  - Permissions     : 关闭不必要的浏览器 API 权限
 *  - Cache-Control   : 敏感 API 响应不缓存
 *  - X-Powered-By    : 移除，防止服务器信息泄露
 *
 * @author SAPiece
 * @since 2026-04-26
 */
@Component
public class SecurityHeadersFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return FilterOrders.REQUEST_LOG + 1; // 在日志过滤器之后，尽早注入安全头
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // 防 XSS / 数据注入：内容安全策略
            // API 网关通常不直接服务 HTML，但注入 CSP 防止潜在的内联脚本注入
            boolean swagger = exchange.getRequest().getPath().value().startsWith("/swagger-ui");
            headers.set("Content-Security-Policy", swagger
                    ? "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"
                    : "default-src 'none'; frame-ancestors 'none'");

            // 防点击劫持
            headers.set("X-Frame-Options", "DENY");

            // 防 MIME 类型嗅探
            headers.set("X-Content-Type-Options", "nosniff");

            // 控制 Referer 信息（不向跨域发送完整 URL）
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");

            // 关闭不必要的浏览器 API 权限
            headers.set("Permissions-Policy",
                    "geolocation=(), microphone=(), camera=(), payment=()");

            // 敏感 API 响应禁止缓存
            String path = exchange.getRequest().getPath().value();
            if (path.startsWith("/auth/") || path.startsWith("/admin/")) {
                headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
                headers.set("Pragma", "no-cache");
            }

            // HSTS：仅 HTTPS 下有效，浏览器下次直接走 HTTPS（includeSubDomains 可选）
            if ("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme())) {
                headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }

            // 移除服务器信息泄露
            headers.remove("X-Powered-By");
            headers.remove("Server");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
