# SAPiece Gateway

基于 Spring Boot 4.0.8、Spring Cloud Gateway 2025.1.3 和 GraalVM Native Image 的响应式后端网关。入口流量由 Nginx 转发到 K3s NodePort，网关再通过 Kubernetes Service 将请求动态分发到下游微服务。

## 当前架构

```text
                         ┌─ gateway Pod @ school-linux ─┐                                  ┌─ HTTP service Pods
Nginx ── NodePort 30096 ─┤                              ├─ Kubernetes Service/EndpointSlice ┤
                         └─ gateway Pod @ ubuntu-server ┘                                  └─ Envoy ── gRPC service Pods
                                      │
                         MySQL StatefulSet + Redis StatefulSet
                                  @ school-linux
```

- `school-linux`：现有 K3s Server/control-plane，32 CPU、61 GiB；同时作为持久数据节点。
- `ubuntu-server`：加入为 K3s Agent/worker，16 CPU、30 GiB；运行第二个网关副本。
- 这是两节点测试集群，只有一个 control-plane，不属于 control-plane HA。真正的控制面高可用需要增加第三台 K3s Server。
- 下游应用无需向注册中心发送心跳。Deployment/Pod 与 Service selector 匹配后，Kubernetes 自动维护 EndpointSlice。
- 数据库路由继续支持 `lb://user-service`。写入 Spring Gateway 前会将它转换为 `http://user-service.sapiece.svc.cluster.local:80`，实际 Pod 选择、摘除与负载均衡由 Kubernetes Service/EndpointSlice 完成。
- 启动配置放在 ConfigMap/Secret。动态业务路由放在 MySQL，并通过 Redis Pub/Sub 同步多个网关实例。

## 为什么不在 Native 镜像中加入 Kubernetes SDK

Spring Cloud Kubernetes 5.0.3 提供 Kubernetes Java Client 和 Fabric8 两套 Discovery/Config starter，但它们会把完整客户端、模型与序列化依赖带入镜像。本项目只需要 Kubernetes Service 的稳定 DNS 地址，因此在路由加载阶段直接改写 `lb://` URI。这样既保留原有数据库路由格式，也不引入 Kubernetes SDK 或 Spring Cloud LoadBalancer 的 Native 体积、反射配置和运行时子上下文。

ConfigMap/Secret 的基础设施配置采用滚动发布生效。Native/AOT 环境不依赖 `RefreshScope` 热重建 Bean；安全配置变化可审计、可回滚。两个 Gateway 副本、`minAvailable: 1` 的 PDB 和一次替换一个副本的策略共同保持服务连续与跨节点分布。路由、IP 名单等业务配置仍可在线更新。

## 主要能力

- JWT Access/Refresh 双 Token、密码版本失效、Redis Token 黑名单。
- Redis Lua 分布式限流、幂等保护、跨实例 IP 黑白名单。
- MySQL R2DBC 动态路由，写入校验、事件刷新和 Redis 多实例广播。
- Sentinel 慢调用/异常比率熔断与统一降级响应。
- OAuth2 state 一次性消费、AES-256-GCM 敏感字段加密、出站 URI SSRF 限制。
- HMAC 下游签名、可信代理解析、CORS 和安全响应头。
- OpenTelemetry tracing、Prometheus 指标、结构化审计查询。
- 标准 HTTP 状态码、优雅停机、startup/liveness/readiness 探针。

## 版本与构建

| 组件 | 版本/方式 |
|---|---|
| 开发 JDK | 26 |
| Native toolchain | GraalVM 25 |
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.3 |
| K3s | `school-linux` 当前为 v1.34.3+k3s1 |
| MySQL | 8.4 StatefulSet |
| Redis | 7.4 StatefulSet |

```bash
mvn -B test
mvn -B -Pnative native:compile
docker build -f Dockerfile.k3s -t sapiece-gateway:native-k3s-v6 .
```

`Dockerfile.k3s` 使用 distroless Debian 13 runtime，不包含 JDK、shell、curl 或包管理器。Kubernetes 直接执行 HTTP 探针。Docker Compose 回滚环境继续使用普通 `Dockerfile`，因为 Compose healthcheck 需要 curl。

## K3s 部署顺序

1. 在 `school-linux` 备份现有 Docker MySQL/Redis。
2. 保留现有 `school-linux` K3s Server，修正 Meta TUN 对节点网段的路由。
3. 使用 K3s node token 将 `ubuntu-server` 加入为 Agent。
4. 两节点 Ready 后配置两个 CoreDNS 副本，每台节点各一个。
5. 将 Native 镜像导入两台节点的 K3s containerd。
6. 在 `school-linux` 创建 Secret、schema ConfigMap、MySQL/Redis StatefulSet，并恢复 SQL 备份。
7. 发布两个网关副本，执行接口和 Pod 故障切换测试。
8. 验证完成后再停止旧 Docker 网关与 Nacos；Docker MySQL/Redis 备份保留到观察期结束。

