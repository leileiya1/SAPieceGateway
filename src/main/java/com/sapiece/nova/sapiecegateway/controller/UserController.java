package com.sapiece.nova.sapiecegateway.controller;

import com.sapiece.nova.sapiecegateway.common.Result;
import com.sapiece.nova.sapiecegateway.exception.BusinessException;
import com.sapiece.nova.sapiecegateway.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * 用户管理控制器
 * 提供用户相关的管理功能，如修改密码等
 *
 * @author SAPiece
 * @since 2025-11-24
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户相关的管理接口")
public class UserController {

    private final UserService userService;

    /**
     * 修改密码
     * 修改成功后，用户所有旧Token将失效，需要重新登录
     *
     * @param request 修改密码请求
     * @return 修改结果
     */
    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "修改当前用户的密码，修改后旧Token将失效")
    @PreAuthorize("isAuthenticated()")
    public Mono<Result<String>> changePassword(@RequestBody ChangePasswordRequest request) {
        if (request == null || request.getOldPassword() == null || request.getOldPassword().isBlank()) {
            throw new IllegalArgumentException("旧密码不能为空");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 12
                || request.getNewPassword().length() > 72) {
            throw new IllegalArgumentException("新密码长度必须在12到72个字符之间");
        }
        if (request.getNewPassword().equals(request.getOldPassword())) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    String username = auth.getName();
                    log.info("用户修改密码, username: {}", username);

                    return userService.changePassword(username, request.getOldPassword(), request.getNewPassword())
                            .map(success -> {
                                Result<String> result;
                                if (success) {
                                    log.info("密码修改成功, username: {}", username);
                                    result = Result.success("密码修改成功，请重新登录", null);
                                } else {
                                    log.warn("密码修改失败, username: {}", username);
                                    throw new BusinessException(400, "密码修改失败，请检查旧密码是否正确");
                                }
                                return result;
                            });
                });
    }

    /**
     * 修改密码请求
     */
    @Data
    public static class ChangePasswordRequest {
        @Parameter(description = "旧密码", required = true)
        private String oldPassword;

        @Parameter(description = "新密码", required = true)
        private String newPassword;
    }
}
