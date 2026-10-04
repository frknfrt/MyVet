# Deployment (Production)

Canlı ortam: **https://uygulama.vetly.com.tr** — Güzel Hosting VPS üzerinde, Docker'sız (sunucu OpenVZ, Docker güvenilir çalışmıyor).

## Sunucu yerleşimi

| Bileşen | Konum / ayar |
|---|---|
| Sunucu | `70.40.139.40` (Ubuntu 22.04, 1 CPU, 2 GB RAM + 2 GB swap) |
| SSH | `ssh furkan@70.40.139.40` — sadece anahtarla giriş, root girişi kapalı |
| Firewall | `ufw`: sadece 22, 80, 443 açık |
| Backend jar | `/opt/vetly/app.jar` (sahibi `vetly` sistem kullanıcısı) |
| Backend servisi | systemd `vetly-backend` — `127.0.0.1:8080`'i dinler, dışarıya kapalı |
| Ortam değişkenleri | `/etc/vetly/backend.env` (`chmod 600`, root) |
| Veritabanı | PostgreSQL 16, db/kullanıcı `vetly`, sadece `localhost` |
| Frontend | `/var/www/vetly` (statik `dist` içeriği) |
| Nginx | `/etc/nginx/sites-available/vetly` — `/` statik dosyalar, `/api/` → backend |
| SSL | Let's Encrypt (certbot, otomatik yenileme) |
| DNS | `uygulama` A kaydı → VPS IP'si (cPanel Zone Editor, `ns*.guzelhosting.com`) |

İkisi birlikte güncellenecekse **önce backend, sonra frontend** — yeni frontend yeni endpoint'lere bağlı olabilir.

> Komutları **tek tek** çalıştırın; birden fazla satırı aynı anda yapıştırmak PowerShell'de komutları birleştirebiliyor.

## Backend güncellemesi

**1. Lokal (PowerShell) — derle ve yükle**
```powershell
cd <repo>\backend
.\mvnw.cmd -DskipTests package          # sonunda BUILD SUCCESS
scp target\myvet-backend-0.1.0-SNAPSHOT.jar furkan@70.40.139.40:~/app.jar
```

**2. Sunucu — veritabanı yedeği** (yeni migration varsa zorunlu, yoksa da önerilir)
```bash
sudo -u postgres pg_dump -Fc vetly > ~/vetly-$(date +%F-%H%M).dump
```

**3. Sunucu — jar'ı değiştir ve yeniden başlat** (30–60 sn kesinti olur)
```bash
sudo cp /opt/vetly/app.jar /opt/vetly/app.jar.prev
sudo mv ~/app.jar /opt/vetly/app.jar && sudo chown vetly:vetly /opt/vetly/app.jar
sudo systemctl restart vetly-backend
```

**4. Sunucu — kontrol**
```bash
sudo journalctl -u vetly-backend -f          # "Started ...Application" görününce Ctrl+C
curl http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
```

**Geri alma**
```bash
sudo cp /opt/vetly/app.jar.prev /opt/vetly/app.jar
sudo systemctl restart vetly-backend
```
Yeni sürüm bir Flyway migration'ı çalıştırdıysa eski jar `ddl-auto: validate` yüzünden açılmayabilir — bu durumda veritabanı 2. adımdaki yedekten geri yüklenmelidir (`pg_restore`).

## Frontend güncellemesi

**1. Lokal (PowerShell) — derle ve yükle**

`npm run dev` çalışıyorsa önce durdurun; aksi halde `npm ci` `esbuild.exe` kilidi yüzünden `EPERM` verir.
```powershell
cd <repo>\frontend
npm ci
npm run build
scp -r dist furkan@70.40.139.40:~/dist
```

**2. Sunucu — dosyaları değiştir**
```bash
sudo rm -rf /var/www/vetly/* && sudo cp -r ~/dist/. /var/www/vetly/ && sudo chmod -R u=rwX,go=rX /var/www/vetly && rm -rf ~/dist
```
`chmod` atlanmamalı: Windows'tan `scp` ile gelen klasörler `700` izniyle oluşuyor, Nginx (`www-data`) `assets/` klasörünü okuyamıyor ve JS/CSS **404** dönüyor.

