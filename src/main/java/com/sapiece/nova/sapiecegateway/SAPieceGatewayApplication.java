package com.sapiece.nova.sapiecegateway;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * SAPiece Gateway 主应用启动类
 *
 * <p>功能特性：</p>
 * <ul>
 *     <li>🔐 Spring Security响应式权限认证 - 基于JWT的无状态认证</li>
 *     <li>⚡ Redis分布式限流 - 令牌桶算法，支持多实例部署</li>
 *     <li>🛡️ Token黑名单 - 用户登出、强制下线功能</li>
 *     <li>🌐 IP黑白名单 - 支持CIDR格式的IP段过滤</li>
 *     <li>🔄 熔断降级 - Resilience4j熔断器，服务自动降级</li>
 *     <li>🔒 接口幂等性 - 防止重复提交（订单、支付等）</li>
 *     <li>🔐 参数签名验证 - 防止参数篡改和重放攻击</li>
 *     <li>📝 AOP切面日志 - 统一请求日志、方法执行日志</li>
 *     <li>⚠️ 全局异常处理 - 统一异常响应格式</li>
 * </ul>
 *
 * <p>技术栈：</p>
 * <ul>
 *     <li>Spring Boot 4.x</li>
 *     <li>Spring Cloud Gateway（WebFlux响应式）</li>
 *     <li>Spring Security Reactive</li>
 *     <li>R2DBC MySQL（响应式数据库访问）</li>
 *     <li>Redis Reactive（缓存、限流、黑名单）</li>
 *     <li>Resilience4j（熔断降级）</li>
 *     <li>JWT 0.12.6（认证令牌）</li>
 * </ul>
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@SpringBootApplication
public class SAPieceGatewayApplication {

    static void main(String[] args) {
        log.info("====================================");
        log.info("SAPiece Gateway 正在启动...");
        log.info("====================================");

        // 启动Spring Boot应用
        ConfigurableApplicationContext context = SpringApplication.run(SAPieceGatewayApplication.class, args);

        // 打印启动成功信息
        printStartupInfo(context.getEnvironment());
    }

    /**
     * 打印应用启动成功信息
     * 包括访问地址、激活的配置文件、启动耗时等
     *
     * @param env Spring环境对象
     */
    private static void printStartupInfo(Environment env) {
        String protocol = "http";
        if (env.getProperty("server.ssl.enabled", Boolean.class, false)) {
            protocol = "https";
        }

        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = "localhost";

        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("无法获取主机地址，使用localhost");
        }

        String[] activeProfiles = env.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "default";

        log.info("""
                        ====================================================================================================
                            ✅ SAPiece Gateway 启动成功！
                        ====================================================================================================
                            🌐 本地访问地址:     {}://localhost:{}{}
                            🌐 外部访问地址:     {}://{}:{}{}
                            📊 激活的配置文件:   {}
                            📝 接口文档地址:     {}://{}:{}{}/doc.html
                            🔍 健康检查地址:     {}://{}:{}{}/actuator/health
                        ====================================================================================================""",
                protocol, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath,
                profile,
                protocol, hostAddress, serverPort, contextPath,
                protocol, hostAddress, serverPort, contextPath
        );
    }

    /**
     * 应用启动完成后的初始化任务
     * 在所有Bean加载完成后执行
     */
    @Slf4j
    @Component
    static class StartupRunner implements ApplicationRunner {

        private final Environment environment;

        public StartupRunner(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void run(@NonNull ApplicationArguments args) {
            log.info("====================================");
            log.info("应用初始化任务开始执行...");
            log.info("====================================");

            // 打印核心配置信息
            printCoreConfig();

            log.info("====================================");
            log.info("应用初始化任务执行完成！");
            log.info("====================================");
        }

        /**
         * 打印核心配置信息
         */
        private void printCoreConfig() {
            // JWT配置
            String jwtExpiration = environment.getProperty("jwt.expiration", "未配置");
            log.info("JWT Token有效期: {} ms", jwtExpiration);

            // Redis配置
            String redisHost = environment.getProperty("spring.data.redis.host", "未配置");
            String redisPort = environment.getProperty("spring.data.redis.port", "未配置");
            log.info("Redis连接地址: {}:{}", redisHost, redisPort);

            // R2DBC配置
            String r2dbcUrl = environment.getProperty("spring.r2dbc.url", "未配置");
            log.info("R2DBC数据库连接: {}", r2dbcUrl.replaceAll("password=[^&]*", "password=******"));

            // 限流配置
            String rateLimitEnabled = environment.getProperty("rate-limit.enabled", "false");
            String rateLimitQps = environment.getProperty("rate-limit.default-qps", "未配置");
            log.info("接口限流: {}, 默认QPS: {}", rateLimitEnabled.equals("true") ? "已启用" : "已禁用", rateLimitQps);

            // IP过滤配置
            String ipBlacklistEnabled = environment.getProperty("ip-filter.blacklist-enabled", "false");
            String ipWhitelistEnabled = environment.getProperty("ip-filter.whitelist-enabled", "false");
            log.info("IP黑名单: {}, IP白名单: {}",
                    ipBlacklistEnabled.equals("true") ? "已启用" : "已禁用",
                    ipWhitelistEnabled.equals("true") ? "已启用" : "已禁用");

            // 签名验证配置
            String signatureEnabled = environment.getProperty("signature.enabled", "false");
            String signatureAlgorithm = environment.getProperty("signature.algorithm", "未配置");
            log.info("参数签名验证: {}, 算法: {}",
                    signatureEnabled.equals("true") ? "已启用" : "已禁用",
                    signatureAlgorithm);
        }
    }
}
