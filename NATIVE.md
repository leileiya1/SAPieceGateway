# Native 与 K3s 运维说明

## 体积口径

当前 `native-k3s-v6` 的 Native 可执行文件约 211 MiB，OCI 镜像大小约 97 MB，gzip 传输归档约 93 MB。可执行文件包含可压缩的数据段，因此文件表观大小不能直接与镜像传输大小比较。

`Dockerfile.k3s` 改用 distroless runtime，删除 shell、curl、apt 和普通 Debian 用户空间。可执行文件本身仍包含 Spring Boot、WebFlux Gateway、Security、R2DBC、Redis、Sentinel、OpenTelemetry 和 OpenAPI 的已编译代码，因此不会接近简单 Go 服务的体积。此前 Native profile 本来就没有 Nacos gRPC 客户端，所以移除 Nacos 主要减少 JVM 构建依赖与运行组件，Native 可执行文件只会小幅变化。

## 当前迁移策略

保留 `school-linux` 已运行的 K3s Server，加入 `ubuntu-server` Agent。这比卸载一个已运行一个多月的集群再调换角色安全。`school-linux` 的 `Meta` TUN 会捕获发往 `10.65.0.0/16` 的流量，必须先运行：

```bash
sudo scripts/k3s/configure-school-network.sh
```

在 `school-linux` 获取 join token：

```bash
sudo cat /var/lib/rancher/k3s/server/node-token
```

把 `scripts/k3s/install-ubuntu-agent.sh` 放到 `ubuntu-server` 后执行，按提示粘贴 token：

```bash
sudo ./scripts/k3s/install-ubuntu-agent.sh
```

所需节点通信包括 Agent 到 Server 的 TCP 6443、两节点之间的 UDP 8472（Flannel VXLAN）和 TCP 10250。安装后必须实际验证 Pod 跨节点通信，单纯 `nc -u` 成功不能证明 UDP 数据路径完整。

两台节点都 Ready 后，在 `school-linux` 配置两个 CoreDNS 副本并让节点优先使用本机 DNS 端点：

```bash
sudo scripts/k3s/configure-coredns.sh
```

脚本要求至少两台可调度的 Ready 节点，并用 hostname 拓扑约束把 CoreDNS 分散到不同节点。

## 数据备份与部署

先在 `school-linux` 的项目目录执行：

```bash
scripts/k3s/backup-school-docker.sh
```

构建并导出镜像：

```bash
docker build -f Dockerfile.k3s -t sapiece-gateway:native-k3s-v6 .
docker save sapiece-gateway:native-k3s-v6 -o sapiece-gateway-native-k3s-v6.tar
```

将 tar 复制到两个节点，并分别执行：

```bash
sudo scripts/k3s/import-gateway-image.sh ./sapiece-gateway-native-k3s-v6.tar
```

在 `school-linux` 执行部署。第二个参数是上一步备份得到的 SQL 文件：

```bash
sudo scripts/k3s/deploy-on-school.sh .env \
  "$HOME/sapiece-gateway-backups/<timestamp>/sapiece_gateway.sql"
```

脚本会：

- 验证必要密钥存在，创建临时 Secret 输入文件并在退出时删除。
- 创建 namespace、Secret 和数据库 schema ConfigMap。
- 给 `school-linux` 节点设置 `sapiece.io/data=true` 标签。
- 启动 MySQL/Redis StatefulSet，等待就绪后恢复 MySQL 数据。
- 发布 Gateway Service、NodePort、PDB、两个网关副本和 HPA。

Redis 备份用于回滚取证。Redis 中主要是缓存、短期 OAuth state、限流和撤销状态；切换期间旧网关会停写，因此 K3s Redis 从空实例启动。切换前应使旧访问令牌失效或设置短观察窗口，避免遗漏旧 Token 黑名单。

## 验证

```bash
sudo k3s kubectl get nodes -o wide
sudo k3s kubectl -n sapiece get pods,svc,pvc -o wide
sudo k3s kubectl -n sapiece rollout status deployment/sapiece-gateway
curl --fail http://127.0.0.1:30096/actuator/health/readiness

KUBECTL='sudo k3s kubectl' \
GATEWAY_BASE_URL=http://127.0.0.1:30096 \
python3 src/test/integration/test_all_endpoints.py

KUBECTL='sudo k3s kubectl' \
GATEWAY_BASE_URL=http://127.0.0.1:30096 \
python3 src/test/integration/test_k3s_lb.py
```

确认两个网关 Pod 分散到两台节点，删除测试后端的一个 Pod 时 `lb://` 路由仍返回 200。随后让 Nginx upstream 指向两台节点的 `30096`。NodePort 使用 `externalTrafficPolicy: Cluster`，即使滚动发布时某节点暂时没有本地 Gateway Pod，也能转发到另一节点的就绪副本；Nginx 仍应启用健康检查。

## 下线与回滚

验证通过前不要删除 Docker volume。完成验证后，旧 Nacos 可停止但先不删除：

```bash
docker stop sapiece-gateway-nacos sapiece-gateway-native
```

出现问题时重新启动旧容器并把 Nginx upstream 切回 `127.0.0.1:8096`：

```bash
docker start sapiece-gateway-mysql sapiece-gateway-redis sapiece-gateway-nacos sapiece-gateway-native
```

K3s 资源可暂停而不删 PVC：

```bash
sudo k3s kubectl -n sapiece scale deployment/sapiece-gateway --replicas=0
sudo k3s kubectl -n sapiece scale statefulset/mysql statefulset/redis --replicas=0
```
