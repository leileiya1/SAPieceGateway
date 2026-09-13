package com.sapiece.nova.sapiecegateway.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/** Issues and atomically consumes short-lived OAuth2 state values. */
@Service
@RequiredArgsConstructor
public class OAuthStateService {

    private static final String PREFIX = "oauth:state:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<String> issue(String provider) {
        String normalizedProvider = normalizeProvider(provider);
        String state = UUID.randomUUID().toString().replace("-", "");
        return redisTemplate.opsForValue().set(PREFIX + state, normalizedProvider, TTL)
                .flatMap(saved -> saved
                        ? Mono.just(state)
                        : Mono.error(new IllegalStateException("OAuth state保存失败")));
    }

    public Mono<Void> consume(String provider, String state) {
        if (state == null || !state.matches("[a-f0-9]{32}")) {
            return Mono.error(new IllegalArgumentException("OAuth state无效或已过期"));
        }
        String normalizedProvider = normalizeProvider(provider);
        return redisTemplate.opsForValue().getAndDelete(PREFIX + state)
                .filter(normalizedProvider::equals)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("OAuth state无效或已过期")))
                .then();
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || !provider.matches("[A-Za-z0-9_-]{1,30}")) {
            throw new IllegalArgumentException("OAuth提供商格式不正确");
        }
        return provider.toLowerCase();
    }
}
