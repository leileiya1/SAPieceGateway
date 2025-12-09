package com.sapiece.nova.sapiecegateway.config;

import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

/**
 * R2DBC响应式数据库配置类
 * 配置R2DBC连接、事务管理、审计等
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Configuration
@EnableR2dbcRepositories(basePackages = "com.sapiece.nova.sapiecegateway.repository")
@EnableR2dbcAuditing  // 启用审计功能（自动填充创建时间、修改时间等）
public class R2dbcConfig {

    /**
     * 配置响应式事务管理器
     * 用于管理R2DBC的事务
     *
     * @param connectionFactory R2DBC连接工厂
     * @return ReactiveTransactionManager
     */
    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        log.info("初始化R2DBC响应式事务管理器");
        return new R2dbcTransactionManager(connectionFactory);
    }
}
