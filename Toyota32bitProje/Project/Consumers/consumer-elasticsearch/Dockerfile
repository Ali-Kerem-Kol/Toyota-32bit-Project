# 1 Temel imaj
FROM openjdk:17-jdk-slim

# 2 Uygulama dizini
WORKDIR /app/Consumers/consumer-elasticsearch

# 3 Timezone ayarı
ENV TZ=Europe/Istanbul

# 4 Jar dosyasını kopyala
COPY target/consumer-elasticsearch.jar consumer-elasticsearch.jar

# 5 Entry point
ENTRYPOINT ["java", "-jar", "consumer-elasticsearch.jar"]