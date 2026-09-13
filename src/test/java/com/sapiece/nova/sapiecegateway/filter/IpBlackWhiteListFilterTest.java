package com.sapiece.nova.sapiecegateway.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IpBlackWhiteListFilterTest {
    @Test
    void matchesExactAddressAndValidCidrBoundaries() {
        assertTrue(IpBlackWhiteListFilter.isIpInList("192.168.1.255", List.of("192.168.1.0/24")));
        assertTrue(IpBlackWhiteListFilter.isIpInList("203.0.113.8", List.of("203.0.113.8")));
        assertFalse(IpBlackWhiteListFilter.isIpInList("192.168.2.1", List.of("192.168.1.0/24")));
        assertFalse(IpBlackWhiteListFilter.isIpInList("192.168.1.1", List.of("192.168.1.0/33", "bad")));
    }
}
