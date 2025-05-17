########################
# 1) Build stage
########################
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# Maven dependency cache-layer’i için önce pom.xml
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Kaynak kodu kopyala ve jar üret
COPY src ./src
RUN mvn -q clean package -DskipTests

########################
# 2) Runtime stage
########################
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="your-name <you@example.com>"

# Saat dilimi ve küçük tweak’ler
ENV TZ=Europe/Istanbul \
    JAVA_OPTS=""

# Uygulama dizini
WORKDIR /app

# Fat-jar’ı kopyala
COPY --from=build /build/target/consumer-elasticsearch-*.jar app.jar

# Log dosyası dizini (host’a mount edeceğiz)
RUN addgroup -S spring && adduser -S spring -G spring \
 && mkdir /app/logs \
 && chown spring:spring /app/logs

USER spring
# EXPOSE 8084          # opsyionel Prometheus/actuator portu

# Graceful shutdown ve opsiyonel JVM parametreleri
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
