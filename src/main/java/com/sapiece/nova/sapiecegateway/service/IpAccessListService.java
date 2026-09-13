package com.sapiece.nova.sapiecegateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Shared, persistent IP access lists backed by Redis Sets. */
@Slf4j
@Service
public class IpAccessListService {
    static final String BLACKLIST_KEY = "gateway:ip:blacklist";
    static final String WHITELIST_KEY = "gateway:ip:whitelist";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final List<String> configuredBlacklist;
    private final List<String> configuredWhitelist;
    private volatile List<String> lastBlacklist;
    private volatile List<String> lastWhitelist;

    public IpAccessListService(ReactiveStringRedisTemplate redisTemplate,
                               @Value("${ip-filter.blacklist:}") List<String> configuredBlacklist,
                               @Value("${ip-filter.whitelist:}") List<String> configuredWhitelist) {
        this.redisTemplate = redisTemplate;
        this.configuredBlacklist = normalized(configuredBlacklist);
        this.configuredWhitelist = normalized(configuredWhitelist);
        this.lastBlacklist = this.configuredBlacklist;
        this.lastWhitelist = this.configuredWhitelist;
    }

    public Mono<List<String>> getBlacklist() { return read(BLACKLIST_KEY, configuredBlacklist, true); }
    public Mono<List<String>> getWhitelist() { return read(WHITELIST_KEY, configuredWhitelist, false); }
    public Mono<Boolean> addToBlacklist(String value) { return add(BLACKLIST_KEY, value); }
    public Mono<Boolean> removeFromBlacklist(String value) { return remove(BLACKLIST_KEY, value); }
    public Mono<Boolean> addToWhitelist(String value) { return add(WHITELIST_KEY, value); }
    public Mono<Boolean> removeFromWhitelist(String value) { return remove(WHITELIST_KEY, value); }

    private Mono<List<String>> read(String key, List<String> configured, boolean blacklist) {
        return redisTemplate.opsForSet().members(key).collectList().map(dynamic -> {
            Set<String> merged = new LinkedHashSet<>(configured);
            merged.addAll(normalized(dynamic));
            List<String> snapshot = List.copyOf(merged);
            if (blacklist) lastBlacklist = snapshot; else lastWhitelist = snapshot;
            return snapshot;
        }).onErrorResume(error -> {
            log.warn("读取Redis IP名单失败，使用最近快照, key={}, error={}", key, error.getMessage());
            return Mono.just(blacklist ? lastBlacklist : lastWhitelist);
        });
    }

    private Mono<Boolean> add(String key, String value) {
        return redisTemplate.opsForSet().add(key, value.trim()).map(count -> count > 0);
    }

    private Mono<Boolean> remove(String key, String value) {
        return redisTemplate.opsForSet().remove(key, value.trim()).map(count -> count > 0);
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : values) if (value != null && !value.isBlank()) result.add(value.trim());
        return List.copyOf(result);
    }
}
