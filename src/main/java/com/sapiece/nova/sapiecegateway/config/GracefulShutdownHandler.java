package com.sapiece.nova.sapiecegateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 优雅停机处理器
 * 监听应用关闭事件，执行清理任务
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
public class GracefulShutdownHandler implements ApplicationListener<ContextClosedEvent> {

    private final DatabaseClient databaseClient;
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 构造函数 - 依赖注入
     * 使用 @Autowired(required = false) 使依赖变为可选
     */
    public GracefulShutdownHandler(
            @Autowired(required = false) DatabaseClient databaseClient,
            @Autowired(required = false) ReactiveStringRedisTemplate redisTemplate) {
        this.databaseClient = databaseClient;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 标记是否正在关闭
     */
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    /**
     * 当前进行中的请求数
     * 注：在实际生产环境中，这个计数器应该在 MetricsFilter 中维护
     */
    private final AtomicBoolean hasInFlightRequests = new AtomicBoolean(true);

    /**
     * 处理应用关闭事件
     *
     * @param event 上下文关闭事件
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        if (!isShuttingDown.compareAndSet(false, true)) {
            log.warn("优雅停机已在进行中");
            return;
        }

        log.info("==================== 开始优雅停机 ====================");
        log.info("应用正在关闭，等待所有请求完成...");

        long startTime = System.currentTimeMillis();

        try {
            // 1. 等待进行中的请求完成
            waitForInFlightRequests();

            // 2. 清理 Redis 连接
            cleanupRedis();

            // 3. 清理数据库连接
            cleanupDatabase();

            // 4. 其他清理任务
            performAdditionalCleanup();

            long duration = System.currentTimeMillis() - startTime;
            log.info("==================== 优雅停机完成 ====================");
            log.info("停机耗时: {}ms", duration);

        } catch (Exception e) {
            log.error("优雅停机过程中出现异常", e);
        }
    }

    /**
     * 等待进行中的请求完成
     */
    private void waitForInFlightRequests() {
        log.info("等待进行中的请求完成...");

        int maxWaitSeconds = 30;
        int waitedSeconds = 0;

        while (hasInFlightRequests.get() && waitedSeconds < maxWaitSeconds) {
            try {
                Thread.sleep(1000);
                waitedSeconds++;

                if (waitedSeconds % 5 == 0) {
                    log.info("已等待 {} 秒，继续等待请求完成...", waitedSeconds);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待请求完成时被中断");
                break;
            }
        }

        if (waitedSeconds >= maxWaitSeconds) {
            log.warn("等待超时（{}秒），强制继续关闭流程", maxWaitSeconds);
        } else {
            log.info("所有进行中的请求已完成");
        }
    }

    /**
     * 清理 Redis 连接
     */
    private void cleanupRedis() {
        if (redisTemplate == null) {
            log.info("Redis 未配置，跳过清理");
            return;
        }

        try {
            log.info("清理 Redis 连接...");

            // 测试连接是否可用
            redisTemplate.hasKey("__shutdown_test__")
                    .timeout(Duration.ofSeconds(3))
                    .doOnNext(result -> log.info("Redis 连接正常关闭"))
                    .doOnError(error -> log.warn("Redis 连接关闭时出现异常: {}", error.getMessage()))
                    .onErrorComplete()
                    .block();

            log.info("Redis 清理完成");
        } catch (Exception e) {
            log.error("清理 Redis 时出现异常", e);
        }
    }

    /**
     * 清理数据库连接
     */
    private void cleanupDatabase() {
        if (databaseClient == null) {
            log.info("数据库未配置，跳过清理");
            return;
        }

        try {
            log.info("清理数据库连接...");

            // 执行一个简单查询测试连接
            databaseClient.sql("SELECT 1")
                    .fetch()
                    .first()
                    .timeout(Duration.ofSeconds(3))
                    .doOnNext(result -> log.info("数据库连接正常关闭"))
                    .doOnError(error -> log.warn("数据库连接关闭时出现异常: {}", error.getMessage()))
                    .onErrorComplete()
                    .block();

            log.info("数据库连接清理完成");
        } catch (Exception e) {
            log.error("清理数据库连接时出现异常", e);
        }
    }

    /**
     * 执行其他清理任务
     */
    private void performAdditionalCleanup() {
        try {
            log.info("执行额外的清理任务...");

            // 这里可以添加其他清理任务，例如：
            // - 刷新日志缓冲区
            // - 关闭外部连接
            // - 保存状态信息
            // - 发送关闭通知

            log.info("额外清理任务完成");
        } catch (Exception e) {
            log.error("执行额外清理任务时出现异常", e);
        }
    }

    /**
     * 检查是否正在关闭
     *
     * @return 是否正在关闭
     */
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
}
