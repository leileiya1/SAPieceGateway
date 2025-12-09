package com.sapiece.nova.sapiecegateway.route;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 路由定义转换器
 * 将数据库实体 SysGatewayRoute 转换为 Spring Cloud Gateway 的 RouteDefinition
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteDefinitionConverter {

    private final ObjectMapper objectMapper;

    // ==================== Metadata Key 常量 ====================

    /** 是否需要认证 */
    public static final String META_REQUIRE_AUTH = "requireAuth";
    /** 权限标识 */
    public static final String META_PERMISSION_CODE = "permissionCode";
    /** 权限逻辑 */
    public static final String META_PERMISSION_LOGIC = "permissionLogic";
    /** 是否启用限流 */
    public static final String META_RATE_LIMIT_ENABLED = "rateLimitEnabled";
    /** 限流QPS */
    public static final String META_RATE_LIMIT_QPS = "rateLimitQps";
    /** 限流策略 */
    public static final String META_RATE_LIMIT_STRATEGY = "rateLimitStrategy";
    /** 是否启用缓存 */
    public static final String META_CACHE_ENABLED = "cacheEnabled";
    /** 缓存TTL */
    public static final String META_CACHE_TTL = "cacheTtl";
    /** 路由名称 */
    public static final String META_ROUTE_NAME = "routeName";

    /**
     * 将数据库实体转换为 RouteDefinition
     *
     * @param entity 数据库路由实体
     * @return Spring Cloud Gateway RouteDefinition
     */
    public RouteDefinition convert(SysGatewayRoute entity) {
        RouteDefinition definition = new RouteDefinition();

        // 设置路由ID
        definition.setId(entity.getRouteId());

        // 设置目标URI
        try {
            definition.setUri(URI.create(entity.getUri()));
        } catch (IllegalArgumentException e) {
            log.error("无效的URI格式: routeId={}, uri={}", entity.getRouteId(), entity.getUri());
            throw new IllegalArgumentException("无效的路由URI: " + entity.getUri(), e);
        }

        // 设置路由顺序
        definition.setOrder(entity.getOrderNum() != null ? entity.getOrderNum() : 0);

        // 转换断言配置
        definition.setPredicates(parsePredicates(entity.getPredicates(), entity.getRouteId()));

        // 转换过滤器配置
        definition.setFilters(parseFilters(entity.getFilters(), entity.getRouteId()));

        // 转换元数据（包含权限配置、限流配置等）
        definition.setMetadata(buildMetadata(entity));

        log.debug("路由转换成功: routeId={}, uri={}, predicates={}, filters={}, requireAuth={}",
                entity.getRouteId(), entity.getUri(),
                definition.getPredicates().size(),
                definition.getFilters().size(),
                entity.getRequireAuth());

        return definition;
    }

    /**
     * 构建路由元数据
     * 将权限配置、限流配置、缓存配置等存入 metadata
     *
     * @param entity 数据库实体
     * @return 元数据 Map
     */
    private Map<String, Object> buildMetadata(SysGatewayRoute entity) {
        // 先解析用户自定义的 metadata
        Map<String, Object> metadata = new HashMap<>(parseMetadata(entity.getMetadata(), entity.getRouteId()));

        // 添加路由名称
        if (entity.getRouteName() != null) {
            metadata.put(META_ROUTE_NAME, entity.getRouteName());
        }

        // 添加权限配置
        metadata.put(META_REQUIRE_AUTH, entity.getRequireAuth() != null ? entity.getRequireAuth() : 1);
        if (entity.getPermissionCode() != null && !entity.getPermissionCode().isBlank()) {
            metadata.put(META_PERMISSION_CODE, entity.getPermissionCode());
        }
        metadata.put(META_PERMISSION_LOGIC, entity.getPermissionLogic() != null ? entity.getPermissionLogic() : "OR");

        // 添加限流配置
        metadata.put(META_RATE_LIMIT_ENABLED, entity.getRateLimitEnabled() != null ? entity.getRateLimitEnabled() : 0);
        metadata.put(META_RATE_LIMIT_QPS, entity.getRateLimitQps() != null ? entity.getRateLimitQps() : 100);
        metadata.put(META_RATE_LIMIT_STRATEGY, entity.getRateLimitStrategy() != null ? entity.getRateLimitStrategy() : "ip");

        // 添加缓存配置
        metadata.put(META_CACHE_ENABLED, entity.getCacheEnabled() != null ? entity.getCacheEnabled() : 0);
        metadata.put(META_CACHE_TTL, entity.getCacheTtl() != null ? entity.getCacheTtl() : 300);

        return metadata;
    }

    /**
     * 解析断言配置 JSON
     *
     * @param json    断言 JSON 字符串
     * @param routeId 路由ID（用于日志）
     * @return PredicateDefinition 列表
     */
    private List<PredicateDefinition> parsePredicates(String json, String routeId) {
        if (json == null || json.isBlank()) {
            log.warn("路由断言配置为空: routeId={}", routeId);
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            return list.stream()
                    .map(this::toPredicateDefinition)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            log.error("解析断言配置失败: routeId={}, json={}, error={}",
                    routeId, json, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 将 Map 转换为 PredicateDefinition
     *
     * @param map 断言配置 Map
     * @return PredicateDefinition
     */
    @SuppressWarnings("unchecked")
    private PredicateDefinition toPredicateDefinition(Map<String, Object> map) {
        PredicateDefinition predicate = new PredicateDefinition();

        // 设置断言名称
        String name = (String) map.get("name");
        predicate.setName(name);

        // 设置断言参数
        Object argsObj = map.get("args");
        if (argsObj instanceof Map) {
            Map<String, Object> argsMap = (Map<String, Object>) argsObj;
            Map<String, String> args = new HashMap<>();
            argsMap.forEach((key, value) -> args.put(key, String.valueOf(value)));
            predicate.setArgs(args);
        }

        return predicate;
    }

    /**
     * 解析过滤器配置 JSON
     *
     * @param json    过滤器 JSON 字符串
     * @param routeId 路由ID（用于日志）
     * @return FilterDefinition 列表
     */
    private List<FilterDefinition> parseFilters(String json, String routeId) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});

            return list.stream()
                    .map(this::toFilterDefinition)
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            log.error("解析过滤器配置失败: routeId={}, json={}, error={}",
                    routeId, json, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 将 Map 转换为 FilterDefinition
     *
     * @param map 过滤器配置 Map
     * @return FilterDefinition
     */
    @SuppressWarnings("unchecked")
    private FilterDefinition toFilterDefinition(Map<String, Object> map) {
        FilterDefinition filter = new FilterDefinition();

        // 设置过滤器名称
        String name = (String) map.get("name");
        filter.setName(name);

        // 设置过滤器参数
        Object argsObj = map.get("args");
        if (argsObj instanceof Map) {
            Map<String, Object> argsMap = (Map<String, Object>) argsObj;
            Map<String, String> args = new HashMap<>();
            argsMap.forEach((key, value) -> args.put(key, String.valueOf(value)));
            filter.setArgs(args);
        }

        return filter;
    }

    /**
     * 解析元数据 JSON
     *
     * @param json    元数据 JSON 字符串
     * @param routeId 路由ID（用于日志）
     * @return 元数据 Map
     */
    private Map<String, Object> parseMetadata(String json, String routeId) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("解析元数据失败: routeId={}, json={}, error={}",
                    routeId, json, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 将 RouteDefinition 转换为数据库实体（用于从外部导入路由）
     *
     * @param definition RouteDefinition
     * @return SysGatewayRoute
     */
    public SysGatewayRoute convertToEntity(RouteDefinition definition) {
        SysGatewayRoute entity = new SysGatewayRoute();

        entity.setRouteId(definition.getId());
        entity.setUri(definition.getUri().toString());
        entity.setOrderNum(definition.getOrder());

        try {
            // 转换断言为 JSON
            List<Map<String, Object>> predicatesList = definition.getPredicates().stream()
                    .map(this::predicateToMap)
                    .collect(Collectors.toList());
            entity.setPredicates(objectMapper.writeValueAsString(predicatesList));

            // 转换过滤器为 JSON
            List<Map<String, Object>> filtersList = definition.getFilters().stream()
                    .map(this::filterToMap)
                    .collect(Collectors.toList());
            entity.setFilters(objectMapper.writeValueAsString(filtersList));

            // 转换元数据为 JSON
            if (definition.getMetadata() != null && !definition.getMetadata().isEmpty()) {
                entity.setMetadata(objectMapper.writeValueAsString(definition.getMetadata()));
            }
        } catch (JsonProcessingException e) {
            log.error("序列化路由配置失败: routeId={}, error={}", definition.getId(), e.getMessage());
        }

        return entity;
    }

    /**
     * 将 PredicateDefinition 转换为 Map
     */
    private Map<String, Object> predicateToMap(PredicateDefinition predicate) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", predicate.getName());
        map.put("args", predicate.getArgs());
        return map;
    }

    /**
     * 将 FilterDefinition 转换为 Map
     */
    private Map<String, Object> filterToMap(FilterDefinition filter) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", filter.getName());
        map.put("args", filter.getArgs());
        return map;
    }
}
