FROM gradle:8.10.2-jdk21 AS builder
WORKDIR /workspace

COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY common common
COPY auth-server auth-server

RUN chmod +x ./gradlew && ./gradlew :auth-server:test :auth-server:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /workspace/auth-server/build/libs/*SNAPSHOT.jar /app/app.jar

EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
