package com.sapiece.nova.sapiecegateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器配置类
 * 将PasswordEncoder单独提取到独立的配置类中，避免循环依赖
 *
 * @author SAPiece
 * @since 2025-11-09
 */
@Slf4j
@Configuration
public class PasswordEncoderConfig {

    /**
     * 配置密码编码器
     * 使用BCrypt加密算法
     *
     * @return PasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        log.info("初始化BCrypt密码编码器");
        return new BCryptPasswordEncoder();
    }
}
