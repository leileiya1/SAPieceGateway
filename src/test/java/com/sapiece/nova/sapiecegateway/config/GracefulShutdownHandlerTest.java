package com.sapiece.nova.sapiecegateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GracefulShutdownHandlerTest {

    @Test
    void tracksConcurrentRequestsWithoutGoingNegative() {
        GracefulShutdownHandler handler = new GracefulShutdownHandler(null, null);
        handler.requestStarted();
        handler.requestStarted();
        assertEquals(2, handler.inFlightRequestCount());

        handler.requestFinished();
        handler.requestFinished();
        handler.requestFinished();
        assertEquals(0, handler.inFlightRequestCount());
    }
}
