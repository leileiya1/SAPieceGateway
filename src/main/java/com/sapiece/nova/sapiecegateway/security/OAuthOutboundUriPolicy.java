package com.sapiece.nova.sapiecegateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Prevents database OAuth configuration from turning the gateway into an SSRF proxy. */
@Component
public class OAuthOutboundUriPolicy {
    private final List<String> allowedHosts;

    public OAuthOutboundUriPolicy(@Value("${oauth2.allowed-hosts:}") List<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
    }

    public URI requireAllowed(String rawUri) {
        URI uri;
        try {
            uri = URI.create(rawUri);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("OAuth地址格式不正确");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank()
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("OAuth地址必须是无用户信息的HTTPS地址");
        }
        boolean allowed = allowedHosts.stream().anyMatch(rule ->
                rule.startsWith(".") ? host.endsWith(rule) && host.length() > rule.length() : host.equals(rule));
        if (!allowed) throw new IllegalArgumentException("OAuth目标主机未加入允许列表");
        return uri;
    }
}
