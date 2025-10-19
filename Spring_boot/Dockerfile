# === Build stage ===
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Копируем Gradle wrapper и скрипты
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .
COPY src ./src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# === Runtime stage ===
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app
COPY --from=build /app/build/libs/*.jar ./app.jar

# ENV переменные
ENV REDIS_HOST=${REDIS_HOST}
ENV REDIS_PORT=${REDIS_PORT}
ENV GIGACHAT_AUTH_KEY=${GIGACHAT_AUTH_KEY}
ENV STT_URL=${STT_URL}

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]
