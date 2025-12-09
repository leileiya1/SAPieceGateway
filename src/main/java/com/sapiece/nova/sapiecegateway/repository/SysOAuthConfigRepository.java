package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysOAuthConfig;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth配置Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Repository
public interface SysOAuthConfigRepository extends R2dbcRepository<SysOAuthConfig, Long> {

    /**
     * 根据提供商查询配置
     *
     * @param provider 提供商标识
     * @return OAuth配置
     */
    Mono<SysOAuthConfig> findByProvider(String provider);

    /**
     * 根据提供商和状态查询配置
     *
     * @param provider 提供商标识
     * @param status   状态
     * @return OAuth配置
     */
    Mono<SysOAuthConfig> findByProviderAndStatus(String provider, Integer status);

    /**
     * 查询所有启用的OAuth配置
     *
     * @return OAuth配置列表
     */
    @Query("SELECT * FROM sys_oauth_config WHERE status = 1 ORDER BY sort_order ASC")
    Flux<SysOAuthConfig> findAllEnabled();
}
