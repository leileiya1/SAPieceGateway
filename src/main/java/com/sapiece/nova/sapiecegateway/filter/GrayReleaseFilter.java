package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.common.FilterOrders;
import com.sapiece.nova.sapiecegateway.entity.SysGrayRule;
import com.sapiece.nova.sapiecegateway.service.GrayRuleService;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 灰度发布过滤器
 * 根据灰度规则动态修改请求的目标URI
 * 支持按用户ID、IP、Header、比例等多种灰度策略
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrayReleaseFilter implements GlobalFilter, Ordered {

    private final GrayRuleService grayRuleService;
    private final JwtUtil jwtUtil;

    /**
     * 是否启用灰度发布
     */
    @Value("${gray.enabled:true}")
    private Boolean grayEnabled;

    /**
     * 灰度Header名称（用于Header策略）
     */
    @Value("${gray.header-name:X-Gray-Tag}")
    private String grayHeaderName;

    /**
     * 灰度参数名称（用于Param策略）
     */
    @Value("${gray.param-name:gray}")
    private String grayParamName;

    /**
     * Token前缀
     */
    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 过滤器优先级
     * 需要在路由匹配之后、实际转发之前执行
     */
    @Override
    public int getOrder() {
        return FilterOrders.GRAY_RELEASE;
    }

    /**
     * 过滤器核心逻辑
     * 检查灰度规则，动态修改目标URI
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 如果灰度发布未启用，直接放行
        if (!grayEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        log.debug("【灰度发布】开始处理, path: {}", path);

        // 获取当前路由信息
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            log.debug("【灰度发布】未找到路由信息, 跳过灰度处理, path: {}", path);
            return chain.filter(exchange);
        }

        String serviceId = route.getId();
        log.debug("【灰度发布】当前服务ID: {}", serviceId);

        // 获取请求相关信息
        Long userId = extractUserId(request);
        String clientIp = extractClientIp(request);
        String headerValue = request.getHeaders().getFirst(grayHeaderName);
        String paramValue = request.getQueryParams().getFirst(grayParamName);

        log.debug("【灰度发布】请求信息 - userId: {}, clientIp: {}, headerValue: {}, paramValue: {}",
                userId, clientIp, headerValue, paramValue);

        // 匹配灰度规则
        return grayRuleService.matchGrayRule(serviceId, userId, clientIp, headerValue, paramValue)
                .flatMap(grayRule -> {
                    // 命中灰度规则，修改目标URI
                    return applyGrayRule(exchange, chain, grayRule);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 未命中灰度规则，继续正常流程
                    log.debug("【灰度发布】未命中灰度规则, serviceId: {}", serviceId);
                    return chain.filter(exchange);
                }))
                .onErrorResume(error -> {
                    // 灰度规则匹配异常，继续正常流程（不影响主流程）
                    log.error("【灰度发布】灰度规则匹配异常, serviceId: {}, error: {}",
                            serviceId, error.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 应用灰度规则，修改目标URI
     *
     * @param exchange  服务器Web交换对象
     * @param chain     过滤器链
     * @param grayRule  灰度规则
     * @return Mono<Void>
     */
    private Mono<Void> applyGrayRule(ServerWebExchange exchange, GatewayFilterChain chain,
                                      SysGrayRule grayRule) {
        String targetUri = grayRule.getTargetUri();
        String path = exchange.getRequest().getPath().value();

        log.info("【灰度发布】命中灰度规则, ruleCode: {}, strategyType: {}, targetUri: {}",
                grayRule.getRuleCode(), grayRule.getStrategyType(), targetUri);

        try {
            // 解析目标URI
            URI newUri = URI.create(targetUri);

            // 创建新的请求，添加灰度标识Header
            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .header("X-Gray-Version", grayRule.getRuleCode())
                    .header("X-Gray-Strategy", grayRule.getStrategyType())
                    .build();

            // 设置新的路由URI
            ServerWebExchange newExchange = exchange.mutate()
                    .request(newRequest)
                    .build();

            // 修改路由目标URI
            newExchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, newUri);

            log.info("【灰度发布】已将请求路由到灰度服务, path: {}, targetUri: {}", path, targetUri);

            return chain.filter(newExchange);
        } catch (Exception e) {
            log.error("【灰度发布】应用灰度规则失败, ruleCode: {}, targetUri: {}, error: {}",
                    grayRule.getRuleCode(), targetUri, e.getMessage());
            // 失败时继续正常流程
            return chain.filter(exchange);
        }
    }

    /**
     * 从请求中提取用户ID
     *
     * @param request 请求对象
     * @return 用户ID（如果存在）
     */
    private Long extractUserId(ServerHttpRequest request) {
        try {
            String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(TOKEN_PREFIX)) {
                String token = bearerToken.substring(TOKEN_PREFIX.length());

                if (jwtUtil.validateToken(token)) {
                    return jwtUtil.getUserIdFromToken(token);
                }
            }
        } catch (Exception e) {
            log.debug("【灰度发布】提取用户ID失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 从请求中提取客户端IP
     *
     * @param request 请求对象
     * @return 客户端IP
     */
    private String extractClientIp(ServerHttpRequest request) {
        // 优先获取 X-Forwarded-For 头
        String ip = request.getHeaders().getFirst("X-Forwarded-For");

        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个IP，取第一个
            int index = ip.indexOf(',');
            if (index > 0) {
                ip = ip.substring(0, index);
            }
            return ip.trim();
        }

        // 尝试其他常见的IP头
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeaders().getFirst("Proxy-Client-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        ip = request.getHeaders().getFirst("WL-Proxy-Client-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }

        // 获取远程地址
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }
}
