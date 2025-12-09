package com.sapiece.nova.sapiecegateway.aspect;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.sapiece.nova.sapiecegateway.annotation.Idempotent;
import com.sapiece.nova.sapiecegateway.exception.BusinessException;
import com.sapiece.nova.sapiecegateway.service.IdempotentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;

/**
 * 幂等性切面
 * 拦截带有@Idempotent注解的方法，进行幂等性校验
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final IdempotentService idempotentService;

    /**
     * 定义切点：拦截所有带有@Idempotent注解的方法
     */
    @Pointcut("@annotation(com.sapiece.nova.sapiecegateway.annotation.Idempotent)")
    public void idempotentPointcut() {
    }

    /**
     * 环绕通知：进行幂等性校验
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("idempotentPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取@Idempotent注解
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        if (idempotent == null) {
            log.warn("未找到@Idempotent注解");
            return joinPoint.proceed();
        }

        // 生成幂等性键
        String idempotentKey = generateIdempotentKey(joinPoint, idempotent);
        log.debug("生成幂等性键: {}", idempotentKey);

        // 计算有效期
        Duration duration = Duration.ofMillis(idempotent.timeUnit().toMillis(idempotent.timeout()));

        // 进行幂等性校验
        return idempotentService.validateAndConsumeToken(idempotentKey, duration)
                .flatMap(valid -> {
                    if (valid) {
                        // 幂等性校验通过，执行目标方法
                        log.info("幂等性校验通过, key: {}", idempotentKey);
                        try {
                            Object result = joinPoint.proceed();
                            if (result instanceof Mono) {
                                return ((Mono<?>) result)
                                        .doOnSuccess(r -> log.debug("幂等性操作执行成功, key: {}", idempotentKey))
                                        .doOnError(error -> {
                                            // 操作失败，删除幂等性标记，允许重试
                                            log.warn("幂等性操作执行失败，删除标记允许重试, key: {}, error: {}",
                                                    idempotentKey, error.getMessage());
                                            idempotentService.deleteToken(idempotentKey).subscribe();
                                        });
                            }
                            return Mono.just(result);
                        } catch (Throwable e) {
                            log.error("执行目标方法异常, key: {}, error: {}", idempotentKey, e.getMessage());
                            // 操作失败，删除幂等性标记，允许重试
                            idempotentService.deleteToken(idempotentKey).subscribe();
                            return Mono.error(e);
                        }
                    } else {
                        // 幂等性校验失败，拒绝重复请求
                        log.warn("幂等性校验失败，拒绝重复请求, key: {}", idempotentKey);
                        String message = idempotent.message();
                        return Mono.error(new BusinessException(409, message));
                    }
                });
    }

    /**
     * 生成幂等性键
     *
     * @param joinPoint  连接点
     * @param idempotent 幂等性注解
     * @return 幂等性键
     */
    private String generateIdempotentKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        // 获取前缀
        String prefix = idempotent.prefix();
        if (prefix == null || prefix.isEmpty()) {
            // 默认使用类名+方法名作为前缀
            prefix = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        }

        // 根据不同的来源生成键
        String key = switch (idempotent.keySource()) {
            case HEADER ->
                // 从请求头获取
                    getKeyFromHeader(joinPoint, idempotent.keyField());
            case PARAMETER ->
                // 从请求参数获取
                    getKeyFromParameter(joinPoint, idempotent.keyField());
            default ->
                // 自动生成（基于方法参数）
                    generateAutoKey(joinPoint);
        };

        return prefix + ":" + key;
    }

    /**
     * 从请求头获取幂等性键
     *
     * @param joinPoint 连接点
     * @param keyField  字段名
     * @return 幂等性键
     */
    private String getKeyFromHeader(ProceedingJoinPoint joinPoint, String keyField) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof ServerWebExchange exchange) {
                String headerValue = exchange.getRequest().getHeaders().getFirst(keyField);
                if (headerValue != null && !headerValue.isEmpty()) {
                    log.debug("从请求头获取幂等性键, header: {}, value: {}", keyField, headerValue);
                    return headerValue;
                }
            }
        }

        log.warn("未从请求头找到幂等性键, header: {}, 使用自动生成", keyField);
        return generateAutoKey(joinPoint);
    }

    /**
     * 从请求参数获取幂等性键
     *
     * @param joinPoint 连接点
     * @param keyField  字段名
     * @return 幂等性键
     */
    private String getKeyFromParameter(ProceedingJoinPoint joinPoint, String keyField) {
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            // 假设第一个参数是包含幂等性键的对象
            Object firstArg = args[0];
            if (firstArg != null) {
                try {
                    // 尝试通过反射获取字段值
                    java.lang.reflect.Field field = firstArg.getClass().getDeclaredField(keyField);
                    field.setAccessible(true);
                    Object value = field.get(firstArg);
                    if (value != null) {
                        log.debug("从请求参数获取幂等性键, field: {}, value: {}", keyField, value);
                        return value.toString();
                    }
                } catch (Exception e) {
                    log.warn("从请求参数获取幂等性键失败, field: {}, error: {}", keyField, e.getMessage());
                }
            }
        }

        log.warn("未从请求参数找到幂等性键, field: {}, 使用自动生成", keyField);
        return generateAutoKey(joinPoint);
    }

    /**
     * 自动生成幂等性键
     * 基于方法参数的MD5哈希
     *
     * @param joinPoint 连接点
     * @return 幂等性键
     */
    private String generateAutoKey(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();

        // 过滤掉ServerWebExchange等不可序列化的参数
        Object[] serializableArgs = Arrays.stream(args)
                .filter(arg -> !(arg instanceof ServerWebExchange))
                .filter(arg -> !(arg instanceof org.springframework.web.server.WebFilterChain))
                .toArray();

        if (serializableArgs.length == 0) {
            log.debug("无可序列化参数，使用时间戳生成键");
            return String.valueOf(System.currentTimeMillis());
        }

        // 将参数序列化为JSON并计算MD5
        String argsJson = JSONUtil.toJsonStr(serializableArgs);
        String md5 = DigestUtil.md5Hex(argsJson);

        log.debug("自动生成幂等性键, md5: {}", md5);
        return md5;
    }
}
