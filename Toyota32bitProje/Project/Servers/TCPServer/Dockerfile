# Build
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app/Servers/TCPServer
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run
FROM openjdk:17-jdk-slim
WORKDIR /app/Servers/TCPServer
ENV TZ=Europe/Istanbul
COPY --from=builder /app/Servers/TCPServer/target/TCPServer-jar-with-dependencies.jar TCPServer-jar-with-dependencies.jar
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "TCPServer-jar-with-dependencies.jar"]