package com.sapiece.nova.sapiecegateway.filter;

import com.sapiece.nova.sapiecegateway.common.FilterOrders;
import com.sapiece.nova.sapiecegateway.util.IpUtil;
import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * IP黑白名单过滤器（WebFilter版本）
 * 实现基于IP地址的访问控制
 * 支持单个IP、IP段（CIDR）配置
 * WebFilter对所有请求生效（包括本地Controller和路由请求）
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Component
public class IpBlackWhiteListFilter implements WebFilter, Ordered {

    /**
     * IP黑名单（从配置文件读取）
     * 格式：192.168.1.100 或 192.168.1.0/24
     */
    @Value("${ip-filter.blacklist:}")
    private List<String> blacklist;

    /**
     * IP白名单（从配置文件读取）
     * 格式：192.168.1.100 或 192.168.1.0/24
     */
    @Value("${ip-filter.whitelist:}")
    private List<String> whitelist;

    /**
     * 是否启用IP黑名单
     */
    @Value("${ip-filter.blacklist-enabled:false}")
    private Boolean blacklistEnabled;

    /**
     * 是否启用IP白名单
     */
    @Value("${ip-filter.whitelist-enabled:false}")
    private Boolean whitelistEnabled;

    @Value("${trusted-proxies:}")
    private List<String> trustedProxies;

    /**
     * 过滤器优先级（数值越小，优先级越高）
     * 确保在认证过滤器之前执行
     */
    @Override
    public int getOrder() {
        return FilterOrders.IP_BLACK_WHITE_LIST;
    }

