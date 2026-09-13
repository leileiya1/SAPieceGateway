package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.entity.SysGrayRule;
import com.sapiece.nova.sapiecegateway.exception.BusinessException;
import com.sapiece.nova.sapiecegateway.service.GrayRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 灰度发布管理控制器
 * 提供灰度规则的增删改查接口
 * 需要管理员权限
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Slf4j
@RestController
@RequestMapping("/admin/gray")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "灰度发布管理", description = "灰度规则的增删改查接口")
public class GrayRuleController {

    private final GrayRuleService grayRuleService;

    /**
     * 获取所有灰度规则
     *
     * @return 灰度规则列表
     */
    @GetMapping("/rules")
    @Operation(summary = "获取所有灰度规则", description = "获取系统中所有配置的灰度规则")
    public Mono<Result<List<SysGrayRule>>> getAllRules() {
        log.info("获取所有灰度规则");

        return grayRuleService.getAllRules()
                .collectList()
                .map(rules -> {
                    log.info("查询到灰度规则数量: {}", rules.size());
                    return Result.success("获取成功", rules);
                });
    }

    /**
     * 根据服务ID获取灰度规则
     *
     * @param serviceId 服务ID
     * @return 灰度规则列表
     */
    @GetMapping("/rules/service/{serviceId}")
    @Operation(summary = "获取服务的灰度规则", description = "根据服务ID获取该服务的所有灰度规则")
    @Parameter(name = "serviceId", description = "服务ID", required = true, example = "user-service")
    public Mono<Result<List<SysGrayRule>>> getRulesByServiceId(@PathVariable String serviceId) {
        log.info("获取服务灰度规则, serviceId: {}", serviceId);

        return grayRuleService.getRulesByServiceId(serviceId)
                .collectList()
                .map(rules -> {
                    log.info("查询到服务灰度规则数量: {}, serviceId: {}", rules.size(), serviceId);
                    return Result.success("获取成功", rules);
                });
    }

    /**
     * 根据ID获取灰度规则
     *
     * @param id 规则ID
     * @return 灰度规则
     */
    @GetMapping("/rules/{id}")
    @Operation(summary = "获取灰度规则详情", description = "根据规则ID获取灰度规则详情")
    @Parameter(name = "id", description = "规则ID", required = true, example = "1")
    public Mono<Result<SysGrayRule>> getRuleById(@PathVariable Long id) {
        log.info("获取灰度规则详情, id: {}", id);

        return grayRuleService.getById(id)
                .map(rule -> {
                    log.info("查询到灰度规则, id: {}, ruleCode: {}", id, rule.getRuleCode());
                    return Result.success("获取成功", rule);
                })
                .switchIfEmpty(Mono.error(new BusinessException(404, "灰度规则不存在")));
    }

    /**
     * 新增灰度规则
     *
     * @param rule 灰度规则
     * @return 保存后的规则
     */
    @PostMapping("/rules")
    @Operation(summary = "新增灰度规则", description = "创建新的灰度规则")
    public Mono<Result<SysGrayRule>> addRule(@RequestBody SysGrayRule rule) {
        log.info("新增灰度规则, ruleCode: {}, serviceId: {}, strategyType: {}",
                rule.getRuleCode(), rule.getServiceId(), rule.getStrategyType());

        // 参数校验
        if (rule.getRuleCode() == null || rule.getRuleCode().isBlank()) {
            throw new IllegalArgumentException("规则编码不能为空");
        }
        if (rule.getServiceId() == null || rule.getServiceId().isBlank()) {
            throw new IllegalArgumentException("服务ID不能为空");
        }
        if (rule.getTargetUri() == null || rule.getTargetUri().isBlank()) {
            throw new IllegalArgumentException("目标URI不能为空");
        }
        if (rule.getStrategyType() == null || rule.getStrategyType().isBlank()) {
            throw new IllegalArgumentException("策略类型不能为空");
        }
        if (rule.getStrategyConfig() == null || rule.getStrategyConfig().isBlank()) {
            throw new IllegalArgumentException("策略配置不能为空");
        }

        return grayRuleService.addRule(rule)
                .map(saved -> {
                    log.info("灰度规则新增成功, id: {}, ruleCode: {}", saved.getId(), saved.getRuleCode());
                    return Result.success("新增成功", saved);
                });
    }

