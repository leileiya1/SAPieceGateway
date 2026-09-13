package com.sapiece.nova.sapiecegateway.route;
import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.repository.SysGatewayRouteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
class DatabaseRouteDefinitionRepositoryTest {
    @Test void malformedRouteDoesNotDiscardOtherRoutes() {
        var store = mock(SysGatewayRouteRepository.class);
        var converter = mock(RouteDefinitionConverter.class);
        var broken = new SysGatewayRoute(); broken.setRouteId("broken");
        var valid = new SysGatewayRoute(); valid.setRouteId("valid");
        var definition = new RouteDefinition(); definition.setId("valid");
        when(store.findAllEnabled()).thenReturn(Flux.just(broken, valid));
        when(converter.convert(broken)).thenThrow(new IllegalArgumentException("invalid predicate"));
        when(converter.convert(valid)).thenReturn(definition);
        var repository = new DatabaseRouteDefinitionRepository(store, converter);
        StepVerifier.create(repository.getRouteDefinitions()).expectNext(definition).verifyComplete();
        StepVerifier.create(repository.getRouteDefinitions()).expectNext(definition).verifyComplete();
        verify(store, times(1)).findAllEnabled();
        repository.invalidateCache();
        StepVerifier.create(repository.getRouteDefinitions()).expectNext(definition).verifyComplete();
        verify(store, times(2)).findAllEnabled();
    }
}
