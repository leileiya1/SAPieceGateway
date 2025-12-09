package com.sapiece.nova.sapiecegateway.service.impl;

import com.sapiece.nova.sapiecegateway.entity.SysMenu;
import com.sapiece.nova.sapiecegateway.repository.SysMenuRepository;
import com.sapiece.nova.sapiecegateway.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 系统菜单权限Service实现类
 * 实现菜单权限相关的业务逻辑
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuRepository menuRepository;

    /**
     * 根据用户ID查询该用户的所有菜单权限
     *
     * @param userId 用户ID
     * @return 菜单权限列表（响应式）
     */
    @Override
    public Flux<SysMenu> findMenusByUserId(Long userId) {
        log.debug("根据用户ID查询菜单权限列表, userId: {}", userId);
        return menuRepository.findMenusByUserId(userId)
                .doOnComplete(() -> log.info("成功查询用户菜单权限列表, userId: {}", userId))
                .doOnError(error -> log.error("查询用户菜单权限列表失败, userId: {}, error: {}", userId, error.getMessage()));
    }

    /**
     * 根据角色ID查询该角色的所有菜单权限
     *
     * @param roleId 角色ID
     * @return 菜单权限列表（响应式）
     */
    @Override
    public Flux<SysMenu> findMenusByRoleId(Long roleId) {
        log.debug("根据角色ID查询菜单权限列表, roleId: {}", roleId);
        return menuRepository.findMenusByRoleId(roleId)
                .doOnComplete(() -> log.info("成功查询角色菜单权限列表, roleId: {}", roleId))
                .doOnError(error -> log.error("查询角色菜单权限列表失败, roleId: {}, error: {}", roleId, error.getMessage()));
    }

    /**
     * 根据用户ID查询该用户的所有权限标识
     *
     * @param userId 用户ID
     * @return 权限标识列表（响应式）
     */
    @Override
    public Flux<String> findPermissionCodesByUserId(Long userId) {
        log.debug("根据用户ID查询权限标识列表, userId: {}", userId);
        return menuRepository.findPermissionCodesByUserId(userId)
                .doOnComplete(() -> log.info("成功查询用户权限标识列表, userId: {}", userId))
                .doOnError(error -> log.error("查询用户权限标识列表失败, userId: {}, error: {}", userId, error.getMessage()));
    }
}
