# Toyota 32bit Project

Bu proje; çoklu platformlardan (TCP & REST) döviz kuru verilerini toplayan, filtreleyen, dinamik JavaScript ile hesaplayan ve sonuçları Kafka üzerinden PostgreSQL ve Elasticsearch'e aktaran, gerçek zamanlı, mikroservis tabanlı bir backend çözümüdür.
Veri işleme, Redis üzerinde aktif/pasif veri yönetimiyle yapılır ve merkezi loglama & izleme için Log4j2 + Filebeat + Kibana kullanılır.


---

## 🛠️ Kullanılan Teknolojiler

- **Java**
- **Spring Boot**
- **Redis & Redis Stream**
- **Apache Kafka**
- **PostgreSQL**
- **ElasticSearch & Kibana**
- **Log4j2 + Filebeat**
- **Docker & Docker Compose**
- **Javascript**

---

## 🧱 Proje Mimarisi

![Proje Mimarisi](Toyota32bitProje/Mimari.png)
Mimari bileşenler:
- `TCPProvider`, `RESTProvider`: Farklı kaynaklardan veri toplar
- `RedisService`: Ara bellek yapısı (Redis) ile iletişime geçer
- `FilterService`: Gelen verileri anomaliye karşı denetler.
- `RateCalculatorService + DynamicFormulaService + Formula.js`: Hesaplama akışı
- `KafkaProducerService`: Hesaplanan verileri Kafka'ya gönderir
- `consumer-postgresql`, `consumer-elasticsearch`: Kafka'dan veri okuyup veritabanlarına yazar
- `Log4j2 + Filebeat`: JSON loglama ve merkezi izleme
---

## 🚀 Kurulum

1. **Repository'yi klonlayın:**

```bash
git clone https://github.com/Ali-Kerem-Kol/Toyota-32bit-Project.git
cd Toyota-32bit-Project
```

2. **Docker ile çalıştırın:**

```bash
docker-compose up --build
```

## ⚙️ Konfigürasyon Dosyaları Açıklamaları

| Dosya Yolu | Açıklama |
|------------|----------|
| `Main/coordinator/config/config.json` | Ana **Coordinator** yapılandırması: Redis & Kafka bağlantı bilgileri, aktif filtre listesi, `Formula.js` dosya yolu vb. |
| `Main/coordinator/config/rest-config.json` | **RESTProvider** parametreleri |
| `Main/coordinator/config/tcp-config.json` | **TCPProvider** parametreleri |
| `Main/coordinator/config/jumpThresholdFilter.json` | **JumpThresholdFilter** parametreleri |
| `Servers/RESTServer/config/config.json` | **RESTServer**’ simülasyon parametreleri |
| `Servers/TCPServer/config/config.json` | **TCPServer** simülasyon parametreleri |
| `Consumers/consumer-postgresql/src/main/resources/application.properties` | **consumer-postgresql** Spring Boot ayar dosyası – PostgreSQL, Kafka bilgisi. |
| `Consumers/consumer-elasticsearch/src/main/resources/application.properties` | **consumer-elasticsearch** Spring Boot ayar dosyası – Elasticsearch, Kafka bilgisi. |
| `filebeat.yml` | Filebeat giriş/çıkış ayarları: Log yolları ve Elasticsearch hedefi. |





---

## 📂 Klasör Yapısı

```
Toyota32bitProje/
│
├── Project/
│   ├── Consumers/
│   │   └── consumer-elasticsearch
│   │   └── consumer-postgresql
│   ├── Main/
│   │   └── coordinator
│   ├── Servers/
│   │   └── RESTServer
│   │   └── TCPServer
│   ├── docker-compose.yml
│   └── filebeat.yml
│
├── Proje Teknik Dokümanı V0.1.docx
├── Mimari.png
```

## 💡 Kısa Akış Özeti

1. **Veri Sağlayıcılar (TCPProvider & RESTProvider)**
   - Farklı platformlardan gerçek zamanlı döviz kuru verileri toplar.
   - Veriler filtrelenir ve Redis’e (raw_rates) gönderilir.

2. **Coordinator (Ana Uygulama)**
   - Redis'ten en güncel aktif raw rates'leri çeker.
   - Hesaplamalar yapılır ve kullanılan veriler tekrar kullanılmamak üzere pasiflenir.
   - Hesaplanan veriler Redis’te calculated_rates olarak saklanır.
   - Redis'ten en güncel aktif calculated rates'leri çeker.
   - Sonuçlar Kafka’ya gönderilir ve kullanılan veriler tekrar kullanılmamak üzere pasiflenir.

3. **Kafka**
   - Hesaplanan verileri ilgili tüketici servislere dağıtır.

4. **Consumer-PostgreSQL & Consumer-Elasticsearch**
   - Kafka’dan gelen verileri kendi veritabanına (PostgreSQL, Elasticsearch) yazar.

5. **Loglama & İzleme**
   - Tüm servisler Log4j2 ile JSON formatında log tutar.
   - Filebeat, logları merkezi olarak Elastic/Kibana’ya yönlendirir.


---


## ✉️ İletişim
- Proje Sahibi : Ali Kerem Kol
- E-posta : ali_.kerem@hotmail.com
