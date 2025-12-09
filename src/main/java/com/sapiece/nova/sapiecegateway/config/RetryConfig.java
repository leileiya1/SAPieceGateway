package com.sapiece.nova.sapiecegateway.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 请求重试配置（重构版）
 *
 * 改进：
 * 1. 只对真正的失败（网络错误、超时、5xx 错误）重试
 * 2. 使用 retryOnException 精确控制重试条件
 * 3. 添加更详细的日志记录
 *
 * @author SAPiece
 * @since 2025-11-25
 */
@Slf4j
@Configuration
public class RetryConfig {

    /**
     * 配置重试策略
     */
    @Bean
    public RetryRegistry retryRegistry() {
        log.info("初始化Resilience4j重试配置");

        io.github.resilience4j.retry.RetryConfig config = io.github.resilience4j.retry.RetryConfig.custom()
                // 最大重试次数（3次）
                .maxAttempts(3)

                // 使用指数退避策略（每次重试间隔时间翻倍）
                // 初始间隔500ms，每次翻倍（500ms -> 1000ms -> 2000ms）
                .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                        Duration.ofMillis(500), // 初始间隔
                        2.0 // 倍数
                ))

                // 使用自定义判断逻辑，精确控制哪些异常应该重试
                .retryOnException(throwable -> {
                    // 1. 网络连接错误 - 重试
                    if (throwable instanceof java.net.ConnectException) {
                        log.debug("检测到网络连接错误，将重试");
                        return true;
                    }

                    // 2. 超时错误 - 重试
                    if (throwable instanceof TimeoutException ||
                            throwable instanceof java.net.SocketTimeoutException) {
                        log.debug("检测到超时错误，将重试");
                        return true;
                    }

                    // 3. IO 异常 - 重试
                    if (throwable instanceof java.io.IOException) {
                        log.debug("检测到IO异常，将重试");
                        return true;
                    }

                    // 4. WebClient 请求异常 - 重试
                    if (throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
                        log.debug("检测到WebClient请求异常，将重试");
                        return true;
                    }

                    // 5. ResponseStatusException - 只有 5xx 错误才重试
                    if (throwable instanceof ResponseStatusException) {
                        ResponseStatusException rse = (ResponseStatusException) throwable;
                        HttpStatus status = (HttpStatus) rse.getStatusCode();
                        boolean shouldRetry = status.is5xxServerError();
                        if (shouldRetry) {
                            log.debug("检测到5xx服务端错误，将重试: {}", status);
                        } else {
                            log.debug("检测到{}错误，不重试", status);
                        }
                        return shouldRetry;
                    }

                    // 6. 其他异常 - 不重试
                    log.debug("检测到不可重试的异常: {}", throwable.getClass().getSimpleName());
                    return false;
                })

                // 不重试的异常类型（业务异常、客户端错误不应重试）
                .ignoreExceptions(
                        IllegalArgumentException.class,
                        IllegalStateException.class,
                        SecurityException.class
                )

                .build();

        RetryRegistry registry = RetryRegistry.of(config);

        // 注册默认重试器
        Retry defaultRetry = registry.retry("default");

        // 添加事件监听器，记录重试事件
        defaultRetry.getEventPublisher()
                .onRetry(event -> {
                    log.warn("【重试】第{}次重试, 异常类型: {}, 异常信息: {}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getClass().getSimpleName(),
                            event.getLastThrowable().getMessage());
                })
                .onSuccess(event -> {
                    if (event.getNumberOfRetryAttempts() > 0) {
                        log.info("【重试成功】总共重试{}次后成功", event.getNumberOfRetryAttempts());
                    }
                })
                .onError(event -> {
                    log.error("【重试失败】已重试{}次仍然失败, 异常类型: {}, 异常信息: {}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getClass().getSimpleName(),
                            event.getLastThrowable().getMessage());
                });

        log.info("重试配置完成: maxAttempts=3, initialInterval=500ms, multiplier=2.0 (exponentialBackoff)");
        return registry;
    }

    /**
     * 创建默认重试器Bean
     */
    @Bean
    public Retry defaultRetry(RetryRegistry retryRegistry) {
        return retryRegistry.retry("default");
    }
}
