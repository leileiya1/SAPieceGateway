package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysGrayRule;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 灰度规则Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Repository
public interface SysGrayRuleRepository extends R2dbcRepository<SysGrayRule, Long> {

    /**
     * 根据规则编码查询
     *
     * @param ruleCode 规则编码
     * @return 灰度规则
     */
    Mono<SysGrayRule> findByRuleCode(String ruleCode);

    /**
     * 根据服务ID查询所有规则
     *
     * @param serviceId 服务ID
     * @return 灰度规则列表
     */
    Flux<SysGrayRule> findByServiceId(String serviceId);

    /**
     * 根据服务ID和状态查询规则（按优先级排序）
     *
     * @param serviceId 服务ID
     * @param status    状态
     * @return 灰度规则列表
     */
    @Query("SELECT * FROM sys_gray_rule WHERE service_id = :serviceId AND status = :status ORDER BY priority ASC")
    Flux<SysGrayRule> findByServiceIdAndStatusOrderByPriority(String serviceId, Integer status);

    /**
     * 查询服务的有效灰度规则（启用且在有效期内）
     *
     * @param serviceId 服务ID
     * @param now       当前时间
     * @return 灰度规则列表
     */
    @Query("""
            SELECT * FROM sys_gray_rule
            WHERE service_id = :serviceId
            AND status = 1
            AND (effective_start IS NULL OR effective_start <= :now)
            AND (effective_end IS NULL OR effective_end >= :now)
            ORDER BY priority ASC
            """)
    Flux<SysGrayRule> findValidRulesByServiceId(String serviceId, LocalDateTime now);

    /**
     * 根据策略类型查询规则
     *
     * @param strategyType 策略类型
     * @return 灰度规则列表
     */
    Flux<SysGrayRule> findByStrategyType(String strategyType);

    /**
     * 查询所有启用的规则
     *
     * @return 灰度规则列表
     */
    @Query("SELECT * FROM sys_gray_rule WHERE status = 1 ORDER BY priority ASC")
    Flux<SysGrayRule> findAllEnabled();

    /**
     * 统计服务的灰度规则数量
     *
     * @param serviceId 服务ID
     * @return 规则数量
     */
    Mono<Long> countByServiceId(String serviceId);
}
