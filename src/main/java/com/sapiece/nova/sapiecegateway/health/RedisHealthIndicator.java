package com.sapiece.nova.sapiecegateway.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis健康检查指示器
 * 用于监控Redis连接状态和响应时间
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
// @Component  // 暂时禁用，排查问题
public class RedisHealthIndicator implements ReactiveHealthIndicator {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public RedisHealthIndicator(@Qualifier("reactiveRedisTemplate") ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 健康检查超时时间（秒）
     */
    private static final int HEALTH_CHECK_TIMEOUT = 3;

    /**
     * 健康检查测试Key
     */
    private static final String HEALTH_CHECK_KEY = "health:check:redis";

    /**
     * 健康检查测试值
     */
    private static final String HEALTH_CHECK_VALUE = "ok";

    @Override
    public Mono<Health> health() {
        log.debug("执行Redis健康检查...");

        long startTime = System.currentTimeMillis();

        return checkRedisConnection()
                .map(isHealthy -> {
                    long responseTime = System.currentTimeMillis() - startTime;

                    if (isHealthy) {
                        log.debug("Redis健康检查通过, 响应时间: {}ms", responseTime);
                        return Health.up()
                                .withDetail("status", "UP")
                                .withDetail("responseTime", responseTime + "ms")
                                .withDetail("message", "Redis连接正常")
                                .build();
                    } else {
                        log.warn("Redis健康检查失败");
                        return Health.down()
                                .withDetail("status", "DOWN")
                                .withDetail("message", "Redis连接失败")
                                .build();
                    }
                })
                .onErrorResume(error -> {
                    log.error("Redis健康检查异常", error);
                    return Mono.just(Health.down()
                            .withDetail("status", "DOWN")
                            .withDetail("error", error.getClass().getName())
                            .withDetail("message", error.getMessage())
                            .build());
                })
                .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT))
                .onErrorResume(error -> {
                    log.error("Redis健康检查超时", error);
                    return Mono.just(Health.down()
                            .withDetail("status", "DOWN")
                            .withDetail("message", "健康检查超时（" + HEALTH_CHECK_TIMEOUT + "秒）")
                            .build());
                });
    }

    /**
     * 检查Redis连接
     * 通过执行PING命令或设置测试键值来验证连接
     *
     * @return Mono<Boolean> true表示连接正常，false表示连接失败
     */
    private Mono<Boolean> checkRedisConnection() {
        // 尝试执行PING命令（通过设置和获取测试值）
        return redisTemplate.opsForValue()
                .set(HEALTH_CHECK_KEY, HEALTH_CHECK_VALUE, Duration.ofSeconds(10))
                .flatMap(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        // 验证是否能读取到刚设置的值
                        return redisTemplate.opsForValue()
                                .get(HEALTH_CHECK_KEY)
                                .map(value -> HEALTH_CHECK_VALUE.equals(value));
                    }
                    return Mono.just(false);
                })
                .defaultIfEmpty(false)
                .doFinally(signalType -> {
                    // 清理测试键
                    redisTemplate.delete(HEALTH_CHECK_KEY)
                            .subscribe(
                                    deleted -> log.trace("清理Redis健康检查测试键: {}", deleted),
                                    error -> log.trace("清理Redis健康检查测试键失败", error)
                            );
                });
    }
}
