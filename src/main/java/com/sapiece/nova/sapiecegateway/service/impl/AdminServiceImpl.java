package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.filter.IpBlackWhiteListFilter;
import com.sapiece.nova.sapiecegateway.service.AdminService;
import com.sapiece.nova.sapiecegateway.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 管理员服务实现类
 * 处理IP黑白名单、Token黑名单、用户黑名单等管理功能
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final IpBlackWhiteListFilter ipFilter;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public List<String> getIpBlacklist() {
        log.debug("获取IP黑名单列表");
        return ipFilter.getBlacklist();
    }

    @Override
    public void addToIpBlacklist(String ip) {
        log.info("添加IP到黑名单, ip: {}", ip);
        ipFilter.addToBlacklist(ip);
    }

    @Override
    public void removeFromIpBlacklist(String ip) {
        log.info("从黑名单中移除IP, ip: {}", ip);
        ipFilter.removeFromBlacklist(ip);
    }

    @Override
    public List<String> getIpWhitelist() {
        log.debug("获取IP白名单列表");
        return ipFilter.getWhitelist();
    }

    @Override
    public void addToIpWhitelist(String ip) {
        log.info("添加IP到白名单, ip: {}", ip);
        ipFilter.addToWhitelist(ip);
    }

    @Override
    public void removeFromIpWhitelist(String ip) {
        log.info("从白名单中移除IP, ip: {}", ip);
        ipFilter.removeFromWhitelist(ip);
    }

    @Override
    public Mono<Boolean> addTokenToBlacklist(String token, Long durationHours) {
        log.info("将Token加入黑名单, duration: {}小时", durationHours);
        Duration duration = Duration.ofHours(durationHours);
        return tokenBlacklistService.addToBlacklist(token, duration)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Token加入黑名单成功");
                    } else {
                        log.error("Token加入黑名单失败");
                    }
                });
    }

    @Override
    public Mono<Boolean> removeTokenFromBlacklist(String token) {
        log.info("从黑名单中移除Token");
        return tokenBlacklistService.removeFromBlacklist(token)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("Token从黑名单移除成功");
                    } else {
                        log.warn("Token不在黑名单中");
                    }
                });
    }

    @Override
    public Mono<Boolean> isTokenBlacklisted(String token) {
        log.debug("检查Token是否在黑名单中");
        return tokenBlacklistService.isBlacklisted(token);
    }

    @Override
    public Mono<Boolean> addUserToBlacklist(Long userId, Long durationHours) {
        log.info("将用户加入黑名单, userId: {}, duration: {}小时", userId, durationHours);
        Duration duration = Duration.ofHours(durationHours);
        return tokenBlacklistService.addUserToBlacklist(userId, duration)
                .doOnSuccess(success -> {
                    if (success) {
                        log.info("用户加入黑名单成功, userId: {}", userId);
                    } else {
                        log.error("用户加入黑名单失败, userId: {}", userId);
                    }
                });
    }

    @Override
    public Mono<Boolean> isUserBlacklisted(Long userId) {
        log.debug("检查用户是否在黑名单中, userId: {}", userId);
        return tokenBlacklistService.isUserBlacklisted(userId);
    }
}
