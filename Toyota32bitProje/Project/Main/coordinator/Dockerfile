# Temel imaj olarak OpenJDK 17 kullanıyoruz.
FROM openjdk:17-jdk-slim

# Çalışma dizinini /app olarak ayarla.
WORKDIR /app

# Maven build sonucu oluşan jar dosyasını imajın içine kopyala.
COPY target/app-jar-with-dependencies.jar app.jar

# Gerekli konfigürasyon dosyalarını da imajın içine kopyalayın.
#COPY src/main/resources/config.json src/main/resources/config.json
#COPY src/main/resources/log4j2.xml src/main/resources/log4j2.xml

# Scripts klasörünü kopyalayın
COPY scripts /app/scripts

# ---> Önemli: "coordinator/logs" klasörünü oluştur (henüz boşsa).
RUN mkdir -p /Main/coordinator/logs

# Container başlatıldığında jar dosyasını çalıştır.
ENTRYPOINT ["java", "-jar", "app.jar"]
