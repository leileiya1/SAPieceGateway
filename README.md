# SAPiece Gateway — 企业级响应式 API 网关

<div align="center">

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.0-blue.svg)](https://spring.io/projects/spring-cloud)
[![Spring Cloud Alibaba](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2025.1.0.0-orange.svg)](https://github.com/alibaba/spring-cloud-alibaba)
[![Sentinel](https://img.shields.io/badge/Sentinel-1.8.9-red.svg)](https://github.com/alibaba/Sentinel)
[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-Native-purple.svg)](https://opentelemetry.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**基于 Spring Boot 4.x + Spring Cloud Gateway 构建的生产级响应式 API 网关**

*集成 JWT 双 Token 认证、Nacos 服务发现与配置、Sentinel 熔断降级、OpenTelemetry 链路追踪、HMAC 下游信任签名、Security Headers、HA 部署等完整的微服务网关解决方案*

[快速开始](#-快速开始) · [核心功能](#-核心功能) · [API 文档](#-api-接口文档) · [部署指南](#-部署指南) · [常见问题](#-常见问题)

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
- [服务发现与配置中心](#-服务发现与配置中心)
- [熔断降级（Sentinel）](#-熔断降级sentinel)
- [链路追踪（OpenTelemetry）](#-链路追踪opentelemetry)
- [下游信任（HMAC 签名）](#-下游信任hmac-签名)
- [安全加固](#-安全加固)
- [API 接口文档](#-api-接口文档)
- [部署指南](#-部署指南)
- [监控运维](#-监控运维)
- [常见问题](#-常见问题)
- [更新日志](#-更新日志)

---

## 项目简介

SAPiece Gateway 是一款基于 **Spring Boot 4.x** 和 **Spring Cloud Gateway 2025.1** 构建的企业级响应式 API 网关，采用完全异步非阻塞架构，为微服务集群提供统一的流量入口、安全认证、服务治理和全链路可观测性。

### 🎯 设计理念

- **高性能**：Reactor + Netty 响应式模型，单机支持数万并发
- **高可用**：Sentinel 熔断 + Nacos 服务发现 + Nginx HA + K8s HPA
- **零信任**：HMAC 签名保障网关 → 下游通信安全，防止绕过网关直接调用
- **全可观测**：OpenTelemetry traceId 贯穿全链路，Prometheus + Grafana 实时看板

### 🌟 适用场景

- ✅ 微服务架构统一流量入口
- ✅ JWT 认证 + RBAC 权限控制
- ✅ 流量控制与 Sentinel 熔断降级
- ✅ 灰度发布与 A/B 测试
- ✅ 全链路分布式追踪
- ✅ 生产级 HA / K8s 弹性部署

---

## 🎯 核心功能

### 🔐 安全认证

| 功能 | 说明 |
|------|------|
| **JWT 双 Token** | Access Token（30min）+ Refresh Token（7天），刷新时旧 Refresh Token 立刻失效 |
| **pwdVer 机制** | 密码修改后所有旧 Token 即刻失效，无需 DB 查询（Redis pwdVer 比对） |
| **Token 黑名单** | 登出后 Token 加入 Redis 黑名单；jti（UUID）确保同秒生成的 Token 不碰撞 |
| **用户黑名单** | 管理员可封禁特定用户，封禁后所有 Token 失效 |
| **RBAC 权限** | 用户 → 角色 → 权限三层模型，基于 `@PreAuthorize` 方法级控制 |
| **权限缓存** | 角色/权限存 Redis Set，登录后热路径无 DB 查询 |

### 🚦 流量管理

| 功能 | 说明 |
|------|------|
| **Lua Token Bucket 限流** | IP / 用户 / 路由三种策略，基于 Redis 原子 Lua 脚本，支持突发流量 |
| **Sentinel 熔断降级** | 慢调用比率 + 异常比率双触发，规则持久化在 Nacos，热更新无需重启 |
| **IP 黑白名单** | 支持精确 IP 和 CIDR 段，动态添加无需重启 |
| **幂等性保护** | `@Idempotent` 注解，HEADER/BODY 两种模式，防重复提交 |

### 🛣️ 路由与服务发现

| 功能 | 说明 |
|------|------|
| **数据库动态路由** | 路由配置存 MySQL，增删改无需重启 |
| **TTL 缓存** | 30s 内存缓存，`Redis Pub/Sub` 多实例强制失效同步 |
| **Nacos 服务发现** | 网关自动注册到 Nacos，下游服务用 `lb://service-name` 自动负载均衡 |
| **灰度发布** | 支持 Header / 参数 / 权重多种灰度策略 |

### 🔭 可观测性

| 功能 | 说明 |
|------|------|
| **OpenTelemetry 追踪** | 内置 `spring-boot-starter-opentelemetry`，自动生成 traceId/spanId |
| **traceparent 注入** | W3C TraceContext 格式注入下游请求头，下游 OTel SDK 自动接管 |
| **审计日志** | 记录登录/登出/操作，支持时间范围查询和清理 |
| **请求日志** | 每次请求记录路径、耗时、状态，慢请求（>3s）额外告警 |
| **Prometheus 指标** | `/actuator/prometheus`，配合 Grafana 实时看板 |

### 🛡️ 安全加固

| 功能 | 说明 |
|------|------|
| **HMAC 下游签名** | `X-Gateway-Signature` + `X-Gateway-Timestamp`，防止绕过网关直接调用下游 |
| **Security Headers** | HSTS / CSP / X-Frame-Options / X-Content-Type-Options / Referrer-Policy 全套 |
| **HTTPS / TLS** | 支持 `SSL_ENABLED=true` 启用，自签证书生成脚本已内置 |
| **X-User-* 防伪造** | 网关剥离客户端伪造的用户信息 header，认证后注入真实值 |

### 🏗️ 高可用与运维

| 功能 | 说明 |
|------|------|
| **docker-compose HA** | Nginx 负载均衡 + 多 Gateway 实例 + Jaeger + Prometheus + Grafana |
| **K8s 生产清单** | Deployment / Service / HPA / ConfigMap / Secret / Namespace 完整 |
| **滚动更新** | `maxUnavailable=0` 零宕机发布，`terminationGracePeriodSeconds=40` 优雅停机 |
| **Nacos 配置中心** | 部分配置外部化，`optional:nacos:` 前缀确保 Nacos 不可用时仍能启动 |

---

## 🏗️ 技术架构

### 核心技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | LTS，虚拟线程支持 |
| Spring Boot | **4.0.5** | 最新稳定版 |
| Spring Cloud Gateway | **2025.1.0** | `gateway-server-webflux` 工件 |
| Spring Cloud Alibaba | **2025.1.0.0** | Nacos Discovery + Config |
| Nacos | 2.x | 服务注册发现 + 配置中心 |
| Sentinel | **1.8.9** | 熔断降级（v6x Gateway 适配器）|
| OpenTelemetry | Spring Boot 4 Native | `spring-boot-starter-opentelemetry` |
| Spring Security WebFlux | 6.x | JWT 认证 + RBAC |
| R2DBC MySQL | `io.asyncer:r2dbc-mysql:1.4.1` | 响应式数据库驱动 |
| Redis Reactive | Lettuce | 限流 / 缓存 / Pub/Sub |
| JJWT | 0.12.6 | JWT 生成与验证（含 jti 唯一标识）|
| Hutool | 5.8.41 | 工具类 |
| Knife4j | 4.4.0 | Swagger API 文档 |

### 架构图

```
                         ┌──────────────────────────┐
                         │       客户端 / 浏览器      │
                         └─────────────┬────────────┘
                                       │ HTTPS
                         ┌─────────────▼────────────┐
                         │         Nginx LB          │  ← 80/443 端口，TLS 终止
                         │   least_conn 负载均衡     │
                         └──────┬──────────┬─────────┘
                                │          │
               ┌────────────────▼──┐  ┌────▼───────────────┐
               │  Gateway 实例 1   │  │  Gateway 实例 2     │  ← 水平扩展
               │  (8080)           │  │  (8080)             │
               └────────────────┬──┘  └────┬───────────────┘
                                │          │  Redis Pub/Sub 路由缓存同步
              ┌─────────────────▼──────────▼──────────────┐
              │          过滤器执行链（按 Order 排序）       │
              │  ① IP黑白名单 (HIGHEST_PRECEDENCE)         │
              │  ② 请求日志   (HIGHEST+1)                  │
              │  ③ 限流过滤器 (Lua Token Bucket, HIGHEST+5)│
              │  ④ Security Headers (响应头, HIGHEST+2)    │
              │  ⑤ Sentinel Gateway Filter (order=-1)     │
              │  ⑥ 认证信息透传 AuthHeaderFilter (order=-50)│
              │  ⑦ 链路追踪 TraceHeaderFilter (order=-49) │
              │  ⑧ 动态路由 / lb:// 负载均衡               │
              │  ⑨ 熔断兜底 CircuitBreakerFilter           │
              └────────────────┬──────────────────────────┘
                               │  X-User-Id/Name/Roles + X-Gateway-Signature
              ┌────────────────▼───────────────────────────┐
              │           下游微服务集群                     │
              │  user-service   order-service   ...        │
              │  (lb://name 经 Nacos 发现)                 │
              └───────┬──────────────┬────────────────┬────┘
                      │              │                │
              ┌───────▼──┐  ┌────────▼──┐  ┌─────────▼───┐
              │  MySQL   │  │  Redis    │  │  Nacos      │
              │  (R2DBC) │  │  (限流/缓存)│  │ (服务发现)  │
              └──────────┘  └───────────┘  └─────────────┘

可观测性：
  所有 Span → Jaeger (OTLP gRPC:4317)
  所有 Metric → Prometheus (:9090) → Grafana (:3000)
```

---

## 📁 项目结构

```
SAPiece-Gateway/
├── sql/                                      # 数据库初始化脚本
│   └── sys_user_role_menu.sql               # 用户/角色/权限/路由一体脚本
│
├── scripts/
│   └── gen-keystore.sh                      # 自签 TLS 证书生成脚本
│
├── deploy/                                   # 部署配置
│   ├── nginx/
│   │   ├── nginx.conf                       # Nginx HA 反向代理配置
│   │   └── ssl/                             # 生产 TLS 证书挂载目录
│   ├── prometheus/
│   │   └── prometheus.yml                   # Prometheus 抓取配置
│   └── grafana/
│       └── provisioning/datasources/        # Grafana 数据源自动配置
│
├── k8s/                                     # K8s 生产清单
│   ├── namespace.yaml                       # Namespace: sapiece
│   ├── configmap.yaml                       # ConfigMap + Secret
│   ├── deployment.yaml                      # Deployment（含探针/资源限制）
│   ├── service.yaml                         # ClusterIP + LoadBalancer
│   └── hpa.yaml                             # HPA（CPU 70% / 内存 80% 触发）
│
├── docker-compose.yml                       # HA 部署：nginx+gateway×N+jaeger+prometheus+grafana
├── Dockerfile                               # 多阶段构建，非 root 运行
│
├── src/main/java/.../sapiecegateway/
│   ├── aspect/
│   │   └── IdempotentAspect.java           # 幂等性切面（Reactor context 兼容）
│   │
│   ├── config/
│   │   ├── HttpsConfig.java                # HTTPS 重定向过滤器
│   │   ├── SecurityConfig.java             # Spring Security WebFlux 配置
│   │   ├── SentinelGatewayConfig.java      # Sentinel 熔断配置 + 自定义 JSON 响应
│   │   ├── CorsConfig.java                 # CORS 跨域配置
│   │   └── R2dbcConfig.java                # R2DBC 事务管理器
│   │
│   ├── controller/
│   │   ├── AuthController.java             # 认证：登录/登出/刷新/用户信息
│   │   ├── GatewayRouteController.java     # 路由管理 CRUD
│   │   ├── AdminController.java            # IP/Token/用户黑名单管理
│   │   ├── GrayRuleController.java         # 灰度规则管理
│   │   └── AuditLogController.java         # 审计日志查询
│   │
│   ├── filter/
│   │   ├── AuthHeaderGatewayFilter.java    # 认证信息透传 + HMAC 签名注入
│   │   ├── TraceHeaderGatewayFilter.java   # OTel traceId/traceparent 注入下游
│   │   ├── SecurityHeadersFilter.java      # Security Headers（HSTS/CSP/...）
│   │   ├── EnhancedRateLimitFilter.java    # Lua Token Bucket 限流
│   │   ├── IpBlackWhiteListFilter.java     # IP 黑白名单（含可信代理 XFF）
│   │   ├── RequestLogFilter.java           # 请求/响应日志
│   │   └── CircuitBreakerFilter.java       # 熔断降级兜底响应
│   │
│   ├── route/
│   │   ├── DatabaseRouteDefinitionRepository.java  # 数据库路由 + TTL 缓存
│   │   ├── RoutePermissionService.java             # 路由权限校验 + Redis Pub/Sub 订阅
│   │   └── impl/DynamicRouteServiceImpl.java       # 路由 CRUD + Pub/Sub 发布
│   │
│   ├── security/
│   │   ├── JwtSecurityContextRepository.java  # JWT 认证核心（热路径无 DB）
│   │   ├── CustomUserDetails.java
│   │   └── CustomReactiveUserDetailsService.java
│   │
│   ├── service/
│   │   ├── AuthService.java / impl/AuthServiceImpl.java         # 登录/登出/刷新
│   │   ├── UserPermissionCacheService.java / impl/...           # Redis 权限缓存（Set）
│   │   ├── TokenBlacklistService.java / impl/...                # Token/用户黑名单
│   │   └── AuditLogService.java / impl/AuditLogServiceImpl.java # 审计日志
│   │
│   └── util/
│       ├── JwtUtil.java                    # JWT 工具（含 jti 唯一标识）
│       ├── IpUtil.java                     # 可信代理 XFF 提取
│       └── GatewaySignatureUtil.java       # HMAC-SHA256 签名工具
│
├── src/main/resources/
│   ├── application.yml                     # 主配置（含 Nacos/Sentinel/OTel/HTTPS）
│   ├── application-dev.yml                 # 开发环境配置
│   └── ssl/gateway.p12                     # 自签证书（gen-keystore.sh 生成）
│
└── pom.xml
```

---

## 🚀 快速开始

### 环境要求

| 软件 | 版本 | 必需 |
|------|------|------|
| JDK | 21+ | ✅ |
| Maven | 3.6+ | ✅ |
| MySQL | 8.0+ | ✅ |
| Redis | 5.0+ | ✅ |
| Nacos | 2.x | ✅ |
| Docker（可选） | 20+ | 推荐 |

### 1. 启动基础设施

#### 使用 Docker 快速启动 MySQL + Redis + Nacos

```bash
# MySQL
docker run -d --name mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=product_test \
  -p 3306:3306 \
  mysql:8.0

# Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Nacos（单机模式）
docker run -d --name nacos \
  -e MODE=standalone \
  -p 8848:8848 \
  nacos/nacos-server:v2.3.0
```

#### 或使用已有服务

本项目当前开发环境：
- MySQL：`10.70.239.17:3306`，数据库 `product_test`
- Redis：`10.70.239.17:6379`
- Nacos：`10.70.239.17:8848`（用户名/密码：nacos/nacos）

### 2. 初始化数据库

```bash
mysql -h 10.70.239.17 -u sapiece -p product_test < sql/sys_user_role_menu.sql
```

### 3. 修改配置

编辑 `src/main/resources/application-dev.yml`，填入你的 MySQL、Redis、Nacos 地址：

```yaml
spring:
  r2dbc:
    url: r2dbc:mysql://YOUR_MYSQL_HOST:3306/product_test?serverTimezone=Asia/Shanghai
    username: YOUR_USER
    password: YOUR_PASSWORD
  data:
    redis:
      host: YOUR_REDIS_HOST
  cloud:
    nacos:
      discovery:
        server-addr: YOUR_NACOS_HOST:8848
        username: nacos
        password: nacos
      config:
        server-addr: YOUR_NACOS_HOST:8848
        username: nacos
        password: nacos
```

### 4. 编译并启动

```bash
# 编译
mvn clean package -DskipTests

# 开发环境启动
java -jar target/SAPiece-Gateway-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# 或
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5. 验证启动

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 测试登录（默认账号 admin/123456 或 superadmin/123456）
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userName": "admin", "password": "123456"}'
```

成功响应示例：

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "userId": 2,
    "userName": "admin",
    "nickName": "管理员"
  }
}
```

---

## ⚙️ 详细配置

### JWT 配置

```yaml
jwt:
  secret: ${JWT_SECRET:SAPiece-Gateway-Secret-Key-2025-...}  # 生产必须通过环境变量注入
  access-token-expiration: 1800000    # Access Token：30分钟
  refresh-token-expiration: 604800000 # Refresh Token：7天
```

**安全要求**：
- 密钥长度 ≥ 256 bits
- 生产环境通过环境变量注入：`export JWT_SECRET=<your-secret>`
- 每个 Token 包含 `jti`（UUID）防止同秒碰撞

### 限流配置

```yaml
rate-limit:
  enabled: true
  strategy: route           # ip / route / user 三种策略

  ip:
    qps: 100                # 每 IP 每秒 100 请求
    capacity: 200

  route:
    default-qps: 50         # 每路由默认 50 QPS
    default-capacity: 100

  user:
    default-qps: 50
    default-capacity: 100
```

### IP 黑白名单

```yaml
ip-filter:
  blacklist-enabled: false
  blacklist:
    - 192.168.1.100           # 精确 IP
    - 10.0.0.0/8              # CIDR 段

  whitelist-enabled: false
  whitelist:
    - 192.168.0.0/16

# 可信代理（仅来自这些 IP 的请求才信任 X-Forwarded-For）
trusted-proxies:
  - 127.0.0.1
  - 10.70.0.0/16             # Nginx 内网段
```

### 下游签名配置

```yaml
gateway:
  downstream-sign:
    enabled: true
    secret: ${GATEWAY_DOWNSTREAM_SECRET:change-me-in-production}
    timestamp-validity-ms: 30000   # 签名有效期 30s，防重放
```

### HTTPS 配置

```bash
# 1. 生成自签证书（开发/测试用）
chmod +x scripts/gen-keystore.sh
./scripts/gen-keystore.sh
# → 输出到 src/main/resources/ssl/gateway.p12

# 2. 启用 HTTPS
export SSL_ENABLED=true
export SSL_KEY_STORE_PASSWORD=changeit  # 对应证书密码
java -jar target/SAPiece-Gateway-*.jar
```

生产环境替换自签证书为 Let's Encrypt 或企业 CA 颁发的证书：

```yaml
server:
  ssl:
    enabled: ${SSL_ENABLED:false}
    key-store: ${SSL_KEY_STORE:classpath:ssl/gateway.p12}
    key-store-password: ${SSL_KEY_STORE_PASSWORD:changeit}
    key-store-type: PKCS12
    key-alias: gateway
```

---

## 🛣️ 动态路由

### 路由管理接口

所有接口需要 `ADMIN` 或 `SUPER_ADMIN` 角色，请求头携带 `Authorization: Bearer {accessToken}`。

| Method | 路径 | 说明 | 权限 |
|--------|------|------|------|
| GET | `/admin/route/list` | 查询所有路由 | `gateway:route:list` |
| GET | `/admin/route/{id}` | 查询路由详情 | `gateway:route:query` |
| POST | `/admin/route` | 新增路由 | `gateway:route:add` |
| PUT | `/admin/route/{id}` | 修改路由 | `gateway:route:update` |
| DELETE | `/admin/route/{id}` | 删除路由 | `gateway:route:delete` |
| POST | `/admin/route/refresh` | 手动刷新路由缓存 | `gateway:route:refresh` |
| GET | `/admin/route/stats` | 路由统计信息 | `gateway:route:list` |

### 路由配置示例

#### 1. 静态地址路由

```bash
curl -X POST http://localhost:8080/admin/route \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "user-service",
    "routeName": "用户服务",
    "uri": "http://user-service:8081",
    "predicates": [{"name":"Path","args":{"pattern":"/api/user/**"}}],
    "filters": [{"name":"StripPrefix","args":{"parts":"1"}}],
    "orderNum": 1,
    "status": 1,
    "requireAuth": 1
  }'
```

#### 2. 负载均衡路由（Nacos 服务发现）

```json
{
  "routeId": "order-service",
  "routeName": "订单服务",
  "uri": "lb://order-service",
  "predicates": [{"name":"Path","args":{"pattern":"/api/order/**"}}],
  "filters": [{"name":"StripPrefix","args":{"parts":"1"}}],
  "orderNum": 2,
  "status": 1
}
```

> `lb://order-service` 中 `order-service` 是下游服务注册到 Nacos 的 `spring.application.name`。

#### 3. 手动刷新路由

```bash
curl -X POST http://localhost:8080/admin/route/refresh \
  -H "Authorization: Bearer {token}"
```

路由变更后会通过 Redis Pub/Sub 自动通知所有网关实例刷新缓存，`POST /admin/route/refresh` 用于强制立即刷新。

---

## 🔐 权限控制

### RBAC 模型

```
用户 → 用户角色关联 → 角色 → 角色菜单关联 → 菜单/权限
```

### 内置角色

| 角色 | role_code | 说明 |
|------|-----------|------|
| 超级管理员 | SUPER_ADMIN | 绕过所有权限校验 |
| 管理员 | ADMIN | 可访问所有 `/admin/**` 接口 |
| 系统运维 | SYS_OPS | 路由和监控权限 |

### 内置测试账号（密码均为 123456）

| 用户名 | ID | 角色 |
|--------|----|------|
| superadmin | 1 | SUPER_ADMIN |
| admin | 2 | ADMIN |
| sysadmin | 3 | SYS_OPS |

### 权限注解

```java
// 方法级权限
@PreAuthorize("hasAuthority('gateway:route:update')")
@PutMapping("/{id}")
public Mono<Result<SysGatewayRoute>> update(...) { }

// 角色判断
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RestController
public class AdminController { }
```

---

## 🌐 服务发现与配置中心

### Nacos 服务注册

网关启动后自动注册到 Nacos，可在 Nacos 控制台 `服务管理 → 服务列表` 中看到 `sapiece-gateway`。

配置（`application.yml`）：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 10.70.239.17:8848
        username: nacos
        password: nacos
        namespace: public
        group: DEFAULT_GROUP
        register-enabled: true
        ip: ${POD_IP:}   # K8s 中通过环境变量注入 Pod IP
```

### 下游服务如何使用 Nacos

下游服务也注册到同一个 Nacos，网关路由 URI 使用 `lb://service-name`，Spring Cloud LoadBalancer 自动轮询健康实例。

```yaml
# 下游服务（示例：user-service）
spring:
  application:
    name: user-service
  cloud:
    nacos:
      discovery:
        server-addr: 10.70.239.17:8848
```

### Nacos 配置中心

网关支持从 Nacos 加载配置，配置文件名为 `sapiece-gateway.yaml`，Group 为 `DEFAULT_GROUP`。

```yaml
# application.yml
spring:
  config:
    import: "optional:nacos:${spring.application.name}.yaml"
```

**`optional:`** 前缀确保 Nacos 不可用时，网关仍能以本地配置启动，不影响可用性。

在 Nacos 控制台可热更新的配置（示例）：

```yaml
# sapiece-gateway.yaml（在 Nacos 中创建）
rate-limit:
  enabled: true
  strategy: route
  route:
    default-qps: 100

ip-filter:
  blacklist-enabled: true
  blacklist:
    - 1.2.3.4
```

---

## ⚡ 熔断降级（Sentinel）

### 架构分工

| 组件 | 职责 |
|------|------|
| **EnhancedRateLimitFilter** | 全局限流（IP/用户/路由维度，Lua Token Bucket）|
| **Sentinel GatewayFilter** | 下游服务熔断降级（慢调用 + 异常比率触发）|

两者独立运行，不冲突。

### 熔断规则配置

#### 方式 1：Nacos 持久化（推荐生产使用）

在 Nacos 中创建配置：

- DataId：`sapiece-gateway-degrade-rules`
- Group：`SENTINEL_GROUP`
- 内容（JSON）：

```json
[
  {
    "resource": "user-service",
    "grade": 0,
    "count": 2000,
    "slowRatioThreshold": 0.5,
    "minRequestAmount": 5,
    "statIntervalMs": 10000,
    "timeWindow": 10
  },
  {
    "resource": "order-service",
    "grade": 1,
    "count": 0.5,
    "minRequestAmount": 5,
    "statIntervalMs": 10000,
    "timeWindow": 10
  }
]
```

字段说明：
- `grade=0`：慢调用比率熔断；`grade=1`：异常比率熔断
- `count`（grade=0）：慢调用阈值，单位 ms
- `count`（grade=1）：异常比率，0.5 = 50%
- `timeWindow`：熔断持续时间，单位 s

#### 方式 2：Sentinel Dashboard

访问 Sentinel Dashboard（需部署），在 `熔断规则` 中添加规则。`resource` 对应 `sys_gateway_route.route_id` 字段。

### 熔断响应格式

熔断触发时，下游返回 HTTP 503：

```json
{
  "code": 503,
  "message": "下游服务繁忙，请稍后重试",
  "data": null,
  "success": false,
  "timestamp": 1777164000000
}
```

---

## 🔭 链路追踪（OpenTelemetry）

### 工作原理

```
客户端请求
  → 网关生成 traceId / spanId（OTel SDK 自动）
  → TraceHeaderGatewayFilter 注入下游请求头：
      X-Trace-Id: 7a918f83b6d352abf35c094ed12aa63b
      X-Span-Id:  1f6d148ab70db1b7
      traceparent: 00-7a918f83b6d352abf35c094ed12aa63b-1f6d148ab70db1b7-01
  → 下游服务 OTel SDK 识别 traceparent，自动创建子 Span
  → 所有 Span 上报到 Jaeger（OTLP gRPC）
  → Jaeger UI 展示完整调用链
```

### 配置

```yaml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0    # 开发：全采样；生产建议 0.1~0.2

otel:
  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
      protocol: grpc
  service:
    name: ${spring.application.name}
```

### 本地 Jaeger 快速启动

```bash
docker run -d --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 \
  -p 4317:4317 \
  jaegertracing/all-in-one:latest
```

Jaeger UI：http://localhost:16686

### 下游服务接入

下游服务添加相同的 OTel 依赖，并配置相同的 Jaeger 地址。`traceparent` header 会被 OTel SDK 自动识别，无需额外代码即可在 Jaeger 中看到完整调用链。

```xml
<!-- 下游服务 pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

---

## 🔑 下游信任（HMAC 签名）

### 问题背景

如果下游服务直接暴露在内网，恶意请求可以绕过网关直接调用，伪造 `X-User-Id` 等 header。HMAC 签名解决这个问题。

### 网关行为

每次请求转发给下游时，网关自动注入：

```
X-Gateway-Timestamp: 1777164000123        ← 毫秒时间戳
X-Gateway-Signature: c81874230db87a...    ← HMAC-SHA256 签名
X-User-Id:          2                     ← 真实用户 ID（认证后）
X-User-Name:        admin
X-User-Roles:       ADMIN,SUPER_ADMIN
X-User-Permissions: gateway:route:list,...
traceparent:        00-{traceId}-{spanId}-01
```

签名算法：

```
payload = "timestamp={ts}&userId={userId}&path={requestPath}"
signature = HMAC-SHA256(GATEWAY_DOWNSTREAM_SECRET, payload)
```

### 下游服务验证（Java 示例）

```java
@Component
public class GatewaySignatureVerifier {

    @Value("${gateway.downstream-sign.secret}")
    private String secret;

    public boolean verify(HttpServletRequest request) {
        String timestamp = request.getHeader("X-Gateway-Timestamp");
        String signature = request.getHeader("X-Gateway-Signature");
        String userId    = request.getHeader("X-User-Id");
        String path      = request.getRequestURI();

        if (timestamp == null || signature == null) return false;

        // 验证时间窗口（防重放，30s 内有效）
        long ts = Long.parseLong(timestamp);
        if (Math.abs(System.currentTimeMillis() - ts) > 30_000) return false;

        // 计算期望签名
        String payload = "timestamp=" + ts + "&userId=" + userId + "&path=" + path;
        String expected = hmacSHA256(secret, payload);
        return expected.equalsIgnoreCase(signature);
    }
}
```

也可直接复制 `GatewaySignatureUtil.java` 到下游服务使用。

### 环境变量同步

```bash
# 网关和所有下游服务必须设置相同的值
export GATEWAY_DOWNSTREAM_SECRET=your-shared-secret-here
```

---

## 🛡️ 安全加固

### Security Headers

所有响应自动注入以下安全响应头：

| Header | 值 | 防御目标 |
|--------|----|---------|
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | XSS / 数据注入 |
| `X-Frame-Options` | `DENY` | 点击劫持（Clickjacking）|
| `X-Content-Type-Options` | `nosniff` | MIME 嗅探攻击 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referer 信息泄露 |
| `Permissions-Policy` | `geolocation=(), microphone=(), camera=()` | 浏览器 API 滥用 |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | 强制 HTTPS（仅 HTTPS 部署有效）|
| `Cache-Control` | `no-store` | 认证接口响应不缓存 |

Server 和 X-Powered-By 头已自动移除，防止服务器信息泄露。

### XFF 可信代理防伪造

只有来自配置的可信代理 IP 的请求，才信任其 `X-Forwarded-For` header：

```yaml
trusted-proxies:
  - 10.70.0.0/16    # Nginx 内网 IP 段
  - 127.0.0.1
```

未在列表中的 IP 发来的 XFF header 会被忽略，直接用 `RemoteAddress` 作为客户端 IP。

---

## 📡 API 接口文档

### 认证接口

#### POST /auth/login — 用户登录

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"userName":"admin","password":"123456"}'
```

响应：

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGci...",   // 30分钟有效
    "refreshToken": "eyJhbGci...",  // 7天有效
    "userId": 2,
    "userName": "admin",
    "nickName": "管理员"
  }
}
```

#### GET /auth/info — 获取当前用户信息

```bash
curl http://localhost:8080/auth/info \
  -H "Authorization: Bearer {accessToken}"
```

#### POST /auth/refresh/token — 刷新 Access Token（双 Token 模式）

```bash
curl -X POST http://localhost:8080/auth/refresh/token \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"eyJhbGci..."}'
```

> **注意**：刷新成功后旧 Refresh Token 立即失效（黑名单），需使用新的 Token 对。

#### POST /auth/logout — 登出

```bash
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer {accessToken}"
```

### 路由管理接口

所有路由管理接口需要 `ADMIN` 或 `SUPER_ADMIN` 角色。

```bash
# 查询路由列表
curl http://localhost:8080/admin/route/list \
  -H "Authorization: Bearer {token}"

# 刷新路由缓存（强制）
curl -X POST http://localhost:8080/admin/route/refresh \
  -H "Authorization: Bearer {token}"

# 路由统计
curl http://localhost:8080/admin/route/stats \
  -H "Authorization: Bearer {token}"
```

### 管理接口

```bash
# IP 黑名单查询
curl http://localhost:8080/admin/ip/blacklist \
  -H "Authorization: Bearer {token}"

# 添加 IP 到黑名单
curl -X POST http://localhost:8080/admin/ip/blacklist \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"ip":"1.2.3.4"}'

# 审计日志时间范围查询（注意日期格式：yyyy-MM-dd HH:mm:ss）
curl -X POST http://localhost:8080/admin/audit-log/time-range \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"startTime":"2026-01-01 00:00:00","endTime":"2027-01-01 00:00:00"}'

# 灰度规则列表
curl http://localhost:8080/admin/gray/rules \
  -H "Authorization: Bearer {token}"
```

### Knife4j 在线文档

启动后访问：http://localhost:8080/doc.html

---

## 🚢 部署指南

### 方式 1：本地开发

```bash
java -jar target/SAPiece-Gateway-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

### 方式 2：docker-compose HA 部署

#### 架构

```
nginx (80/443) → gateway×N (8080) → MySQL + Redis + Nacos
                                   → Jaeger (链路追踪)
                                   → Prometheus → Grafana
```

#### 启动

```bash
# 构建镜像
mvn clean package -DskipTests
docker build -t sapiece-gateway:latest .

# 单实例启动
docker-compose up -d

# HA：2 个网关实例
docker-compose up -d --scale gateway=2

# HA：3 个网关实例
docker-compose up -d --scale gateway=3
```

#### 访问

| 服务 | 地址 |
|------|------|
| 网关（HTTP） | http://localhost:80 |
| 网关（HTTPS）| https://localhost:443 |
| Jaeger UI | http://localhost:16686 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000（admin/admin123）|

#### 关键环境变量

在 `docker-compose.yml` 中或通过 `.env` 文件配置：

```env
DB_HOST=10.70.239.17
DB_USER=sapiece
DB_PASSWORD=159357
REDIS_HOST=10.70.239.17
NACOS_HOST=10.70.239.17
JWT_SECRET=your-secret
GATEWAY_DOWNSTREAM_SECRET=your-hmac-secret
GRAFANA_PASSWORD=admin123
```

### 方式 3：Kubernetes 生产部署

#### 前置条件

- K8s 集群（1.24+）
- 已推送镜像到 Registry（修改 `k8s/deployment.yaml` 中的 image）

#### 部署步骤

```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 修改 Secret 中的敏感配置
vim k8s/configmap.yaml  # 修改 stringData 中的密码等

# 3. 部署配置
kubectl apply -f k8s/configmap.yaml

# 4. 部署应用
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/hpa.yaml

# 5. 查看部署状态
kubectl get pods -n sapiece
kubectl get svc -n sapiece
kubectl get hpa -n sapiece
```

#### HPA 扩缩策略

| 指标 | 阈值 | 行为 |
|------|------|------|
| CPU 使用率 | >70% | 快速扩容（每次 +2 Pod，间隔 60s）|
| 内存使用率 | >80% | 快速扩容 |
| 缩容 | 稳定 5 分钟 | 保守缩容（每次 -1 Pod，间隔 120s）|
| 最小副本 | 2 | 保证 HA |
| 最大副本 | 10 | 防止无限扩容 |

#### 健康探针

- **Startup Probe**：等待 Nacos 注册完成（最长等 60s）
- **Liveness Probe**：检测应用是否卡死，失败后重启
- **Readiness Probe**：检测是否就绪接收流量，未就绪时从 Service 摘除

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
```

### 方式 4：systemd 服务（传统部署）

```ini
# /etc/systemd/system/sapiece-gateway.service
[Unit]
Description=SAPiece Gateway
After=network.target

[Service]
User=sapiece
WorkingDirectory=/opt/sapiece-gateway
ExecStart=/usr/bin/java \
  -XX:+UseZGC \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar SAPiece-Gateway.jar \
  --spring.profiles.active=prod
SuccessExitStatus=143
Restart=always
RestartSec=10
EnvironmentFile=/opt/sapiece-gateway/.env

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now sapiece-gateway
```

---

## 📊 监控运维

### Actuator 端点

| 端点 | 说明 |
|------|------|
| `/actuator/health` | 健康状态（含 DB/Redis/Sentinel/JVM）|
| `/actuator/health/liveness` | K8s Liveness Probe |
| `/actuator/health/readiness` | K8s Readiness Probe |
| `/actuator/info` | 应用版本信息 |
| `/actuator/metrics` | Micrometer 指标 |
| `/actuator/prometheus` | Prometheus 格式指标 |

### Prometheus + Grafana

`deploy/prometheus/prometheus.yml` 已配置自动发现网关实例。

推荐 Grafana Dashboard：
- **Spring Boot**：Dashboard ID `4701`（Spring Boot 2.x/3.x/4.x Micrometer）
- **JVM**：Dashboard ID `11955`

### Jaeger 链路追踪

1. 确认 `OTEL_EXPORTER_OTLP_ENDPOINT` 指向 Jaeger（docker-compose 中为 `http://jaeger:4317`）
2. 访问 Jaeger UI：`http://localhost:16686`
3. 在 `Service` 下拉选择 `sapiece-gateway`，查看请求追踪详情

### 日志管理

日志文件：`logs/sapiece-gateway.log`（滚动，每文件 10MB，保留 30 天）

日志格式示例（包含 traceId）：

```
2026-04-26 15:30:12.953 [reactor-http-nio-2] INFO  c.s.n.s.security.JwtSecurityContextRepository - 【JWT认证】认证成功, user: admin, path: /api/user/list
```

ELK 接入：日志格式为结构化 JSON（通过 logstash-logback-encoder），可直接发送到 Logstash。

### 告警规则示例（Prometheus Alertmanager）

```yaml
groups:
  - name: gateway
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 3m
        annotations:
          summary: "网关 5xx 错误率过高（>5%）"

      - alert: SentinelCircuitOpen
        expr: increase(sentinel_pass_total{result="block"}[1m]) > 100
        annotations:
          summary: "Sentinel 熔断频繁触发"
```

---

## ❓ 常见问题

### Q1：启动报 `found duplicate key management`

**原因**：`application.yml` 中有重复的 `management:` 顶级 key（YAML 不允许）。

**解决**：检查 application.yml，将所有 management 配置合并到一个块。

---

### Q2：Nacos 注册慢（`Cannot determine local hostname`）

**原因**：macOS 环境下 DNS 解析本机 hostname 超时。

**解决**：在 Nacos Discovery 配置中显式指定 IP：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        ip: 127.0.0.1   # 开发环境填本机 IP；K8s 中用 ${POD_IP}
```

---

### Q3：登出后立即登录，新 Token 被识别为黑名单中

**原因**：同一秒内生成的 JWT 若缺少 `jti`（JWT ID），内容相同导致 MD5 哈希碰撞。

**状态**：已修复（`JwtUtil.java` 中每个 Token 包含 `IdUtil.simpleUUID()` 作为 jti）。

---

### Q4：路由更新后未立即生效

**原因**：路由定义有 30s TTL 缓存，多实例间通过 Redis Pub/Sub 同步。

**解决**：调用 `POST /admin/route/refresh` 强制立即刷新。如需缩短自动刷新时间，修改 `DatabaseRouteDefinitionRepository.CACHE_TTL_MS`。

---

### Q5：`401 Token无效` 但 Token 刚签发

**排查步骤**：
1. 确认请求头格式：`Authorization: Bearer {accessToken}`（注意有空格）
2. 确认使用 `accessToken` 不是 `refreshToken`（refresh token 不能用于 API 访问）
3. 检查是否调用了 `/auth/refresh/token` 刷新，旧 Refresh Token 已失效，需使用新 Access Token

---

### Q6：审计日志时间范围查询报 `No request body`

**原因**：日期格式错误，`@JsonFormat` 要求 `yyyy-MM-dd HH:mm:ss`（空格，非 T）。

**正确格式**：

```json
{
  "startTime": "2026-01-01 00:00:00",
  "endTime":   "2027-01-01 00:00:00"
}
```

---

### Q7：Sentinel 熔断后一直不恢复

**原因**：`timeWindow`（熔断持续时间）配置过长，或下游服务仍不健康。

**查看**：Sentinel Dashboard → 熔断降级 → 查看对应资源状态（CLOSED / OPEN / HALF_OPEN）。

**手动重置**：重启网关实例可清除内存中的熔断状态（Nacos 数据源中的规则依然保留）。

---

### Q8：下游服务无法验证 X-Gateway-Signature

**排查步骤**：
1. 确认下游服务 `GATEWAY_DOWNSTREAM_SECRET` 与网关一致
2. 确认服务器时间差在 30s 内（`X-Gateway-Timestamp` 有效期 30s）
3. 确认签名 payload 构造：`timestamp={ts}&userId={uid}&path={exactPath}`
4. 路径需完全匹配，含查询参数的路径不包含 `?` 之后的部分

---

## 📝 更新日志

### v2.0.0（2026-04-26）— 生产就绪版本

**新增**

- ✅ Spring Boot 升级至 **4.0.5**，Spring Cloud 升级至 **2025.1.0**
- ✅ **Nacos 服务发现**：网关自动注册，支持 `lb://service-name` 路由
- ✅ **Nacos 配置中心**：`optional:nacos:` 热更新，不可用时优雅降级
- ✅ **Sentinel 熔断降级**：替换 Resilience4j，v6x Gateway 适配器，规则持久化 Nacos
- ✅ **OpenTelemetry 链路追踪**：`spring-boot-starter-opentelemetry`，traceparent 注入下游
- ✅ **HMAC 下游信任签名**：`X-Gateway-Signature` + `X-Gateway-Timestamp`，防绕过攻击
- ✅ **Security Headers 过滤器**：HSTS / CSP / X-Frame-Options / 防 MIME 嗅探
- ✅ **HTTPS 支持**：`SSL_ENABLED=true` 启用，内置证书生成脚本
- ✅ **HA docker-compose**：Nginx + gateway×N + Jaeger + Prometheus + Grafana
- ✅ **K8s 生产清单**：Deployment / Service / HPA / ConfigMap / Secret（含健康探针）
- ✅ **JWT jti 唯一标识**：防止同秒生成相同 Token 的哈希碰撞
- ✅ **权限缓存 Redis Set**：替换 List，去重且无 `__EMPTY__` 占位符
- ✅ **Redis Pub/Sub 路由缓存同步**：多实例路由变更即时同步
- ✅ **XFF 可信代理防伪造**：只信任配置的代理 IP 的 X-Forwarded-For

**修复**

- ✅ JWT 同秒生成哈希碰撞导致合法 Token 被黑名单误判
- ✅ `getTokenInfo` 登出后返回 code=500 而非 401
- ✅ `@Idempotent` 在 WebFlux 环境下 HEADER 模式失效
- ✅ 路由更新 NOT NULL 字段被覆盖为 null
- ✅ 审计日志清理 0 行时空响应

**移除**

- ❌ Resilience4j（由 Sentinel 替代）

---

### v1.0.0（2025-11-24）— 初始版本

- ✅ JWT 双 Token 认证
- ✅ RBAC 权限控制
- ✅ IP 黑白名单
- ✅ Lua Token Bucket 限流
- ✅ 数据库动态路由
- ✅ 审计日志
- ✅ 请求日志
- ✅ 幂等性保护
- ✅ 灰度发布
- ✅ Prometheus 监控
- ✅ Swagger 文档

---

## 📚 参考资料

- [Spring Cloud Gateway 文档](https://docs.spring.io/spring-cloud-gateway/reference/)
- [Spring Cloud Alibaba 文档](https://sca.aliyun.com/docs/2023/overview/what-is-sca/)
- [Sentinel 文档](https://sentinelguard.io/zh-cn/docs/introduction.html)
- [OpenTelemetry Java 文档](https://opentelemetry.io/docs/instrumentation/java/)
- [Nacos 文档](https://nacos.io/zh-cn/docs/v2/what-is-nacos.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/)

---

## 📄 License

本项目采用 [MIT](LICENSE) 开源协议。

---

## 👥 作者

**SAPiece Team**

- Email：zhouleileisapiece@gmail.com

---

<div align="center">

**Made with ❤️ by SAPiece Team**

</div>
