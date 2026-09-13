package com.sapiece.nova.sapiecegateway.route;

import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import com.sapiece.nova.sapiecegateway.route.impl.DynamicRouteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicRouteServiceRobustnessTest {

    private SysGatewayRouteRepository repository;
    private DynamicRouteServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(SysGatewayRouteRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        DatabaseRouteDefinitionRepository definitions = mock(DatabaseRouteDefinitionRepository.class);
        RouteDefinitionConverter converter = mock(RouteDefinitionConverter.class);
        when(redis.convertAndSend(any(), any())).thenReturn(Mono.just(0L));
        service = new DynamicRouteServiceImpl(repository, events, redis, definitions, converter);
    }

    @Test
    void rejectsUnsafeUriBeforeDatabaseWrite() {
        SysGatewayRoute route = validRoute();
        route.setUri("file:///etc/passwd");

        StepVerifier.create(Mono.defer(() -> service.addRoute(route)))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("URI"))
                .verify();
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidOperationalLimits() {
        SysGatewayRoute route = validRoute();
        route.setRateLimitQps(0);

        StepVerifier.create(Mono.defer(() -> service.addRoute(route)))
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && error.getMessage().contains("QPS"))
                .verify();
    }

    @Test
    void databaseSaveCompletesOnlyAfterRefreshPipeline() {
        SysGatewayRoute route = validRoute();
        when(repository.existsByRouteId(route.getRouteId())).thenReturn(Mono.just(false));
        when(repository.save(route)).thenReturn(Mono.just(route));

        StepVerifier.create(service.addRoute(route)).expectNext(route).verifyComplete();
        verify(repository).save(route);
    }

    private static SysGatewayRoute validRoute() {
        SysGatewayRoute route = new SysGatewayRoute();
        route.setRouteId("integration-route");
        route.setUri("http://example.internal:8080");
        route.setPredicates("[{\"name\":\"Path\",\"args\":{\"pattern\":\"/api/**\"}}]");
        route.setRateLimitQps(10);
        route.setCacheTtl(60);
        route.setRetryTimes(1);
        route.setTimeoutMs(1000);
        return route;
    }
}
