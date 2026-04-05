FROM eclipse-temurin:21-jdk-alpine AS builder

ARG SERVICE_NAME

WORKDIR /app

COPY gradle/ gradle/
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .

COPY common/ common/
COPY ${SERVICE_NAME}/ ${SERVICE_NAME}/

RUN chmod +x gradlew && \
    ./gradlew :${SERVICE_NAME}:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_NAME

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder /app/${SERVICE_NAME}/build/libs/*.jar app.jar

RUN chown -R app:app /app

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
