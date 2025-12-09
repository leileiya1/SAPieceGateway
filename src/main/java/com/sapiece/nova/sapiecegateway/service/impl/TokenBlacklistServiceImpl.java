package com.sapiece.nova.sapiecegateway.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.sapiece.nova.sapiecegateway.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Token黑名单Service实现类
 * 使用Redis存储黑名单，利用Redis的过期机制自动清理
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    /**
     * Token黑名单Key前缀
     */
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 用户黑名单Key前缀
     */
    private static final String USER_BLACKLIST_PREFIX = "user:blacklist:";

    /**
     * 将Token加入黑名单
     *
     * @param token    JWT Token
     * @param duration Token在黑名单中的有效期
     * @return 是否成功（响应式）
     */
    @Override
    public Mono<Boolean> addToBlacklist(String token, Duration duration) {
        log.debug("将Token加入黑名单, duration: {}秒", duration.getSeconds());

        // 对Token进行MD5哈希，避免存储完整Token
        String tokenHash = hashToken(token);
        String key = TOKEN_BLACKLIST_PREFIX + tokenHash;

        return reactiveRedisTemplate.opsForValue()
                .set(key, "1", duration)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("成功将Token加入黑名单, tokenHash: {}, ttl: {}秒", tokenHash, duration.getSeconds());
                    } else {
                        log.warn("Token加入黑名单失败, tokenHash: {}", tokenHash);
                    }
                })
                .doOnError(error -> log.error("Token加入黑名单异常, error: {}", error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 检查Token是否在黑名单中
     *
     * @param token JWT Token
     * @return 是否在黑名单中（响应式）
     */
    @Override
    public Mono<Boolean> isBlacklisted(String token) {
        log.debug("检查Token是否在黑名单中");

        String tokenHash = hashToken(token);
        String key = TOKEN_BLACKLIST_PREFIX + tokenHash;

        return reactiveRedisTemplate.hasKey(key)
                .doOnSuccess(exists -> {
                    if (exists) {
                        log.warn("Token已在黑名单中, tokenHash: {}", tokenHash);
                    } else {
                        log.debug("Token不在黑名单中, tokenHash: {}", tokenHash);
                    }
                })
                .doOnError(error -> log.error("检查Token黑名单异常, error: {}", error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 从黑名单中移除Token
     *
     * @param token JWT Token
     * @return 是否成功（响应式）
     */
    @Override
    public Mono<Boolean> removeFromBlacklist(String token) {
        log.debug("从黑名单中移除Token");

        String tokenHash = hashToken(token);
        String key = TOKEN_BLACKLIST_PREFIX + tokenHash;

        return reactiveRedisTemplate.delete(key)
                .map(count -> count > 0)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("成功从黑名单中移除Token, tokenHash: {}", tokenHash);
                    } else {
                        log.warn("Token不在黑名单中，无需移除, tokenHash: {}", tokenHash);
                    }
                })
                .doOnError(error -> log.error("从黑名单移除Token异常, error: {}", error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 根据用户ID将该用户的所有Token加入黑名单
     *
     * @param userId   用户ID
     * @param duration 黑名单有效期
     * @return 是否成功（响应式）
     */
    @Override
    public Mono<Boolean> addUserToBlacklist(Long userId, Duration duration) {
        log.debug("将用户加入黑名单, userId: {}, duration: {}秒", userId, duration.getSeconds());

        String key = USER_BLACKLIST_PREFIX + userId;

        return reactiveRedisTemplate.opsForValue()
                .set(key, "1", duration)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("成功将用户加入黑名单, userId: {}, ttl: {}秒", userId, duration.getSeconds());
                    } else {
                        log.warn("用户加入黑名单失败, userId: {}", userId);
                    }
                })
                .doOnError(error -> log.error("用户加入黑名单异常, userId: {}, error: {}", userId, error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 检查用户是否在黑名单中
     *
     * @param userId 用户ID
     * @return 是否在黑名单中（响应式）
     */
    @Override
    public Mono<Boolean> isUserBlacklisted(Long userId) {
        log.debug("检查用户是否在黑名单中, userId: {}", userId);

        String key = USER_BLACKLIST_PREFIX + userId;

        return reactiveRedisTemplate.hasKey(key)
                .doOnSuccess(exists -> {
                    if (exists) {
                        log.warn("用户已在黑名单中, userId: {}", userId);
                    } else {
                        log.debug("用户不在黑名单中, userId: {}", userId);
                    }
                })
                .doOnError(error -> log.error("检查用户黑名单异常, userId: {}, error: {}", userId, error.getMessage()))
                .onErrorReturn(false);
    }

    /**
     * 清除过期的黑名单记录
     * 由于使用Redis的过期机制，通常不需要手动清除
     *
     * @return 清除的数量（响应式）
     */
    @Override
    public Mono<Long> clearExpiredTokens() {
        log.info("开始清除过期的黑名单记录（Redis会自动清除过期Key）");
        // Redis会自动清除过期的Key，这里只是提供一个接口
        return Mono.just(0L);
    }

    /**
     * 对Token进行哈希处理
     * 避免在Redis中存储完整的Token，提高安全性
     *
     * @param token JWT Token
     * @return Token的MD5哈希值
     */
    private String hashToken(String token) {
        return DigestUtil.md5Hex(token);
    }
}
