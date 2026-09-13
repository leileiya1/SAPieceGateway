package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.entity.SysAuditLog;
import com.sapiece.nova.sapiecegateway.dto.AuditLogSearchCriteria;
import com.sapiece.nova.sapiecegateway.exception.BusinessException;
import com.sapiece.nova.sapiecegateway.service.AuditLogService;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志控制器
 * 提供审计日志查询、统计和管理功能
 * 需要ADMIN角色权限
 *
 * @author SAPiece
 * @since 2025-12-10
 */
@Slf4j
@RestController
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@Tag(name = "审计日志管理", description = "提供审计日志查询、统计和清理功能")
public class AuditLogController {

    private final AuditLogService auditLogService;

    // ==================== 单条查询 ====================

    /**
     * 根据ID查询审计日志详情
     *
     * @param id 日志ID
     * @return 审计日志详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询审计日志", description = "查询单条审计日志的详细信息")
    public Mono<Result<SysAuditLog>> getById(
            @Parameter(description = "日志ID", required = true) @PathVariable Long id) {
        log.info("查询审计日志详情, id: {}", id);
        return auditLogService.getById(id)
                .map(auditLog -> Result.success("查询成功", auditLog))
                .switchIfEmpty(Mono.error(new BusinessException(404, "日志不存在")));
    }

    // ==================== 列表查询 ====================

    /**
     * 根据用户ID查询审计日志
     *
     * @param userId 用户ID
     * @return 审计日志列表
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "根据用户ID查询审计日志", description = "查询指定用户的所有审计日志")
    public Mono<Result<List<SysAuditLog>>> getByUserId(
            @Parameter(description = "用户ID", required = true) @PathVariable Long userId) {
        log.info("根据用户ID查询审计日志, userId: {}", userId);
        return auditLogService.getByUserId(userId)
                .collectList()
                .map(list -> Result.success("查询成功", list));
    }

    /**
     * 根据时间范围查询审计日志
     *
     * @param request 查询请求
     * @return 审计日志列表
     */
    @PostMapping("/time-range")
    @Operation(summary = "根据时间范围查询审计日志", description = "查询指定时间范围内的审计日志")
    public Mono<Result<List<SysAuditLog>>> getByTimeRange(@RequestBody TimeRangeRequest request) {
        log.info("根据时间范围查询审计日志, startTime: {}, endTime: {}", request.getStartTime(), request.getEndTime());

        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new IllegalArgumentException("开始时间和结束时间不能为空");
        }

        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }

        return auditLogService.getByTimeRange(request.getStartTime(), request.getEndTime())
                .collectList()
                .map(list -> Result.success("查询成功", list));
    }

    /**
     * 综合条件查询审计日志
     *
     * @param request 查询请求
     * @return 审计日志列表
     */
    @PostMapping("/search")
    @Operation(summary = "综合条件查询审计日志", description = "支持多条件组合查询审计日志")
    public Mono<Result<List<SysAuditLog>>> search(@RequestBody AuditLogSearchRequest request) {
        log.info("综合条件查询审计日志, request: {}", request);
        if (request == null || !request.hasAnyCondition()) {
            throw new IllegalArgumentException("请至少指定一个查询条件");
        }
        if (request.getUserId() != null && request.getUserId() <= 0) {
            throw new IllegalArgumentException("用户ID必须大于0");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new IllegalArgumentException("状态只能是0或1");
        }
        if (request.getStartTime() != null && request.getEndTime() != null
                && request.getStartTime().isAfter(request.getEndTime())) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        request.validateLengths();
        int limit = request.getLimit() == null ? 200 : Math.max(1, Math.min(request.getLimit(), 1000));
        AuditLogSearchCriteria criteria = AuditLogSearchCriteria.builder()
                .userId(request.getUserId()).userName(request.getUserName())
                .module(request.getModule()).operation(request.getOperation())
                .status(request.getStatus()).clientIp(request.getClientIp())
                .startTime(request.getStartTime()).endTime(request.getEndTime())
                .limit(limit).build();
        return auditLogService.search(criteria).collectList()
                .map(list -> Result.success("查询成功", list));
    }

    // ==================== 登录记录查询 ====================

    /**
     * 查询用户最近登录记录
     *
     * @param userName 用户名
     * @param limit    限制数量（默认10）
     * @return 登录记录列表
     */
    @GetMapping("/login-history/{userName}")
    @Operation(summary = "查询用户最近登录记录", description = "查询指定用户最近的登录记录（包含成功和失败）")
    public Mono<Result<List<SysAuditLog>>> getRecentLogin(
            @Parameter(description = "用户名", required = true) @PathVariable String userName,
            @Parameter(description = "返回数量限制，默认10") @RequestParam(defaultValue = "10") int limit) {
        log.info("查询用户最近登录记录, userName: {}, limit: {}", userName, limit);

        // 限制最大查询数量
        int actualLimit = Math.max(1, Math.min(limit, 100));

        return auditLogService.getRecentLogin(userName, actualLimit)
                .collectList()
                .map(list -> Result.success("查询成功", list));
    }

    // ==================== 统计功能 ====================

    /**
     * 统计用户登录失败次数
     *
     * @param userName 用户名
     * @param minutes  时间范围（分钟，默认30分钟）
     * @return 失败次数
     */
    @GetMapping("/login-failed/count/{userName}")
    @Operation(summary = "统计用户登录失败次数", description = "统计指定用户在指定时间内的登录失败次数")
    public Mono<Result<LoginFailedCountResponse>> countLoginFailed(
            @Parameter(description = "用户名", required = true) @PathVariable String userName,
            @Parameter(description = "时间范围（分钟），默认30") @RequestParam(defaultValue = "30") int minutes) {
        log.info("统计用户登录失败次数, userName: {}, minutes: {}", userName, minutes);

        int actualMinutes = normalizeMinutes(minutes);

        return auditLogService.countLoginFailed(userName, actualMinutes)
                .map(count -> {
                    LoginFailedCountResponse response = new LoginFailedCountResponse();
                    response.setUserName(userName);
                    response.setMinutes(actualMinutes);
                    response.setFailedCount(count);
                    return Result.success("统计成功", response);
                });
    }

    /**
     * 统计IP登录失败次数
     *
     * @param request 请求参数
     * @return 失败次数
     */
    @PostMapping("/login-failed/count/ip")
    @Operation(summary = "统计IP登录失败次数", description = "统计指定IP在指定时间内的登录失败次数")
    public Mono<Result<IpLoginFailedCountResponse>> countLoginFailedByIp(@RequestBody IpLoginFailedRequest request) {
        log.info("统计IP登录失败次数, clientIp: {}, minutes: {}", request.getClientIp(), request.getMinutes());

        if (request == null || request.getClientIp() == null || request.getClientIp().isBlank()) {
            throw new IllegalArgumentException("客户端IP不能为空");
        }
        int minutes = normalizeMinutes(request.getMinutes() != null ? request.getMinutes() : 30);

        return auditLogService.countLoginFailedByIp(request.getClientIp(), minutes)
                .map(count -> {
                    IpLoginFailedCountResponse response = new IpLoginFailedCountResponse();
                    response.setClientIp(request.getClientIp());
                    response.setMinutes(minutes);
                    response.setFailedCount(count);
                    return Result.success("统计成功", response);
                });
    }

    // ==================== 日志清理 ====================

    /**
     * 清理历史审计日志
     *
     * @param request 清理请求
     * @return 删除的记录数
     */
    @PostMapping("/clean")
    @Operation(summary = "清理历史审计日志", description = "删除指定天数之前的审计日志，最少保留7天")
    public Mono<Result<CleanHistoryResponse>> cleanHistory(@RequestBody CleanHistoryRequest request) {
        log.info("清理历史审计日志请求, beforeDays: {}", request.getBeforeDays());

        // 最少保留7天
        if (request == null || request.getBeforeDays() == null) {
            throw new IllegalArgumentException("保留天数不能为空");
        }
        int beforeDays = Math.max(request.getBeforeDays(), 7);
        if (beforeDays > 36500) {
            throw new IllegalArgumentException("保留天数不能超过36500");
        }

        return auditLogService.cleanHistory(beforeDays)
                .map(count -> {
                    CleanHistoryResponse response = new CleanHistoryResponse();
                    response.setBeforeDays(beforeDays);
                    response.setDeletedCount(count);
                    return Result.success("清理成功", response);
                });
    }

    // ==================== DTO类 ====================

    private static int normalizeMinutes(int minutes) {
        if (minutes <= 0 || minutes > 7 * 24 * 60) {
            throw new IllegalArgumentException("统计时间必须在1到10080分钟之间");
        }
        return minutes;
    }

    /**
     * 时间范围查询请求
     */
    @Data
    public static class TimeRangeRequest {
        /**
         * 开始时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;

        /**
         * 结束时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endTime;

    }

    /**
     * 审计日志综合查询请求
     */
    @Data
    public static class AuditLogSearchRequest {
        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 用户名
         */
        private String userName;

        /**
         * 操作模块：AUTH-认证、USER-用户、ROLE-角色、ROUTE-路由、ADMIN-管理、GRAY-灰度、OAUTH-第三方登录
         */
        private String module;

        /**
         * 操作类型：LOGIN-登录、LOGOUT-登出、CREATE-新增、UPDATE-修改、DELETE-删除等
         */
        private String operation;

        /**
         * 操作状态：0-失败 1-成功
         */
        private Integer status;

        /**
         * 客户端IP
         */
        private String clientIp;

        /**
         * 开始时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;

        /**
         * 结束时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endTime;

        /** 最大返回条数，默认200，最大1000。 */
        private Integer limit;

        boolean hasAnyCondition() {
            return userId != null || hasText(userName) || hasText(module) || hasText(operation)
                    || status != null || hasText(clientIp) || startTime != null || endTime != null;
        }

        void validateLengths() {
            checkLength(userName, 64, "用户名");
            checkLength(module, 32, "模块");
            checkLength(operation, 32, "操作类型");
            checkLength(clientIp, 64, "客户端IP");
        }

        private static boolean hasText(String value) { return value != null && !value.isBlank(); }
        private static void checkLength(String value, int max, String label) {
            if (value != null && value.trim().length() > max) throw new IllegalArgumentException(label + "过长");
        }
    }

    /**
     * IP登录失败统计请求
     */
    @Data
    public static class IpLoginFailedRequest {
        /**
         * 客户端IP
         */
        private String clientIp;

        /**
         * 时间范围（分钟），默认30
         */
        private Integer minutes;
    }

    /**
     * 清理历史日志请求
     */
    @Data
    public static class CleanHistoryRequest {
        /**
         * 保留天数（删除此天数之前的日志），最小7天
         */
        private Integer beforeDays;
    }

    /**
     * 用户登录失败统计响应
     */
    @Data
    public static class LoginFailedCountResponse {
        /**
         * 用户名
         */
        private String userName;

        /**
         * 统计时间范围（分钟）
         */
        private Integer minutes;

        /**
         * 失败次数
         */
        private Long failedCount;
    }

    /**
     * IP登录失败统计响应
     */
    @Data
    public static class IpLoginFailedCountResponse {
        /**
         * 客户端IP
         */
        private String clientIp;

        /**
         * 统计时间范围（分钟）
         */
        private Integer minutes;

        /**
         * 失败次数
         */
        private Long failedCount;
    }

    /**
     * 清理历史日志响应
     */
    @Data
    public static class CleanHistoryResponse {
        /**
         * 清理天数阈值
         */
        private Integer beforeDays;

        /**
         * 删除的记录数
         */
        private Long deletedCount;
    }
}
