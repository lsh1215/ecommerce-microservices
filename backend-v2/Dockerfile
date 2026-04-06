# ── Stage 1: dependency cache ─────────────────────────────────────────────────
# Copy only build scripts; source changes do NOT invalidate this layer.
FROM eclipse-temurin:21-jdk-alpine AS deps

ARG SERVICE_NAME

WORKDIR /app

COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .

# Copy only build.gradle files from each module (no source)
COPY common/build.gradle common/build.gradle
COPY ${SERVICE_NAME}/build.gradle ${SERVICE_NAME}/build.gradle

# Resolve and cache all dependencies. Layer is invalidated only when a
# build.gradle changes, not when source files change.
RUN chmod +x gradlew && \
    ./gradlew :${SERVICE_NAME}:dependencies --no-daemon

# ── Stage 2: compile & package ────────────────────────────────────────────────
FROM deps AS builder

# Copy source code after deps are cached
COPY common/src common/src
COPY ${SERVICE_NAME}/src ${SERVICE_NAME}/src

# Deps already downloaded; this stage only compiles
RUN ./gradlew :${SERVICE_NAME}:bootJar -x test --no-daemon

# ── Stage 3: extract layered jar ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS extractor

ARG SERVICE_NAME

WORKDIR /app

COPY --from=builder /app/${SERVICE_NAME}/build/libs/*.jar app.jar

RUN java -Djarmode=layertools -jar app.jar extract

# ── Stage 4: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

# Copy layers in stability order: rarely-changing first, frequently-changing last.
# Docker caches each COPY as a separate layer.
COPY --from=extractor /app/spring-boot-loader/ ./
COPY --from=extractor /app/dependencies/ ./
COPY --from=extractor /app/snapshot-dependencies/ ./
COPY --from=extractor /app/application/ ./

RUN chown -R app:app /app

USER app

EXPOSE 8080

# JarLauncher is the correct entrypoint for exploded layered jars
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
