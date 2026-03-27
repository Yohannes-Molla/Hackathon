FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY common common
COPY vci-service vci-service

RUN chmod +x ./gradlew && ./gradlew :vci-service:test :vci-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/vci-service/build/libs/*SNAPSHOT.jar /app/app.jar

EXPOSE 9004
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
