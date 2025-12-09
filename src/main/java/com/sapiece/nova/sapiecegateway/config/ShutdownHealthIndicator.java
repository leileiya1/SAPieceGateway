package com.sapiece.nova.sapiecegateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 停机状态健康检查
 * 当应用开始停机时，此健康检查会返回 DOWN 状态
 * 用于告知负载均衡器停止路由流量到此实例
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Component("shutdown")
@RequiredArgsConstructor
public class ShutdownHealthIndicator implements ReactiveHealthIndicator {

    private final GracefulShutdownHandler shutdownHandler;

    @Override
    public Mono<Health> health() {
        if (shutdownHandler.isShuttingDown()) {
            return Mono.just(Health.down()
                    .withDetail("status", "shutting_down")
                    .withDetail("message", "应用正在优雅停机")
                    .build());
        }

        return Mono.just(Health.up()
                .withDetail("status", "running")
                .withDetail("message", "应用运行正常")
                .build());
    }
}
