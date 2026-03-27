FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY common common
COPY auth-server auth-server
COPY ekyc-service ekyc-service
COPY vci-service vci-service
COPY tx-service tx-service
COPY gateway gateway

RUN gradle :ekyc-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/ekyc-service/build/libs/*SNAPSHOT.jar /app/app.jar

EXPOSE 9002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
