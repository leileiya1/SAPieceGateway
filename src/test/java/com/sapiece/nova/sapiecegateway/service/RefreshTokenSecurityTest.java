package com.sapiece.nova.sapiecegateway.service;
import com.sapiece.nova.sapiecegateway.entity.SysUser;
import com.sapiece.nova.sapiecegateway.exception.BusinessException;
import com.sapiece.nova.sapiecegateway.service.impl.AuthServiceImpl;
import com.sapiece.nova.sapiecegateway.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTokenSecurityTest {
    private JwtUtil jwt;
    private SysUserService users;
    private SysRoleService roles;
    private SysMenuService menus;
    private TokenBlacklistService blacklist;
    private UserPermissionCacheService cache;
    private AuthServiceImpl auth;
    private SysUser user;
    @BeforeEach void setup() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt,"secret","test-only-key-abcdefghijklmnopqrstuvwxyz-0123456789-abcdefghijklmnopqrstuvwxyz");
        ReflectionTestUtils.setField(jwt,"accessTokenExpiration",1800000L);
        ReflectionTestUtils.setField(jwt,"refreshTokenExpiration",604800000L);
        users=mock(SysUserService.class); roles=mock(SysRoleService.class); menus=mock(SysMenuService.class);
        blacklist=mock(TokenBlacklistService.class); cache=mock(UserPermissionCacheService.class);
        auth = new AuthServiceImpl(users,roles,menus,jwt,mock(PasswordEncoder.class),blacklist,cache,mock(AuditLogService.class));
        user = new SysUser(); user.setId(42L); user.setUserName("tester"); user.setStatus(1); user.setDelFlag(0);
        when(users.findById(42L)).thenReturn(Mono.just(user));
        when(blacklist.isUserBlacklisted(42L)).thenReturn(Mono.just(false));
        when(roles.findRoleCodesByUserId(42L)).thenReturn(Flux.just("USER"));
        when(menus.findPermissionCodesByUserId(42L)).thenReturn(Flux.just("read"));
        when(cache.cacheUserPermissions(anyLong(),anyList(),anyList())).thenReturn(Mono.just(true));
        when(cache.cachePwdVer(anyLong(),anyLong())).thenReturn(Mono.just(true));
    }
    @Test void oldRefreshEndpointCannotBypassRevocation() {
        StepVerifier.create(auth.refreshToken(jwt.generateAccessToken(42L,"tester")))
                .expectError(BusinessException.class).verify();
        verifyNoInteractions(users,blacklist);
    }
    @Test void accessTokenCannotBeUsedAsRefreshToken() {
        StepVerifier.create(auth.refreshAccessToken(jwt.generateAccessToken(42L,"tester")))
                .expectError(BusinessException.class).verify();
        verifyNoInteractions(users,blacklist);
    }
    @Test void disabledAccountCannotRefresh() {
        user.setStatus(0);
        StepVerifier.create(auth.refreshAccessToken(jwt.generateRefreshToken(42L,"tester")))
                .expectError(BusinessException.class).verify();
        verify(blacklist,never()).consumeRefreshToken(anyString(),any());
    }
    @Test void passwordChangeInvalidatesOldRefreshToken() {
        String token=jwt.generateRefreshToken(42L,"tester");
        user.setPasswordLastChangedAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(2));
        StepVerifier.create(auth.refreshAccessToken(token)).expectError(BusinessException.class).verify();
        verify(blacklist,never()).consumeRefreshToken(anyString(),any());
    }
    @Test void onlyWinnerOfAtomicConsumptionGetsNewTokens() {
        String token=jwt.generateRefreshToken(42L,"tester");
        AtomicBoolean consumed=new AtomicBoolean();
        when(blacklist.consumeRefreshToken(eq(token),any())).thenAnswer(invocation ->
                Mono.fromSupplier(() -> consumed.compareAndSet(false,true)));
        StepVerifier.create(auth.refreshAccessToken(token)).assertNext(pair -> {
            assertTrue(jwt.isAccessToken((String)pair.get("accessToken")));
            assertTrue(jwt.isRefreshToken((String)pair.get("refreshToken")));
            assertNotEquals(token,pair.get("refreshToken"));
        }).verifyComplete();
        StepVerifier.create(auth.refreshAccessToken(token)).expectError(BusinessException.class).verify();
        verify(cache,times(1)).cacheUserPermissions(anyLong(),anyList(),anyList());
    }
    @Test void redisFailureCannotIssueNewTokens() {
        when(blacklist.consumeRefreshToken(anyString(),any())).thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        StepVerifier.create(auth.refreshAccessToken(jwt.generateRefreshToken(42L,"tester")))
                .expectError(IllegalStateException.class).verify();
        verifyNoInteractions(cache);
    }
}
