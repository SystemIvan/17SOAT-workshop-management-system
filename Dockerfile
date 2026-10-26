# Base images are pinned to the full Temurin version plus the multi-arch index digest (amd64 and arm64), resolved on
# 2026-10-06 with `docker buildx imagetools inspect eclipse-temurin:<tag>`. Bump tag and digest together.

# Build stage: plain JDK without Maven; ./mvnw downloads the Maven version pinned by the wrapper, the same one used
# locally and in CI. Tests do not run here; they run in the CI quality gate.
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87 AS builder

WORKDIR /build

# Dependencies change less often than the sources, so they get their own cached layer.
COPY mvnw pom.xml ./
COPY .mvn ./.mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q package -DskipTests \
    && cp target/workshop-management-system-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime stage: JRE only.
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c

# Fixed numeric non-root UID/GID so Kubernetes can enforce runAsNonRoot. 10001 avoids the `ubuntu` user (UID 1000)
# that already exists in the Noble base image.
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app

WORKDIR /app

# Spring Boot layers, least to most frequently changed. Files stay owned by root and read-only for the app user.
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# Used by the docker-compose healthcheck; Kubernetes uses its own probes instead of a HEALTHCHECK instruction.
COPY --chmod=0755 docker/healthcheck.sh /app/healthcheck.sh

# Container-oriented default: size the heap from the container memory limit. Overridable without rebuilding.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

USER 10001:10001

# 8080: business API. 8081: management (health probes only), never published by the Service/LoadBalancer.
EXPOSE 8080 8081

# `exec` replaces the shell, so the JVM is PID 1 and receives SIGTERM directly; `sh -c` expands $JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
