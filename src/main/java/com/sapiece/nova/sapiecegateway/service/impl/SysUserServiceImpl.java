package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.entity.SysUser;
import com.sapiece.nova.sapiecegateway.repository.SysUserRepository;
import com.sapiece.nova.sapiecegateway.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * 系统用户Service实现类
 * 实现用户相关的业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 根据用户名查询用户
     *
     * @param userName 用户名
     * @return 用户信息（响应式）
     */
    @Override
    public Mono<SysUser> findByUserName(String userName) {
        log.debug("查询用户信息, userName: {}", userName);
        return userRepository.findByUserName(userName)
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("成功查询到用户信息, userId: {}, userName: {}", user.getId(), user.getUserName());
                    } else {
                        log.warn("用户不存在, userName: {}", userName);
                    }
                })
                .doOnError(error -> log.error("查询用户信息失败, userName: {}, error: {}", userName, error.getMessage()));
    }

    /**
     * 根据用户名查询启用状态的用户
     *
     * @param userName 用户名
     * @return 用户信息（响应式）
     */
    @Override
    public Mono<SysUser> findActiveUserByUserName(String userName) {
        log.debug("查询启用状态的用户信息, userName: {}", userName);
        return userRepository.findActiveUser(userName, 1, 0)
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("成功查询到启用状态的用户, userId: {}, userName: {}", user.getId(), user.getUserName());
                    } else {
                        log.warn("未找到启用状态的用户, userName: {}", userName);
                    }
                })
                .doOnError(error -> log.error("查询启用状态用户失败, userName: {}, error: {}", userName, error.getMessage()));
    }

    /**
     * 根据用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户信息（响应式）
     */
    @Override
    public Mono<SysUser> findById(Long userId) {
        log.debug("根据ID查询用户信息, userId: {}", userId);
        return userRepository.findById(userId)
                .doOnSuccess(user -> {
                    if (user != null) {
                        log.info("成功查询到用户信息, userId: {}, userName: {}", user.getId(), user.getUserName());
                    } else {
                        log.warn("用户不存在, userId: {}", userId);
                    }
                })
                .doOnError(error -> log.error("根据ID查询用户失败, userId: {}, error: {}", userId, error.getMessage()));
    }

    /**
     * 更新用户最后登录信息
     *
     * @param userId  用户ID
     * @param loginIp 登录IP
     * @return 更新后的用户信息（响应式）
     */
    @Override
    public Mono<SysUser> updateLoginInfo(Long userId, String loginIp) {
        log.debug("更新用户登录信息, userId: {}, loginIp: {}", userId, loginIp);
        return userRepository.findById(userId)
                .flatMap(user -> {
                    // 更新登录信息
                    user.setLoginIp(loginIp);
                    user.setLoginTime(LocalDateTime.now());
                    user.setLastActiveAt(LocalDateTime.now());
                    return userRepository.save(user);
                })
                .doOnSuccess(user -> log.info("成功更新用户登录信息, userId: {}, loginIp: {}", userId, loginIp))
                .doOnError(error -> log.error("更新用户登录信息失败, userId: {}, error: {}", userId, error.getMessage()));
    }

    /**
     * 验证用户密码
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配（响应式）
     */
    @Override
    public Mono<Boolean> verifyPassword(String rawPassword, String encodedPassword) {
        log.debug("验证用户密码");
        return Mono.fromCallable(() -> passwordEncoder.matches(rawPassword, encodedPassword))
                .doOnSuccess(matches -> {
                    if (matches) {
                        log.debug("密码验证成功");
                    } else {
                        log.warn("密码验证失败");
                    }
                })
                .doOnError(error -> log.error("密码验证异常, error: {}", error.getMessage()));
    }
}
