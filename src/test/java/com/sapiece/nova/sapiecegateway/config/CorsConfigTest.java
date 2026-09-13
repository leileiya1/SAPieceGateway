package com.sapiece.nova.sapiecegateway.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;
class CorsConfigTest {
    @Test void permitsOnlyConfiguredOrigin() {
        var filter = new CorsConfig().corsWebFilter("https://app.example.com");
        var allowed = MockServerWebExchange.from(MockServerHttpRequest.options("http://localhost/private")
                .header("Origin", "https://app.example.com")
                .header("Access-Control-Request-Method", "POST"));
        StepVerifier.create(filter.filter(allowed, exchange -> Mono.error(new AssertionError("preflight reached auth")))).verifyComplete();
        assertEquals("https://app.example.com", allowed.getResponse().getHeaders().getAccessControlAllowOrigin());
        var denied = MockServerWebExchange.from(MockServerHttpRequest.options("http://localhost/private")
                .header("Origin", "https://attacker.example.com")
                .header("Access-Control-Request-Method", "POST"));
        StepVerifier.create(filter.filter(denied, exchange -> Mono.empty())).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, denied.getResponse().getStatusCode());
        assertNull(denied.getResponse().getHeaders().getAccessControlAllowOrigin());
    }
    @Test void credentialedWildcardIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CorsConfig().corsWebFilter("*"));
    }
}
