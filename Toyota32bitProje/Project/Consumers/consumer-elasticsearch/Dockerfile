# Build
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app/Consumers/consumer-elasticsearch
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run
FROM openjdk:17-jdk-slim
WORKDIR /app/Consumers/consumer-elasticsearch
ENV TZ=Europe/Istanbul
COPY --from=builder /app/Consumers/consumer-elasticsearch/target/consumer-elasticsearch.jar consumer-elasticsearch.jar
ENTRYPOINT ["java", "-jar", "consumer-elasticsearch.jar"]