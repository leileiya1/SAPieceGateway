package com.sapiece.nova.sapiecegateway.service.impl;

import cn.hutool.core.net.Ipv4Util;
import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.entity.SysGrayRule;
import com.sapiece.nova.sapiecegateway.repository.SysGrayRuleRepository;
import com.sapiece.nova.sapiecegateway.service.GrayRuleService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灰度发布Service实现类
 * 实现灰度规则管理和匹配功能
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrayRuleServiceImpl implements GrayRuleService {

    private final SysGrayRuleRepository grayRuleRepository;
    private final ObjectMapper objectMapper;

    /**
     * 灰度规则缓存
     * Key: serviceId, Value: 该服务的灰度规则列表
     */
    private final ConcurrentHashMap<String, List<SysGrayRule>> ruleCache = new ConcurrentHashMap<>();

    /**
     * 初始化时加载灰度规则到缓存
     */
    @PostConstruct
    public void init() {
        log.info("初始化灰度规则缓存...");
        refreshCache().subscribe(
                null,
                error -> log.error("初始化灰度规则缓存失败: {}", error.getMessage()),
                () -> log.info("灰度规则缓存初始化完成")
        );
    }

    // ==================== 规则匹配方法 ====================

    /**
     * 判断请求是否命中灰度规则
     */
    @Override
    public Mono<SysGrayRule> matchGrayRule(String serviceId, Long userId, String clientIp,
                                            String headerValue, String paramValue) {
        log.debug("匹配灰度规则, serviceId: {}, userId: {}, clientIp: {}", serviceId, userId, clientIp);

        return getValidRulesByServiceId(serviceId)
                .filter(rule -> matchRule(rule, userId, clientIp, headerValue, paramValue))
                .next() // 返回第一个匹配的规则（按优先级排序）
                .doOnSuccess(rule -> {
                    if (rule != null) {
                        log.info("命中灰度规则, serviceId: {}, ruleCode: {}, strategyType: {}",
                                serviceId, rule.getRuleCode(), rule.getStrategyType());
                    } else {
                        log.debug("未命中灰度规则, serviceId: {}", serviceId);
                    }
                });
    }

    /**
     * 获取灰度目标URI
     */
    @Override
    public Mono<String> getGrayTargetUri(String serviceId, Long userId, String clientIp,
                                          String headerValue, String paramValue) {
        return matchGrayRule(serviceId, userId, clientIp, headerValue, paramValue)
                .map(SysGrayRule::getTargetUri);
    }

    /**
     * 判断请求是否匹配指定规则
     *
     * @param rule        灰度规则
     * @param userId      用户ID
     * @param clientIp    客户端IP
     * @param headerValue Header值
     * @param paramValue  参数值
     * @return 是否匹配
     */
    private boolean matchRule(SysGrayRule rule, Long userId, String clientIp,
                               String headerValue, String paramValue) {
        // 检查规则是否有效
        if (!rule.isValid()) {
            return false;
        }

        String strategyType = rule.getStrategyType();
        String configJson = rule.getStrategyConfig();

        try {
            switch (strategyType) {
                case SysGrayRule.StrategyType.USER_ID:
                    return matchByUserId(configJson, userId);

                case SysGrayRule.StrategyType.IP:
                    return matchByIp(configJson, clientIp);

                case SysGrayRule.StrategyType.HEADER:
                    return matchByHeader(configJson, headerValue);

                case SysGrayRule.StrategyType.WEIGHT:
                    return matchByWeight(configJson);

                case SysGrayRule.StrategyType.PARAM:
                    return matchByParam(configJson, paramValue);

                case SysGrayRule.StrategyType.USER_TAG:
                    // 用户标签匹配需要额外查询用户标签，这里暂时返回false
                    log.debug("用户标签匹配策略暂未实现");
                    return false;

                default:
                    log.warn("未知的灰度策略类型: {}", strategyType);
                    return false;
            }
        } catch (Exception e) {
            log.error("灰度规则匹配异常, ruleCode: {}, error: {}", rule.getRuleCode(), e.getMessage());
            return false;
        }
    }

    /**
     * 按用户ID匹配
     * 配置格式：{"userIds": [1, 2, 3, 4, 5]}
     */
    private boolean matchByUserId(String configJson, Long userId) throws JsonProcessingException {
        if (userId == null) {
            return false;
        }

        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<Number> userIds = (List<Number>) config.get("userIds");

        if (userIds == null || userIds.isEmpty()) {
            return false;
        }

        boolean matched = userIds.stream()
                .map(Number::longValue)
                .anyMatch(id -> id.equals(userId));

        log.debug("用户ID匹配结果: {}, userId: {}, configUserIds: {}", matched, userId, userIds);
        return matched;
    }

    /**
     * 按IP匹配
     * 配置格式：{"ips": ["192.168.1.100", "10.0.0.0/8"]}
     */
    private boolean matchByIp(String configJson, String clientIp) throws JsonProcessingException {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }

        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<String> ips = (List<String>) config.get("ips");

        if (ips == null || ips.isEmpty()) {
            return false;
        }

        for (String ipPattern : ips) {
            if (matchIpPattern(clientIp, ipPattern)) {
                log.debug("IP匹配成功, clientIp: {}, pattern: {}", clientIp, ipPattern);
                return true;
            }
        }

        log.debug("IP匹配失败, clientIp: {}", clientIp);
        return false;
    }

    /**
     * 匹配IP模式（支持CIDR格式）
     */
    private boolean matchIpPattern(String clientIp, String pattern) {
        try {
            if (pattern.contains("/")) {
                // CIDR格式，如 192.168.0.0/16
                String[] parts = pattern.split("/");
                String networkIp = parts[0];
                int mask = Integer.parseInt(parts[1]);

                long clientIpLong = Ipv4Util.ipv4ToLong(clientIp);
                long networkIpLong = Ipv4Util.ipv4ToLong(networkIp);
                long maskLong = (0xFFFFFFFFL << (32 - mask)) & 0xFFFFFFFFL;

                return (clientIpLong & maskLong) == (networkIpLong & maskLong);
            } else {
                // 精确匹配
                return clientIp.equals(pattern);
            }
        } catch (Exception e) {
            log.warn("IP匹配异常, clientIp: {}, pattern: {}, error: {}", clientIp, pattern, e.getMessage());
            return false;
        }
    }

    /**
     * 按请求头匹配
     * 配置格式：{"headerName": "X-Gray-Tag", "headerValues": ["beta", "canary"]}
     */
    private boolean matchByHeader(String configJson, String headerValue) throws JsonProcessingException {
        if (headerValue == null || headerValue.isEmpty()) {
            return false;
        }

        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<String> headerValues = (List<String>) config.get("headerValues");

        if (headerValues == null || headerValues.isEmpty()) {
            return false;
        }

        boolean matched = headerValues.contains(headerValue);
        log.debug("Header匹配结果: {}, headerValue: {}", matched, headerValue);
        return matched;
    }

    /**
     * 按比例匹配（随机）
     * 配置格式：{"grayWeight": 10} -- 10%流量走灰度
     */
    private boolean matchByWeight(String configJson) throws JsonProcessingException {
        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        Number grayWeight = (Number) config.get("grayWeight");

        if (grayWeight == null) {
            return false;
        }

        int weight = grayWeight.intValue();
        int random = RandomUtil.randomInt(100);

        boolean matched = random < weight;
        log.debug("比例匹配结果: {}, weight: {}%, random: {}", matched, weight, random);
        return matched;
    }

    /**
     * 按请求参数匹配
     * 配置格式：{"paramName": "version", "paramValues": ["v2", "beta"]}
     */
    private boolean matchByParam(String configJson, String paramValue) throws JsonProcessingException {
        if (paramValue == null || paramValue.isEmpty()) {
            return false;
        }

        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        @SuppressWarnings("unchecked")
        List<String> paramValues = (List<String>) config.get("paramValues");

        if (paramValues == null || paramValues.isEmpty()) {
            return false;
        }

        boolean matched = paramValues.contains(paramValue);
        log.debug("参数匹配结果: {}, paramValue: {}", matched, paramValue);
        return matched;
    }

    // ==================== 规则管理方法 ====================

    /**
     * 获取服务的所有灰度规则
     */
    @Override
    public Flux<SysGrayRule> getRulesByServiceId(String serviceId) {
        log.debug("获取服务灰度规则, serviceId: {}", serviceId);
        return grayRuleRepository.findByServiceId(serviceId);
    }

    /**
     * 获取服务的有效灰度规则
     */
    @Override
    public Flux<SysGrayRule> getValidRulesByServiceId(String serviceId) {
        log.debug("获取服务有效灰度规则, serviceId: {}", serviceId);

        // 先从缓存获取
        List<SysGrayRule> cachedRules = ruleCache.get(serviceId);
        if (cachedRules != null && !cachedRules.isEmpty()) {
            log.debug("从缓存获取灰度规则, serviceId: {}, count: {}", serviceId, cachedRules.size());
            return Flux.fromIterable(cachedRules)
                    .filter(SysGrayRule::isValid);
        }

        // 缓存未命中，从数据库查询
        return grayRuleRepository.findValidRulesByServiceId(serviceId, LocalDateTime.now())
                .doOnComplete(() -> log.debug("从数据库获取灰度规则完成, serviceId: {}", serviceId));
    }

    /**
     * 获取所有灰度规则
     */
    @Override
    public Flux<SysGrayRule> getAllRules() {
        log.debug("获取所有灰度规则");
        return grayRuleRepository.findAll();
    }

    /**
     * 根据ID获取灰度规则
     */
    @Override
    public Mono<SysGrayRule> getById(Long id) {
        log.debug("根据ID获取灰度规则, id: {}", id);
        return grayRuleRepository.findById(id);
    }

    /**
     * 根据规则编码获取灰度规则
     */
    @Override
    public Mono<SysGrayRule> getByRuleCode(String ruleCode) {
        log.debug("根据规则编码获取灰度规则, ruleCode: {}", ruleCode);
        return grayRuleRepository.findByRuleCode(ruleCode);
    }

    /**
     * 新增灰度规则
     */
    @Override
    public Mono<SysGrayRule> addRule(SysGrayRule rule) {
        log.info("新增灰度规则, ruleCode: {}, serviceId: {}", rule.getRuleCode(), rule.getServiceId());

        validateRule(rule, false);

        // 设置默认值
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        if (rule.getStatus() == null) {
            rule.setStatus(SysGrayRule.Status.ENABLED);
        }
        rule.setCreateTime(LocalDateTime.now());
        rule.setUpdateTime(LocalDateTime.now());

        return grayRuleRepository.save(rule)
                .flatMap(saved -> refreshCache().thenReturn(saved))
                .doOnSuccess(saved -> log.info("灰度规则新增成功, id: {}, ruleCode: {}", saved.getId(), saved.getRuleCode()));
    }

    /**
     * 更新灰度规则
     */
    @Override
    public Mono<SysGrayRule> updateRule(Long id, SysGrayRule rule) {
        log.info("更新灰度规则, id: {}", id);

        validateRule(rule, true);

        return grayRuleRepository.findById(id)
                .flatMap(existing -> {
                    // 更新字段
                    if (rule.getRuleName() != null) {
                        existing.setRuleName(rule.getRuleName());
                    }
                    if (rule.getDescription() != null) {
                        existing.setDescription(rule.getDescription());
                    }
                    if (rule.getTargetUri() != null) {
                        existing.setTargetUri(rule.getTargetUri());
                    }
                    if (rule.getStableUri() != null) {
                        existing.setStableUri(rule.getStableUri());
                    }
                    if (rule.getStrategyType() != null) {
                        existing.setStrategyType(rule.getStrategyType());
                    }
                    if (rule.getStrategyConfig() != null) {
                        existing.setStrategyConfig(rule.getStrategyConfig());
                    }
                    if (rule.getPriority() != null) {
                        existing.setPriority(rule.getPriority());
                    }
                    if (rule.getEffectiveStart() != null) {
                        existing.setEffectiveStart(rule.getEffectiveStart());
                    }
                    if (rule.getEffectiveEnd() != null) {
                        existing.setEffectiveEnd(rule.getEffectiveEnd());
                    }
                    existing.setUpdater(rule.getUpdater());
                    existing.setUpdateTime(LocalDateTime.now());

                    return grayRuleRepository.save(existing);
                })
                .flatMap(updated -> refreshCache().thenReturn(updated))
                .doOnSuccess(updated -> log.info("灰度规则更新成功, id: {}, ruleCode: {}", updated.getId(), updated.getRuleCode()));
    }

    /**
     * 删除灰度规则
     */
    @Override
    public Mono<Void> deleteRule(Long id) {
        log.info("删除灰度规则, id: {}", id);

        return grayRuleRepository.deleteById(id)
                .then(refreshCache())
                .doOnSuccess(v -> log.info("灰度规则删除成功, id: {}", id));
    }

    /**
     * 启用灰度规则
     */
    @Override
    public Mono<SysGrayRule> enableRule(Long id) {
        log.info("启用灰度规则, id: {}", id);

        return grayRuleRepository.findById(id)
                .flatMap(rule -> {
                    rule.setStatus(SysGrayRule.Status.ENABLED);
                    rule.setUpdateTime(LocalDateTime.now());
                    return grayRuleRepository.save(rule);
                })
                .flatMap(rule -> refreshCache().thenReturn(rule))
                .doOnSuccess(rule -> log.info("灰度规则启用成功, id: {}, ruleCode: {}", rule.getId(), rule.getRuleCode()));
    }

    /**
     * 禁用灰度规则
     */
    @Override
    public Mono<SysGrayRule> disableRule(Long id) {
        log.info("禁用灰度规则, id: {}", id);

        return grayRuleRepository.findById(id)
                .flatMap(rule -> {
                    rule.setStatus(SysGrayRule.Status.DISABLED);
                    rule.setUpdateTime(LocalDateTime.now());
                    return grayRuleRepository.save(rule);
                })
                .flatMap(rule -> refreshCache().thenReturn(rule))
                .doOnSuccess(rule -> log.info("灰度规则禁用成功, id: {}, ruleCode: {}", rule.getId(), rule.getRuleCode()));
    }

    /**
     * 刷新灰度规则缓存
     */
    @Override
    public Mono<Void> refreshCache() {
        log.info("刷新灰度规则缓存...");

        // 清空缓存
        ruleCache.clear();

        // 重新加载所有启用的规则
        return grayRuleRepository.findAllEnabled()
                .collectList()
                .flatMap(rules -> {
                    // 按服务ID分组
                    rules.forEach(rule -> {
                        ruleCache.computeIfAbsent(rule.getServiceId(), k -> new java.util.ArrayList<>())
                                .add(rule);
                    });

                    log.info("灰度规则缓存刷新完成, 服务数: {}, 规则总数: {}",
                            ruleCache.size(), rules.size());
                    return Mono.empty();
                });
    }

    private void validateRule(SysGrayRule rule, boolean patch) {
        if (rule == null) {
            throw new IllegalArgumentException("灰度规则不能为空");
        }
        if (!patch) {
            if (rule.getRuleCode() == null || !rule.getRuleCode().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
                throw new IllegalArgumentException("规则编码格式不正确");
            }
            if (rule.getServiceId() == null || rule.getServiceId().isBlank()) {
                throw new IllegalArgumentException("服务ID不能为空");
            }
            if (rule.getStrategyType() == null || rule.getStrategyType().isBlank()) {
                throw new IllegalArgumentException("策略类型不能为空");
            }
            if (rule.getStrategyConfig() == null || rule.getStrategyConfig().isBlank()) {
                throw new IllegalArgumentException("策略配置不能为空");
            }
        }
        if (rule.getTargetUri() != null && !(rule.getTargetUri().startsWith("http://")
                || rule.getTargetUri().startsWith("https://") || rule.getTargetUri().startsWith("lb://"))) {
            throw new IllegalArgumentException("灰度目标URI只允许http、https或lb协议");
        }
        if (rule.getStableUri() != null && !(rule.getStableUri().startsWith("http://")
                || rule.getStableUri().startsWith("https://") || rule.getStableUri().startsWith("lb://"))) {
            throw new IllegalArgumentException("稳定版本URI只允许http、https或lb协议");
        }
        if (rule.getStatus() != null && rule.getStatus() != 0 && rule.getStatus() != 1) {
            throw new IllegalArgumentException("灰度规则状态只能是0或1");
        }
        if (rule.getEffectiveStart() != null && rule.getEffectiveEnd() != null
                && rule.getEffectiveStart().isAfter(rule.getEffectiveEnd())) {
            throw new IllegalArgumentException("生效开始时间不能晚于结束时间");
        }
        if (rule.getStrategyConfig() != null) {
            try {
                objectMapper.readTree(rule.getStrategyConfig());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("策略配置必须是有效JSON", e);
            }
        }
    }
}
