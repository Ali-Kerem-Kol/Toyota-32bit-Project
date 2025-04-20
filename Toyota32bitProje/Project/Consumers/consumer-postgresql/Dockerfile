# Temel imaj olarak OpenJDK 17 kullanıyoruz.
FROM openjdk:17-jdk-slim

# Çalışma dizinini /app olarak ayarla.
WORKDIR /app

# Maven build sonucu oluşan consumer jar dosyasını imajın içine kopyala.
COPY target/consumer-postgresql-0.0.1-SNAPSHOT.jar app.jar

# Eğer Consumer için ayrıca konfigürasyon dosyaları varsa, onları da kopyalayın.
# Örneğin:
COPY src/main/resources/config.json src/main/resources/config.json
COPY src/main/resources/log4j2.xml src/main/resources/log4j2.xml

# Consumer'ın dinleyeceği portu expose et (örneğin 8082).
EXPOSE 8082

# Container başlatıldığında jar dosyasını çalıştır.
ENTRYPOINT ["java", "-jar", "app.jar"]
