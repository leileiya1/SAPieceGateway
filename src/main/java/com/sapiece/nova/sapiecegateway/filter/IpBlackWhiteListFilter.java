package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.common.FilterOrders;
import com.sapiece.nova.sapiecegateway.service.IpAccessListService;
import com.sapiece.nova.sapiecegateway.util.IpUtil;
import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/** Applies the shared Redis-backed IP allow and deny lists to every request. */
@Slf4j
@Component
public class IpBlackWhiteListFilter implements WebFilter, Ordered {
    private final IpAccessListService accessListService;

    @Value("${ip-filter.blacklist-enabled:false}")
    private boolean blacklistEnabled;
    @Value("${ip-filter.whitelist-enabled:false}")
    private boolean whitelistEnabled;
    @Value("${trusted-proxies:}")
    private List<String> trustedProxies;

    public IpBlackWhiteListFilter(IpAccessListService accessListService) {
        this.accessListService = accessListService;
    }

    @Override
    public int getOrder() { return FilterOrders.IP_BLACK_WHITE_LIST; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientIp = IpUtil.extractClientIp(exchange.getRequest(), trustedProxies);
        String path = exchange.getRequest().getPath().value();
        Mono<Boolean> allowed = whitelistEnabled
                ? accessListService.getWhitelist().map(list -> isIpInList(clientIp, list))
                : Mono.just(true);
        return allowed.flatMap(inWhitelist -> {
            if (!inWhitelist) {
                log.warn("IP不在白名单中，拒绝访问, clientIp={}, path={}", clientIp, path);
                return ResponseUtil.forbidden(exchange, "IP不在白名单中，访问被拒绝");
            }
            Mono<Boolean> denied = blacklistEnabled
                    ? accessListService.getBlacklist().map(list -> isIpInList(clientIp, list))
                    : Mono.just(false);
            return denied.flatMap(inBlacklist -> {
                if (inBlacklist) {
                    log.warn("IP在黑名单中，拒绝访问, clientIp={}, path={}", clientIp, path);
                    return ResponseUtil.forbidden(exchange, "IP已被封禁，访问被拒绝");
                }
                return chain.filter(exchange);
            });
        });
    }

    static boolean isIpInList(String clientIp, List<String> ipList) {
        if (clientIp == null || ipList == null) return false;
        for (String pattern : ipList) {
            if (pattern == null || pattern.isBlank()) continue;
            String value = pattern.trim();
            if (value.contains("/") ? isIpInCidr(clientIp, value) : clientIp.equals(value)) return true;
        }
        return false;
    }

    private static boolean isIpInCidr(String clientIp, String cidr) {
        try {
            String[] parts = cidr.split("/", -1);
            if (parts.length != 2) return false;
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) return false;
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            return (ipToLong(clientIp) & mask) == (ipToLong(parts[0]) & mask);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static long ipToLong(String ipAddress) {
        String[] parts = ipAddress.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("invalid IPv4");
        long result = 0;
        for (String part : parts) {
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) throw new IllegalArgumentException("invalid IPv4");
            result = (result << 8) | value;
        }
        return result;
    }
}
