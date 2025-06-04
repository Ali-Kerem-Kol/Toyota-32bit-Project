# Build
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /app/Servers/RESTServer
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run
FROM openjdk:17-jdk-slim
WORKDIR /app/Servers/RESTServer
ENV TZ=Europe/Istanbul
COPY --from=builder /app/Servers/RESTServer/target/RESTServer.jar RESTServer.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "RESTServer.jar"]