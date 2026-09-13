package com.sapiece.nova.sapiecegateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoutePermissionServiceTest {

    @Test
    void remoteRouteChangeRebuildsSpringGatewayRoutes() {
        SysGatewayRouteRepository routeRepository = mock(SysGatewayRouteRepository.class);
        @SuppressWarnings("unchecked")
        ReactiveRedisTemplate<String, String> redisTemplate = mock(ReactiveRedisTemplate.class);
        DatabaseRouteDefinitionRepository definitionRepository = mock(DatabaseRouteDefinitionRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        RoutePermissionService service = new RoutePermissionService(
                routeRepository, new ObjectMapper(), redisTemplate, definitionRepository, eventPublisher);

        service.handleRouteChange();

        verify(definitionRepository).invalidateCache();
        verify(eventPublisher).publishEvent(isA(RefreshRoutesEvent.class));
    }
}
