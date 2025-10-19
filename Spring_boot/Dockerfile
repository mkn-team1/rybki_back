# === Build stage ===
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon

# Устанавливаем сертификаты в build stage
RUN apk add --no-cache curl

# Скачивание и установка российского корневого сертификата НУЦ Минцифры
RUN curl -k "https://gu-st.ru/content/lending/russian_trusted_root_ca_pem.crt" -o /tmp/russian_trusted_root_ca.crt && \
    keytool -importcert -noprompt -trustcacerts \
    -alias russian_trusted_root_ca \
    -file /tmp/russian_trusted_root_ca.crt \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit && \
    rm /tmp/russian_trusted_root_ca.crt

# Скачивание sub CA сертификата
RUN curl -k "https://gu-st.ru/content/lending/russian_trusted_sub_ca_pem.crt" -o /tmp/russian_trusted_sub_ca.crt && \
    keytool -importcert -noprompt -trustcacerts \
    -alias russian_trusted_sub_ca \
    -file /tmp/russian_trusted_sub_ca.crt \
    -keystore $JAVA_HOME/lib/security/cacerts \
    -storepass changeit && \
    rm /tmp/russian_trusted_sub_ca.crt

# === Runtime stage ===
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Копируем cacerts с установленными сертификатами из build stage
COPY --from=build $JAVA_HOME/lib/security/cacerts $JAVA_HOME/lib/security/cacerts

# Копируем собранный JAR
COPY --from=build /app/build/libs/*.jar ./app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
