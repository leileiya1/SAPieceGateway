package com.sapiece.nova.sapiecegateway.service;

import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 幂等性Service接口
 * 提供幂等性Token的生成、验证和删除
 *
 * @author SAPiece
 * @since 2025-11-08
 */
public interface IdempotentService {

    /**
     * 生成幂等性Token
     * 客户端在提交请求前需要先获取Token
     *
     * @param prefix 幂等性键前缀
     * @return 幂等性Token（响应式）
     */
    Mono<String> generateToken(String prefix);

    /**
     * 验证并消费幂等性Token
     * 验证成功后Token会被删除，防止重复使用
     *
     * @param key      幂等性键
     * @param duration 有效期
     * @return 是否验证成功（响应式）
     */
    Mono<Boolean> validateAndConsumeToken(String key, Duration duration);

    /**
     * 检查幂等性Token是否存在
     *
     * @param key 幂等性键
     * @return 是否存在（响应式）
     */
    Mono<Boolean> exists(String key);

    /**
     * 删除幂等性Token
     *
     * @param key 幂等性键
     * @return 是否成功（响应式）
     */
    Mono<Boolean> deleteToken(String key);

    /**
     * 设置幂等性标记
     * 用于标记某个操作正在进行中
     *
     * @param key      幂等性键
     * @param duration 有效期
     * @return 是否成功（响应式）
     */
    Mono<Boolean> setIdempotentMark(String key, Duration duration);
}