    /**
     * 过滤器核心逻辑
     * 检查请求IP是否在黑白名单中
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = IpUtil.extractClientIp(request, trustedProxies);
        String path = request.getPath().value();

        log.debug("IP黑白名单过滤器执行, clientIp: {}, path: {}", clientIp, path);

        // 1. 检查IP白名单（如果启用）
        if (whitelistEnabled && whitelist != null && !whitelist.isEmpty()) {
            boolean inWhitelist = isIpInList(clientIp, whitelist);
            if (!inWhitelist) {
                log.warn("IP不在白名单中，拒绝访问, clientIp: {}, path: {}", clientIp, path);
                return handleIpBlocked(exchange, clientIp, "IP不在白名单中，访问被拒绝");
            }
            log.debug("IP在白名单中，允许访问, clientIp: {}", clientIp);
        }

        // 2. 检查IP黑名单（如果启用）
        if (blacklistEnabled && blacklist != null && !blacklist.isEmpty()) {
            boolean inBlacklist = isIpInList(clientIp, blacklist);
            if (inBlacklist) {
                log.warn("IP在黑名单中，拒绝访问, clientIp: {}, path: {}", clientIp, path);
                return handleIpBlocked(exchange, clientIp, "IP已被封禁，访问被拒绝");
            }
            log.debug("IP不在黑名单中，允许访问, clientIp: {}", clientIp);
        }

        // 3. IP检查通过，继续执行过滤器链
        return chain.filter(exchange);
    }

    /**
     * 检查IP是否在指定列表中
     * 支持单个IP和CIDR格式的IP段
     *
     * @param clientIp 客户端IP
     * @param ipList   IP列表
     * @return 是否在列表中
     */
    private boolean isIpInList(String clientIp, List<String> ipList) {
        if (ipList == null || ipList.isEmpty()) {
            return false;
        }

        for (String ipPattern : ipList) {
            if (ipPattern == null || ipPattern.trim().isEmpty()) {
                continue;
            }

            // 移除空格
            ipPattern = ipPattern.trim();

            // 检查是否为CIDR格式（如：192.168.1.0/24）
            if (ipPattern.contains("/")) {
                if (isIpInCidr(clientIp, ipPattern)) {
                    log.debug("IP匹配CIDR段, clientIp: {}, cidr: {}", clientIp, ipPattern);
                    return true;
                }
            } else {
                // 精确匹配单个IP
                if (clientIp.equals(ipPattern)) {
                    log.debug("IP精确匹配, clientIp: {}, pattern: {}", clientIp, ipPattern);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查IP是否在CIDR范围内
     * 例如：192.168.1.100 是否在 192.168.1.0/24 范围内
     *
     * @param clientIp 客户端IP
     * @param cidr     CIDR格式的IP段（如：192.168.1.0/24）
     * @return 是否在范围内
     */
    private boolean isIpInCidr(String clientIp, String cidr) {
        try {
            String[] cidrParts = cidr.split("/");
            if (cidrParts.length != 2) {
                log.warn("CIDR格式错误: {}", cidr);
                return false;
            }

            String networkIp = cidrParts[0];
            int prefixLength = Integer.parseInt(cidrParts[1]);

            // 将IP地址转换为long类型便于计算
            long clientIpLong = ipToLong(clientIp);
            long networkIpLong = ipToLong(networkIp);

            // 计算网络掩码
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;

            // 判断IP是否在网段内
            return (clientIpLong & mask) == (networkIpLong & mask);
        } catch (Exception e) {
            log.error("检查IP是否在CIDR范围内异常, clientIp: {}, cidr: {}, error: {}",
                    clientIp, cidr, e.getMessage());
            return false;
        }
    }

    /**
     * 将IP地址转换为long类型
     * 例如：192.168.1.100 -> 3232235876
     *
     * @param ipAddress IP地址
     * @return long类型的IP
     */
    private long ipToLong(String ipAddress) {
        String[] ipParts = ipAddress.split("\\.");
        if (ipParts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            int part = Integer.parseInt(ipParts[i]);
            if (part < 0 || part > 255) {
                throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
            }
            result += (long) part << (24 - (8 * i));
        }
        return result;
    }

    // IP提取已统一到 IpUtil.extractClientIp(request, trustedProxies)

    private Mono<Void> handleIpBlocked(ServerWebExchange exchange, String clientIp, String message) {
        log.debug("IP封禁响应, clientIp: {}", clientIp);
        return ResponseUtil.forbidden(exchange, message);
    }

    /**
     * 动态添加IP到黑名单
     * 可用于运行时封禁IP
     *
     * @param ip IP地址或CIDR
     */
    public void addToBlacklist(String ip) {
        if (blacklist == null) {
            blacklist = new ArrayList<>();
        }
        if (!blacklist.contains(ip)) {
            blacklist.add(ip);
            log.info("成功添加IP到黑名单: {}", ip);
        } else {
            log.warn("IP已在黑名单中: {}", ip);
        }
    }

    /**
     * 从黑名单中移除IP
     *
     * @param ip IP地址或CIDR
     */
    public void removeFromBlacklist(String ip) {
        if (blacklist != null && blacklist.remove(ip)) {
            log.info("成功从黑名单中移除IP: {}", ip);
        } else {
            log.warn("IP不在黑名单中: {}", ip);
        }
    }

    /**
     * 动态添加IP到白名单
     *
     * @param ip IP地址或CIDR
     */
    public void addToWhitelist(String ip) {
        if (whitelist == null) {
            whitelist = new ArrayList<>();
        }
        if (!whitelist.contains(ip)) {
            whitelist.add(ip);
            log.info("成功添加IP到白名单: {}", ip);
        } else {
            log.warn("IP已在白名单中: {}", ip);
        }
    }

    /**
     * 从白名单中移除IP
     *
     * @param ip IP地址或CIDR
     */
    public void removeFromWhitelist(String ip) {
        if (whitelist != null && whitelist.remove(ip)) {
            log.info("成功从白名单中移除IP: {}", ip);
        } else {
            log.warn("IP不在白名单中: {}", ip);
        }
    }

    /**
     * 获取当前黑名单
     *
     * @return IP黑名单
     */
    public List<String> getBlacklist() {
        return blacklist != null ? new ArrayList<>(blacklist) : new ArrayList<>();
    }

    /**
     * 获取当前白名单
     *
     * @return IP白名单
     */
    public List<String> getWhitelist() {
        return whitelist != null ? new ArrayList<>(whitelist) : new ArrayList<>();
    }
}
