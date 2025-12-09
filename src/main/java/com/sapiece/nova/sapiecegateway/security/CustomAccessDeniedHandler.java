package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.common.ErrorCode;
import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 自定义访问拒绝处理器
 * 处理权限不足的请求，返回统一的JSON格式错误信息
 *
 * @author SAPiece
 * @since 2025-11-24
 */
@Slf4j
@Component
public class CustomAccessDeniedHandler implements ServerAccessDeniedHandler {

    /**
     * 处理权限不足（403 Forbidden）
     * 当用户已登录但没有访问权限时触发
     *
     * @param exchange ServerWebExchange对象
     * @param denied   访问拒绝异常
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        String path = exchange.getRequest().getPath().value();
        log.warn("权限不足，拒绝访问: path={}, error={}", path, denied.getMessage());

        // 使用 ResponseUtil 返回统一的 JSON 格式，使用 ErrorCode
        return ResponseUtil.error(exchange, ErrorCode.PERMISSION_DENIED);
    }
}
