package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 提供用户登录、登出、Token刷新等接口
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录、登出、Token刷新等接口")
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录接口
     *
     * @param loginRequest 登录请求
     * @return 登录结果（包含Token）
     */
    @Operation(summary = "用户登录", description = "使用用户名和密码登录获取Token")
    @PostMapping("/login")
    public Mono<Result<Map<String, Object>>> login(@RequestBody LoginRequest loginRequest) {
        log.info("用户登录请求, userName: {}", loginRequest.getUserName());

        return authService.login(loginRequest.getUserName(), loginRequest.getPassword())
                .map(data -> Result.success("登录成功", data))
                .onErrorResume(e -> {
                    log.error("用户登录失败, userName: {}, error: {}", loginRequest.getUserName(), e.getMessage());
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

    /**
     * 用户登出接口
     * 将当前Token加入黑名单，使其失效
     *
     * @param token JWT Token
     * @return 登出结果
     */
    @Operation(summary = "用户登出", description = "将当前Token加入黑名单，使其失效")
    @Parameter(name = "Authorization", description = "JWT Token", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
    @PostMapping("/logout")
    public Mono<Result<Map<String, Object>>> logout(@RequestHeader("Authorization") String token) {
        log.info("用户登出请求");

        return authService.logout(token)
                .map(success -> {
                    Map<String, Object> resultData = new HashMap<>();
                    resultData.put("success", success);
                    return success ?
                            Result.success("登出成功", resultData) :
                            Result.<Map<String, Object>>error("登出失败");
                })
                .onErrorResume(e -> {
                    log.error("用户登出失败, error: {}", e.getMessage());
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

    /**
     * 刷新Token接口（旧接口，保留兼容性）
     *
     * @param token 旧Token
     * @return 新Token
     */
    @Operation(summary = "刷新Token（旧接口）", description = "使用旧Token换取新Token，建议使用/auth/refresh/token接口")
    @Parameter(name = "Authorization", description = "旧Token", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
    @PostMapping("/refresh")
    public Mono<Result<Map<String, String>>> refreshToken(@RequestHeader("Authorization") String token) {
        log.info("Token刷新请求（旧接口）");

        return authService.refreshToken(token)
                .map(newToken -> {
                    Map<String, String> resultData = new HashMap<>();
                    resultData.put("token", newToken);
                    return Result.success("Token刷新成功", resultData);
                })
                .onErrorResume(e -> {
                    log.error("Token刷新失败, error: {}", e.getMessage());
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

    /**
     * 使用Refresh Token刷新Access Token（双Token模式）
     *
     * @param refreshTokenRequest 包含refreshToken的请求体
     * @return 新的Token对（accessToken + refreshToken）
     */
    @Operation(summary = "刷新Access Token", description = "使用Refresh Token换取新的Access Token和Refresh Token（双Token模式）")
    @PostMapping("/refresh/token")
    public Mono<Result<Map<String, Object>>> refreshAccessToken(@RequestBody RefreshTokenRequest refreshTokenRequest) {
        log.info("Access Token刷新请求（双Token模式）");

        return authService.refreshAccessToken(refreshTokenRequest.getRefreshToken())
                .map(tokenData -> Result.success("Token刷新成功", tokenData))
                .onErrorResume(e -> {
                    log.error("Access Token刷新失败, error: {}", e.getMessage());
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

    /**
     * 获取当前用户信息接口
     *
     * @param token JWT Token
     * @return 用户信息
     */
    @Operation(summary = "获取用户信息", description = "根据Token获取当前登录用户信息")
    @Parameter(name = "Authorization", description = "JWT Token", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
    @GetMapping("/info")
    public Mono<Result<Map<String, Object>>> getUserInfo(@RequestHeader("Authorization") String token) {
        log.info("获取当前用户信息请求");

        return authService.getTokenInfo(token)
                .map(tokenInfo -> Result.success("获取用户信息成功", tokenInfo))
                .onErrorResume(e -> {
                    log.error("获取用户信息失败, error: {}", e.getMessage());
                    return Mono.just(Result.error(e.getMessage()));
                });
    }

    /**
     * 登录请求DTO
     */
    @Data
    @Schema(description = "登录请求参数")
    public static class LoginRequest {
        /**
         * 用户名
         */
        @Schema(description = "用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
        private String userName;

        /**
         * 密码
         */
        @Schema(description = "密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;
    }

    /**
     * 刷新Token请求DTO
     */
    @Data
    @Schema(description = "刷新Token请求参数")
    public static class RefreshTokenRequest {
        /**
         * Refresh Token
         */
        @Schema(description = "Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
        private String refreshToken;
    }
}
