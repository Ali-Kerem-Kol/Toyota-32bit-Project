# Toyota 32bit Project

Bu proje, farklı veri sağlayıcılardan (TCP & REST) döviz kuru verilerini gerçek zamanlı olarak toplayıp, filtreleyip, hesaplayan ve bu verileri dış sistemlere (Kafka, PostgreSQL, Elasticsearch) aktaran mikroservis tabanlı bir backend uygulamasıdır. 
Uygulama, Redis Stream altyapısı ile veri akışını kontrol eder, kur hesaplamalarını dinamik olarak JavaScript ile yapar ve dağıtık sistemler arasında senkronizasyonu sağlar.


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
- `RateCache`: Platform + rate bazlı geçmişi saklar
- `FilterService`: JumpThresholdFilter, MovingAverageFilter ile veri kalitesini kontrol eder
- `RedisProducer/Consumer`: Stream yazımı/okuması
- `RateCalculatorService`: Dinamik `Formula.js` dosyasını çalıştırır
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

2. **Gerekli Konfigürasyon Dosyalarını Kontrol Edin:**

- `Main/coordinator/config/config.json` – `Coordinator` uygulamasının ana config dosyası
- `Main/coordinator/config/rest-config.json` - `RESTProvider` sınıfının config dosyası
- `Main/coordinator/config/tcp-config.json` - `TCPProvider` sınıfının config dosyası
- `Servers/RESTServer/config/config.json` - `RESTServer` uygulamasının config dosyası
- `Servers/TCPServer/config/config.json` - `TCPServer` uygulamasının config dosyası
- `Consumers/consumer-postgresql/src/main/resources/application.properties` - `consumer-postgresql` uygulamasının config dosyası
- `Consumers/consumer-elasticsearch/src/main/resources/application.properties` - `consumer-elasticsearch` uygulamasının config dosyası

3. **Docker ile çalıştırın:**

```bash
docker-compose up --build
```


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


---


## ✉️ İletişim
- Proje Sahibi : Ali Kerem Kol
- E-posta : ali_.kerem@hotmail.com
