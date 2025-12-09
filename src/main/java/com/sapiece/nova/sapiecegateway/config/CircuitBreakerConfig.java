package com.sapiece.nova.sapiecegateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 熔断降级配置
 * 使用Resilience4j实现熔断器模式
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
@Configuration
public class CircuitBreakerConfig {

    /**
     * 配置熔断器
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        log.info("初始化Resilience4j熔断器配置");

        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        // 滑动窗口大小（统计最近N次请求）
                        .slidingWindowSize(10)

                        // 最小请求数（达到这个数量才会计算失败率）
                        .minimumNumberOfCalls(5)

                        // 失败率阈值（50%的请求失败则打开熔断器）
                        .failureRateThreshold(50.0f)

                        // 慢调用时间阈值（超过3秒算慢调用）
                        .slowCallDurationThreshold(Duration.ofSeconds(3))

                        // 慢调用比例阈值（50%的请求是慢调用则打开熔断器）
                        .slowCallRateThreshold(50.0f)

                        // 熔断器打开后，等待30秒进入半开状态
                        .waitDurationInOpenState(Duration.ofSeconds(30))

                        // 半开状态下允许5次调用测试服务是否恢复
                        .permittedNumberOfCallsInHalfOpenState(5)

                        // 自动从打开状态转换为半开状态
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)

                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        // 超时时间（5秒）
                        .timeoutDuration(Duration.ofSeconds(5))
                        .build())
                .build());
    }

    /**
     * 熔断器注册表
     * 可以动态获取和监控熔断器状态
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

        // 添加事件监听器，记录熔断器状态变化
        registry.circuitBreaker("default").getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("熔断器状态变化: {} -> {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                })
                .onError(event -> {
                    log.error("熔断器捕获异常: {}", event.getThrowable().getMessage());
                });

        log.info("熔断器注册表初始化完成");
        return registry;
    }
}
