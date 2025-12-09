package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.entity.SysGatewayRoute;
import com.sapiece.nova.sapiecegateway.route.DynamicRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 网关路由管理控制器
 * 提供动态路由的 CRUD 操作 API
 *
 * @author SAPiece
 * @since 2025-11-26
 */
@Slf4j
@RestController
@RequestMapping("/admin/route")
@RequiredArgsConstructor
@Tag(name = "路由管理", description = "网关动态路由配置管理")
public class GatewayRouteController {

    private final DynamicRouteService dynamicRouteService;

    /**
     * 查询所有路由
     */
    @GetMapping("/list")
    @Operation(summary = "查询所有路由", description = "查询所有路由配置（包括禁用的）")
    @PreAuthorize("hasAuthority('gateway:route:list')")
    public Mono<Result<List<SysGatewayRoute>>> list() {
        log.debug("查询所有路由");
        return dynamicRouteService.listAllRoutes()
                .collectList()
                .map(Result::success)
                .doOnSuccess(r -> log.info("查询路由列表成功，共 {} 条", r.getData().size()));
    }

    /**
     * 查询启用的路由
     */
    @GetMapping("/enabled")
    @Operation(summary = "查询启用的路由", description = "只查询状态为启用的路由")
    @PreAuthorize("hasAuthority('gateway:route:list')")
    public Mono<Result<List<SysGatewayRoute>>> listEnabled() {
        log.debug("查询启用的路由");
        return dynamicRouteService.listEnabledRoutes()
                .collectList()
                .map(Result::success);
    }

    /**
     * 根据ID查询路由详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询路由详情", description = "根据主键ID查询路由详情")
    @PreAuthorize("hasAuthority('gateway:route:query')")
    public Mono<Result<SysGatewayRoute>> getById(
            @Parameter(description = "路由主键ID") @PathVariable Long id) {
        log.debug("查询路由详情: id={}", id);
        return dynamicRouteService.getById(id)
                .map(Result::success)
                .defaultIfEmpty(Result.error(404, "路由不存在"));
    }

    /**
     * 根据路由ID查询
     */
    @GetMapping("/routeId/{routeId}")
    @Operation(summary = "根据路由ID查询", description = "根据路由ID（如 user-service）查询路由详情")
    @PreAuthorize("hasAuthority('gateway:route:query')")
    public Mono<Result<SysGatewayRoute>> getByRouteId(
            @Parameter(description = "路由ID") @PathVariable String routeId) {
        log.debug("查询路由详情: routeId={}", routeId);
        return dynamicRouteService.getByRouteId(routeId)
                .map(Result::success)
                .defaultIfEmpty(Result.error(404, "路由不存在"));
    }

    /**
     * 搜索路由
     */
    @GetMapping("/search")
    @Operation(summary = "搜索路由", description = "根据关键字搜索路由（匹配路由ID或路由名称）")
    @PreAuthorize("hasAuthority('gateway:route:list')")
    public Mono<Result<List<SysGatewayRoute>>> search(
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        log.debug("搜索路由: keyword={}", keyword);
        return dynamicRouteService.searchByKeyword(keyword)
                .collectList()
                .map(Result::success);
    }

    /**
     * 新增路由
     */
    @PostMapping
    @Operation(summary = "新增路由", description = "新增一条路由配置，新增后自动生效")
    @PreAuthorize("hasAuthority('gateway:route:add')")
    public Mono<Result<SysGatewayRoute>> add(
            @RequestBody SysGatewayRoute route,
            Authentication authentication) {
        log.info("新增路由: routeId={}, uri={}", route.getRouteId(), route.getUri());

        // 设置创建人
        if (authentication != null) {
            route.setCreator(authentication.getName());
        }

        return dynamicRouteService.addRoute(route)
                .map(saved -> Result.success("路由创建成功", saved))
                .onErrorResume(e -> {
                    log.error("新增路由失败: {}", e.getMessage());
                    return Mono.just(Result.error(400, e.getMessage()));
                });
    }

