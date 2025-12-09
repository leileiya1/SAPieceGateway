package com.sapiece.nova.sapiecegateway.security;

import com.sapiece.nova.sapiecegateway.common.ErrorCode;
import com.sapiece.nova.sapiecegateway.util.ResponseUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 自定义认证入口点
 * 处理未认证的请求，返回统一的JSON格式错误信息
 *
 * @author SAPiece
 * @since 2025-11-24
 */
@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    /**
     * 处理认证失败（401 Unauthorized）
     * 当用户未登录或Token无效时触发
     *
     * @param exchange ServerWebExchange对象
     * @param e        认证异常
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException e) {
        String path = exchange.getRequest().getPath().value();
        log.warn("认证失败，拒绝访问: path={}, error={}", path, e.getMessage());

        // 使用 ResponseUtil 返回统一的 JSON 格式，使用 ErrorCode
        return ResponseUtil.error(exchange, ErrorCode.AUTH_TOKEN_INVALID);
    }
}
