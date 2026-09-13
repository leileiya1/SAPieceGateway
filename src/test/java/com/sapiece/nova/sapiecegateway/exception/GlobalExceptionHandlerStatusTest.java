package com.sapiece.nova.sapiecegateway.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerStatusTest {
    @Test
    void mapsDomainCodesToHttpSemantics() {
        assertEquals(HttpStatus.UNAUTHORIZED, GlobalExceptionHandler.statusForBusinessCode(20002));
        assertEquals(HttpStatus.FORBIDDEN, GlobalExceptionHandler.statusForBusinessCode(20010));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, GlobalExceptionHandler.statusForBusinessCode(30001));
        assertEquals(HttpStatus.NOT_FOUND, GlobalExceptionHandler.statusForBusinessCode(40001));
        assertEquals(HttpStatus.BAD_GATEWAY, GlobalExceptionHandler.statusForBusinessCode(40003));
        assertEquals(HttpStatus.CONFLICT, GlobalExceptionHandler.statusForBusinessCode(50006));
        assertEquals(HttpStatus.BAD_REQUEST, GlobalExceptionHandler.statusForBusinessCode(50001));
        assertEquals(HttpStatus.NOT_FOUND, GlobalExceptionHandler.statusForBusinessCode(60002));
    }
}
