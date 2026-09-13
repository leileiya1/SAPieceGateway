package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.service.TokenBlacklistService;
import com.sapiece.nova.sapiecegateway.service.UserPermissionCacheService;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtSecurityTest {
    private JwtUtil jwt;
    private TokenBlacklistService blacklist;
    private UserPermissionCacheService permissions;
    private CustomReactiveUserDetailsService users;
    private JwtSecurityContextRepository repository;

    @BeforeEach void setup() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "test-only-key-abcdefghijklmnopqrstuvwxyz-0123456789-abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(jwt, "accessTokenExpiration", 1800000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpiration", 604800000L);
        blacklist = mock(TokenBlacklistService.class);
        permissions = mock(UserPermissionCacheService.class);
        users = mock(CustomReactiveUserDetailsService.class);
        repository = new JwtSecurityContextRepository(jwt, users, blacklist, permissions);
    }
    private MockServerWebExchange request(String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/private")
                .header("Authorization", "Bearer " + token));
    }
    private String acceptedToken() {
        String token = jwt.generateAccessToken(42L, "tester", 10L);
        when(blacklist.isBlacklisted(token)).thenReturn(Mono.just(false));
        when(blacklist.isUserBlacklisted(42L)).thenReturn(Mono.just(false));
        when(permissions.getPwdVer(42L)).thenReturn(Mono.just(10L));
        when(permissions.getUserRoles(42L)).thenReturn(Mono.just(List.of("USER")));
        when(permissions.getUserPermissions(42L)).thenReturn(Mono.just(List.of("read")));
        return token;
    }
    @Test void validAccessTokenAuthenticatesFromCache() {
        String token = acceptedToken();
        assertTrue(jwt.isAccessToken(token));
        StepVerifier.create(repository.load(request(token))).assertNext(context -> {
            assertTrue(context.getAuthentication().isAuthenticated());
            assertEquals("tester", context.getAuthentication().getName());
        }).verifyComplete();
        verifyNoInteractions(users);
    }
    @Test void refreshTokenCannotAuthenticateRequests() {
        String token = jwt.generateRefreshToken(42L, "tester");
        assertFalse(jwt.isAccessToken(token));
        StepVerifier.create(repository.load(request(token))).verifyComplete();
        verifyNoInteractions(blacklist, permissions, users);
    }
    @Test void malformedTokenIsNeverAnAccessToken() {
        assertFalse(jwt.isAccessToken("invalid"));
        StepVerifier.create(repository.load(request("invalid"))).verifyComplete();
    }
    @Test void blacklistedTokenCannotContinueToUserLookup() {
        String token = jwt.generateAccessToken(42L, "tester");
        when(blacklist.isBlacklisted(token)).thenReturn(Mono.just(true));
        StepVerifier.create(repository.load(request(token))).verifyComplete();
        verify(blacklist, never()).isUserBlacklisted(anyLong());
        verifyNoInteractions(permissions, users);
    }
    @Test void oldPasswordVersionIsRejected() {
        String token = acceptedToken();
        when(permissions.getPwdVer(42L)).thenReturn(Mono.just(11L));
        StepVerifier.create(repository.load(request(token))).verifyComplete();
        verifyNoInteractions(users);
    }
    @Test void disabledAccountIsRejectedOnDatabaseFallback() {
        String token = acceptedToken();
        when(permissions.getUserRoles(42L)).thenReturn(Mono.just(List.of()));
        when(permissions.getUserPermissions(42L)).thenReturn(Mono.just(List.of()));
        CustomUserDetails disabled = new CustomUserDetails();
        disabled.setEnabled(false);
        when(users.findByUserId(42L)).thenReturn(Mono.just(disabled));
        StepVerifier.create(repository.load(request(token))).verifyComplete();
    }
    @Test void redisFailureDoesNotAuthenticate() {
        String token = acceptedToken();
        when(blacklist.isBlacklisted(token)).thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        StepVerifier.create(repository.load(request(token))).verifyComplete();
    }
}
