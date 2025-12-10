package com.sapiece.nova.sapiecegateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * CORS跨域配置
 * 解决前后端分离跨域问题
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
@Configuration
public class CorsConfig {

    /**
     * 配置CORS过滤器
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        log.info("初始化CORS跨域配置");

        CorsConfiguration config = new CorsConfiguration();

        // 允许所有来源（生产环境建议配置具体域名）
        config.addAllowedOriginPattern("*");

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许所有HTTP方法
        config.addAllowedMethod("*");

        // 允许携带认证信息（如Cookie、Authorization header）
        config.setAllowCredentials(true);

        // 预检请求的有效期（秒）
        config.setMaxAge(3600L);

        // 暴露的响应头（允许前端访问的响应头）
        config.addExposedHeader(HttpHeaders.AUTHORIZATION);
        config.addExposedHeader("X-Request-ID");
        config.addExposedHeader("X-Total-Count");
        config.addExposedHeader("X-Token-Expired-Soon");
        config.addExposedHeader("X-Token-Expires-In");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        log.info("CORS配置完成: 允许所有源、所有方法、所有请求头");
        return new CorsWebFilter(source);
    }

    /**
     * OPTIONS预检请求处理过滤器
     * 确保OPTIONS请求能快速响应，不经过认证等过滤器
     */
    @Bean
    public WebFilter corsPreFlightFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (HttpMethod.OPTIONS.equals(request.getMethod())) {
                ServerHttpResponse response = exchange.getResponse();
                HttpHeaders headers = response.getHeaders();

                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "*");
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
                headers.add(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
                headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

                response.setStatusCode(HttpStatus.OK);
                log.debug("处理OPTIONS预检请求: {}", request.getPath());
                return Mono.empty();
            }

            return chain.filter(exchange);
        };
    }
}
