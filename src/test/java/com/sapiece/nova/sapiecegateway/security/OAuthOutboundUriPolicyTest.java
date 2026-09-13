package com.sapiece.nova.sapiecegateway.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OAuthOutboundUriPolicyTest {
    private final OAuthOutboundUriPolicy policy =
            new OAuthOutboundUriPolicy(List.of("github.com", ".googleapis.com"));

    @Test
    void acceptsExactAndConfiguredSubdomainHosts() {
        assertEquals("github.com", policy.requireAllowed("https://github.com/login/oauth").getHost());
        assertEquals("oauth2.googleapis.com",
                policy.requireAllowed("https://oauth2.googleapis.com/token").getHost());
    }

    @Test
    void rejectsHttpUserInfoAndSuffixConfusion() {
        assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("http://github.com/token"));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("https://github.com@127.0.0.1/token"));
        assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("https://evilgoogleapis.com/token"));
    }
}
