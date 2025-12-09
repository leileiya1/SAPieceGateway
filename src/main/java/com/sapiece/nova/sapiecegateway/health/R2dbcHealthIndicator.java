package com.sapiece.nova.sapiecegateway.health;

import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * R2DBC数据库健康检查指示器
 * 用于监控数据库连接状态和响应时间
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
// @Component  // 暂时禁用
@RequiredArgsConstructor
public class R2dbcHealthIndicator implements ReactiveHealthIndicator {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;
    private final ConnectionFactory connectionFactory;

    /**
     * 健康检查超时时间（秒）
     */
    private static final int HEALTH_CHECK_TIMEOUT = 3;

    /**
     * 数据库验证查询SQL
     */
    private static final String VALIDATION_QUERY = "SELECT 1";

    @Override
    public Mono<Health> health() {
        log.debug("执行R2DBC数据库健康检查...");

        long startTime = System.currentTimeMillis();

        return checkDatabaseConnection()
                .map(isHealthy -> {
                    long responseTime = System.currentTimeMillis() - startTime;

                    if (isHealthy) {
                        log.debug("R2DBC数据库健康检查通过, 响应时间: {}ms", responseTime);
                        return Health.up()
                                .withDetail("status", "UP")
                                .withDetail("database", "MySQL")
                                .withDetail("responseTime", responseTime + "ms")
                                .withDetail("validationQuery", VALIDATION_QUERY)
                                .withDetail("message", "数据库连接正常")
                                .build();
                    } else {
                        log.warn("R2DBC数据库健康检查失败");
                        return Health.down()
                                .withDetail("status", "DOWN")
                                .withDetail("database", "MySQL")
                                .withDetail("message", "数据库连接失败")
                                .build();
                    }
                })
                .onErrorResume(error -> {
                    log.error("R2DBC数据库健康检查异常", error);
                    return Mono.just(Health.down()
                            .withDetail("status", "DOWN")
                            .withDetail("database", "MySQL")
                            .withDetail("error", error.getClass().getName())
                            .withDetail("message", error.getMessage())
                            .build());
                })
                .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT))
                .onErrorResume(error -> {
                    log.error("R2DBC数据库健康检查超时", error);
                    return Mono.just(Health.down()
                            .withDetail("status", "DOWN")
                            .withDetail("database", "MySQL")
                            .withDetail("message", "健康检查超时（" + HEALTH_CHECK_TIMEOUT + "秒）")
                            .build());
                });
    }

    /**
     * 检查数据库连接
     * 执行简单的SELECT查询来验证连接
     *
     * @return Mono<Boolean> true表示连接正常，false表示连接失败
     */
    private Mono<Boolean> checkDatabaseConnection() {
        return r2dbcEntityTemplate.getDatabaseClient()
                .sql(VALIDATION_QUERY)
                .fetch()
                .one()
                .map(result -> {
                    // 如果能成功执行查询并返回结果，说明数据库连接正常
                    log.trace("数据库健康检查查询结果: {}", result);
                    return true;
                })
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }
}
