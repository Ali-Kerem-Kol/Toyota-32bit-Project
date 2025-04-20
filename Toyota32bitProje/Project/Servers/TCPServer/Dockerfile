# Temel imaj
FROM openjdk:17-jdk-slim

# Çalışma dizinini ayarla
WORKDIR /app

# Maven build sonucu oluşan jar dosyasını kopyala
COPY target/TCPServer-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar

# Konfigürasyon dosyasını imajın içine kopyala (varsayılan olarak projedeki konumdan)
COPY src/main/java/resources/config.json src/main/java/resources/config.json

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "app.jar"]