**3. Kontrol** — tarayıcıda `Ctrl+F5`. Nginx/backend yeniden başlatma gerekmez, kesinti olmaz.

## Ortam değişkenleri (`/etc/vetly/backend.env`)

Düzenleme: `sudo nano /etc/vetly/backend.env`, ardından `sudo systemctl restart vetly-backend`.

- Satırlar **boşluksuz** başlamalı, değerler tırnaksız yazılmalı (şablondan kopyalarken girinti gelebiliyor — `^DB_PASSWORD=` gibi aramalar eşleşmez).
- Zorunlular (`prod` profilinde eksikse uygulama açılmaz): `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD`, `FRONTEND_BASE_URL`, `VETLY_SITE_ORIGIN`.
- Nginx arkasında gerekli: `SERVER_ADDRESS=127.0.0.1`, `SERVER_FORWARD_HEADERS_STRATEGY=native` (aksi halde `RateLimitFilter` ve KVKK onay kaydı tüm istekleri `127.0.0.1`'den gelmiş görür).
- Dış servis geri dönüşleri: `IYZICO_CALLBACK_BASE_URL`, `EFATURA_FATURAENTEGRATOR_CALLBACK_BASE_URL` = `https://uygulama.vetly.com.tr`.
- İsteğe bağlı entegrasyonlar (`AI_*`, `ANTHROPIC_*`, `ILETI_MERKEZI_*`, `META_WHATSAPP_*`, `IYZICO_*`, `EFATURA_*`): boşsa ilgili adaptör simüle moda düşer. Tam liste ve açıklamalar: `backend/src/main/resources/application.yml`.
- `PLATFORM_ADMIN_*` sadece ilk açılışta (tablo boşken) hesap oluşturur; sonradan değiştirmek mevcut hesabı güncellemez.
- DB şifresini değiştirmek: `sudo -u postgres psql -c "\password vetly"` + `backend.env`'de `DB_PASSWORD`'ü güncelle.

## Güvenlik notları

- **iyzico:** `IYZICO_API_KEY`/`IYZICO_SECRET_KEY` boşken `/api/v1/public/payments/iyzico/callback?token=SIMULATED-<id>` herhangi bir faturayı ödenmiş işaretleyebiliyor. Bu yüzden Nginx bu yolu `403` ile kapatıyor. Anahtarlar tanımlanınca (`IYZICO_BASE_URL=https://api.iyzipay.com` ile birlikte) Nginx'teki `location /api/v1/public/payments/iyzico/` bloğu kaldırılmalı.
- Nginx `X-Forwarded-For`'u `$remote_addr` ile **üzerine yazar** — istemcinin gönderdiği sahte header backend'e ulaşmaz.

## Bilinen açık işler

- [ ] Otomatik günlük veritabanı yedeği + sunucu dışına kopyalama
- [ ] Uptime izleme (UptimeRobot vb.)
- [ ] `META_WHATSAPP_TEMPLATE_NAME` (şablonsuz WhatsApp sadece 24 saatlik pencere içinde gider)
- [ ] WhatsApp numarasi hala Meta "Test Number"inda -- gercek musteri numaralarina gonderim #131030 ("Recipient phone number not in allowed list") ile reddediliyor. Cozum: WhatsApp Yoneticisi > Telefon numaralari'na WhatsApp'ta aktif OLMAYAN gercek bir numara eklemek (kullanicinin kendi numarasi WhatsApp'ta aktif oldugu icin su an kullanilamiyor, ayri bir SIM gerekiyor), SMS/arama ile dogrulamak, kalici bir System User token'i olusturmak, ve META_WHATSAPP_PHONE_NUMBER_ID / META_WHATSAPP_ACCESS_TOKEN'i guncelleyip servisi yeniden baslatmak.
- [ ] Tanıtım sitesi (`vetly.com.tr`) API adresini `https://myvet-21n4.onrender.com`'dan `https://uygulama.vetly.com.tr`'ye taşımak — ardından Render servisini kapatmak
- [ ] Bu adımları bir `deploy.ps1` betiğine çevirmek
