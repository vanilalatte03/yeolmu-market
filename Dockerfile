FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon bootJar
RUN set -e; \
    jar_file="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -print -quit)"; \
    cp "$jar_file" /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=builder --chown=app:app /workspace/app.jar /app/app.jar

USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
