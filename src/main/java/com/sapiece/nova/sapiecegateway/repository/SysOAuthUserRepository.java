package com.sapiece.nova.sapiecegateway.repository;

import com.sapiece.nova.sapiecegateway.entity.SysOAuthUser;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth用户Repository接口
 * 提供响应式数据访问能力
 *
 * @author SAPiece
 * @since 2025-12-07
 */
@Repository
public interface SysOAuthUserRepository extends R2dbcRepository<SysOAuthUser, Long> {

    /**
     * 根据提供商和OAuth ID查询
     *
     * @param provider 提供商
     * @param oauthId  OAuth用户ID
     * @return OAuth用户
     */
    Mono<SysOAuthUser> findByProviderAndOauthId(String provider, String oauthId);

    /**
     * 根据系统用户ID查询所有绑定的OAuth账号
     *
     * @param userId 系统用户ID
     * @return OAuth用户列表
     */
    Flux<SysOAuthUser> findByUserId(Long userId);

    /**
     * 根据系统用户ID和提供商查询
     *
     * @param userId   系统用户ID
     * @param provider 提供商
     * @return OAuth用户
     */
    Mono<SysOAuthUser> findByUserIdAndProvider(Long userId, String provider);

    /**
     * 删除用户与指定提供商的绑定关系
     *
     * @param userId   系统用户ID
     * @param provider 提供商
     * @return 删除数量
     */
    Mono<Long> deleteByUserIdAndProvider(Long userId, String provider);
}
