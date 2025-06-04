# 1 Temel imaj
FROM openjdk:17-jdk-slim

# 2 Uygulama dizini
WORKDIR /app/Servers/RESTServer

# 3 Timezone ayarı
ENV TZ=Europe/Istanbul

# 4 Jar dosyasını kopyala
COPY target/RESTServer.jar RESTServer.jar

# 5 Portu dışa aç
EXPOSE 8081

# 6 Entry point
ENTRYPOINT ["java", "-jar", "RESTServer.jar"]