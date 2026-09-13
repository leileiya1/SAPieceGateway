package com.sapiece.nova.sapiecegateway.security;
import com.sapiece.nova.sapiecegateway.service.impl.TokenBlacklistServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
class TokenBlacklistFailureTest {
    @Test @SuppressWarnings("unchecked") void lookupFailureMustNotBecomeNotBlacklisted() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        when(redis.hasKey(anyString())).thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        var service = new TokenBlacklistServiceImpl(redis);
        StepVerifier.create(service.isBlacklisted("token")).expectError(IllegalStateException.class).verify();
        StepVerifier.create(service.isUserBlacklisted(42L)).expectError(IllegalStateException.class).verify();
    }
}
