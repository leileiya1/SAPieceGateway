package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.config.GracefulShutdownHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Small public endpoint for Nginx. Dependency-rich readiness stays on the internal management port. */
@RestController
@RequiredArgsConstructor
public class PublicHealthController {

    private final GracefulShutdownHandler shutdownHandler;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (shutdownHandler.isShuttingDown()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "OUT_OF_SERVICE"));
        }
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
