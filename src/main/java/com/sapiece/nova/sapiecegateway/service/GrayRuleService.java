package com.sapiece.nova.sapiecegateway.service;

import com.sapiece.nova.sapiecegateway.entity.SysGrayRule;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 灰度发布Service接口
 * 提供灰度规则管理和匹配功能
 *
 * @author SAPiece
 * @since 2025-12-07
 */
public interface GrayRuleService {

    // ==================== 规则匹配方法 ====================

    /**
     * 判断请求是否命中灰度规则
     *
     * @param serviceId 服务ID
     * @param userId    用户ID（可为空）
     * @param clientIp  客户端IP
     * @param headerValue 指定Header的值（可为空）
     * @param paramValue  指定参数的值（可为空）
     * @return 命中的灰度规则（如果命中），否则返回空
     */
    Mono<SysGrayRule> matchGrayRule(String serviceId, Long userId, String clientIp,
                                     String headerValue, String paramValue);

    /**
     * 获取灰度目标URI
     * 如果命中灰度规则，返回灰度URI；否则返回空
     *
     * @param serviceId 服务ID
     * @param userId    用户ID（可为空）
     * @param clientIp  客户端IP
     * @param headerValue 指定Header的值（可为空）
     * @param paramValue  指定参数的值（可为空）
     * @return 灰度目标URI，未命中则返回空
     */
    Mono<String> getGrayTargetUri(String serviceId, Long userId, String clientIp,
                                   String headerValue, String paramValue);

    // ==================== 规则管理方法 ====================

    /**
     * 获取服务的所有灰度规则
     *
     * @param serviceId 服务ID
     * @return 灰度规则列表
     */
    Flux<SysGrayRule> getRulesByServiceId(String serviceId);

    /**
     * 获取服务的有效灰度规则
     *
     * @param serviceId 服务ID
     * @return 有效的灰度规则列表
     */
    Flux<SysGrayRule> getValidRulesByServiceId(String serviceId);

    /**
     * 获取所有灰度规则
     *
     * @return 灰度规则列表
     */
    Flux<SysGrayRule> getAllRules();

    /**
     * 根据ID获取灰度规则
     *
     * @param id 规则ID
     * @return 灰度规则
     */
    Mono<SysGrayRule> getById(Long id);

    /**
     * 根据规则编码获取灰度规则
     *
     * @param ruleCode 规则编码
     * @return 灰度规则
     */
    Mono<SysGrayRule> getByRuleCode(String ruleCode);

    /**
     * 新增灰度规则
     *
     * @param rule 灰度规则
     * @return 保存后的规则
     */
    Mono<SysGrayRule> addRule(SysGrayRule rule);

    /**
     * 更新灰度规则
     *
     * @param id   规则ID
     * @param rule 灰度规则
     * @return 更新后的规则
     */
    Mono<SysGrayRule> updateRule(Long id, SysGrayRule rule);

    /**
     * 删除灰度规则
     *
     * @param id 规则ID
     * @return Mono<Void>
     */
    Mono<Void> deleteRule(Long id);

    /**
     * 启用灰度规则
     *
     * @param id 规则ID
     * @return 更新后的规则
     */
    Mono<SysGrayRule> enableRule(Long id);

    /**
     * 禁用灰度规则
     *
     * @param id 规则ID
     * @return 更新后的规则
     */
    Mono<SysGrayRule> disableRule(Long id);

    /**
     * 刷新灰度规则缓存
     *
     * @return Mono<Void>
     */
    Mono<Void> refreshCache();
}
