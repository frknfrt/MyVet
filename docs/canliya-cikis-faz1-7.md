# Canlıya çıkış listesi — Faz 1–7 sürümü (2026-10)

Bu sürüm yol haritasındaki Faz 1, 2, 3, 3b, 4, 5, 6, 7'yi ve eklenti sürüm kaydını canlıya taşır. Faz 8 (anlık bildirimler) bu sürümde yok.

**Canlıdaki mevcut durum (2026-10-20, dışarıdan yoklandı):** hata yanıtlarında `requestId` yok, `/health/ready` ve `/downloads/...` web uygulamasının sayfasını dönüyor. Yani sunucuda bu fazların hiçbiri yok. Hepsi birlikte çıkacak.

**Kesinti:** yalnız backend yeniden başlarken 30–60 sn (5. adım). Diğer adımlar kesintisiz.

**Davranış değişmeyecek olanlar (bilerek kapalı gelir):**
- Paket özellik kısıtlaması (Faz 2) ve aylık kotalar (Faz 4) yalnız **sayar**, engellemez (`FEATURES_ENFORCEMENT_ENABLED` varsayılanı `false`).
- Eklenti sürüm kontrolü kimseyi engellemez (`VETLY_EXTENSION_MIN_VERSION` varsayılanı `0.1.0`); kliniklerdeki 0.1.0 eklenti çalışmaya devam eder.

