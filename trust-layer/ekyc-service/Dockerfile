FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY common common
COPY ekyc-service ekyc-service

RUN chmod +x ./gradlew && ./gradlew :ekyc-service:test :ekyc-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/ekyc-service/build/libs/*SNAPSHOT.jar /app/app.jar

EXPOSE 9002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
