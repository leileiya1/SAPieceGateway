package com.sapiece.nova.sapiecegateway.aspect;


import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * 方法执行日志切面
 * 记录Service层和Controller层方法的执行情况
 * 包括：方法名、参数、返回值、耗时等
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Aspect
// @Component  // 禁用AOP日志，改用Spring Cloud Gateway的GlobalFilter
public class MethodLogAspect {

    /**
     * 日期时间格式化器
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 最大参数日志长度
     */
    private static final int MAX_PARAM_LENGTH = 500;

    /**
     * 定义切点：拦截Service层的所有公共方法
     */
    @Pointcut("execution(public * com.sapiece.nova.sapiecegateway.service..*.*(..))")
    public void servicePointcut() {
    }

    /**
     * 定义切点：拦截Controller层的所有公共方法
     */
    @Pointcut("execution(public * com.sapiece.nova.sapiecegateway.controller..*.*(..))")
    public void controllerPointcut() {
    }

    /**
     * 定义切点：拦截Repository层的所有公共方法
     */
    @Pointcut("execution(public * com.sapiece.nova.sapiecegateway.repository..*.*(..))")
    public void repositoryPointcut() {
    }

    /**
     * 环绕通知：记录Service层方法执行日志
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("servicePointcut()")
    public Object aroundService(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Service");
    }

    /**
     * 环绕通知：记录Controller层方法执行日志
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("controllerPointcut()")
    public Object aroundController(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Controller");
    }

    /**
     * 环绕通知：记录Repository层方法执行日志
     *
     * @param joinPoint 连接点
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    @Around("repositoryPointcut()")
    public Object aroundRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Repository");
    }

    /**
     * 记录方法执行日志
     *
     * @param joinPoint 连接点
     * @param layer     层级名称（Service/Controller/Repository）
     * @return 方法执行结果
     * @throws Throwable 异常
     */
    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        Object[] args = joinPoint.getArgs();

        // 记录方法开始执行
        LocalDateTime startTime = LocalDateTime.now();
        long startMillis = System.currentTimeMillis();

        logMethodStart(layer, className, methodName, args, startTime);

        try {
            // 执行目标方法
            Object result = joinPoint.proceed();

            // 如果返回值是Mono或Flux，需要特殊处理
            if (result instanceof Mono) {
                return handleMonoResult((Mono<?>) result, layer, className, methodName, startMillis);
            } else if (result instanceof Flux) {
                return handleFluxResult((Flux<?>) result, layer, className, methodName, startMillis);
            } else {
                // 同步方法，直接记录结束日志
                long duration = System.currentTimeMillis() - startMillis;
                logMethodEnd(layer, className, methodName, duration, result, null);
                return result;
            }
        } catch (Throwable e) {
            // 记录异常
            long duration = System.currentTimeMillis() - startMillis;
            logMethodEnd(layer, className, methodName, duration, null, e);
            throw e;
        }
    }

    /**
     * 处理Mono类型的返回值
     * 在Mono完成时记录日志
     */
    private Mono<?> handleMonoResult(Mono<?> mono, String layer, String className,
                                     String methodName, long startMillis) {
        return mono
                .doOnSuccess(result -> {
                    long duration = System.currentTimeMillis() - startMillis;
                    logMethodEnd(layer, className, methodName, duration, result, null);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startMillis;
                    logMethodEnd(layer, className, methodName, duration, null, error);
                });
    }

    /**
     * 处理Flux类型的返回值
     * 在Flux完成时记录日志
     */
    private Flux<?> handleFluxResult(Flux<?> flux, String layer, String className,
                                     String methodName, long startMillis) {
        return flux
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startMillis;
                    logMethodEnd(layer, className, methodName, duration, "Flux completed", null);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startMillis;
                    logMethodEnd(layer, className, methodName, duration, null, error);
                });
    }

    /**
     * 记录方法开始执行日志
     */
    private void logMethodStart(String layer, String className, String methodName,
                               Object[] args, LocalDateTime startTime) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n┌─────────────────────────────────────────────────────────────┐\n");
        logMessage.append(String.format("│ [%s] 方法开始执行\n", layer));
        logMessage.append(String.format("│ 类名        : %s\n", className));
        logMessage.append(String.format("│ 方法名      : %s\n", methodName));
        logMessage.append(String.format("│ 开始时间    : %s\n", startTime.format(DATE_TIME_FORMATTER)));

        if (args != null && args.length > 0) {
            logMessage.append(String.format("│ 方法参数    : %s\n", formatArgs(args)));
        }

        logMessage.append("└─────────────────────────────────────────────────────────────┘");

        log.debug(logMessage.toString());
    }

    /**
     * 记录方法结束执行日志
     */
    private void logMethodEnd(String layer, String className, String methodName,
                             long duration, Object result, Throwable error) {
        LocalDateTime endTime = LocalDateTime.now();

        StringBuilder logMessage = new StringBuilder();
        logMessage.append("\n┌─────────────────────────────────────────────────────────────┐\n");
        logMessage.append(String.format("│ [%s] 方法执行结束\n", layer));
        logMessage.append(String.format("│ 类名        : %s\n", className));
        logMessage.append(String.format("│ 方法名      : %s\n", methodName));
        logMessage.append(String.format("│ 结束时间    : %s\n", endTime.format(DATE_TIME_FORMATTER)));
        logMessage.append(String.format("│ 执行耗时    : %d ms\n", duration));

        if (error != null) {
            logMessage.append("│ 执行状态    : 失败 ❌\n");
            logMessage.append(String.format("│ 异常类型    : %s\n", error.getClass().getSimpleName()));
            logMessage.append(String.format("│ 异常信息    : %s\n", error.getMessage()));
        } else {
            logMessage.append("│ 执行状态    : 成功 ✓\n");
            if (result != null) {
                logMessage.append(String.format("│ 返回值      : %s\n", formatResult(result)));
            }
        }

        logMessage.append("└─────────────────────────────────────────────────────────────┘");

        if (error != null) {
            log.error(logMessage.toString(), error);
        } else if (duration > 1000) {
            // 执行耗时超过1秒，记录警告
            log.warn(logMessage.toString());
            log.warn("⚠️ 慢方法警告 - {}.{}, duration: {} ms", className, methodName, duration);
        } else {
            log.debug(logMessage.toString());
        }
    }

    /**
     * 格式化方法参数
     */
    private String formatArgs(Object[] args) {
        try {
            String argsStr = Arrays.toString(args);
            if (argsStr.length() > MAX_PARAM_LENGTH) {
                return argsStr.substring(0, MAX_PARAM_LENGTH) + "...";
            }
            return argsStr;
        } catch (Exception e) {
            return "无法序列化参数";
        }
    }

    /**
     * 格式化返回值
     */
    private String formatResult(Object result) {
        try {
            String resultStr = result.toString();
            if (resultStr.length() > MAX_PARAM_LENGTH) {
                return resultStr.substring(0, MAX_PARAM_LENGTH) + "...";
            }
            return resultStr;
        } catch (Exception e) {
            return "无法序列化返回值";
        }
    }
}
