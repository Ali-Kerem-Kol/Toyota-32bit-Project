# Build
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app/Consumers/consumer-postgresql
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run
FROM openjdk:17-jdk-slim
WORKDIR /app/Consumers/consumer-postgresql
ENV TZ=Europe/Istanbul
COPY --from=builder /app/Consumers/consumer-postgresql/target/consumer-postgresql.jar consumer-postgresql.jar
ENTRYPOINT ["java", "-jar", "consumer-postgresql.jar"]