相关脚本位于 `scripts/k3s/`，详细命令和回滚方式见 [NATIVE.md](NATIVE.md)。Kubernetes 清单位于 `k8s/`，`k8s/secret.yaml` 永远不提交。

## 下游服务约定

每个下游服务创建一个与路由服务名相同的 Kubernetes Service，并将稳定端口统一命名为 `http`、默认暴露为 80：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: sapiece
spec:
  selector:
    app: user-service
  ports:
    - name: http
      port: 80
      targetPort: 8080
```

数据库路由使用 `lb://user-service`。若某个 Service 不使用 80，可通过 `gateway.kubernetes-dns.ports.<service-name>` 配置显式端口。

协议边界是：客户端和 Nginx 到网关使用 HTTP/JSON，便于浏览器、鉴权、限流和统一错误处理；网关到下游服务使用 gRPC，获得强类型契约、HTTP/2 多路复用和流式调用。Spring Gateway 负责 HTTP 路由，Envoy 根据已编译的 `.proto` 描述符完成 JSON/gRPC 转码，Kubernetes Service/EndpointSlice 负责实例发现、健康摘除和负载均衡。每个业务接口必须在 `.proto` 中维护稳定映射，网关不会从任意 HTTP 请求猜测 gRPC 方法。

可运行的转码示例位于 [`examples/grpc-echo`](examples/grpc-echo/README.md)，部署清单位于 `k8s/grpc-demo.yaml`。三个 Go gRPC 实例和两个 Envoy 1.39 转码实例跨两节点分布，Gateway 到下游使用 HMAC v2 防篡改和防重放，Envoy 到 Go 使用 TLS 1.3 双向认证。测试覆盖并发分流、服务端流、错误码、超时、动态路由跨网关同步以及 Go/Envoy Pod 零错误摘除。示例使用 Envoy `auto_mapping` 和 Gateway 路径改写；正式业务可以在 `.proto` 中加入 `google.api.http` 注解，直接声明稳定 REST 路径。

## Jenkins 自动发布

`ubuntu-server` 上的 Jenkins Controller 监听 `8088`，通过固定主机密钥和专用 SSH 凭据连接 `school-linux` Agent。仓库推送由 GitHub webhook 触发，内网无法接收 webhook 时由五分钟 SCM 轮询兜底。流水线在 school 节点完成 Java、Go、Buf 和 Kustomize 校验，以 Git commit 作为不可变镜像标签，构建完成后将同一份 SHA-256 校验镜像包导入两个 K3s 节点，再滚动部署和执行黑盒故障切换测试。任一步失败会恢复部署前的三个镜像标签。

为避免构建影响 K3s 控制面和 MySQL/Redis，Jenkins 只有一个 school executor，项目禁止并发构建，并使用主机级 `flock`。Native Image 限制为 12 个 CPU 和 12 GiB Java heap。服务器配置和流水线说明见 [`ops/jenkins`](ops/jenkins/README.md)。

## 测试

```bash
# 43 个 Java 单元/健壮性测试
mvn -B test

# Docker 或 K3s 中的全接口黑盒测试；会创建随机隔离数据并在 finally 中清理
python3 src/test/integration/test_all_endpoints.py

# K3s Service + lb:// 路由 + 删除 Pod 后继续可用
KUBECTL='sudo k3s kubectl' \
GATEWAY_BASE_URL=http://127.0.0.1:30096 \
python3 src/test/integration/test_k3s_lb.py

# HTTP/JSON -> Spring Gateway -> Envoy -> 三个 Go gRPC 实例
KUBECTL='sudo k3s kubectl' \
GATEWAY_BASE_URL=http://127.0.0.1:30096 \
python3 src/test/integration/test_http_grpc_transcoding.py

# Docker 回滚环境故障注入
ALLOW_FAULT_TESTS=1 python3 src/test/integration/test_resilience.py
```

集成测试需要 `python3-bcrypt`。针对非回环网关地址运行时，还需显式设置 `ALLOW_REMOTE_TEST=1`。

## 安全要求

- `.env`、Kubernetes Secret、JWT/HMAC/OAuth 密钥不可提交。
- Nginx 与 NodePort 之间使用受控内网；公网 TLS 在 Nginx 终止。
- 生产前将 `trusted-proxies` 和 CORS 来源收紧到实际地址。
- MySQL、Redis 只发布 ClusterIP，不开放宿主机端口。
- Secret 更新后执行 `kubectl rollout restart deployment/sapiece-gateway -n sapiece`。
- ConfigMap 更新可运行 `sudo scripts/k3s/apply-config.sh`，脚本会等待零停机滚动发布完成。
