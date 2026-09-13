package com.sapiece.nova.sapiecegateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthStateServiceTest {

    private ReactiveValueOperations<String, String> values;
    private OAuthStateService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new OAuthStateService(redis);
    }

    @Test
    void issuedStateIsStoredWithProviderAndShortTtl() {
        when(values.set(any(), eq("github"), eq(Duration.ofMinutes(10)))).thenReturn(Mono.just(true));

        StepVerifier.create(service.issue("GitHub"))
                .assertNext(state -> {
                    assert state.matches("[a-f0-9]{32}");
                    verify(values).set(eq("oauth:state:" + state), eq("github"), eq(Duration.ofMinutes(10)));
                })
                .verifyComplete();
    }

    @Test
    void stateIsConsumedAtomicallyAndCannotBeReplayed() {
        String state = "0123456789abcdef0123456789abcdef";
        when(values.getAndDelete("oauth:state:" + state))
                .thenReturn(Mono.just("github"))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.consume("github", state)).verifyComplete();
        StepVerifier.create(service.consume("github", state))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("无效或已过期"))
                .verify();
    }

    @Test
    void stateCannotBeUsedForAnotherProvider() {
        String state = "abcdef0123456789abcdef0123456789";
        when(values.getAndDelete("oauth:state:" + state)).thenReturn(Mono.just("github"));

        StepVerifier.create(service.consume("gitee", state))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
