# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build
# ─────────────────────────────────────────────────────────────────────────────
# Use a full JDK image to compile and package the application.
# We pin the minor version in CI (not here) to balance reproducibility with
# automatic patch-level security updates from the base image.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy only the POM first. Docker caches this layer — if only source files
# change, the dependency download layer is reused on the next build.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source and build. -DskipTests because integration tests require
# running containers; they are executed in CI with docker-compose, not here.
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime
# ─────────────────────────────────────────────────────────────────────────────
# JRE-only image: ~100 MB smaller than the JDK image.
# Alpine keeps the attack surface minimal and the image pull time short.
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL org.opencontainers.image.title="nyxn-ecommerce-api" \
      org.opencontainers.image.description="NYXN e-commerce REST API — Java 21 / Spring Boot 3" \
      org.opencontainers.image.source="https://github.com/carlostajandev/nyxn-ecommerce-api"

# Run as a non-root user: if the JVM process is compromised, the attacker has
# no write access outside /app and cannot escalate to root via file-system tricks.
RUN addgroup -S nyxn && adduser -S nyxn -G nyxn
WORKDIR /app
RUN chown nyxn:nyxn /app

# Copy the fat JAR from the build stage — nothing else is needed at runtime.
COPY --from=builder /app/target/*.jar app.jar
RUN chown nyxn:nyxn app.jar

USER nyxn

EXPOSE 8080

# HEALTHCHECK allows the Docker daemon (and orchestrators like ECS) to mark the
# container as unhealthy and restart it without operator intervention.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# -XX:+UseContainerSupport: JVM reads cgroup limits for heap sizing rather than
#   the host's total RAM — critical in container environments with memory limits.
# -XX:MaxRAMPercentage=75: leaves 25 % headroom for off-heap (metaspace, NIO
#   buffers, GC overhead) so the container doesn't get OOM-killed.
# -Djava.security.egd=file:/dev/./urandom: speeds up SecureRandom initialisation
#   in containers where /dev/random blocks waiting for entropy.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
