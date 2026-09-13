package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.ratelimit.RateLimitStrategy;
import com.sapiece.nova.sapiecegateway.service.GatewayMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnhancedRateLimitFilterTest {
    private ReactiveRedisTemplate<String, String> redis;
    private GatewayMetricsService metrics;
    private EnhancedRateLimitFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(ReactiveRedisTemplate.class);
        metrics = mock(GatewayMetricsService.class);
        RateLimitStrategy strategy = mock(RateLimitStrategy.class);
        when(strategy.getStrategyName()).thenReturn("route");
        when(strategy.getRateLimitKey(any())).thenReturn("rate:test");
        when(strategy.getQpsLimit(any())).thenReturn(Mono.just(10));
        when(strategy.getCapacity(any())).thenReturn(Mono.just(20));

        filter = new EnhancedRateLimitFilter(redis, List.of(strategy), metrics);
        ReflectionTestUtils.setField(filter, "rateLimitEnabled", true);
        ReflectionTestUtils.setField(filter, "strategyName", "route");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void downstreamFailureIsPropagatedWithoutRunningChainTwice() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(1L));
        IllegalStateException downstreamFailure = new IllegalStateException("downstream");
        AtomicInteger subscriptions = new AtomicInteger();
        WebFilterChain chain = exchange -> Mono.defer(() -> {
            subscriptions.incrementAndGet();
            return Mono.error(downstreamFailure);
        });

        StepVerifier.create(filter.filter(exchange(), chain))
                .expectErrorMatches(error -> error == downstreamFailure)
                .verify();

        assertEquals(1, subscriptions.get());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void redisFailureFailsOpenAndRunsChainOnce() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new IllegalStateException("redis unavailable")));
        AtomicInteger subscriptions = new AtomicInteger();
        WebFilterChain chain = exchange -> Mono.fromRunnable(subscriptions::incrementAndGet);

        StepVerifier.create(filter.filter(exchange(), chain)).verifyComplete();

        assertEquals(1, subscriptions.get());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectedRequestDoesNotReachDownstreamChain() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn(Flux.just(0L));
        WebFilterChain chain = mock(WebFilterChain.class);
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(429, exchange.getResponse().getStatusCode().value());
        verify(chain, never()).filter(any());
        verify(metrics).recordRateLimitHit("/api/test");
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/test").build());
    }
}
