package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.entity.SysRole;
import com.sapiece.nova.sapiecegateway.repository.SysRoleRepository;
import com.sapiece.nova.sapiecegateway.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 系统角色Service实现类
 * 实现角色相关的业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleRepository roleRepository;

    /**
     * 根据角色编码查询角色
     *
     * @param roleCode 角色编码
     * @return 角色信息（响应式）
     */
    @Override
    public Mono<SysRole> findByRoleCode(String roleCode) {
        log.debug("查询角色信息, roleCode: {}", roleCode);
        return roleRepository.findByRoleCode(roleCode)
                .doOnSuccess(role -> {
                    if (role != null) {
                        log.info("成功查询到角色信息, roleId: {}, roleCode: {}", role.getId(), role.getRoleCode());
                    } else {
                        log.warn("角色不存在, roleCode: {}", roleCode);
                    }
                })
                .doOnError(error -> log.error("查询角色信息失败, roleCode: {}, error: {}", roleCode, error.getMessage()));
    }

    /**
     * 根据用户ID查询该用户的所有角色
     *
     * @param userId 用户ID
     * @return 角色列表（响应式）
     */
    @Override
    public Flux<SysRole> findRolesByUserId(Long userId) {
        log.debug("根据用户ID查询角色列表, userId: {}", userId);
        return roleRepository.findRolesByUserId(userId)
                .doOnComplete(() -> log.info("成功查询用户角色列表, userId: {}", userId))
                .doOnError(error -> log.error("查询用户角色列表失败, userId: {}, error: {}", userId, error.getMessage()));
    }

    /**
     * 根据用户ID查询该用户的所有角色编码
     *
     * @param userId 用户ID
     * @return 角色编码列表（响应式）
     */
    @Override
    public Flux<String> findRoleCodesByUserId(Long userId) {
        log.debug("根据用户ID查询角色编码列表, userId: {}", userId);
        return roleRepository.findRolesByUserId(userId)
                .map(SysRole::getRoleCode)
                .doOnComplete(() -> log.info("成功查询用户角色编码列表, userId: {}", userId))
                .doOnError(error -> log.error("查询用户角色编码列表失败, userId: {}, error: {}", userId, error.getMessage()));
    }
}
