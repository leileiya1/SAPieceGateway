package com.sapiece.nova.sapiecegateway.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpUtilTest {

    @Test
    void ignoresForwardedHeadersFromAnUntrustedClient() {
        var request = MockServerHttpRequest.get("http://gateway/auth/login")
                .remoteAddress(new InetSocketAddress("192.0.2.44", 12345))
                .header("X-Forwarded-For", "203.0.113.9")
                .build();

        assertEquals("192.0.2.44", IpUtil.extractClientIp(request, List.of("127.0.0.1")));
    }

    @Test
    void acceptsForwardedClientFromATrustedProxy() {
        var request = MockServerHttpRequest.get("http://gateway/auth/login")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                .header("X-Forwarded-For", "203.0.113.9, 127.0.0.1")
                .build();

        assertEquals("203.0.113.9", IpUtil.extractClientIp(request, List.of("127.0.0.1")));
    }
}
