# Build
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app/Main/coordinator
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run
FROM openjdk:17-jdk-slim
WORKDIR /app/Main/coordinator
ENV TZ=Europe/Istanbul
COPY --from=builder /app/Main/coordinator/target/Coordinator-jar-with-dependencies.jar Coordinator-jar-with-dependencies.jar
ENTRYPOINT ["java", "-jar", "Coordinator-jar-with-dependencies.jar"]