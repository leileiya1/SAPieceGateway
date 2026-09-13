package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.service.AdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员控制器
 * 提供IP黑白名单、Token黑名单等管理功能
 * 需要ADMIN角色权限
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    // ==================== IP黑名单管理 ====================

    /**
     * 获取IP黑名单列表
     *
     * @return IP黑名单
     */
    @GetMapping("/ip/blacklist")
    public Mono<Result<List<String>>> getIpBlacklist() {
        log.info("获取IP黑名单列表请求");
        return adminService.getIpBlacklist().map(list -> Result.success("获取成功", list));
    }

    /**
     * 添加IP到黑名单
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @PostMapping("/ip/blacklist")
    public Mono<Result<Void>> addToIpBlacklist(@RequestBody IpRequest request) {
        requireIp(request);
        log.info("添加IP到黑名单请求, ip: {}", request.getIp());
        return adminService.addToIpBlacklist(request.getIp())
                .map(added -> Result.success(added ? "添加成功" : "IP已存在", null));
    }

    /**
     * 从黑名单中移除IP
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @DeleteMapping("/ip/blacklist")
    public Mono<Result<Void>> removeFromIpBlacklist(@RequestBody IpRequest request) {
        requireIp(request);
        log.info("从黑名单中移除IP请求, ip: {}", request.getIp());
        return adminService.removeFromIpBlacklist(request.getIp())
                .map(removed -> Result.success(removed ? "移除成功" : "IP不存在", null));
    }

    // ==================== IP白名单管理 ====================

    /**
     * 获取IP白名单列表
     *
     * @return IP白名单
     */
    @GetMapping("/ip/whitelist")
    public Mono<Result<List<String>>> getIpWhitelist() {
        log.info("获取IP白名单列表请求");
        return adminService.getIpWhitelist().map(list -> Result.success("获取成功", list));
    }

    /**
     * 添加IP到白名单
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @PostMapping("/ip/whitelist")
    public Mono<Result<Void>> addToIpWhitelist(@RequestBody IpRequest request) {
        requireIp(request);
        log.info("添加IP到白名单请求, ip: {}", request.getIp());
        return adminService.addToIpWhitelist(request.getIp())
                .map(added -> Result.success(added ? "添加成功" : "IP已存在", null));
    }

    /**
     * 从白名单中移除IP
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @DeleteMapping("/ip/whitelist")
    public Mono<Result<Void>> removeFromIpWhitelist(@RequestBody IpRequest request) {
        requireIp(request);
        log.info("从白名单中移除IP请求, ip: {}", request.getIp());
        return adminService.removeFromIpWhitelist(request.getIp())
                .map(removed -> Result.success(removed ? "移除成功" : "IP不存在", null));
    }

    // ==================== Token黑名单管理 ====================

    /**
     * 将Token加入黑名单
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @PostMapping("/token/blacklist")
    public Mono<Result<Void>> addTokenToBlacklist(@RequestBody TokenBlacklistRequest request) {
        requireToken(request == null ? null : request.getToken());
        requirePositiveHours(request.getDurationHours());
        log.info("将Token加入黑名单请求, duration: {}小时", request.getDurationHours());

        return adminService.addTokenToBlacklist(request.getToken(), request.getDurationHours())
                .map(success -> success ?
                        Result.<Void>success("添加成功", null) :
                        Result.<Void>error("添加失败"));
    }

    /**
     * 从黑名单中移除Token
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @DeleteMapping("/token/blacklist")
    public Mono<Result<Void>> removeTokenFromBlacklist(@RequestBody TokenRequest request) {
        requireToken(request == null ? null : request.getToken());
        log.info("从黑名单中移除Token请求");

        return adminService.removeTokenFromBlacklist(request.getToken())
                .map(success -> success ?
                        Result.<Void>success("移除成功", null) :
                        Result.<Void>error("Token不在黑名单中"));
    }

    /**
     * 检查Token是否在黑名单中
     *
     * @param request 请求参数
     * @return 检查结果
     */
    @PostMapping("/token/blacklist/check")
    public Mono<Result<Map<String, Boolean>>> checkTokenBlacklist(@RequestBody TokenRequest request) {
        requireToken(request == null ? null : request.getToken());
        log.info("检查Token是否在黑名单中请求");

        return adminService.isTokenBlacklisted(request.getToken())
                .map(isBlacklisted -> {
                    Map<String, Boolean> result = new HashMap<>();
                    result.put("isBlacklisted", isBlacklisted);
                    return Result.success("检查完成", result);
                });
    }

    // ==================== 用户黑名单管理 ====================

    /**
     * 将用户加入黑名单（强制下线）
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @PostMapping("/user/blacklist")
    public Mono<Result<Void>> addUserToBlacklist(@RequestBody UserBlacklistRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId() <= 0) {
            throw new IllegalArgumentException("用户ID必须大于0");
        }
        requirePositiveHours(request.getDurationHours());
        log.info("将用户加入黑名单请求, userId: {}, duration: {}小时",
                request.getUserId(), request.getDurationHours());

        return adminService.addUserToBlacklist(request.getUserId(), request.getDurationHours())
                .map(success -> success ?
                        Result.<Void>success("用户已强制下线", null) :
                        Result.<Void>error("操作失败"));
    }

    /**
     * 检查用户是否在黑名单中
     *
     * @param userId 用户ID
     * @return 检查结果
     */
    @GetMapping("/user/blacklist/check/{userId}")
    public Mono<Result<Map<String, Boolean>>> checkUserBlacklist(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户ID必须大于0");
        }
        log.info("检查用户是否在黑名单中请求, userId: {}", userId);

        return adminService.isUserBlacklisted(userId)
                .map(isBlacklisted -> {
                    Map<String, Boolean> result = new HashMap<>();
                    result.put("isBlacklisted", isBlacklisted);
                    return Result.success("检查完成", result);
                });
    }

    // ==================== DTO类 ====================

    private static void requireIp(IpRequest request) {
        if (request == null || request.getIp() == null || request.getIp().isBlank()
                || !request.getIp().matches("[0-9A-Fa-f:.]+(?:/[0-9]{1,3})?")) {
            throw new IllegalArgumentException("IP地址或CIDR格式不正确");
        }
    }

    private static void requireToken(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) {
            throw new IllegalArgumentException("Token不能为空且长度不能超过8192");
        }
    }

    private static void requirePositiveHours(Long hours) {
        if (hours == null || hours <= 0 || hours > 24 * 365) {
            throw new IllegalArgumentException("有效期必须在1到8760小时之间");
        }
    }

    /**
     * IP请求DTO
     */
    @Data
    public static class IpRequest {
        /**
         * IP地址或CIDR（如：192.168.1.100 或 192.168.1.0/24）
         */
        private String ip;
    }

    /**
     * Token黑名单请求DTO
     */
    @Data
    public static class TokenBlacklistRequest {
        /**
         * JWT Token
         */
        private String token;

        /**
         * 黑名单有效期（小时）
         */
        private Long durationHours;
    }

    /**
     * Token请求DTO
     */
    @Data
    public static class TokenRequest {
        /**
         * JWT Token
         */
        private String token;
    }

    /**
     * 用户黑名单请求DTO
     */
    @Data
    public static class UserBlacklistRequest {
        /**
         * 用户ID
         */
        private Long userId;

        /**
         * 黑名单有效期（小时）
         */
        private Long durationHours;
    }
}
