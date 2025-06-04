# 1 Temel imaj
FROM openjdk:17-jdk-slim

# 2 Uygulama dizini
WORKDIR /app/Servers/TCPServer

# 3 Timezone ayarı
ENV TZ=Europe/Istanbul

# 4 Jar dosyasını kopyala
COPY target/TCPServer-jar-with-dependencies.jar TCPServer-jar-with-dependencies.jar

# 5 Portu dışa aç
EXPOSE 5000

# 6 Entry point
ENTRYPOINT ["java", "-jar", "TCPServer-jar-with-dependencies.jar"]