package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysAuditLog;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 审计日志Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Repository
public interface SysAuditLogRepository extends R2dbcRepository<SysAuditLog, Long> {

    /**
     * 根据用户ID查询审计日志
     *
     * @param userId 用户ID
     * @return 审计日志列表
     */
    Flux<SysAuditLog> findByUserId(Long userId);

    /**
     * 根据用户名查询审计日志
     *
     * @param userName 用户名
     * @return 审计日志列表
     */
    Flux<SysAuditLog> findByUserName(String userName);

    /**
     * 根据操作模块查询审计日志
     *
     * @param module 操作模块
     * @return 审计日志列表
     */
    Flux<SysAuditLog> findByModule(String module);

    /**
     * 根据操作类型查询审计日志
     *
     * @param operation 操作类型
     * @return 审计日志列表
     */
    Flux<SysAuditLog> findByOperation(String operation);

    /**
     * 根据客户端IP查询审计日志
     *
     * @param clientIp 客户端IP
     * @return 审计日志列表
     */
    Flux<SysAuditLog> findByClientIp(String clientIp);

    /**
     * 根据链路追踪ID查询审计日志
     *
     * @param traceId 链路追踪ID
     * @return 审计日志列表
     */
    Flux<SysAuditLog> findByTraceId(String traceId);

    /**
     * 根据时间范围查询审计日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 审计日志列表
     */
    @Query("SELECT * FROM sys_audit_log WHERE operate_time BETWEEN :startTime AND :endTime ORDER BY operate_time DESC")
    Flux<SysAuditLog> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据用户ID和时间范围查询审计日志
     *
     * @param userId    用户ID
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 审计日志列表
     */
    @Query("SELECT * FROM sys_audit_log WHERE user_id = :userId AND operate_time BETWEEN :startTime AND :endTime ORDER BY operate_time DESC")
    Flux<SysAuditLog> findByUserIdAndTimeRange(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询用户最近的登录记录
     *
     * @param userName 用户名
     * @param limit    限制数量
     * @return 审计日志列表
     */
    @Query("SELECT * FROM sys_audit_log WHERE user_name = :userName AND operation IN ('LOGIN', 'LOGIN_FAILED', 'OAUTH_LOGIN') ORDER BY operate_time DESC LIMIT :limit")
    Flux<SysAuditLog> findRecentLoginByUserName(String userName, int limit);

    /**
     * 统计指定时间范围内的登录失败次数
     *
     * @param userName  用户名
     * @param startTime 开始时间
     * @return 失败次数
     */
    @Query("SELECT COUNT(*) FROM sys_audit_log WHERE user_name = :userName AND operation = 'LOGIN_FAILED' AND operate_time >= :startTime")
    Mono<Long> countLoginFailedSince(String userName, LocalDateTime startTime);

    /**
     * 统计指定IP在时间范围内的登录失败次数
     *
     * @param clientIp  客户端IP
     * @param startTime 开始时间
     * @return 失败次数
     */
    @Query("SELECT COUNT(*) FROM sys_audit_log WHERE client_ip = :clientIp AND operation = 'LOGIN_FAILED' AND operate_time >= :startTime")
    Mono<Long> countLoginFailedByIpSince(String clientIp, LocalDateTime startTime);

    /**
     * 删除指定时间之前的审计日志（用于日志清理）
     *
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    @Query("DELETE FROM sys_audit_log WHERE operate_time < :beforeTime")
    Mono<Long> deleteByOperateTimeBefore(LocalDateTime beforeTime);
}
