# 1 Temel imaj
FROM openjdk:17-jdk-slim

# 2 Uygulama dizini
WORKDIR /app/Consumers/consumer-postgresql

# 3 Timezone ayarı
ENV TZ=Europe/Istanbul

# 4 Jar dosyasını kopyala
COPY target/consumer-postgresql.jar consumer-postgresql.jar

# 5 Entry point
ENTRYPOINT ["java", "-jar", "consumer-postgresql.jar"]