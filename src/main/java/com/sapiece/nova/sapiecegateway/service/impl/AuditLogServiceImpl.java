package com.sapiece.nova.sapiecegateway.service.impl;

import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.sapiece.nova.sapiecegateway.entity.SysAuditLog;
import com.sapiece.nova.sapiecegateway.repository.SysAuditLogRepository;
import com.sapiece.nova.sapiecegateway.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审计日志Service实现类
 * 实现审计日志记录和查询功能
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final SysAuditLogRepository auditLogRepository;

    /**
     * 记录审计日志（异步，不影响主流程）
     *
     * @param auditLog 审计日志实体
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> recordAsync(SysAuditLog auditLog) {
        log.debug("异步记录审计日志, module: {}, operation: {}, userName: {}",
                auditLog.getModule(), auditLog.getOperation(), auditLog.getUserName());

        // 补充默认值
        fillDefaultValues(auditLog);

        // 异步保存，不阻塞主流程
        return auditLogRepository.save(auditLog)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(saved -> log.info("审计日志记录成功, id: {}, module: {}, operation: {}",
                        saved.getId(), saved.getModule(), saved.getOperation()))
                .doOnError(error -> log.error("审计日志记录失败, module: {}, operation: {}, error: {}",
                        auditLog.getModule(), auditLog.getOperation(), error.getMessage()))
                .then();
    }

    /**
     * 记录审计日志（同步）
     *
     * @param auditLog 审计日志实体
     * @return 保存后的审计日志
     */
    @Override
    public Mono<SysAuditLog> record(SysAuditLog auditLog) {
        log.debug("同步记录审计日志, module: {}, operation: {}, userName: {}",
                auditLog.getModule(), auditLog.getOperation(), auditLog.getUserName());

        // 补充默认值
        fillDefaultValues(auditLog);

        return auditLogRepository.save(auditLog)
                .doOnSuccess(saved -> log.info("审计日志记录成功, id: {}, module: {}, operation: {}",
                        saved.getId(), saved.getModule(), saved.getOperation()))
                .doOnError(error -> log.error("审计日志记录失败, module: {}, operation: {}, error: {}",
                        auditLog.getModule(), auditLog.getOperation(), error.getMessage()));
    }

    /**
     * 记录登录日志
     */
    @Override
    public Mono<Void> recordLogin(Long userId, String userName, String clientIp, String userAgent, boolean success, String message) {
        log.info("记录登录日志, userName: {}, clientIp: {}, success: {}", userName, clientIp, success);

        SysAuditLog auditLog = SysAuditLog.builder()
                .userId(userId)
                .userName(userName)
                .module(SysAuditLog.Module.AUTH)
                .operation(success ? SysAuditLog.Operation.LOGIN : SysAuditLog.Operation.LOGIN_FAILED)
                .description(success ? "用户登录成功" : "用户登录失败：" + message)
                .requestUrl("/auth/login")
                .requestMethod("POST")
                .clientIp(clientIp)
                .userAgent(userAgent)
                .status(success ? SysAuditLog.Status.SUCCESS : SysAuditLog.Status.FAIL)
                .responseMsg(message)
                .build();

        // 解析UserAgent
        parseUserAgent(auditLog, userAgent);

        return recordAsync(auditLog);
    }

    /**
     * 记录登出日志
     */
    @Override
    public Mono<Void> recordLogout(Long userId, String userName, String clientIp, String userAgent) {
        log.info("记录登出日志, userName: {}, clientIp: {}", userName, clientIp);

        SysAuditLog auditLog = SysAuditLog.builder()
                .userId(userId)
                .userName(userName)
                .module(SysAuditLog.Module.AUTH)
                .operation(SysAuditLog.Operation.LOGOUT)
                .description("用户登出")
                .requestUrl("/auth/logout")
                .requestMethod("POST")
                .clientIp(clientIp)
                .userAgent(userAgent)
                .status(SysAuditLog.Status.SUCCESS)
                .responseMsg("登出成功")
                .build();

        // 解析UserAgent
        parseUserAgent(auditLog, userAgent);

        return recordAsync(auditLog);
    }

    /**
     * 记录修改密码日志
     */
    @Override
    public Mono<Void> recordPasswordChange(Long userId, String userName, String clientIp, String userAgent, boolean success) {
        log.info("记录修改密码日志, userName: {}, clientIp: {}, success: {}", userName, clientIp, success);

        SysAuditLog auditLog = SysAuditLog.builder()
                .userId(userId)
                .userName(userName)
                .module(SysAuditLog.Module.USER)
                .operation(SysAuditLog.Operation.PASSWORD_CHANGE)
                .description(success ? "用户修改密码成功" : "用户修改密码失败")
                .requestUrl("/user/change-password")
                .requestMethod("POST")
                .clientIp(clientIp)
                .userAgent(userAgent)
                .status(success ? SysAuditLog.Status.SUCCESS : SysAuditLog.Status.FAIL)
                .responseMsg(success ? "密码修改成功" : "密码修改失败")
                .build();

        // 解析UserAgent
        parseUserAgent(auditLog, userAgent);

        return recordAsync(auditLog);
    }

    /**
     * 记录OAuth登录日志
     */
    @Override
    public Mono<Void> recordOAuthLogin(Long userId, String userName, String provider, String oauthUserId,
                                        String clientIp, String userAgent, boolean success, String message) {
        log.info("记录OAuth登录日志, provider: {}, oauthUserId: {}, success: {}", provider, oauthUserId, success);

        SysAuditLog auditLog = SysAuditLog.builder()
                .userId(userId)
                .userName(userName)
                .module(SysAuditLog.Module.OAUTH)
                .operation(SysAuditLog.Operation.OAUTH_LOGIN)
                .description(success ? "OAuth登录成功（" + provider + "）" : "OAuth登录失败（" + provider + "）：" + message)
                .requestUrl("/auth/oauth2/callback/" + provider.toLowerCase())
                .requestMethod("GET")
                .clientIp(clientIp)
                .userAgent(userAgent)
                .oauthProvider(provider)
                .oauthUserId(oauthUserId)
                .status(success ? SysAuditLog.Status.SUCCESS : SysAuditLog.Status.FAIL)
                .responseMsg(message)
                .build();

        // 解析UserAgent
        parseUserAgent(auditLog, userAgent);

        return recordAsync(auditLog);
    }

    /**
     * 记录操作日志（通用方法）
     */
    @Override
    public Mono<Void> recordOperation(Long userId, String userName, String module, String operation,
                                       String description, String requestUrl, String requestMethod,
                                       String clientIp, boolean success, String errorMsg, Long costTime) {
        log.debug("记录操作日志, module: {}, operation: {}, userName: {}", module, operation, userName);

        SysAuditLog auditLog = SysAuditLog.builder()
                .userId(userId)
                .userName(userName)
                .module(module)
                .operation(operation)
                .description(description)
                .requestUrl(requestUrl)
                .requestMethod(requestMethod)
                .clientIp(clientIp)
                .status(success ? SysAuditLog.Status.SUCCESS : SysAuditLog.Status.FAIL)
                .errorMsg(errorMsg)
                .costTime(costTime)
                .build();

        return recordAsync(auditLog);
    }

    // ==================== 查询方法 ====================

    /**
     * 根据ID查询审计日志
     */
    @Override
    public Mono<SysAuditLog> getById(Long id) {
        log.debug("根据ID查询审计日志, id: {}", id);
        return auditLogRepository.findById(id)
                .doOnSuccess(auditLog -> {
                    if (auditLog != null) {
                        log.debug("查询到审计日志, id: {}", id);
                    } else {
                        log.debug("未查询到审计日志, id: {}", id);
                    }
                });
    }

    /**
     * 根据用户ID查询审计日志
     */
    @Override
    public Flux<SysAuditLog> getByUserId(Long userId) {
        log.debug("根据用户ID查询审计日志, userId: {}", userId);
        return auditLogRepository.findByUserId(userId)
                .doOnComplete(() -> log.debug("用户审计日志查询完成, userId: {}", userId));
    }

    /**
     * 根据时间范围查询审计日志
     */
    @Override
    public Flux<SysAuditLog> getByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        log.debug("根据时间范围查询审计日志, startTime: {}, endTime: {}", startTime, endTime);
        return auditLogRepository.findByTimeRange(startTime, endTime)
                .doOnComplete(() -> log.debug("时间范围审计日志查询完成"));
    }

    /**
     * 查询用户最近的登录记录
     */
    @Override
    public Flux<SysAuditLog> getRecentLogin(String userName, int limit) {
        log.debug("查询用户最近登录记录, userName: {}, limit: {}", userName, limit);
        return auditLogRepository.findRecentLoginByUserName(userName, limit)
                .doOnComplete(() -> log.debug("用户最近登录记录查询完成, userName: {}", userName));
    }

    /**
     * 统计用户最近登录失败次数
     */
    @Override
    public Mono<Long> countLoginFailed(String userName, int minutes) {
        log.debug("统计用户登录失败次数, userName: {}, minutes: {}", userName, minutes);
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(minutes);
        return auditLogRepository.countLoginFailedSince(userName, startTime)
                .doOnSuccess(count -> log.debug("用户登录失败次数: {}, userName: {}", count, userName));
    }

    /**
     * 统计IP最近登录失败次数
     */
    @Override
    public Mono<Long> countLoginFailedByIp(String clientIp, int minutes) {
        log.debug("统计IP登录失败次数, clientIp: {}, minutes: {}", clientIp, minutes);
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(minutes);
        return auditLogRepository.countLoginFailedByIpSince(clientIp, startTime)
                .doOnSuccess(count -> log.debug("IP登录失败次数: {}, clientIp: {}", count, clientIp));
    }

    /**
     * 清理历史审计日志
     */
    @Override
    public Mono<Long> cleanHistory(int beforeDays) {
        log.info("清理历史审计日志, beforeDays: {}", beforeDays);
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(beforeDays);
        return auditLogRepository.deleteByOperateTimeBefore(beforeTime)
                .defaultIfEmpty(0L)
                .doOnSuccess(count -> log.info("清理历史审计日志完成, 删除记录数: {}", count));
    }

    // ==================== 私有方法 ====================

    /**
     * 填充默认值
     *
     * @param auditLog 审计日志
     */
    private void fillDefaultValues(SysAuditLog auditLog) {
        // 设置操作时间
        if (auditLog.getOperateTime() == null) {
            auditLog.setOperateTime(LocalDateTime.now());
        }

        // 设置链路追踪ID
        if (auditLog.getTraceId() == null) {
            auditLog.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        }

        // 设置默认状态
        if (auditLog.getStatus() == null) {
            auditLog.setStatus(SysAuditLog.Status.SUCCESS);
        }
    }

    /**
     * 解析UserAgent信息
     *
     * @param auditLog  审计日志
     * @param userAgent UserAgent字符串
     */
    private void parseUserAgent(SysAuditLog auditLog, String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            auditLog.setDeviceType("Unknown");
            return;
        }

        try {
            UserAgent ua = UserAgentUtil.parse(userAgent);
            if (ua != null) {
                // 浏览器
                if (ua.getBrowser() != null) {
                    auditLog.setBrowser(ua.getBrowser().getName());
                }

                // 操作系统
                if (ua.getOs() != null) {
                    auditLog.setOs(ua.getOs().getName());
                }

                // 设备类型
                if (ua.isMobile()) {
                    auditLog.setDeviceType("Mobile");
                } else {
                    auditLog.setDeviceType("PC");
                }
            }
        } catch (Exception e) {
            log.warn("解析UserAgent失败, userAgent: {}, error: {}", userAgent, e.getMessage());
            auditLog.setDeviceType("Unknown");
        }
    }
}
