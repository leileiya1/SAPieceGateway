package com.sapiece.nova.sapiecegateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 响应式配置类
 * 配置ReactiveRedisTemplate用于响应式Redis操作
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * 配置ReactiveRedisTemplate
     * 使用String序列化Key，Jackson序列化Value
     *
     * @param connectionFactory Redis连接工厂
     * @return ReactiveRedisTemplate
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        log.info("初始化ReactiveRedisTemplate");

        // String序列化器
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        StringRedisSerializer valueSerializer = new StringRedisSerializer();

        // 配置序列化上下文
        RedisSerializationContext<String, String> serializationContext =
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(keySerializer)
                        .value(valueSerializer)
                        .hashKey(keySerializer)
                        .hashValue(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

    /**
     * 配置ObjectMapper
     * 用于JSON序列化和反序列化
     * 增强配置以避免RouteDefinition循环引用导致的StackOverflowError
     *
     * @return ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        log.info("初始化ObjectMapper - 配置循环引用处理");
        ObjectMapper objectMapper = new ObjectMapper();

        // 注册Java 8时间模块
        objectMapper.registerModule(new JavaTimeModule());

        // 配置循环引用处理，避免StackOverflowError
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        // 忽略未知属性
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 禁用时间戳格式,使用ISO-8601格式
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        // 忽略null值
        objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        return objectMapper;
    }
}
