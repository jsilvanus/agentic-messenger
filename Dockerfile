# Multi-stage Dockerfile for Agentic Messenger Blind Relay (amp-server)
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Install bash for gradlew execution
RUN apk add --no-cache bash

# Copy Gradle wrapper and configuration
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY amp-core/build.gradle.kts amp-core/
COPY amp-server/build.gradle.kts amp-server/

# Fix line endings and permissions on wrapper, remove Windows-specific java home
RUN tr -d '\r' < gradlew > gradlew_unix && mv gradlew_unix gradlew && chmod +x gradlew && \
    sed -i '/org.gradle.java.home/d' gradle.properties

# Pre-fetch dependencies
RUN ./gradlew :amp-server:dependencies --no-daemon || true

# Copy sources
COPY amp-core/ amp-core/
COPY amp-server/ amp-server/

# Build production distribution
RUN ./gradlew :amp-server:installDist --no-daemon

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl bash

COPY --from=builder /workspace/amp-server/build/install/amp-server /app/

RUN mkdir -p /app/data && chmod +x /app/bin/amp-server

EXPOSE 8080

ENTRYPOINT ["/app/bin/amp-server"]
