FROM openjdk:17-jdk-slim
WORKDIR /app

COPY target/consumer-elasticsearch.jar app.jar
COPY src/main/resources/log4j2.xml log4j2.xml
COPY src/main/resources/config.json config.json

# Klasörü oluştur
RUN mkdir -p logs

ENTRYPOINT ["java", "-jar", "app.jar"]
