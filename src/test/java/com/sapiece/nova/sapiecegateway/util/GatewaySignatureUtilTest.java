package com.sapiece.nova.sapiecegateway.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewaySignatureUtilTest {

    private static final String SECRET = "test-secret-with-more-than-32-bytes";

    @Test
    void signsEverySecurityRelevantRequestAttribute() {
        long timestamp = 1_700_000_000_000L;
        String signature = GatewaySignatureUtil.sign(
                SECRET, timestamp, "42", "POST", "/api/orders", "request-1");

        assertTrue(GatewaySignatureUtil.verify(
                SECRET, timestamp, "42", "POST", "/api/orders", "request-1", signature));
        assertFalse(GatewaySignatureUtil.verify(
                SECRET, timestamp, "42", "GET", "/api/orders", "request-1", signature));
        assertFalse(GatewaySignatureUtil.verify(
                SECRET, timestamp, "42", "POST", "/api/admin", "request-1", signature));
        assertFalse(GatewaySignatureUtil.verify(
                SECRET, timestamp, "42", "POST", "/api/orders", "request-2", signature));
    }

    @Test
    void rejectsBlankSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> GatewaySignatureUtil.sign("", 1L, "0", "GET", "/", "request"));
    }
}