    /**
     * 修改路由
     */
    @PutMapping("/{id}")
    @Operation(summary = "修改路由", description = "修改路由配置，修改后自动生效")
    @PreAuthorize("hasAuthority('gateway:route:update')")
    public Mono<Result<SysGatewayRoute>> update(
            @Parameter(description = "路由主键ID") @PathVariable Long id,
            @RequestBody SysGatewayRoute route,
            Authentication authentication) {
        log.info("修改路由: id={}, routeId={}", id, route.getRouteId());

        // 设置更新人
        if (authentication != null) {
            route.setUpdater(authentication.getName());
        }

        return dynamicRouteService.updateRoute(id, route)
                .map(updated -> Result.success("路由更新成功", updated))
                .onErrorResume(e -> {
                    log.error("修改路由失败: {}", e.getMessage());
                    return Mono.just(Result.error(400, e.getMessage()));
                });
    }

    /**
     * 删除路由
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除路由", description = "删除路由配置，删除后自动生效")
    @PreAuthorize("hasAuthority('gateway:route:delete')")
    public Mono<Result<Void>> delete(
            @Parameter(description = "路由主键ID") @PathVariable Long id) {
        log.info("删除路由: id={}", id);
        return dynamicRouteService.deleteRoute(id)
                .then(Mono.just(Result.<Void>success("路由删除成功", null)))
                .onErrorResume(e -> {
                    log.error("删除路由失败了: {}", e.getMessage());
                    return Mono.just(Result.error(400, e.getMessage()));
                });
    }

    /**
     * 根据路由ID删除
     */
    @DeleteMapping("/routeId/{routeId}")
    @Operation(summary = "根据路由ID删除", description = "根据路由ID删除路由配置")
    @PreAuthorize("hasAuthority('gateway:route:delete')")
    public Mono<Result<Void>> deleteByRouteId(
            @Parameter(description = "路由ID") @PathVariable String routeId) {
        log.info("删除路由: routeId={}", routeId);
        return dynamicRouteService.deleteByRouteId(routeId)
                .then(Mono.just(Result.<Void>success("路由删除成功", null)))
                .onErrorResume(e -> {
                    log.error("删除路由失败: {}", e.getMessage());
                    return Mono.just(Result.error(400, e.getMessage()));
                });
    }

    /**
     * 启用/禁用路由
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "启用/禁用路由", description = "修改路由状态，修改后自动生效")
    @PreAuthorize("hasAuthority('gateway:route:update')")
    public Mono<Result<SysGatewayRoute>> updateStatus(
            @Parameter(description = "路由主键ID") @PathVariable Long id,
            @Parameter(description = "状态：0-禁用，1-启用") @RequestParam Integer status) {
        log.info("修改路由状态: id={}, status={}", id, status);
        return dynamicRouteService.updateStatus(id, status)
                .map(updated -> {
                    String statusText = status == 1 ? "启用" : "禁用";
                    return Result.success("路由" + statusText + "成功", updated);
                })
                .onErrorResume(e -> {
                    log.error("修改路由状态失败: {}", e.getMessage());
                    return Mono.just(Result.error(400, e.getMessage()));
                });
    }

    /**
     * 手动刷新路由
     */
    @PostMapping("/refresh")
    @Operation(summary = "手动刷新路由", description = "手动触发路由刷新，重新加载所有路由配置")
    @PreAuthorize("hasAuthority('gateway:route:refresh')")
    public Mono<Result<String>> refresh() {
        log.info("手动刷新路由");
        return dynamicRouteService.refreshRoutes()
                .then(Mono.just(Result.success("路由刷新成功")));
    }

    /**
     * 统计路由数量
     */
    @GetMapping("/stats")
    @Operation(summary = "统计路由数量", description = "统计总路由数和启用路由数")
    @PreAuthorize("hasAuthority('gateway:route:list')")
    public Mono<Result<Map<String, Long>>> stats() {
        return Mono.zip(
                dynamicRouteService.listAllRoutes().count(),
                dynamicRouteService.countEnabled()
        ).map(tuple -> {
            Map<String, Long> stats = Map.of(
                    "total", tuple.getT1(),
                    "enabled", tuple.getT2(),
                    "disabled", tuple.getT1() - tuple.getT2()
            );
            return Result.success(stats);
        });
    }
}
