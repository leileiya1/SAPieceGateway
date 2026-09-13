package com.sapiece.nova.sapiecegateway.service;

import com.sapiece.nova.sapiecegateway.entity.SysAuditLog;
import com.sapiece.nova.sapiecegateway.dto.AuditLogSearchCriteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 审计日志Service接口
 * 提供审计日志记录和查询功能
 *
 * @author SAPiece
 * @since 2025-12-07
 */
public interface AuditLogService {

    /**
     * 记录审计日志（异步，不影响主流程）
     *
     * @param auditLog 审计日志实体
     * @return Mono<Void>
     */
    Mono<Void> recordAsync(SysAuditLog auditLog);

    /**
     * 记录审计日志（同步）
     *
     * @param auditLog 审计日志实体
     * @return 保存后的审计日志
     */
    Mono<SysAuditLog> record(SysAuditLog auditLog);

    /**
     * 记录登录日志
     *
     * @param userId    用户ID
     * @param userName  用户名
     * @param clientIp  客户端IP
     * @param userAgent 用户代理
     * @param success   是否成功
     * @param message   消息
     * @return Mono<Void>
     */
    Mono<Void> recordLogin(Long userId, String userName, String clientIp, String userAgent, boolean success, String message);

    /**
     * 记录登出日志
     *
     * @param userId    用户ID
     * @param userName  用户名
     * @param clientIp  客户端IP
     * @param userAgent 用户代理
     * @return Mono<Void>
     */
    Mono<Void> recordLogout(Long userId, String userName, String clientIp, String userAgent);

    /**
     * 记录修改密码日志
     *
     * @param userId    用户ID
     * @param userName  用户名
     * @param clientIp  客户端IP
     * @param userAgent 用户代理
     * @param success   是否成功
     * @return Mono<Void>
     */
    Mono<Void> recordPasswordChange(Long userId, String userName, String clientIp, String userAgent, boolean success);

    /**
     * 记录OAuth登录日志
     *
     * @param userId        系统用户ID（首次登录可能为空）
     * @param userName      用户名
     * @param provider      OAuth提供商
     * @param oauthUserId   OAuth用户ID
     * @param clientIp      客户端IP
     * @param userAgent     用户代理
     * @param success       是否成功
     * @param message       消息
     * @return Mono<Void>
     */
    Mono<Void> recordOAuthLogin(Long userId, String userName, String provider, String oauthUserId,
                                 String clientIp, String userAgent, boolean success, String message);

    /**
     * 记录操作日志（通用方法）
     *
     * @param userId       用户ID
     * @param userName     用户名
     * @param module       操作模块
     * @param operation    操作类型
     * @param description  操作描述
     * @param requestUrl   请求URL
     * @param requestMethod 请求方法
     * @param clientIp     客户端IP
     * @param success      是否成功
     * @param errorMsg     错误信息
     * @param costTime     耗时（毫秒）
     * @return Mono<Void>
     */
    Mono<Void> recordOperation(Long userId, String userName, String module, String operation,
                                String description, String requestUrl, String requestMethod,
                                String clientIp, boolean success, String errorMsg, Long costTime);

    // ==================== 查询方法 ====================

    /**
     * 根据ID查询审计日志
     *
     * @param id 日志ID
     * @return 审计日志
     */
    Mono<SysAuditLog> getById(Long id);

    /**
     * 根据用户ID查询审计日志
     *
     * @param userId 用户ID
     * @return 审计日志列表
     */
    Flux<SysAuditLog> getByUserId(Long userId);

    /**
     * 根据时间范围查询审计日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 审计日志列表
     */
    Flux<SysAuditLog> getByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    Flux<SysAuditLog> search(AuditLogSearchCriteria criteria);

    /**
     * 查询用户最近的登录记录
     *
     * @param userName 用户名
     * @param limit    限制数量
     * @return 审计日志列表
     */
    Flux<SysAuditLog> getRecentLogin(String userName, int limit);

    /**
     * 统计用户最近登录失败次数
     *
     * @param userName 用户名
     * @param minutes  时间范围（分钟）
     * @return 失败次数
     */
    Mono<Long> countLoginFailed(String userName, int minutes);

    /**
     * 统计IP最近登录失败次数
     *
     * @param clientIp 客户端IP
     * @param minutes  时间范围（分钟）
     * @return 失败次数
     */
    Mono<Long> countLoginFailedByIp(String clientIp, int minutes);

    /**
     * 清理历史审计日志
     *
     * @param beforeDays 保留天数（删除此天数之前的日志）
     * @return 删除的记录数
     */
    Mono<Long> cleanHistory(int beforeDays);
}
