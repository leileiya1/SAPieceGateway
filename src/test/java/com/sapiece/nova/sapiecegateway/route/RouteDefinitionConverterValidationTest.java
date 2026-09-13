package com.sapiece.nova.sapiecegateway.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.discovery.KubernetesDnsDiscoveryProperties;
import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteDefinitionConverterValidationTest {

    private final KubernetesDnsDiscoveryProperties kubernetesDns = new KubernetesDnsDiscoveryProperties();
    private final RouteDefinitionConverter converter = new RouteDefinitionConverter(new ObjectMapper(), kubernetesDns);

    @Test
    void rejectsMalformedPredicateJsonInsteadOfSilentlyCreatingAnEmptyRoute() {
        SysGatewayRoute route = route("not-json");
        assertThrows(IllegalArgumentException.class, () -> converter.convert(route));
    }

    @Test
    void rejectsAnEmptyPredicateArray() {
        SysGatewayRoute route = route("[]");
        assertThrows(IllegalArgumentException.class, () -> converter.convert(route));
    }

    @Test
    void rewritesLbRouteToStableKubernetesServiceDns() {
        kubernetesDns.setEnabled(true);
        kubernetesDns.setNamespace("sapiece");
        SysGatewayRoute route = route("[{\"name\":\"Path\",\"args\":{\"pattern\":\"/orders/**\"}}]");
        route.setUri("lb://order-service/api?version=1");

        assertEquals("http://order-service.sapiece.svc.cluster.local:80/api?version=1",
                converter.convert(route).getUri().toString());
    }

    @Test
    void rejectsLbRouteWhenKubernetesDnsIsDisabled() {
        SysGatewayRoute route = route("[{\"name\":\"Path\",\"args\":{\"pattern\":\"/orders/**\"}}]");
        route.setUri("lb://order-service");

        assertThrows(IllegalArgumentException.class, () -> converter.convert(route));
    }

    private static SysGatewayRoute route(String predicates) {
        SysGatewayRoute route = new SysGatewayRoute();
        route.setRouteId("validation-test");
        route.setUri("http://example.internal");
        route.setPredicates(predicates);
        return route;
    }
}
