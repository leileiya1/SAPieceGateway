package com.sapiece.nova.sapiecegateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * HTTPS 配置
 *
 * 功能：
 * 1. SSL_ENABLED=true 时，Spring Boot 在 server.port(8080) 上启用 HTTPS
 * 2. HTTP → HTTPS 重定向过滤器（健康探针端点豁免，避免 K8s 探针失败）
 *
 * 生产建议：
 *   - Nginx / Ingress 负责 80(HTTP)→443(HTTPS) 重定向，Spring Boot 只监听 8443 HTTPS
 *   - 或者 SSL_ENABLED=false，TLS 在负载均衡层（Nginx/ALB）终止（更常见）
 *
 * 自签证书生成：scripts/gen-keystore.sh
 * 生产证书：Let's Encrypt certbot 或企业 CA
 *
 * @author SAPiece
 * @since 2026-04-26
 */
@Slf4j
@Configuration
public class HttpsConfig {

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * HTTP → HTTPS 重定向过滤器
     *
     * 仅在 SSL_ENABLED=true 时激活（前置 nginx 负责 TLS 终止时不需要）。
     * 健康检查端点 /actuator/health 永远豁免，保证 K8s probe 正常工作。
     */
    @Bean
    public WebFilter httpToHttpsRedirectFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            if (!sslEnabled) {
                return chain.filter(exchange);
            }
            ServerHttpRequest req = exchange.getRequest();

            // 已经是 HTTPS，直接放行
            String scheme = req.getURI().getScheme();
            if ("https".equalsIgnoreCase(scheme)) {
                return chain.filter(exchange);
            }

            // 健康检查和 actuator 豁免（K8s liveness/readiness probe 走 HTTP）
            String path = req.getPath().value();
            if (path.startsWith("/actuator/")) {
                return chain.filter(exchange);
            }

            // 301 永久重定向到 HTTPS
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
            String httpsUrl = req.getURI().toString()
                    .replaceFirst("^http://", "https://");
            response.getHeaders().setLocation(URI.create(httpsUrl));
            log.debug("HTTP → HTTPS 重定向: {}", httpsUrl);
            return response.setComplete();
        };
    }
}
