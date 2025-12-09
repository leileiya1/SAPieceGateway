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
@PreAuthorize("hasRole('ADMIN')")  // 需要管理员权限
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
        List<String> blacklist = adminService.getIpBlacklist();
        return Mono.just(Result.success("获取成功", blacklist));
    }

    /**
     * 添加IP到黑名单
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @PostMapping("/ip/blacklist")
    public Mono<Result<Void>> addToIpBlacklist(@RequestBody IpRequest request) {
        log.info("添加IP到黑名单请求, ip: {}", request.getIp());
        adminService.addToIpBlacklist(request.getIp());
        return Mono.just(Result.success("添加成功", null));
    }

    /**
     * 从黑名单中移除IP
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @DeleteMapping("/ip/blacklist")
    public Mono<Result<Void>> removeFromIpBlacklist(@RequestBody IpRequest request) {
        log.info("从黑名单中移除IP请求, ip: {}", request.getIp());
        adminService.removeFromIpBlacklist(request.getIp());
        return Mono.just(Result.success("移除成功", null));
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
        List<String> whitelist = adminService.getIpWhitelist();
        return Mono.just(Result.success("获取成功", whitelist));
    }

    /**
     * 添加IP到白名单
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @PostMapping("/ip/whitelist")
    public Mono<Result<Void>> addToIpWhitelist(@RequestBody IpRequest request) {
        log.info("添加IP到白名单请求, ip: {}", request.getIp());
        adminService.addToIpWhitelist(request.getIp());
        return Mono.just(Result.success("添加成功", null));
    }

    /**
     * 从白名单中移除IP
     *
     * @param request 请求参数
     * @return 操作结果
     */
    @DeleteMapping("/ip/whitelist")
    public Mono<Result<Void>> removeFromIpWhitelist(@RequestBody IpRequest request) {
        log.info("从白名单中移除IP请求, ip: {}", request.getIp());
        adminService.removeFromIpWhitelist(request.getIp());
        return Mono.just(Result.success("移除成功", null));
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
        log.info("检查用户是否在黑名单中请求, userId: {}", userId);

        return adminService.isUserBlacklisted(userId)
                .map(isBlacklisted -> {
                    Map<String, Boolean> result = new HashMap<>();
                    result.put("isBlacklisted", isBlacklisted);
                    return Result.success("检查完成", result);
                });
    }

    // ==================== DTO类 ====================

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
