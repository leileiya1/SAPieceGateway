package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.repository.SysUserRepository;
import com.sapiece.nova.sapiecegateway.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 用户服务实现类
 *
 * @author SAPiece
 * @since 2025-11-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseClient databaseClient;

    /**
     * 修改密码
     * 修改成功后会更新 password_last_changed_at 字段，使所有旧Token失效
     *
     * @param username    用户名
     * @param oldPassword 旧密码（明文）
     * @param newPassword 新密码（明文）
     * @return 是否成功
     */
    @Override
    public Mono<Boolean> changePassword(String username, String oldPassword, String newPassword) {
        log.info("开始修改密码, username: {}", username);

        return sysUserRepository.findByUserName(username)
                .flatMap(user -> {
                    // 1. 验证旧密码是否正确
                    if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                        log.warn("旧密码验证失败, username: {}", username);
                        return Mono.just(false);
                    }

                    // 2. 加密新密码
                    String encodedNewPassword = passwordEncoder.encode(newPassword);

                    // 3. 更新密码和密码修改时间
                    // 注意：必须更新 password_last_changed_at 字段为当前时间，这样旧Token才会失效
                    String sql = "UPDATE sys_user " +
                            "SET password = :password, " +
                            "    password_last_changed_at = :passwordLastChangedAt, " +
                            "    update_time = :updateTime " +
                            "WHERE user_name = :userName";

                    LocalDateTime now = LocalDateTime.now();

                    return databaseClient.sql(sql)
                            .bind("password", encodedNewPassword)
                            .bind("passwordLastChangedAt", now)
                            .bind("updateTime", now)
                            .bind("userName", username)
                            .fetch()
                            .rowsUpdated()
                            .map(count -> {
                                boolean success = count > 0;
                                if (success) {
                                    log.info("密码修改成功, username: {}, passwordLastChangedAt: {}", username, now);
                                } else {
                                    log.warn("密码修改失败，未找到用户, username: {}", username);
                                }
                                return success;
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("用户不存在, username: {}", username);
                    return Mono.just(false);
                }))
                .onErrorResume(e -> {
                    log.error("修改密码异常, username: {}, error: {}", username, e.getMessage(), e);
                    return Mono.just(false);
                });
    }
}
