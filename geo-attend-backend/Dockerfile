# ==============================================================================
# GeoAttend Backend - Production Multi-Stage Dockerfile (Render / Cloud)
# Architecture: Eclipse Temurin 21 (JDK builder -> JRE runtime)
# Non-root user: appuser (UID: 10001)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build & Package Artifact
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy Maven wrapper and descriptor
COPY mvnw pom.xml ./
COPY .mvn .mvn/

RUN chmod +x mvnw

# Copy application source code (excluding files in .dockerignore)
COPY src ./src

# Build production executable JAR
# Note: Unit/integration tests are verified during CI/local runs.
# Integration tests (@SpringBootTest) require datasource secrets unavailable during Docker build.
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runner

WORKDIR /app

# Create unprivileged group and user for security hardening
RUN groupadd -g 10001 appgroup && \
    useradd -u 10001 -g appgroup -s /bin/sh -m appuser

# Copy executable jar from builder stage with proper ownership
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar /app/app.jar

# Render assigns dynamic port via PORT environment variable (defaults to 8080)
ENV PORT=8080
EXPOSE 8080

# Run container as non-root user
USER appuser

# Container-aware JVM options
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "/app/app.jar"]
