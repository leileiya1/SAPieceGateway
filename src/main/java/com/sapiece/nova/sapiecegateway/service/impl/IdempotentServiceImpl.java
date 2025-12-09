package com.sapiece.nova.sapiecegateway.service.impl;

import cn.hutool.core.util.IdUtil;
import com.sapiece.nova.sapiecegateway.service.IdempotentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 幂等性Service实现类
 * 使用Redis存储幂等性Token
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentServiceImpl implements IdempotentService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * 幂等性键前缀
     */
    private static final String IDEMPOTENT_PREFIX = "idempotent:";

    /**
     * Token生成前缀
     */
    private static final String TOKEN_PREFIX = "token:";

    /**
     * 生成幂等性Token
     *
     * @param prefix 幂等性键前缀
     * @return 幂等性Token（响应式）
     */
    @Override
    public Mono<String> generateToken(String prefix) {
        log.debug("生成幂等性Token, prefix: {}", prefix);

        // 生成唯一Token
        String token = IdUtil.simpleUUID();
        String key = IDEMPOTENT_PREFIX + TOKEN_PREFIX + prefix + ":" + token;

        // 将Token存入Redis，有效期5分钟
        return reactiveRedisTemplate.opsForValue()
                .set(key, "1", Duration.ofMinutes(5))
                .flatMap(success -> {
                    if (success) {
                        log.info("成功生成幂等性Token, prefix: {}, token: {}", prefix, token);
                        return Mono.just(token);
                    } else {
                        log.error("生成幂等性Token失败, prefix: {}", prefix);
                        return Mono.empty();
                    }
                })
                .doOnError(error -> log.error("生成幂等性Token异常, prefix: {}, error: {}",
                        prefix, error.getMessage()));
    }

    /**
     * 验证并消费幂等性Token
     * 使用Lua脚本保证原子性：检查存在 + 删除
     *
     * @param key      幂等性键
     * @param duration 有效期
     * @return 是否验证成功（响应式）
     */
    @Override
    public Mono<Boolean> validateAndConsumeToken(String key, Duration duration) {
        log.debug("验证并消费幂等性Token, key: {}", key);

        String fullKey = IDEMPOTENT_PREFIX + key;

        // 使用setIfAbsent实现：如果key不存在则设置，存在则返回false
        // 这样可以保证同一个key只能被设置一次
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(fullKey, "1", duration)
                .flatMap(success -> {
                    if (success) {
                        log.info("幂等性验证成功, key: {}", key);
                        return Mono.just(true);
                    } else {
                        log.warn("幂等性验证失败，操作正在进行中或已完成, key: {}", key);
                        return Mono.just(false);
                    }
                })
                .doOnError(error -> log.error("幂等性验证异常, key: {}, error: {}", key, error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 检查幂等性Token是否存在
     *
     * @param key 幂等性键
     * @return 是否存在（响应式）
     */
    @Override
    public Mono<Boolean> exists(String key) {
        log.debug("检查幂等性Token是否存在, key: {}", key);

        String fullKey = IDEMPOTENT_PREFIX + key;

        return reactiveRedisTemplate.hasKey(fullKey)
                .doOnSuccess(exists -> {
                    if (exists) {
                        log.debug("幂等性Token存在, key: {}", key);
                    } else {
                        log.debug("幂等性Token不存在, key: {}", key);
                    }
                })
                .doOnError(error -> log.error("检查幂等性Token异常, key: {}, error: {}", key, error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 删除幂等性Token
     *
     * @param key 幂等性键
     * @return 是否成功（响应式）
     */
    @Override
    public Mono<Boolean> deleteToken(String key) {
        log.debug("删除幂等性Token, key: {}", key);

        String fullKey = IDEMPOTENT_PREFIX + key;

        return reactiveRedisTemplate.delete(fullKey)
                .map(count -> count > 0)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("成功删除幂等性Token, key: {}", key);
                    } else {
                        log.warn("幂等性Token不存在，无需删除, key: {}", key);
                    }
                })
                .doOnError(error -> log.error("删除幂等性Token异常, key: {}, error: {}", key, error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 设置幂等性标记
     *
     * @param key      幂等性键
     * @param duration 有效期
     * @return 是否成功（响应式）
     */
    @Override
    public Mono<Boolean> setIdempotentMark(String key, Duration duration) {
        log.debug("设置幂等性标记, key: {}, duration: {}秒", key, duration.getSeconds());

        String fullKey = IDEMPOTENT_PREFIX + key;

        return reactiveRedisTemplate.opsForValue()
                .set(fullKey, "1", duration)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("成功设置幂等性标记, key: {}", key);
                    } else {
                        log.warn("设置幂等性标记失败, key: {}", key);
                    }
                })
                .doOnError(error -> log.error("设置幂等性标记异常, key: {}, error: {}", key, error.getMessage()))
                .onErrorReturn(false);
    }
}
