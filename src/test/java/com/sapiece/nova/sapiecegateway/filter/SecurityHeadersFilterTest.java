package com.sapiece.nova.sapiecegateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void writesHeadersBeforeAnHttpResponseIsCommitted() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("http://localhost/auth/info"));

        StepVerifier.create(filter.filter(exchange, current -> current.getResponse().setComplete()))
                .verifyComplete();

        var headers = exchange.getResponse().getHeaders();
        assertEquals("default-src 'none'; frame-ancestors 'none'",
                headers.getFirst("Content-Security-Policy"));
        assertEquals("no-store, no-cache, must-revalidate", headers.getFirst("Cache-Control"));
        assertNull(headers.getFirst("Strict-Transport-Security"));
    }

    @Test
    void addsHstsOnlyForHttpsAndAllowsSwaggerAssetsFromSelf() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("https://gateway.example/swagger-ui.html"));

        StepVerifier.create(filter.filter(exchange, current -> current.getResponse().setComplete()))
                .verifyComplete();

        var headers = exchange.getResponse().getHeaders();
        assertEquals("max-age=31536000; includeSubDomains",
                headers.getFirst("Strict-Transport-Security"));
        assertEquals("default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'",
                headers.getFirst("Content-Security-Policy"));
    }
}
