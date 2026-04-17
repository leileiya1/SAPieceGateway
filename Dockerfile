# ============================================================
# SAPiece Gateway — 多阶段构建 Dockerfile
# 运行时：Eclipse Temurin JRE 21（Alpine）
# ============================================================

# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# 先只拷贝 POM，利用 Docker 层缓存下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 拷贝源码并打包（跳过测试）
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- 运行阶段 ----
FROM eclipse-temurin:21-jre-alpine

# 使用非 root 用户运行
RUN addgroup -S sapiece && adduser -S sapiece -G sapiece

WORKDIR /app

# 从构建阶段复制 jar
COPY --from=builder /build/target/SAPiece-Gateway-*.jar app.jar

# 日志目录，挂载宿主机卷可持久化日志
RUN mkdir -p logs && chown -R sapiece:sapiece /app

USER sapiece

EXPOSE 8080

# JVM 参数说明：
#   -XX:+UseZGC                  ZGC 低停顿 GC，适合响应式服务
#   -XX:MaxRAMPercentage=75.0    最多使用容器内存的 75%，避免 OOM Kill
#   -Djava.security.egd=...      加速 SecureRandom 初始化（影响 JWT 签发速度）
#   SPRING_PROFILES_ACTIVE       通过环境变量切换配置文件，默认 prod
ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]

# 默认激活 prod profile，可通过 -e SPRING_PROFILES_ACTIVE=dev 覆盖
ENV SPRING_PROFILES_ACTIVE=prod
