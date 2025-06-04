# 1 Temel imaj
FROM openjdk:17-jdk-slim

# 2 Uygulama dizini
WORKDIR /app/Main/coordinator

# 3 Timezone ayarı
ENV TZ=Europe/Istanbul

# 4 Jar dosyasını kopyala
COPY target/Coordinator-jar-with-dependencies.jar Coordinator-jar-with-dependencies.jar

# 5 Entry point
ENTRYPOINT ["java", "-jar", "Coordinator-jar-with-dependencies.jar"]