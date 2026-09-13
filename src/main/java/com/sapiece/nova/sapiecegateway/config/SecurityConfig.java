package com.sapiece.nova.sapiecegateway.config;

import com.sapiece.nova.sapiecegateway.security.CustomAccessDeniedHandler;
import com.sapiece.nova.sapiecegateway.security.CustomAuthenticationEntryPoint;
import com.sapiece.nova.sapiecegateway.security.CustomReactiveUserDetailsService;
import com.sapiece.nova.sapiecegateway.security.JwtSecurityContextRepository;
import com.sapiece.nova.sapiecegateway.security.RouteReactiveAuthorizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security WebFlux响应式安全配置
 * 配置JWT认证、权限控制、异常处理等
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity  // 启用方法级别的权限控制
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomReactiveUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final JwtSecurityContextRepository jwtSecurityContextRepository;
    private final RouteReactiveAuthorizationManager routeReactiveAuthorizationManager;

    /**
     * 配置响应式认证管理器
     * 用于处理用户认证逻辑
     *
     * @return ReactiveAuthenticationManager
     */
    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        log.info("初始化响应式认证管理器");
        UserDetailsRepositoryReactiveAuthenticationManager authenticationManager =
                new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder);
        return authenticationManager;
    }

    /**
     * 配置安全过滤器链
     * 这是Spring Security的核心配置
     *
     * @param http ServerHttpSecurity
     * @return SecurityWebFilterChain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        log.info("初始化Spring Security过滤器链");

        return http
                // 禁用CSRF（跨站请求伪造）保护，因为使用的是JWT Token
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // 禁用HTTP Basic认证
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                // 禁用表单登录
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                // 禁用登出
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                // 配置 JWT SecurityContext 仓储（核心）
                // JwtSecurityContextRepository 负责从请求中提取 JWT、验证并创建 SecurityContext
                // 这是 Spring Security WebFlux 的标准认证方式
                .securityContextRepository(jwtSecurityContextRepository)

                // 配置异常处理（返回统一JSON格式）
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        // 未认证处理：返回401 JSON响应
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        // 无权限处理：返回403 JSON响应
                        .accessDeniedHandler(customAccessDeniedHandler)
                )

                // 配置URL访问权限
                .authorizeExchange(authorize -> authorize
                        // ==================== 公开接口（无需登录）====================
                        // 认证相关接口（登录、注册等）
                        .pathMatchers("/auth/**").permitAll()
                        // Actuator监控端点
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/actuator/prometheus").permitAll()
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
                        // Swagger文档
                        .pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 健康检查
                        .pathMatchers("/health", "/health/**").permitAll()
                        // Nova 公开接口：验证码（注册/找回密码前调用）
                        .pathMatchers("/v1/notification/captcha", "/v1/notification/captcha/verify").permitAll()
                        // Nova 公开接口：支付宝异步回调
                        .pathMatchers("/v1/payments/alipay/notify").permitAll()

                        // ==================== 需要认证的接口 ====================
                        // 使用自定义权限管理器进行细粒度权限验证
                        // 权限规则从 sys_gateway_route 表读取
                        .anyExchange().access(routeReactiveAuthorizationManager)
                )

                // 构建安全过滤器链
                .build();
    }
}
