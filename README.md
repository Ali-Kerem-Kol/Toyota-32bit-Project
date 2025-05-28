# Toyota 32bit Project

Bu proje, finansal veri sağlayıcılardan (REST ve TCP protokolüyle) alınan döviz kuru verilerinin gerçek zamanlı olarak toplanmasını, hesaplanmasını ve Kafka üzerinden dış sistemlere iletilmesini amaçlayan bir mikroservis mimarisi üzerine kuruludur. Uygulama, gelen verileri Redis Stream altyapısıyla yönetir ve kur hesaplamalarını JavaScript ile dinamik olarak gerçekleştiren bir hesaplayıcı modüle sahiptir. Hesaplanan sonuçlar hem Kafka'ya gönderilir hem de ikinci bir Redis Stream üzerinde saklanır.

Sistem, platformlar arası esneklik sağlayarak veri akışındaki kopmaları tolere edebilir ve merkezi koordinatör yapısı sayesinde dağıtık bileşenleri senkronize eder.

---

## 🛠️ Kullanılan Teknolojiler

- **Java**
- **Spring Boot**
- **Redis & Redis Stream**
- **Apache Kafka**
- **PostgreSQL**
- **ElasticSearch**
- **Docker & Docker Compose**
- **Log4j2 + Filebeat + Kibana**

---

## 🧱 Proje Mimarisi

![Proje Mimarisi](Toyota32bitProje/Mimari.png)

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

3. **Gerekli Konfigürasyon Dosyalarını Kontrol Edin:**

- "config.json" – Hangi kurların hangi platformlardan alınacağı burada belirtilir.
- "log4j2.xml" – Log yapısı bu dosya üzerinden ayarlanır.
- "filebeat.yml" – Log'ların Elasticsearch'e aktarımı için yapılandırma.



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
