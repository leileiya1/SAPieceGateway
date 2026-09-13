# syntax=docker/dockerfile:1
# Build for the deployment host architecture, not the macOS host.
FROM maven:3.9-eclipse-temurin-25@sha256:31618505df21177d2baa3dc574be2d0b0b32614c8539baca1f23a9136b766eb0 AS maven
FROM container-registry.oracle.com/graalvm/native-image:25i3@sha256:18253bc0069911faa4326c2e37144210e0d51d84fbb06411cc1b0b39e4fb04a3 AS builder
COPY --from=maven /usr/share/maven /usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"
WORKDIR /build
COPY pom.xml ./
COPY .mvn ./.mvn
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -s .mvn/settings.xml -B -ntp -Pnative native:compile \
    || { cat target/svm_err_*.md 2>/dev/null; exit 1; }

FROM debian:trixie-slim@sha256:d7e12182ce18b85b93007c1dedf31f2d29e01ccf3182cc4017c709b6259bc132 AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl libzstd1 zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10001 --create-home gateway
WORKDIR /app
COPY --from=builder --chown=gateway:gateway /build/target/sapiece-gateway ./sapiece-gateway
COPY --from=builder --chown=gateway:gateway /build/target/*.so ./
RUN mkdir logs && chown gateway:gateway logs
USER gateway
ENV SPRING_PROFILES_ACTIVE=prod SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail --silent http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness || exit 1
ENTRYPOINT ["/app/sapiece-gateway", "-Duser.home=/tmp"]
