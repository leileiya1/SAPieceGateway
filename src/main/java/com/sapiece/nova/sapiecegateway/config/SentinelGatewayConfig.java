package com.sapiece.nova.sapiecegateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sentinel 熔断降级配置
 *
 * 架构分工：
 *  - EnhancedRateLimitFilter（Lua token bucket）→ 全局限流（IP/用户/路由维度）
 *  - Sentinel → 下游服务熔断降级（慢调用、异常比率自动熔断）
 *
 * 熔断规则通过 Nacos 持久化（application.yml sentinel.datasource.*），
 * 也可在 Sentinel Dashboard 实时修改，无需重启服务。
 *
 * @author SAPiece
 * @since 2026-04-26
 */
@Slf4j
@Configuration
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelGatewayConfig(ObjectProvider<List<ViewResolver>> viewResolversProvider,
                                  ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * Sentinel 熔断异常处理器（最高优先级，确保在路由处理之前捕获）
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    /**
     * Sentinel Gateway 全局过滤器（order=-1，在 AuthHeaderGatewayFilter(-50) 之后、路由代理之前）
     */
    @Bean
    @Order(-1)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    /**
     * 初始化：
     * 1. 注册统一 JSON 格式的熔断响应（替换默认的 "Blocked by Sentinel" 文本）
     * 2. 加载默认兜底熔断规则（Nacos 数据源就绪后会自动覆盖）
     */
    @PostConstruct
    public void init() {
        initBlockHandler();
        initDefaultDegradeRules();
    }

    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, ex) ->
                ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(Map.of(
                                "code", 503,
                                "message", "下游服务繁忙，请稍后重试",
                                "data", null,
                                "success", false,
                                "timestamp", System.currentTimeMillis()
                        )))
        );
        log.info("Sentinel 熔断响应格式已自定义为 JSON");
    }

    /**
     * 默认兜底熔断规则（Nacos 数据源就绪后会被覆盖）
     *
     * 资源名约定：与 sys_gateway_route 表的 route_id 字段一致
     * 例如：user-service、order-service 等
     */
    private void initDefaultDegradeRules() {
        List<DegradeRule> rules = List.of(
                // 慢调用比率：RT > 2s 且比率 > 50%，连续 5 请求 → 熔断 10s
                buildSlowCallRule("_default_slow_", 2000, 0.5, 5, 10),
                // 异常比率：异常率 > 50%，连续 5 请求 → 熔断 10s
                buildExceptionRatioRule("_default_error_", 0.5, 5, 10)
        );
        DegradeRuleManager.loadRules(rules);
        log.info("Sentinel 默认兜底熔断规则已加载（共 {} 条），Nacos 数据源就绪后将自动覆盖", rules.size());
    }

    private DegradeRule buildSlowCallRule(String resource, int rtMs, double slowRatio,
                                          int minRequests, int recoverySec) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        rule.setCount(rtMs);
        rule.setSlowRatioThreshold(slowRatio);
        rule.setMinRequestAmount(minRequests);
        rule.setStatIntervalMs(10_000);
        rule.setTimeWindow(recoverySec);
        return rule;
    }

    private DegradeRule buildExceptionRatioRule(String resource, double ratio,
                                                 int minRequests, int recoverySec) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        rule.setCount(ratio);
        rule.setMinRequestAmount(minRequests);
        rule.setStatIntervalMs(10_000);
        rule.setTimeWindow(recoverySec);
        return rule;
    }
}