Komutları **tek tek** çalıştırın (PowerShell'de çok satır yapıştırmak komutları birleştirebiliyor).

---

## 1. Lokal hazırlık

- [ ] **Commit** — changelist'teki değişiklikleri IntelliJ'den commit edin (geri dönülecek net nokta). Push isteğe bağlı.
- [ ] **Testler** (son durum 2026-10-20: backend 866, frontend 17, eklenti 261 — hepsi yeşil). Docker Postgres açık olmalı:
  ```powershell
  cd <repo>\backend;   .\mvnw.cmd test
  cd <repo>\frontend;  npm test
  cd <repo>\extension; npm test
  ```
- [ ] **Elle kısa tur** (lokalde): giriş → hasta → muayene → AI SOAP taslağı → randevu → fatura → Ayarlar > İşlem Geçmişi'nde giriş kaydı görünüyor mu → Ayarlar > Entegrasyonlar'da TARBİL kartı.
- [ ] **Eklenti denemesi** (TARBİL'e girilebildiğinde) — sürümü bekletmek zorunda değil; en düşük sürüm 0.1.0'da kaldığı için eski eklenti etkilenmez.

## 2. Lokal derleme

`npm run dev` çalışıyorsa önce durdurun (`EPERM`).

- [ ] Backend jar:
  ```powershell
  cd <repo>\backend
  .\mvnw.cmd -DskipTests package
  ```
- [ ] Eklenti paketi — **frontend derlemesinden önce**:
  ```powershell
  cd <repo>\extension
  npm ci
  npm run package
  ```
  Kontrol: `frontend\public\downloads\` içinde `vetly-tarbil-eklenti.zip` ve `vetly-tarbil-eklenti.json` (`{"version":"0.2.0"}`) var.
- [ ] Frontend:
  ```powershell
  cd <repo>\frontend
  npm ci
  npm run build
  ```
  Kontrol: `dist\downloads\vetly-tarbil-eklenti.zip` var.
- [ ] Sunucuya yükle:
  ```powershell
  scp <repo>\backend\target\myvet-backend-0.1.0-SNAPSHOT.jar furkan@70.40.139.40:~/app.jar
  scp -r <repo>\frontend\dist furkan@70.40.139.40:~/dist
  ```

## 3. Sunucu — ön kontrol ve yedek

`ssh furkan@70.40.139.40`

- [ ] Veritabanının şu anki sürümünü not edin (sonda V82 olmalı):
  ```bash
  sudo -u postgres psql vetly -c "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"
  ```
- [ ] Disk yeri: `df -h ~` (yedek için birkaç yüz MB yeter).
- [ ] **Veritabanı yedeği (zorunlu — yeni migration'lar var):**
  ```bash
  sudo -u postgres pg_dump -Fc vetly > ~/vetly-$(date +%F-%H%M).dump
  ls -lh ~/vetly-*.dump
  ```
- [ ] Mevcut web uygulamasının ve Nginx ayarının yedeği:
  ```bash
  rm -rf ~/www-prev && cp -r /var/www/vetly ~/www-prev
  sudo cp /etc/nginx/sites-available/vetly ~/nginx-vetly.prev
  ```
- [ ] Gerekli ortam değişkenleri yerinde mi (yeni zorunlu değişken **yok**; bunlar Faz 4'ün doğru IP görmesi için):
  ```bash
  sudo grep -E "^(SERVER_ADDRESS|SERVER_FORWARD_HEADERS_STRATEGY|SPRING_PROFILES_ACTIVE)=" /etc/vetly/backend.env
  ```
  Beklenen: `SERVER_ADDRESS=127.0.0.1`, `SERVER_FORWARD_HEADERS_STRATEGY=native`, `SPRING_PROFILES_ACTIVE=prod`.
  `FEATURES_ENFORCEMENT_ENABLED` ve `VETLY_EXTENSION_MIN_VERSION` **eklemeyin** (varsayılanlar doğru).

## 4. Sunucu — Nginx

`sudo nano /etc/nginx/sites-available/vetly` — certbot'un yönettiği `listen 443` server bloğu içinde:

- [ ] **Yükleme sınırı** (Faz 1'den ertelenen): server bloğunda yoksa ekleyin. Uygulama 15 MB'a kadar dosya kabul ediyor; Nginx varsayılanı 1 MB olduğu için büyük görüntü/lab dosyaları Nginx'te 413 ile kesilir.
  ```nginx
  client_max_body_size 16m;
  ```
- [ ] **`/api` location'ı** içinde şu iki satır olmalı (Faz 4: gerçek istemci IP'si; Faz 5: AI isteği ~75 sn sürebilir):
  ```nginx
  proxy_set_header X-Forwarded-For $remote_addr;
  proxy_read_timeout 90s;
  ```
- [ ] **Sağlık adresleri** (Faz 3) — server bloğuna:
  ```nginx
  location = /health/ready    { proxy_pass http://127.0.0.1:8081/actuator/health/readiness; }
  location = /health/external { proxy_pass http://127.0.0.1:8081/actuator/health/external; }
  ```
  (Yeni backend açılana kadar bu iki adres 502 döner; normal.)
- [ ] Uygula:
  ```bash
  sudo nginx -t && sudo systemctl reload nginx
  grep -n "client_max_body_size\|X-Forwarded-For\|proxy_read_timeout\|health/" /etc/nginx/sites-enabled/vetly
  ```
  `nginx -t` hata verirse reload etmeyin; `sudo cp ~/nginx-vetly.prev /etc/nginx/sites-available/vetly` ile geri alın.

## 5. Sunucu — backend (30–60 sn kesinti)

- [ ] Jar'ı değiştir ve başlat:
  ```bash
  sudo cp /opt/vetly/app.jar /opt/vetly/app.jar.prev
  sudo mv ~/app.jar /opt/vetly/app.jar && sudo chown vetly:vetly /opt/vetly/app.jar
  sudo systemctl restart vetly-backend
  sudo journalctl -u vetly-backend -f
  ```
  Logda Flyway'in migration'ları uyguladığını (`Successfully applied N migrations ... v82`) ve `Started ...Application` satırını görünce `Ctrl+C`.
  Açılmazsa: logdaki hatayı kaydedin, **Geri alma** bölümüne gidin.
- [ ] Sunucu içinden kontrol:
  ```bash
  curl -s http://127.0.0.1:8081/actuator/health/readiness        # {"status":"UP"}
  curl -s http://127.0.0.1:8081/actuator/health/external         # UP (pasif kontrol: dış servislere son çağrılar başarısız olmadıkça UP)
  curl -s 127.0.0.1:8081/actuator/prometheus | grep -c vetly_    # 0'dan büyük
  curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/v3/api-docs   # 401 (API dokümanı canlıda kapalı)
  sudo -u postgres psql vetly -c "SELECT max(version::int) FROM flyway_schema_history WHERE version IS NOT NULL;"   # 82
  ```

## 6. Sunucu — web uygulaması (kesintisiz)

- [ ] Dosyaları değiştir (`chmod` atlanmamalı, yoksa JS/CSS 404):
  ```bash
  sudo rm -rf /var/www/vetly/* && sudo cp -r ~/dist/. /var/www/vetly/ && sudo chmod -R u=rwX,go=rX /var/www/vetly && rm -rf ~/dist
  ```

## 7. Dışarıdan kontrol (tarayıcı / lokal PowerShell)

- [ ] `https://uygulama.vetly.com.tr/health/ready` → `{"status":"UP"}` (HTML değil).
- [ ] `https://uygulama.vetly.com.tr/downloads/vetly-tarbil-eklenti.json` → `{"version":"0.2.0"}` (HTML değil).
- [ ] Hata yanıtında istek numarası: `curl.exe -s https://uygulama.vetly.com.tr/api/v1/patients` → gövdede `"requestId"` var.
- [ ] Tarayıcıda `Ctrl+F5`, giriş yap:
  - [ ] Ana sayfa özetleri (gelir, satış, yeni hasta, randevu) açılıyor.
  - [ ] Bir muayenede AI SOAP taslağı oluşuyor.
  - [ ] Ayarlar > İşlem Geçmişi'nde az önceki giriş görünüyor.
  - [ ] Ayarlar > Entegrasyonlar > TARBİL kartı: "Eklentiyi indir (0.2.0)" bağlantısı ve kurulum adımları görünüyor; bağlı cihazda "Sürüm bilinmiyor (0.2.0 öncesi)" yazıyor (eski eklenti — beklenen).
  - [ ] Admin paneli: Kiracılar → bir kiracı → "Bu ayki kullanım" kutusu ve "Klinik işlem geçmişi" açılıyor.
- [ ] Gerçek IP kontrolü (Faz 4): bir KVKK onayı kaydedildikten sonra
  ```bash
  sudo -u postgres psql vetly -c "SELECT ip_address, granted_at FROM consent_records ORDER BY granted_at DESC LIMIT 3;"
  ```
  `127.0.0.1` görünüyorsa 4. adımdaki `X-Forwarded-For` satırı ya da `SERVER_FORWARD_HEADERS_STRATEGY=native` eksik.
- [ ] Mevcut TARBİL eklentisi (0.1.0) bekleyen listeyi açabiliyor.

## 8. İzleme kurulumu (bir kez)

- [ ] UptimeRobot'ta iki HTTP(s) monitörü, 5 dk aralık, e-posta bildirimi:
  `https://uygulama.vetly.com.tr/health/ready` ve `https://uygulama.vetly.com.tr/health/external`.

---

## Geri alma

**Web uygulaması bozuksa** (kesintisiz):
```bash
sudo rm -rf /var/www/vetly/* && sudo cp -r ~/www-prev/. /var/www/vetly/ && sudo chmod -R u=rwX,go=rX /var/www/vetly
```

**Backend açılmıyor / ciddi hata:** yeni migration'lar çalıştığı için eski jar tek başına açılmayabilir (`ddl-auto: validate`). Veritabanını da yedekten dönün:
```bash
sudo systemctl stop vetly-backend
sudo -u postgres dropdb vetly
sudo -u postgres createdb -O vetly vetly
sudo -u postgres pg_restore -d vetly ~/vetly-<tarih>.dump
sudo cp /opt/vetly/app.jar.prev /opt/vetly/app.jar
sudo systemctl start vetly-backend
```
Yedek ile geri dönüş arasındaki sürede girilen kayıtlar kaybolur — bu yüzden geri alma kararını açılıştan sonraki ilk dakikalarda verin. Web uygulamasını da `~/www-prev`'den geri alın (eski backend'le yeni frontend uyumsuz olabilir).

**Nginx:** `sudo cp ~/nginx-vetly.prev /etc/nginx/sites-available/vetly && sudo nginx -t && sudo systemctl reload nginx`.

---

## Sonra (acele yok, ayrı ayrı)

1. **Eklentiyi kliniklere duyurun:** Ayarlar > Entegrasyonlar'dan 0.2.0'ı indirip güncellesinler. Kimin güncellediği: `docs/deployment.md` > Faz 7 > "Kimler güncelledi?" sorgusu. `VETLY_EXTENSION_MIN_VERSION`'ı ancak herkes güncelledikten sonra yükseltin.
2. **Paket kısıtlaması ve kotalar (Faz 2 + 4):** birkaç hafta "Bu ayki kullanım"ı izleyin → Admin > Paket Yönetimi'nde her paketin "Modül Erişimi" kutularını ve aylık AI/mesaj haklarını doldurun → `FEATURES_ENFORCEMENT_ENABLED=true` + `sudo systemctl restart vetly-backend`. Bu ayar Modül Erişimi işaretlerini de açar; işaretler boşsa klinikler modüllere erişemez.
3. **İsteğe bağlı JSON log (Faz 3):** `LOG_FORMAT=ecs` (önce JSON `message` alanında 11 haneli sayıların maskelendiğini doğrulayın).
4. 1–2 gün sonra yedek dosyalarını (`~/vetly-*.dump`, `~/www-prev`, `/opt/vetly/app.jar.prev`) temizleyin.
