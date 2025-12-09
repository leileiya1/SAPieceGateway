# SAPiece Gateway - 企业级响应式API网关

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-2025.0.0-blue.svg)](https://spring.io/projects/spring-cloud-gateway)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-Reactive-red.svg)](https://redis.io/)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-2.2.0-yellow.svg)](https://resilience4j.readme.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**基于 Spring Boot 3.x 和 Spring Cloud Gateway 构建的企业级响应式API网关**

*集成JWT认证、动态路由、权限控制、限流熔断、日志监控等完整的网关解决方案*

[快速开始](#-快速开始) • [核心功能](#-核心功能) • [API文档](#-api接口文档) • [部署指南](#-部署指南) • [常见问题](#-常见问题)

</div>

---

## 📖 目录

- [项目简介](#项目简介)
- [核心功能](#-核心功能)
- [技术架构](#-技术架构)
- [项目结构](#-项目结构)
- [快速开始](#-快速开始)
- [详细配置](#-详细配置)
- [动态路由](#-动态路由)
- [权限控制](#-权限控制)
- [API接口文档](#-api接口文档)
- [部署指南](#-部署指南)
- [监控运维](#-监控运维)
- [性能调优](#-性能调优)
- [安全最佳实践](#-安全最佳实践)
- [常见问题](#-常见问题)

---

## 项目简介

SAPiece Gateway 是一款基于 **Spring Cloud Gateway** 和 **WebFlux** 构建的企业级响应式API网关，采用完全异步非阻塞架构，为微服务架构提供统一的流量入口和安全防护。

### 🎯 设计理念

- **高性能**: 基于 Reactor 响应式编程模型，支持高并发场景
- **高可用**: 集成熔断降级、请求重试、限流保护等容错机制
- **易扩展**: 模块化设计，支持自定义过滤器和路由策略
- **易运维**: 完善的日志、监控、链路追踪支持

### 🌟 适用场景

- ✅ 微服务架构统一网关入口
- ✅ API接口的安全认证与鉴权
- ✅ 流量控制与限流保护
- ✅ 服务熔断与降级
- ✅ 请求日志与监控
- ✅ 跨域资源共享（CORS）
- ✅ API版本管理与灰度发布

---

## 🎯 核心功能

### 🔐 安全认证

- [x] **JWT认证机制**
  - 完整的Token生成、验证、刷新流程
  - 支持Token黑名单（基于Redis）
  - 自动Token续期机制
  - 多端Token隔离（支持Web、APP等不同端）

- [x] **RBAC权限控制**
  - 用户-角色-权限三层模型
  - 基于注解的方法级权限控制
  - 动态权限加载与缓存
  - 权限继承与级联

- [x] **安全防护**
  - IP黑白名单过滤
  - 参数签名验证（防篡改、防重放）
  - XSS/SQL注入防护
  - HTTPS强制跳转

### 🚦 流量管理

- [x] **接口限流**
  - 基于Redis令牌桶算法
  - 支持全局限流、IP限流、用户限流
  - QPS可配置，支持突发流量
  - 限流降级响应

- [x] **熔断降级**
  - Resilience4j熔断器集成
  - 故障率、慢调用双重熔断策略
  - 半开状态自动恢复
  - 自定义降级响应

- [x] **请求重试**
  - 智能重试机制（仅重试幂等请求）
  - 指数退避策略
  - 可配置重试次数和间隔
  - 异常分类重试

### 🛣️ 动态路由

- [x] **数据库驱动路由**
  - 基于sys_menu表的动态路由配置
  - 支持热更新，无需重启
  - 可视化路由管理界面（配合前端）
  - 路由版本管理

- [x] **灵活配置**
  - 支持Path、Method、Header等多种断言
  - 支持StripPrefix、AddHeader等过滤器
  - 路由优先级控制
  - 负载均衡集成（lb://）

### 📊 可观测性

- [x] **请求日志**
  - 详细的请求/响应日志
  - 唯一请求ID追踪
  - 耗时统计与慢请求告警
  - 支持日志脱敏

- [x] **方法日志**
  - AOP切面统一日志记录
  - Service/Controller/Repository全覆盖
  - 方法参数、返回值、异常记录
  - 性能分析支持

- [x] **健康检查**
  - Spring Boot Actuator集成
  - 数据库连接健康检查
  - Redis连接健康检查
  - 系统资源监控

### ⚡ 性能优化

- [x] **响应缓存**
  - Redis分布式缓存
  - 智能缓存策略（GET请求）
  - 缓存命中率统计
  - 缓存预热与更新

- [x] **HTTP/2支持**
  - 多路复用
  - 服务器推送
  - 头部压缩

### 🌐 跨域支持

- [x] **完善的CORS配置**
  - 支持前后端分离
  - 可配置允许的域名、方法、头部
  - 预检请求优化
  - 凭证支持

---

## 🏗️ 技术架构

### 核心技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | LTS版本，虚拟线程支持 |
| Spring Boot | 3.5.7 | 企业级应用框架 |
| Spring Cloud Gateway | 2025.0.0 | 响应式API网关 |
| Spring Security | 6.x | 安全认证框架 |
| Spring Data R2DBC | 3.x | 响应式数据库访问 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 5.0+ | 缓存与限流 |
| Resilience4j | 2.2.0 | 熔断降级库 |
| JJWT | 0.12.6 | JWT处理库 |
| Hutool | 5.8.41 | Java工具类库 |
| Lombok | Latest | 简化代码 |
| Knife4j | 4.4.0 | API文档 |

### 架构图

```
                                   ┌─────────────────┐
                                   │   Client/前端    │
                                   └────────┬────────┘
                                            │
                                            ▼
                         ┌──────────────────────────────────┐
                         │     SAPiece Gateway (网关)       │
                         │  ┌────────────────────────────┐  │
                         │  │  JWT认证过滤器             │  │
                         │  └──────────┬─────────────────┘  │
                         │             ▼                     │
                         │  ┌────────────────────────────┐  │
                         │  │  IP黑白名单过滤器          │  │
                         │  └──────────┬─────────────────┘  │
                         │             ▼                     │
                         │  ┌────────────────────────────┐  │
                         │  │  限流过滤器 (Redis)        │  │
                         │  └──────────┬─────────────────┘  │
                         │             ▼                     │
                         │  ┌────────────────────────────┐  │
                         │  │  请求日志过滤器            │  │
                         │  └──────────┬─────────────────┘  │
                         │             ▼                     │
                         │  ┌────────────────────────────┐  │
                         │  │  动态路由定位              │  │
                         │  └──────────┬─────────────────┘  │
                         │             ▼                     │
                         │  ┌────────────────────────────┐  │
                         │  │  熔断降级 (Resilience4j)   │  │
                         │  └──────────┬─────────────────┘  │
                         │             ▼                     │
                         │  ┌────────────────────────────┐  │
                         │  │  请求重试                  │  │
                         │  └──────────┬─────────────────┘  │
                         └─────────────┼─────────────────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
                    ▼                  ▼                  ▼
          ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
          │ 用户服务     │    │ 订单服务     │    │ 商品服务     │
          │ :8081        │    │ :8082        │    │ :8083        │
          └─────────────┘    └─────────────┘    └─────────────┘

                    ┌──────────────────┬──────────────────┐
                    │                  │                  │
                    ▼                  ▼                  ▼
          ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
          │  MySQL       │    │  Redis       │    │ Actuator    │
          │  (R2DBC)     │    │  (限流/缓存) │    │ (监控)      │
          └─────────────┘    └─────────────┘    └─────────────┘
```

---

## 📁 项目结构

```
SAPiece-Gateway/
├── sql/                                      # 数据库脚本
│   ├── sys_user.sql                         # 用户表
│   ├── sys_role.sql                         # 角色表
│   ├── sys_menu.sql                         # 菜单权限表（含路由配置）
│   ├── sys_user_role.sql                    # 用户角色关联表
│   ├── sys_role_menu.sql                    # 角色菜单关联表
│   └── sys_menu_route_examples.sql          # 路由配置示例数据
│
├── src/main/java/.../sapiecegateway/
│   ├── aspect/                              # AOP切面
│   │   ├── IdempotentAspect.java           # 幂等性切面
│   │   └── MethodLogAspect.java            # 方法日志切面
│   │
│   ├── common/                              # 公共类
│   │   ├── Constants.java                   # 常量定义
│   │   └── Result.java                      # 统一响应结果
│   │
│   ├── config/                              # 配置类
│   │   ├── CircuitBreakerConfig.java       # 熔断器配置
│   │   ├── CorsConfig.java                  # CORS跨域配置
│   │   ├── PasswordEncoderConfig.java       # 密码编码器配置
│   │   ├── R2dbcConfig.java                 # R2DBC配置
│   │   ├── RedisConfig.java                 # Redis配置
│   │   ├── RetryConfig.java                 # 重试配置
│   │   ├── SecurityConfig.java              # Spring Security配置
│   │   └── SwaggerConfig.java               # Swagger文档配置
│   │
│   ├── controller/                          # 控制器
│   │   ├── AdminController.java             # 管理员控制器
│   │   ├── AuthController.java              # 认证控制器
│   │   ├── RouteController.java             # 路由管理控制器
│   │   └── TestController.java              # 测试控制器
│   │
│   ├── entity/                              # 实体类
│   │   ├── SysMenu.java                     # 菜单实体（含路由字段）
│   │   ├── SysRole.java                     # 角色实体
│   │   ├── SysRoleMenu.java                 # 角色菜单关联
│   │   ├── SysUser.java                     # 用户实体
│   │   └── SysUserRole.java                 # 用户角色关联
│   │
│   ├── exception/                           # 异常处理
│   │   ├── BusinessException.java           # 业务异常
│   │   └── GlobalExceptionHandler.java      # 全局异常处理器
│   │
│   ├── filter/                              # 网关过滤器
│   │   ├── CircuitBreakerFilter.java       # 熔断器过滤器
│   │   ├── GatewayRequestLogFilter.java    # 网关请求日志过滤器
│   │   ├── IpBlackWhiteListFilter.java     # IP黑白名单过滤器
│   │   ├── RateLimitFilter.java            # 限流过滤器
│   │   ├── RequestLogFilter.java           # 请求日志过滤器
│   │   ├── ResponseCacheFilter.java        # 响应缓存过滤器
│   │   ├── RetryFilter.java                # 重试过滤器
│   │   └── SignatureVerificationFilter.java # 签名验证过滤器
│   │
│   ├── handler/                             # 处理器
│   │   └── FallbackHandler.java            # 降级处理器
│   │
│   ├── health/                              # 健康检查
│   │   ├── R2dbcHealthIndicator.java       # R2DBC健康检查
│   │   ├── RedisHealthIndicator.java       # Redis健康检查
│   │   └── SystemHealthIndicator.java      # 系统健康检查
│   │
│   ├── listener/                            # 监听器
│   │   └── DynamicRouteLoader.java         # 动态路由加载器
│   │
│   ├── repository/                          # 数据访问层
│   │   ├── SysMenuRepository.java
│   │   ├── SysRoleMenuRepository.java
│   │   ├── SysRoleRepository.java
│   │   ├── SysUserRepository.java
│   │   └── SysUserRoleRepository.java
│   │
│   ├── security/                            # 安全相关
│   │   ├── CustomReactiveUserDetailsService.java   # 用户详情服务
│   │   ├── CustomUserDetails.java                  # 自定义用户详情
│   │   └── JwtAuthenticationFilter.java            # JWT认证过滤器
│   │
│   ├── service/                             # 服务层
│   │   ├── AdminService.java                # 管理服务
│   │   ├── AuthService.java                 # 认证服务
│   │   ├── DynamicRouteService.java         # 动态路由服务
│   │   ├── IdempotentService.java           # 幂等性服务
│   │   ├── SysMenuService.java              # 菜单服务
│   │   ├── SysRoleService.java              # 角色服务
│   │   ├── SysUserService.java              # 用户服务
│   │   ├── TokenBlacklistService.java       # Token黑名单服务
│   │   └── impl/                            # 服务实现类
│   │       ├── AdminServiceImpl.java
│   │       ├── AuthServiceImpl.java
│   │       ├── DynamicRouteServiceImpl.java
│   │       ├── IdempotentServiceImpl.java
│   │       ├── SysMenuServiceImpl.java
│   │       ├── SysRoleServiceImpl.java
│   │       ├── SysUserServiceImpl.java
│   │       └── TokenBlacklistServiceImpl.java
│   │
│   ├── util/                                # 工具类
│   │   ├── JwtUtil.java                     # JWT工具类
│   │   └── ResponseUtil.java                # 响应工具类
│   │
│   └── SaPieceGatewayApplication.java       # 应用启动类
│
├── src/main/resources/
│   ├── application.yml                      # 主配置文件
│   ├── application-dev.yml                  # 开发环境配置
│   └── application-prod.yml                 # 生产环境配置
│
├── DYNAMIC_ROUTE_README.md                  # 动态路由使用指南
├── README.md                                # 项目说明文档（本文件）
└── pom.xml                                  # Maven配置文件
```

---

## 🚀 快速开始

### 1. 环境要求

#### 必需环境

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | 推荐使用OpenJDK或Oracle JDK |
| Maven | 3.6+ | 项目构建工具 |
| MySQL | 8.0+ | 数据库 |
| Redis | 5.0+ | 缓存和限流 |

#### 可选环境

- Docker（推荐用于快速部署MySQL和Redis）
- Nacos/Eureka（使用负载均衡时需要）

### 2. 克隆项目

```bash
git clone https://github.com/your-repo/SAPiece-Gateway.git
cd SAPiece-Gateway
```

### 3. 数据库初始化

#### 方式1：使用MySQL命令行

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE product_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 执行SQL脚本（按顺序）
mysql -u root -p product_test < sql/sys_user.sql
mysql -u root -p product_test < sql/sys_role.sql
mysql -u root -p product_test < sql/sys_menu.sql
mysql -u root -p product_test < sql/sys_user_role.sql
mysql -u root -p product_test < sql/sys_role_menu.sql

# 3. 导入路由示例数据（可选）
mysql -u root -p product_test < sql/sys_menu_route_examples.sql
```

#### 方式2：使用数据库客户端

使用 Navicat、DBeaver 等工具依次执行 `sql/` 目录下的脚本。

### 4. 配置修改

编辑 `src/main/resources/application-dev.yml`：

```yaml
spring:
  # R2DBC 数据库配置
  r2dbc:
    url: r2dbc:mysql://localhost:3306/product_test?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password    # 修改为你的MySQL密码

  # Redis 配置
  data:
    redis:
      host: localhost
      port: 6379
      password:                # 如果Redis设置了密码，在这里配置

# JWT 配置（生产环境务必修改secret）
jwt:
  secret: your-secret-key-please-change-in-production-at-least-256-bits
  expiration: 604800000        # 7天
  refresh: 259200000           # 3天
```

### 5. 启动Redis

#### 使用Docker启动（推荐）

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

#### 或使用本地Redis

```bash
redis-server
```

### 6. 编译项目

```bash
mvn clean compile
```

### 7. 启动应用

#### 开发环境

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

或使用IDE运行 `SaPieceGatewayApplication.java`，配置 Active profiles 为 `dev`

#### 生产环境

```bash
# 打包
mvn clean package -DskipTests

# 运行
java -jar target/SAPiece-Gateway-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 8. 验证启动

访问健康检查端点：

```bash
curl http://localhost:8080/actuator/health
```

预期响应：

```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"},
    "r2dbc": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

### 9. 测试登录

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "userName": "admin",
    "password": "123456"
  }'
```

如果返回Token，说明启动成功！🎉

---

## ⚙️ 详细配置

### 数据库配置

#### R2DBC连接池

```yaml
spring:
  r2dbc:
    url: r2dbc:mysql://localhost:3306/product_test
    username: root
    password: your_password
    pool:
      initial-size: 10         # 初始连接数
      max-size: 50            # 最大连接数
      max-idle-time: 30m      # 最大空闲时间
```

#### 性能优化建议

- `initial-size`: 根据预估的并发量设置，一般10-20
- `max-size`: 不要设置过大，避免数据库连接耗尽
- `max-idle-time`: 根据数据库的wait_timeout设置

### Redis配置

#### 连接池配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 20      # 最大活跃连接
          max-idle: 10        # 最大空闲连接
          min-idle: 5         # 最小空闲连接
          max-wait: 3000ms    # 最大等待时间
```

### JWT配置

```yaml
jwt:
  secret: your-secret-key-at-least-256-bits
  expiration: 604800000        # Token过期时间（毫秒）7天
  refresh: 259200000           # Token刷新时间（毫秒）3天
```

**安全建议**：
- 密钥长度至少256位
- 生产环境通过环境变量注入：`${JWT_SECRET}`
- 定期轮换密钥

### 限流配置

```yaml
rate-limit:
  enabled: true                # 是否启用限流
  qps: 10                     # 每秒请求数限制
  capacity: 20                # 令牌桶容量（突发流量）
```

**调优建议**：
- `qps`: 根据实际业务设置，建议从保守值开始逐步调整
- `capacity`: 一般设置为 qps 的 1.5-2 倍

### IP黑白名单配置

```yaml
ip-filter:
  # 黑名单配置
  blacklist-enabled: false
  blacklist:
    - 192.168.1.100           # 单个IP
    - 10.0.0.0/8              # IP段（CIDR格式）

  # 白名单配置
  whitelist-enabled: false
  whitelist:
    - 192.168.1.0/24          # 允许整个局域网
    - 127.0.0.1               # 允许本机访问
```

### 签名验证配置

```yaml
signature:
  enabled: false                              # 是否启用签名验证
  secret: your-signature-secret-key
  algorithm: MD5                              # 签名算法（MD5、SHA256）
  timestamp-validity: 300                     # 时间戳有效期（秒）
```

### 响应缓存配置

```yaml
response-cache:
  enabled: true                               # 是否启用响应缓存
  ttl: 300                                    # 缓存过期时间（秒）
```

### 请求重试配置

```yaml
retry:
  enabled: true                               # 是否启用请求重试
```

重试策略在代码中配置：
- 最大重试次数：3次
- 退避策略：指数退避（500ms → 1000ms → 2000ms）
- 仅重试幂等方法（GET、PUT、DELETE、HEAD、OPTIONS）

### 日志配置

```yaml
logging:
  level:
    root: INFO
    com.sapiece.nova.sapiecegateway: DEBUG
    org.springframework.r2dbc: DEBUG
    org.springframework.cloud.gateway: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"
  file:
    name: logs/sapiece-gateway.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30
```

---

## 🛣️ 动态路由

### 功能概述

SAPiece Gateway 支持基于数据库的动态路由配置，实现路由的热更新和可视化管理。

### 核心特性

- ✅ 数据库驱动（sys_menu表）
- ✅ 热更新（无需重启）
- ✅ 支持多种断言（Path、Method、Header等）
- ✅ 支持多种过滤器（StripPrefix、AddHeader等）
- ✅ 与权限系统集成
- ✅ RESTful管理接口

### 快速配置

#### 1. 更新表结构

sys_menu表已包含路由相关字段：
- `target_uri`: 目标服务URI（如 http://localhost:8081 或 lb://user-service）
- `route_predicates`: 路由断言配置（JSON格式）
- `route_filters`: 路由过滤器配置（JSON格式）
- `menu_type`: 设置为 'R' 表示路由配置

#### 2. 插入路由配置

```sql
INSERT INTO `sys_menu` (
    `parent_id`, `menu_name`, `menu_type`, `menu_sort`, `permission_code`,
    `target_uri`, `route_predicates`, `route_filters`,
    `status`, `del_flag`, `creator`, `remark`
) VALUES (
    0,
    '用户服务路由',
    'R',                                           -- 路由类型
    1,
    'gateway:route:user-service',
    'http://localhost:8081',                       -- 目标服务
    '[{"name":"Path","args":{"pattern":"/api/user/**"}}]',  -- 路径断言
    '[{"name":"StripPrefix","args":{"parts":"1"}}]',         -- 去除前缀
    1,                                             -- 启用
    0,
    'admin',
    '用户服务路由'
);
```

#### 3. 应用启动自动加载

应用启动时会自动从数据库加载所有满足条件的路由（`menu_type='R'`, `status=1`, `del_flag=0`）

### 管理接口

#### 查询所有路由

```bash
GET /gateway/routes/list
Authorization: Bearer {token}
```

#### 刷新路由配置

```bash
POST /gateway/routes/refresh
Authorization: Bearer {token}
```

#### 添加单个路由

```bash
POST /gateway/routes/add/{menuId}
Authorization: Bearer {token}
```

#### 删除路由

```bash
DELETE /gateway/routes/delete/{routeId}
Authorization: Bearer {token}
```

### 配置示例

#### 示例1：基础路径路由

```json
{
  "target_uri": "http://localhost:8081",
  "route_predicates": [
    {"name":"Path","args":{"pattern":"/api/user/**"}}
  ],
  "route_filters": [
    {"name":"StripPrefix","args":{"parts":"1"}}
  ]
}
```

#### 示例2：方法限制

```json
{
  "target_uri": "http://localhost:8083",
  "route_predicates": [
    {"name":"Path","args":{"pattern":"/api/product/**"}},
    {"name":"Method","args":{"methods":"GET,POST"}}
  ],
  "route_filters": [
    {"name":"StripPrefix","args":{"parts":"1"}}
  ]
}
```

#### 示例3：请求头匹配

```json
{
  "target_uri": "http://localhost:8084",
  "route_predicates": [
    {"name":"Path","args":{"pattern":"/api/payment/**"}},
    {"name":"Header","args":{"header":"X-Version","regexp":"v[1-9]"}}
  ],
  "route_filters": [
    {"name":"StripPrefix","args":{"parts":"1"}},
    {"name":"AddRequestHeader","args":{"name":"X-Gateway","value":"SAPiece"}}
  ]
}
```

### 详细文档

完整的动态路由使用指南请参考：[DYNAMIC_ROUTE_README.md](DYNAMIC_ROUTE_README.md)

---

## 🔐 权限控制

### RBAC权限模型

系统采用经典的RBAC（Role-Based Access Control）模型：

```
用户(User) ──→ 用户角色(UserRole) ──→ 角色(Role) ──→ 角色菜单(RoleMenu) ──→ 菜单/权限(Menu)
```

### 数据库表设计

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| sys_user | 用户表 | id, user_name, password, nick_name, status |
| sys_role | 角色表 | id, role_name, role_code, status |
| sys_menu | 菜单权限表 | id, menu_name, permission_code, menu_type |
| sys_user_role | 用户角色关联 | user_id, role_id |
| sys_role_menu | 角色菜单关联 | role_id, menu_id |

### 权限标识规范

权限标识格式：`模块:资源:操作`

#### 示例

```
system:user:list      # 查询用户列表
system:user:add       # 添加用户
system:user:update    # 更新用户
system:user:delete    # 删除用户
system:role:*         # 角色管理所有权限
```

### 权限注解使用

#### 1. 基于权限码

```java
@PreAuthorize("hasAuthority('system:user:list')")
@GetMapping("/users")
public Mono<Result<List<User>>> getUserList() {
    return userService.findAll()
        .collectList()
        .map(Result::success);
}
```

#### 2. 基于角色

```java
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/users/{id}")
public Mono<Result<Void>> deleteUser(@PathVariable Long id) {
    return userService.deleteById(id)
        .then(Mono.just(Result.success()));
}
```

#### 3. 组合条件

```java
@PreAuthorize("hasRole('ADMIN') or hasAuthority('system:user:update')")
@PutMapping("/users/{id}")
public Mono<Result<User>> updateUser(@PathVariable Long id, @RequestBody User user) {
    return userService.update(id, user)
        .map(Result::success);
}
```

### 权限缓存策略

用户登录后，权限信息会缓存到Redis，避免每次请求都查询数据库：

```
Key格式: user:permissions:{userId}
过期时间: 与Token过期时间一致
```

---

## 📡 API接口文档

### 认证相关

#### 用户登录

**接口**: `POST /auth/login`

**请求参数**:

```json
{
  "userName": "admin",
  "password": "123456"
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInVzZXJJZCI6MSwidXNlck5hbWUiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl0sInBlcm1pc3Npb25zIjpbInN5c3RlbTp1c2VyOmxpc3QiXSwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjE3MDA2MDQ4MDB9.xxx",
    "userId": 1,
    "userName": "admin",
    "nickName": "管理员",
    "roles": ["ADMIN"],
    "permissions": ["system:user:list", "system:user:add"]
  },
  "timestamp": 1700000000000
}
```

#### 获取用户信息

**接口**: `GET /auth/info`

**请求头**: `Authorization: Bearer {token}`

**响应示例**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 1,
    "userName": "admin",
    "nickName": "管理员",
    "email": "admin@example.com",
    "roles": ["ADMIN"],
    "permissions": ["system:user:list"]
  },
  "timestamp": 1700000000000
}
```

#### 刷新Token

**接口**: `POST /auth/refresh`

**请求头**: `Authorization: Bearer {old_token}`

**响应示例**:

```json
{
  "code": 200,
  "message": "Token刷新成功",
  "data": {
    "token": "new_token_string"
  },
  "timestamp": 1700000000000
}
```

#### 用户登出

**接口**: `POST /auth/logout`

**请求头**: `Authorization: Bearer {token}`

**响应示例**:

```json
{
  "code": 200,
  "message": "登出成功",
  "data": null,
  "timestamp": 1700000000000
}
```

### 路由管理

#### 查询所有路由

**接口**: `GET /gateway/routes/list`

**权限**: `system:route:query`

**响应示例**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "route-1",
      "uri": "http://localhost:8081",
      "predicates": [...],
      "filters": [...],
      "order": 1,
      "metadata": {
        "menuId": 1,
        "menuName": "用户服务路由"
      }
    }
  ],
  "timestamp": 1700000000000
}
```

#### 刷新所有路由

**接口**: `POST /gateway/routes/refresh`

**权限**: `system:route:refresh`

**响应示例**:

```json
{
  "code": 200,
  "message": "路由刷新成功，共加载 5 条路由",
  "data": null,
  "timestamp": 1700000000000
}
```

### 管理员接口

#### 创建用户

**接口**: `POST /admin/users`

**权限**: `system:user:add`

**请求参数**:

```json
{
  "userName": "test",
  "password": "123456",
  "nickName": "测试用户",
  "email": "test@example.com",
  "roleIds": [2]
}
```

### API文档访问

启动应用后，访问：

```
http://localhost:8080/doc.html
```

---

## 🚢 部署指南

### Docker部署

#### 1. 构建Docker镜像

创建 `Dockerfile`:

```dockerfile
FROM openjdk:21-jdk-slim
VOLUME /tmp
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
```

构建镜像：

```bash
mvn clean package -DskipTests
docker build -t sapiece-gateway:latest .
```

#### 2. 使用Docker Compose

创建 `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: sapiece-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: product_test
    ports:
      - "3306:3306"
    volumes:
      - ./mysql-data:/var/lib/mysql
      - ./sql:/docker-entrypoint-initdb.d

  redis:
    image: redis:7-alpine
    container_name: sapiece-redis
    ports:
      - "6379:6379"

  gateway:
    image: sapiece-gateway:latest
    container_name: sapiece-gateway
    depends_on:
      - mysql
      - redis
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_R2DBC_URL: r2dbc:mysql://mysql:3306/product_test
      SPRING_R2DBC_USERNAME: root
      SPRING_R2DBC_PASSWORD: root123
      SPRING_REDIS_HOST: redis
      JWT_SECRET: your-production-secret-key-at-least-256-bits
    ports:
      - "8080:8080"
```

启动服务：

```bash
docker-compose up -d
```

### Kubernetes部署

#### 1. 创建ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: gateway-config
data:
  application-prod.yml: |
    spring:
      r2dbc:
        url: r2dbc:mysql://mysql-service:3306/product_test
        username: root
        password: ${MYSQL_PASSWORD}
      data:
        redis:
          host: redis-service
          port: 6379
```

#### 2. 创建Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sapiece-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      containers:
      - name: gateway
        image: sapiece-gateway:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: gateway-secrets
              key: jwt-secret
        volumeMounts:
        - name: config
          mountPath: /config
      volumes:
      - name: config
        configMap:
          name: gateway-config
```

#### 3. 创建Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 8080
  selector:
    app: gateway
```

### 云服务部署

#### 阿里云ECS

1. 安装Java 21、MySQL、Redis
2. 配置安全组（开放8080端口）
3. 上传jar包并运行：

```bash
nohup java -jar SAPiece-Gateway-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  > gateway.log 2>&1 &
```

#### 使用systemd管理

创建 `/etc/systemd/system/sapiece-gateway.service`:

```ini
[Unit]
Description=SAPiece Gateway
After=syslog.target network.target

[Service]
User=app
ExecStart=/usr/bin/java -jar /opt/sapiece-gateway/SAPiece-Gateway.jar --spring.profiles.active=prod
SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
systemctl daemon-reload
systemctl start sapiece-gateway
systemctl enable sapiece-gateway
```

---

## 📊 监控运维

### 健康检查

#### Actuator端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康状态 |
| `/actuator/info` | 应用信息 |
| `/actuator/metrics` | 性能指标 |
| `/actuator/prometheus` | Prometheus格式指标 |

#### 自定义健康检查

系统实现了以下健康检查：

1. **R2DBC健康检查** - 检查数据库连接
2. **Redis健康检查** - 检查Redis连接
3. **系统健康检查** - 检查CPU、内存使用率

### 日志管理

#### 日志级别动态调整

通过Actuator动态调整日志级别（需启用loggers端点）：

```bash
# 查看当前日志级别
curl http://localhost:8080/actuator/loggers/com.sapiece.nova.sapiecegateway

# 调整日志级别为DEBUG
curl -X POST http://localhost:8080/actuator/loggers/com.sapiece.nova.sapiecegateway \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

#### 日志收集

推荐使用ELK/EFK栈收集日志：

```
应用日志 → Filebeat → Logstash → Elasticsearch → Kibana
```

### 性能监控

#### Prometheus + Grafana

1. 启用Prometheus端点（已默认启用）
2. 配置Prometheus抓取指标：

```yaml
scrape_configs:
  - job_name: 'sapiece-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['gateway:8080']
```

3. 在Grafana中导入Spring Boot仪表板

### 告警配置

#### Prometheus告警规则

```yaml
groups:
  - name: gateway_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "网关错误率过高"
```

---

## ⚡ 性能调优

### JVM参数优化

#### 推荐配置（4GB内存）

```bash
java -jar SAPiece-Gateway.jar \
  -Xms2g \
  -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/logs/heapdump.hprof \
  -Dspring.profiles.active=prod
```

#### 参数说明

- `-Xms2g -Xmx2g`: 堆内存2GB（初始值和最大值相同，避免动态扩容）
- `-XX:+UseG1GC`: 使用G1垃圾回收器（推荐）
- `-XX:MaxGCPauseMillis=200`: GC暂停时间目标200ms
- `-XX:+HeapDumpOnOutOfMemoryError`: OOM时自动dump堆内存

### 连接池优化

#### R2DBC连接池

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 20        # 根据并发量调整
      max-size: 100           # 不超过数据库max_connections
      max-idle-time: 30m
      validation-query: SELECT 1
```

#### Redis连接池

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 10
```

### Reactor线程模型优化

WebFlux使用事件循环模型，线程数不宜过多：

```yaml
spring:
  reactor:
    netty:
      ioWorkerCount: 8        # IO工作线程数，一般为CPU核心数
```

### 缓存策略优化

1. **热点数据缓存**: 用户信息、权限、配置等
2. **缓存预热**: 应用启动时预加载热点数据
3. **缓存更新**: 使用发布订阅模式实现多实例缓存同步

---

## 🔒 安全最佳实践

### 1. JWT安全

#### 密钥管理

```bash
# 通过环境变量注入（推荐）
export JWT_SECRET="your-production-secret-key-at-least-256-bits"
java -jar SAPiece-Gateway.jar
```

#### Token黑名单

用户登出时将Token加入黑名单：

```java
tokenBlacklistService.addToBlacklist(token);
```

### 2. 密码安全

#### 密码策略

- 最小长度：8位
- 必须包含：大写字母、小写字母、数字、特殊字符
- 使用BCrypt加密存储

#### 密码加密

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10); // strength=10
}
```

### 3. HTTPS配置

#### 生成证书

```bash
keytool -genkeypair -alias sapiece-gateway \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 3650
```

#### 配置SSL

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: your_password
    key-store-type: PKCS12
    key-alias: sapiece-gateway
```

### 4. SQL注入防护

- 使用参数化查询（R2DBC自动处理）
- 避免字符串拼接SQL
- 输入验证和过滤

### 5. XSS防护

- 响应头设置：

```yaml
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'
```

### 6. 限流防护

- IP限流：防止单IP恶意请求
- 用户限流：防止单用户滥用
- 接口限流：保护关键接口

---

## ❓ 常见问题

### Q1: 启动时报"Bean名称冲突"错误

**问题**: `BeanDefinitionOverrideException: Invalid bean definition with name 'routeRefreshListener'`

**解决方案**:
已修复。我们的监听器已重命名为 `DynamicRouteLoader`，避免与Spring Cloud Gateway内置的Bean冲突。

---

### Q2: 动态路由加载失败，报SpEL错误

**问题**: `SpelEvaluationException: EL1005E: Type cannot be found 'System'`

**原因**:
过滤器参数中使用了 `#{T(System).currentTimeMillis()}` 等SpEL表达式，但Gateway的SimpleEvaluationContext不支持类型引用。

**解决方案**:
使用静态字符串值替代SpEL表达式：

```json
// ❌ 错误
{"name":"AddRequestHeader","args":{"name":"X-Time","value":"#{T(System).currentTimeMillis()}"}}

// ✅ 正确
{"name":"AddRequestHeader","args":{"name":"X-Gateway","value":"SAPiece-Gateway"}}
```

---

### Q3: 数据库连接失败

**问题**: `Unable to connect to database`

**排查步骤**:
1. 检查MySQL是否启动：`systemctl status mysql`
2. 检查端口是否开放：`netstat -an | grep 3306`
3. 检查用户名密码是否正确
4. 检查数据库URL格式：`r2dbc:mysql://localhost:3306/database_name`

---

### Q4: Redis连接超时

**问题**: `RedisConnectionException: Unable to connect to Redis`

**排查步骤**:
1. 检查Redis是否启动：`redis-cli ping`
2. 检查防火墙规则
3. 检查Redis配置中的bind地址
4. 如果Redis设置了密码，确保配置中填写了密码

---

### Q5: JWT Token验证失败

**问题**: `401 Unauthorized`

**可能原因**:
1. Token过期
2. Token格式错误（缺少"Bearer "前缀）
3. JWT密钥配置错误
4. Token在黑名单中

**解决方案**:
```bash
# 正确的请求头格式
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

# 检查Token是否过期
curl http://localhost:8080/auth/info \
  -H "Authorization: Bearer {your_token}"
```

---

### Q6: 限流触发后如何处理

**问题**: 收到429状态码

**原因**: 请求频率超过配置的QPS限制

**解决方案**:
1. 检查是否是正常业务需求，考虑调整限流配置
2. 实现客户端退避重试机制
3. 对于高频接口，考虑使用缓存

---

### Q7: 熔断器打开后一直无法恢复

**问题**: 服务持续返回503

**排查步骤**:
1. 检查下游服务是否已恢复
2. 查看熔断器配置中的`waitDurationInOpenState`（默认30秒）
3. 手动重置熔断器状态（如果提供了管理接口）

---

### Q8: 如何查看当前所有路由配置

**方法1**: 调用管理接口

```bash
curl -X GET http://localhost:8080/gateway/routes/list \
  -H "Authorization: Bearer {token}"
```

**方法2**: 使用Actuator端点（需启用）

```bash
curl http://localhost:8080/actuator/gateway/routes
```

---

### Q9: 如何在生产环境中管理JWT密钥

**推荐方案**:

1. **环境变量**（推荐）:
```bash
export JWT_SECRET="your-secret-key"
```

2. **配置中心**:
使用Nacos、Apollo等配置中心

3. **密钥管理服务**:
AWS KMS、Azure Key Vault、HashiCorp Vault

---

### Q10: 应用启动慢怎么优化

**优化建议**:

1. **懒加载Bean**:
```java
@Lazy
@Bean
public SomeBean someBean() {
    return new SomeBean();
}
```

2. **异步初始化**:
```java
@Async
@EventListener(ApplicationReadyEvent.class)
public void init() {
    // 初始化逻辑
}
```

3. **减少自动配置**:
```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class
})
```

---

## 📚 参考资料

- [Spring Cloud Gateway官方文档](https://spring.io/projects/spring-cloud-gateway)
- [Spring Security官方文档](https://spring.io/projects/spring-security)
- [R2DBC官方文档](https://r2dbc.io/)
- [Resilience4j官方文档](https://resilience4j.readme.io/)
- [JWT.io](https://jwt.io/)

---

## 📝 更新日志

### v1.0.0 (2025-11-24)

- ✅ 实现JWT认证与授权
- ✅ 实现RBAC权限控制
- ✅ 实现接口限流
- ✅ 实现熔断降级
- ✅ 实现请求重试
- ✅ 实现响应缓存
- ✅ 实现IP黑白名单
- ✅ 实现参数签名验证
- ✅ 实现动态路由配置
- ✅ 实现请求日志记录
- ✅ 实现健康检查
- ✅ 集成Swagger文档

---

## 📄 License

本项目采用 [MIT](LICENSE) 开源协议。

---

## 👥 作者

**SAPiece Team**

- GitHub: [@sapiece](https://github.com/leileiya1)
- Email: zhouleileisapiece@gmail.com  17685219818@163.com

---

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 贡献流程

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交Pull Request

### 代码规范

- 遵循阿里巴巴Java开发手册
- 使用Lombok简化代码
- 添加必要的注释和JavaDoc
- 单元测试覆盖率 > 70%

---

## 🌟 Star History

如果这个项目对你有帮助，请给一个⭐️Star支持一下！

---

<div align="center">

**Made with ❤️ by SAPiece Team**

</div>