    /**
     * 更新灰度规则
     *
     * @param id   规则ID
     * @param rule 灰度规则
     * @return 更新后的规则
     */
    @PutMapping("/rules/{id}")
    @Operation(summary = "更新灰度规则", description = "更新指定ID的灰度规则")
    @Parameter(name = "id", description = "规则ID", required = true, example = "1")
    public Mono<Result<SysGrayRule>> updateRule(@PathVariable Long id, @RequestBody SysGrayRule rule) {
        log.info("更新灰度规则, id: {}", id);

        return grayRuleService.updateRule(id, rule)
                .map(updated -> {
                    log.info("灰度规则更新成功, id: {}, ruleCode: {}", updated.getId(), updated.getRuleCode());
                    return Result.success("更新成功", updated);
                })
                .switchIfEmpty(Mono.error(new BusinessException(404, "灰度规则不存在")));
    }

    /**
     * 删除灰度规则
     *
     * @param id 规则ID
     * @return 操作结果
     */
    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除灰度规则", description = "删除指定ID的灰度规则")
    @Parameter(name = "id", description = "规则ID", required = true, example = "1")
    public Mono<Result<Void>> deleteRule(@PathVariable Long id) {
        log.info("删除灰度规则, id: {}", id);

        return grayRuleService.deleteRule(id)
                .then(Mono.fromCallable(() -> {
                    log.info("灰度规则删除成功, id: {}", id);
                    return Result.<Void>success("删除成功", null);
                }));
    }

    /**
     * 启用灰度规则
     *
     * @param id 规则ID
     * @return 更新后的规则
     */
    @PostMapping("/rules/{id}/enable")
    @Operation(summary = "启用灰度规则", description = "启用指定ID的灰度规则")
    @Parameter(name = "id", description = "规则ID", required = true, example = "1")
    public Mono<Result<SysGrayRule>> enableRule(@PathVariable Long id) {
        log.info("启用灰度规则, id: {}", id);

        return grayRuleService.enableRule(id)
                .map(rule -> {
                    log.info("灰度规则启用成功, id: {}, ruleCode: {}", rule.getId(), rule.getRuleCode());
                    return Result.success("启用成功", rule);
                })
                .switchIfEmpty(Mono.error(new BusinessException(404, "灰度规则不存在")));
    }

    /**
     * 禁用灰度规则
     *
     * @param id 规则ID
     * @return 更新后的规则
     */
    @PostMapping("/rules/{id}/disable")
    @Operation(summary = "禁用灰度规则", description = "禁用指定ID的灰度规则")
    @Parameter(name = "id", description = "规则ID", required = true, example = "1")
    public Mono<Result<SysGrayRule>> disableRule(@PathVariable Long id) {
        log.info("禁用灰度规则, id: {}", id);

        return grayRuleService.disableRule(id)
                .map(rule -> {
                    log.info("灰度规则禁用成功, id: {}, ruleCode: {}", rule.getId(), rule.getRuleCode());
                    return Result.success("禁用成功", rule);
                })
                .switchIfEmpty(Mono.error(new BusinessException(404, "灰度规则不存在")));
    }

    /**
     * 刷新灰度规则缓存
     *
     * @return 操作结果
     */
    @PostMapping("/rules/refresh")
    @Operation(summary = "刷新灰度规则缓存", description = "手动刷新灰度规则缓存")
    public Mono<Result<Void>> refreshCache() {
        log.info("刷新灰度规则缓存");

        return grayRuleService.refreshCache()
                .then(Mono.fromCallable(() -> {
                    log.info("灰度规则缓存刷新成功");
                    return Result.<Void>success("缓存刷新成功", null);
                }));
    }
}
