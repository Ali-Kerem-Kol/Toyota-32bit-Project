FROM openjdk:17-jdk-slim
WORKDIR /app

# Rest server jar'ını kopyala
COPY target/rest-server.jar app.jar

# Konfigürasyon dosyasını kopyala
COPY src/main/resources/config.json src/main/resources/config.json

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
