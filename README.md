# Finansal Veri Toplama & Hesaplama Projesi

## Açıklama  
Bu proje, birden fazla finansal veri sağlayıcıdan (TCP simülatörü, REST API) gerçek zamanlı kur verisi toplayıp  
1. Ham verileri Redis’e (`raw:` prefix’li)  
2. Türev hesaplamaları (USDTRY, EURTRY, GBPTRY) yapıp Redis’e (`calculated:` prefix’li)  
3. Hesaplanan verileri Kafka’ya yayınlayıp  
4. Spring‑Boot tabanlı bir consumer ile PostgreSQL’e kalıcı olarak yazmayı  
5. Filebeat→Elasticsearch→Kibana hattıyla logları izlemeyi sağlar.

---

## İçindekiler
- [Projeyi Çalıştırma](#projeyi-%C3%A7al%C4%B1%C5%9Ft%C4%B1rma)  
- [Bileşenler](#bile%C5%9Fenler)  
- [Konfigürasyon Dosyaları](#konfig%C3%BCrasyon-dosyalar%C4%B1)  
- [Kullanım Örnekleri](#kullan%C4%B1m-%C3%B6rnekleri)  
- [İletişim](#ileti%C5%9Fim)

---

## Projeyi Çalıştırma

### Gereksinimler
- Java 17 (OpenJDK 17+)  
- Maven 3.6+  
- Docker & Docker Compose  
- PostgreSQL (locale veya Docker)  
- Redis (locale veya Docker)  

### Yerelde Maven ile
Her modülde:
```bash
cd <modül-dizini>
mvn clean package
java -jar target/*.jar
- TCPServer: port 5000

- RESTServer: port 8081

- Coordinator: Redis → Kafka

- Consumer‑PostgreSQL: Kafka → PostgreSQL
```

### Docker Compose ile Tam Entegrasyon
```bash
docker-compose up --build
```
- Zookeeper, Kafka, PostgreSQL, Redis, TCPServer, RESTServer, Coordinator, Consumer, Elasticsearch, Kibana, Filebeat hepsi bir arada.

## Bileşenler

### TCP Simülatör
- Telnet publish/subscribe

- subscribe|RATE_NAME, unsubscribe|RATE_NAME

- ConfigReader ile initialRates, publishFrequency, publishCount

### REST API Simülatör
- Spring Boot, /api/rates/{rateName}

- Authorization: Bearer <apiKey>

### Coordinator (Ana Uygulama)
- Dinamik provider yükleme (reflection)

- Redis’e raw & calculated veriler

- RateCalculatorService + DynamicFormulaService (JS engine)

- KafkaProducerService ile Kafka’ya yayın

### Kafka Producer
- Asenkron edilir, eksikse yeniden init

- sendCalculatedRatesToKafka(...)

### Redis Service
- Jedis, auto‑reconnect monitor

- putRawRate / getRawRate

- putCalculatedRate / getCalculatedRate

### Rate Calculator
- calculateUsdTry, calculateEurTry, calculateGbpTry

- Formüller JS dosyasında

### Kafka Consumer (PostgreSQL)
- Spring Kafka listener → RatesRepository ile tbl_rates tablosuna kayıt

### Filebeat → Elasticsearch → Kibana
- filebeat.yml ile Coordinator log’larını index’ler

- Kibana Discover ile canlı log

## Konfigürasyon Dosyaları
- Servers/TCPServer/src/.../config.json

- Servers/RESTServer/src/.../config.json

- Main/coordinator/src/.../config.json

- Consumers/consumer-postgresql/src/.../config.json

- filebeat.yml

- docker-compose.yml

## Kullanım Örnekleri
- Telnet ile abone olmak

```bash
telnet localhost 5000
subscribe|PF1_EURUSD
```
- REST API ile veri çekmek

```bash
curl -H "Authorization: Bearer 8f5d3c9a-94b0-49d4-87e9-12a5c13e6c7a" \
     http://localhost:8081/api/rates/PF2_USDTRY
```
- Kibana’da log izlemek

```bash
Tarayıcıda http://localhost:5601
```

-PostgreSQL’de sonuçları görmek

```bash
psql -U postgres -d exchange_rates -c "SELECT * FROM tbl_rates;"
```
## İletişim
- Proje Sahibi: Ali Kerem Kol

- E‑posta: ali_.kerem@hotmail.com

Teşekkürler!
