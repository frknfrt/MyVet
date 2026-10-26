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

## Her çıkıştan önce: canlı ile depo uyumlu mu?

Canlıya birden fazla geliştirici çıkıyor. 2026-10-23'te canlıda depoda olmayan bir migration (`V83__admin_sent_emails`) vardı; depodan derlenen jar Flyway doğrulamasında açılmadı ve backend ~20 dk kapalı kaldı. Jar'ı değiştirmeden **önce**:

```bash
# Canlı veritabanının son migration'ları
sudo -u postgres psql vetly -c "SELECT version, description FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```
```powershell
# Depodaki son migration'lar
dir <repo>\backend\src\main\resources\db\migration | sort Name | select -Last 5
```
- Canlıdaki her sürüm numarası depoda **aynı adla** olmalı. Canlıda olup depoda olmayan bir migration varsa ya da aynı numara farklı bir açıklamayla duruyorsa **DURUN**. O kodu çıkaran geliştiriciden değişikliğini `main`'e göndermesini isteyin.
- Depoda canlıdan **büyük** numaralı migration'lar varsa, bunlar bu çıkışta uygulanacak olanlardır. Yedek zorunludur.
- Jar'ı değiştirmeden önce `git pull` yapın; başkasının sabah çıktığı kod sizde yoksa onu canlıdan silersiniz.

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
curl -s http://127.0.0.1:8081/actuator/health/readiness   # {"status":"UP"}
curl -s http://127.0.0.1:8081/actuator/health/external
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

**Eklenti zip'i git'te yok** (`frontend/public/downloads/` gitignore'da). Derlemeden önce üretilmezse canlıdaki "Eklentiyi indir" bağlantısı kırılır (2026-10-23'te oldu):
```powershell
cd <repo>\extension
npm ci
npm run package          # frontend\public\downloads\ altına zip + json yazar
npm run build:dev        # yerel test için dist'i yeniden localhost'a çevirir
```
```powershell
cd <repo>\frontend
npm ci
npm run build
dir dist\downloads       # vetly-tarbil-eklenti.zip ve .json OLMALI; yoksa durun
scp -r dist furkan@70.40.139.40:~/dist
```

**2. Sunucu — dosyaları değiştir**
```bash
sudo rm -rf /var/www/vetly/* && sudo cp -r ~/dist/. /var/www/vetly/ && sudo chmod -R u=rwX,go=rX /var/www/vetly && rm -rf ~/dist
```
`chmod` atlanmamalı: Windows'tan `scp` ile gelen klasörler `700` izniyle oluşuyor, Nginx (`www-data`) `assets/` klasörünü okuyamıyor ve JS/CSS **404** dönüyor.

**3. Kontrol** — tarayıcıda `Ctrl+F5`. Nginx/backend yeniden başlatma gerekmez, kesinti olmaz. `https://uygulama.vetly.com.tr/downloads/vetly-tarbil-eklenti.json` sürüm numarasını (`{"version":"…"}`) dönmeli, HTML sayfası değil.

## Ortam değişkenleri (`/etc/vetly/backend.env`)

Düzenleme: `sudo nano /etc/vetly/backend.env`, ardından `sudo systemctl restart vetly-backend`.

- Satırlar **boşluksuz** başlamalı, değerler tırnaksız yazılmalı (şablondan kopyalarken girinti gelebiliyor — `^DB_PASSWORD=` gibi aramalar eşleşmez).
- Zorunlular (`prod` profilinde eksikse uygulama açılmaz): `SPRING_PROFILES_ACTIVE=prod`, `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD`, `FRONTEND_BASE_URL`, `VETLY_SITE_ORIGIN`.
- Nginx arkasında gerekli: `SERVER_ADDRESS=127.0.0.1`, `SERVER_FORWARD_HEADERS_STRATEGY=native` (aksi halde `RateLimitFilter` ve KVKK onay kaydı tüm istekleri `127.0.0.1`'den gelmiş görür).
- Dış servis geri dönüşleri: `IYZICO_CALLBACK_BASE_URL`, `EFATURA_FATURAENTEGRATOR_CALLBACK_BASE_URL` = `https://uygulama.vetly.com.tr`.
- İsteğe bağlı entegrasyonlar (`AI_*`, `ANTHROPIC_*`, `ILETI_MERKEZI_*`, `META_WHATSAPP_*`, `IYZICO_*`, `EFATURA_*`): boşsa ilgili adaptör simüle moda düşer. Tam liste ve açıklamalar: `backend/src/main/resources/application.yml`.
- `VETLY_EXTENSION_MIN_VERSION` (varsayılan `0.1.0`): bundan eski TARBİL eklentisi 426 alır. Yükseltmeden önce yeni sürüm Vetly'den indirilebilir olmalı (sıra: `docs/api-conventions.md` > Versiyonlama).
- `PLATFORM_ADMIN_*` sadece ilk açılışta (tablo boşken) hesap oluşturur; sonradan değiştirmek mevcut hesabı güncellemez.
- DB şifresini değiştirmek: `sudo -u postgres psql -c "\password vetly"` + `backend.env`'de `DB_PASSWORD`'ü güncelle.

## Güvenlik notları

- **iyzico:** `IYZICO_API_KEY`/`IYZICO_SECRET_KEY` boşken `/api/v1/public/payments/iyzico/callback?token=SIMULATED-<id>` herhangi bir faturayı ödenmiş işaretleyebiliyor. Bu yüzden Nginx bu yolu `403` ile kapatıyor. Anahtarlar tanımlanınca (`IYZICO_BASE_URL=https://api.iyzipay.com` ile birlikte) Nginx'teki `location /api/v1/public/payments/iyzico/` bloğu kaldırılmalı.
- Nginx `X-Forwarded-For`'u `$remote_addr` ile **üzerine yazar** — istemcinin gönderdiği sahte header backend'e ulaşmaz.

## TARBİL eklentisi

Kaynak: `extension/` (tasarım: `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`).

```powershell
cd <repo>\extension
npm ci
npm run package        # build + ..\frontend\public\downloads\vetly-tarbil-eklenti.zip ve .json
```
Ardından **Frontend güncellemesi** adımlarıyla web uygulamasını yayınlayın; zip `dist/downloads/` içinde sunucuya gider. Klinikler eklentiyi Vetly > Ayarlar > Entegrasyonlar > TARBİL kartından indirip "Paketlenmemiş öğe yükle" ile kurar; kart yüklü sürümü ve yeni sürüm olup olmadığını gösterir.

- Eklenti kimliği manifest'teki `key` alanından türetilir ve sabittir (`npm run extension-id`); frontend'de `VITE_TARBIL_EXTENSION_ID` aynı değerdir. `extension/key.pem` repoda DEĞİLDİR, güvenli yedekte saklanır (Web Store'a geçişte gerekir).
- Hekim eklentiyi Vetly > Ayarlar > Entegrasyonlar > "Eklentiyi bağla" ile bağlar; bağlantılar aynı ekrandan iptal edilir.
- **0.3.0 (açık/parça stok, spec `2026-10-24-acik-parca-stok-design.md`):**
  - Eklenti ilaç reçetesinde açık şişe varsa "Açık Stok"tan seçip ml yazar; aşıda açık kutu varsa "Parça Stok"tan seçer.
  - Stok gönderiminde kutu başına doz sayısını ve parça stoğun okunduğunu bildirir.
  - Backend V85 + V86 ile birlikte çıkmalı.
  - **En düşük eklenti sürümü 0.3.0** (koddaki varsayılan, `application.yml`). Eski eklenti (0.2.x) ml'li satırları dolduramaz ve onaydan düşürür; bu yüzden backend açılır açılmaz eski eklenti 426 alır ve yan panelde "güncelleyin" uyarısı gösterir.
  - Sıra: önce web (0.3.0 zip'i `downloads` içinde, indirilebilir olmalı), sonra backend. Klinikleri önceden haberdar edin: güncellemeyene kadar TARBİL aktarımı durur.
  - `/etc/vetly/backend.env` içinde `VETLY_EXTENSION_MIN_VERSION` daha düşük bir değerle tanımlıysa satırı silin ya da 0.3.0 yapın.

## İzleme (Faz 3)

Actuator uçları yönetim portunda (`127.0.0.1:8081`) çalışır, dışarıya kapalıdır; sadece iki health adresi Nginx ile açılır.

**1. Nginx** — `/etc/nginx/sites-available/vetly` dosyasında certbot'un yönettiği `listen 443` server bloğunun içine ekleyin:
```nginx
location = /health/ready    { proxy_pass http://127.0.0.1:8081/actuator/health/readiness; }
location = /health/external { proxy_pass http://127.0.0.1:8081/actuator/health/external; }
```
```bash
sudo nginx -t && sudo systemctl reload nginx
```
Yanıtta sadece `{"status":"UP"}` / `{"status":"DOWN"}` döner (HTTP 200 / 503), ayrıntı yoktur.

**2. UptimeRobot** — iki HTTP(s) monitörü: `https://uygulama.vetly.com.tr/health/ready` ve `https://uygulama.vetly.com.tr/health/external`; aralık 5 dk, bildirim e-posta. Metod GET veya HEAD (ikisi de çalışır; ücretsiz planda varsayılan HEAD'dir).

**3. Metrikleri okuma**
```bash
curl -s 127.0.0.1:8081/actuator/prometheus | grep vetly_
```

**4. Hangi entegrasyon DOWN?** (health ayrıntıyı gizler)
```bash
sudo journalctl -u vetly-backend | grep "Entegrasyon DOWN"
curl -s 127.0.0.1:8081/actuator/prometheus | grep 'vetly_integration_calls_total.*failure'
```
DOWN / düzeldi log satırları sadece health sorgulandığında (UptimeRobot kontrolü) yazılır.

**5. İsteğe bağlı: JSON log** — `/etc/vetly/backend.env` dosyasına `LOG_FORMAT=ecs` ekleyip `sudo systemctl restart vetly-backend`. Geçmeden önce JSON `message` alanında 11 haneli bir sayının maskelendiğini doğrulayın.

## Rate limit ve paket kotaları (Faz 4)

**Nginx kontrolü (bir kez):** `/api` location'ında şu satır olmalı — yoksa ekleyip `sudo nginx -t && sudo systemctl reload nginx`:

    proxy_set_header X-Forwarded-For $remote_addr;

Doğrulama:

1. Ayar kontrolü: `grep -n "X-Forwarded-For" /etc/nginx/sites-enabled/vetly` — satır `proxy_set_header X-Forwarded-For $remote_addr;` olmalı ve `/api` location'ının içinde olmalı.
2. Kayıtlı IP kontrolü: KVKK onay kayıtları istemci IP'sini saklar. Uygulama üzerinden bir onay kaydedildikten sonra `sudo -u postgres psql vetly -c "SELECT ip_address, granted_at FROM consent_records ORDER BY granted_at DESC LIMIT 3;"` gerçek istemci IP'lerini göstermeli. 127.0.0.1 görüyorsan ayar eksik — tüm kullanıcılar tek IP limitini paylaşır.

**Koruma sınırları** (herkese aynı, `/etc/vetly/backend.env` ile değiştirilebilir):

| Ayar | Varsayılan | Env |
|---|---|---|
| Kiracı başına genel tavan | 600 / dk | `VETLY_RATE_LIMIT_TENANT_GENERAL_PER_MINUTE` |
| Kiracı başına AI üretimi | 10 / dk | `VETLY_RATE_LIMIT_TENANT_AI_PER_MINUTE` |
| Kiracı başına kampanya alıcısı | 2000 / saat | `VETLY_RATE_LIMIT_CAMPAIGN_RECIPIENTS_PER_HOUR` |
| Tek kampanyada alıcı | 1000 | kodda sabit |

**Paket kotaları:** Admin paneli → Planlar → paketi düzenle → "Aylık AI hakkı", "Aylık mesaj hakkı (SMS + WhatsApp)". Boş = sınırsız. Deneme paketi: `VETLY_QUOTA_TRIAL_AI` (30), `VETLY_QUOTA_TRIAL_MESSAGE` (100).

**Önerilen açılış sırası:** `FEATURES_ENFORCEMENT_ENABLED=false` iken kotalar yalnızca sayılır. Birkaç hafta Kiracılar → ilgili kiracı (detay sayfası) → "Bu ayki kullanım" kutusundan gerçek kullanımı izle, paketlere kota gir, sonra `FEATURES_ENFORCEMENT_ENABLED=true` yap (bu ayar Modül Erişimi işaretlerini de açar — onların da dolu olduğundan emin ol).

## İşlem geçmişi / denetim kaydı (Faz 3b)

Klinik içindeki hassas işlemler (giriş ve başarısız giriş denemesi, personel ve davet değişiklikleri, fatura iptali ve ödeme, kasa kapatma, reçete yazma/iptal, muayene kesinleştirme, KVKK onayı geri çekme, TARBİL işaretleme/yok sayma/geri alma, TARBİL eklentisi eşleştirme/anahtar iptali, Vetly destek ekibinin hesaba girmesi) `audit_events` tablosuna yazılır.

- **Klinik yöneticisi:** Ayarlar → İşlem Geçmişi.
- **Platform admin:** Kiracılar → ilgili kiracı → "Klinik işlem geçmişi".
- Kayıtlar **değiştirilemez**: tablo üzerindeki tetikleyici UPDATE ve DELETE'i reddeder. Hasta/sahip adı, telefon, TCKN kayda yazılmaz.
- Destek ekibi "Bu Klinik Olarak Gir" ile bir hesaba girdiğinde bu, kliniğin İşlem Geçmişi'nde görünür ve o oturumdaki her işlem "Vetly destek ekibi aracılığıyla" diye işaretlenir.
- Kayıt bu sürümden itibaren başlar; eski işlemler için geçmiş yoktur. Saklama süresi şimdilik sınırsız.

## Dış servis koruması (Faz 5)

Claude (AI), iyzico ve FaturaEntegratör çağrıları devre kesiciyle korunur: son 10 çağrının yarısı kesinti (bağlantı hatası, zaman aşımı, 5xx, 401/402/403/429) ya da çok yavaşsa devre **açılır** ve 30 saniye boyunca o servis hiç çağrılmaz.

- **AI:** devre açıkken hekim hemen "[AI modeli henuz baglanmadi -- bu, dikte edilen/yazilan metnin oldugu gibi Subjective alanina aktarilmasidir. ...]" notlu taslağı görür (kota hakkı iade edilir). Aynı anda en fazla 4 AI çağrısı yapılır; 5.'si en fazla 10 sn bekler, yer açılmazsa "AI şu an yoğun" mesajı (503) döner. Cevap bekleme sınırı 60 sn.
- **iyzico:** devre açıkken ödeme başlatma hemen "Ödeme sistemine şu an ulaşılamıyor" (503) döner. Ödeme sonucu sorgusu (kullanıcı iyzico'dan dönerken) devre kesicisiz, kesintide 3 kez denenir.
- **FaturaEntegratör:** devre açıkken gönderim yapılmaz, kuyruk normal takvimle (2 dk / 10 dk / 1 sa / 6 sa) tekrar dener. Durum sorgusu kesintide 3 kez denenir; sağlayıcı kesintisinde (devre açıkken de, 3 deneme tükenince de) fatura "işleniyor" durumunda kalır, "başarısız" işaretlenmez (aksi halde "Tekrar Dene" çift fatura kesebilirdi).
- Fatura kesme ve ödeme başlatma gibi **yazma** çağrıları anında/eşzamanlı olarak asla tekrar denenmez (çift kayıt riski); e-fatura kuyruğu ise başarısız gönderimi normal takvimiyle (2 dk / 10 dk / 1 sa / 6 sa) sonradan yeniden gönderir, bu bir çelişki değildir.
- **Dağıtım notu (nginx):** Bir AI isteği en fazla ~75 sn sürebilir (10 sn kuyruk + 60 sn cevap). nginx `/api` için `proxy_read_timeout` en az 90 sn olmalı (`sudo grep -n proxy_read_timeout /etc/nginx/sites-enabled/vetly`; nginx varsayılanı 60 sn). Aksi halde kullanıcı zaman aşımı hatası görür, ama arka uç işi bitirir ve kota hakkını harcar.
  - 2026-10-25: Tanı/tedavi önerisinde cevap 2048 parça sınırına takılırsa Claude'dan bir kez devam istenir (toplam 4096). Devam yalnızca ilk parça 40 sn'den kısa sürdüyse istenir; böylece toplam süre 90 sn içinde kalır (`ClaudeTextCompletion`). 90 sn değeri düşürülmemeli.

**Log:** devre açılınca `Devre kesici ACIK: <ad>` (WARN), kapanınca `Devre kesici kapandi: <ad>` (INFO):

    sudo journalctl -u vetly-backend --since "1 hour ago" | grep "Devre kesici"

**Metrik (8081):** `curl -s 127.0.0.1:8081/actuator/prometheus | grep resilience4j_circuitbreaker_state` — `state="open"` satırı 1 ise o devre açık.

**Ayarlar:** `application.yml` → `resilience4j.*` (env ile ezilebilir, ör. `RESILIENCE4J_BULKHEAD_INSTANCES_AI_MAXCONCURRENTCALLS=6`; emin değilsen `application.yml`'i düzenleyip uygulamayı yeniden başlat).

## Önbellek (Faz 6)

Uygulama bazı sık okunan, az değişen bilgileri kendi belleğinde tutar (Redis yok, tek sunucu).

| Önbellek | Ne tutar | Ne zaman tazelenir |
|---|---|---|
| `tenantPlanCode` | Kliniğin paket kodu | Admin aboneliği değiştirince genellikle hemen (aynı anda süren bir istek nadiren eski değeri geri yazabilir); her durumda en geç 5 dk |
| `planByCode` | Paketin açık modülleri ve aylık kotaları | Admin paket oluşturur/günceller/silerse genellikle hemen (aynı anda süren bir istek nadiren eski değeri geri yazabilir); her durumda en geç 5 dk |
| `tarbilDiseases` | TARBİL hastalık listesi | Uygulama yeniden başlayınca (liste yalnızca yeni sürümle değişir) |

- Paket bilgisi **admin paneli dışından** (doğrudan veritabanında) değiştirilirse, yeni değerin kliniklere yansıması en fazla 5 dakika sürer. Hemen gerekiyorsa `sudo systemctl restart vetly-backend`.
- Önbellekte kişisel veri (hasta, sahip, personel, telefon, TCKN) tutulmaz.
- Metrikler (8081, Prometheus): `cache_gets_total{cache="tenantPlanCode",result="hit"|"miss"}`, `cache_size`, `cache_evictions_total`.
  Örnek: `curl -s localhost:8081/actuator/prometheus | grep '^cache_gets_total'`
- Ana sayfa özetleri (gelir, bugünkü satış, yeni hasta, randevu) önbelleğe alınmaz; her açılışta veritabanında toplanır. Ay ve gün sınırları İstanbul saatine göredir.

## Eklenti dağıtımı ve API dokümanı (Faz 7)

- **Sıra:** frontend derlenmeden önce `extension/` içinde `npm ci && npm run package`. Unutulursa sürüm dosyası (json) bulunamaz; Ayarlar'daki kart indirme bağlantısını gizler ve "Eklenti dosyası şu an hazır değil" der (kart bozulmaz).
- `frontend/public/downloads/` repoda değildir (derleme çıktısı). `key.pem` gerekmez: kimliği `manifest.json`'daki `key` alanı sabit tutar.
- **Zorunlu güncelleme:** `VETLY_EXTENSION_MIN_VERSION` yükseltilince eski eklenti 426 alır. 0.1.0 eklentiler 426'yı tanımadığı için yan panelde her listelemede "Beklenmeyen yanıt: 426" gösterir; TARBİL onayları eklentide kuyrukta bekler ve güncelleme sonrası gönderilir. Bu yüzden önce kart üzerinden güncellemeyi duyurun.
- **Kimler güncelledi?** Yükseltmeden önce sunucuda son 30 günde kullanılan eklentileri sürüme göre sayın (boş sürüm = 0.1.0):
  `sudo -u postgres psql vetly -c "SELECT COALESCE(last_client_version, '0.1.0 (sürüm göndermiyor)') AS surum, COUNT(*) AS eklenti, COUNT(DISTINCT tenant_id) AS klinik FROM tarbil_extension_token WHERE revoked_at IS NULL AND last_used_at > now() - interval '30 days' GROUP BY 1 ORDER BY 1;"`
  Yeni en düşük sürümden eski satır kalmadıysa (ya da kalanlar bilerek göze alındıysa) yükseltin. Sürüm her istekte yazılır; "son kullanım" ise en çok 5 dakikada bir güncellenir.
- **API dokümanı:** `/swagger-ui.html` yalnız geliştirmede; `application-prod.yml` (`springdoc.*.enabled: false`) canlıda hem üretimi hem kimliksiz erişimi kapatır. Nginx `/v3` ve `/swagger-ui` yollarını hiç yönlendirmez (yalnız `/api` ve `/health/*` backend'e gider); prod bayrakları ikinci kilittir. Kontrol (sunucuda, backend'e doğrudan): `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/v3/api-docs` → `401`.

## Bilinen açık işler

- [ ] Otomatik günlük veritabanı yedeği + sunucu dışına kopyalama
- [x] Uptime izleme (UptimeRobot vb.) -- bkz. "İzleme (Faz 3)" bölümü
- [ ] `META_WHATSAPP_TEMPLATE_NAME` (şablonsuz WhatsApp sadece 24 saatlik pencere içinde gider)
- [ ] WhatsApp numarasi hala Meta "Test Number"inda -- gercek musteri numaralarina gonderim #131030 ("Recipient phone number not in allowed list") ile reddediliyor. Cozum: WhatsApp Yoneticisi > Telefon numaralari'na WhatsApp'ta aktif OLMAYAN gercek bir numara eklemek (kullanicinin kendi numarasi WhatsApp'ta aktif oldugu icin su an kullanilamiyor, ayri bir SIM gerekiyor), SMS/arama ile dogrulamak, kalici bir System User token'i olusturmak, ve META_WHATSAPP_PHONE_NUMBER_ID / META_WHATSAPP_ACCESS_TOKEN'i guncelleyip servisi yeniden baslatmak.
- [ ] Tanıtım sitesi (`vetly.com.tr`) API adresini `https://myvet-21n4.onrender.com`'dan `https://uygulama.vetly.com.tr`'ye taşımak — ardından Render servisini kapatmak
- [ ] Bu adımları bir `deploy.ps1` betiğine çevirmek

## Eklenti 0.3.1 ve stok birimi düzeltmeleri (2026-10-25)

- Migration **V87** (inventory_items: piece_size, piece_label, opened_expiry_date; tarbil_stock_snapshot_line: opened_expiry_date). Çıkıştan önce canlıdaki migration listesini kontrol edin.
- Eklenti **0.3.1** zip'i: TARBİL Miktar'ına 0,01 altını yazmaz, açık ürün son kullanma tarihini okur. Sunucunun en düşük eklenti sürümü **0.3.0 kalır** (birim çevirisini sunucu yapar; 0.3.0 da doğru miktarı yazar).
- Mevcut ürünlerin birimi (tablet, pipet) bir sonraki TARBİL stok yüklemesinde kendiliğinden güncellenir; elle ayarlanmış birim ve bekleyen düşümü olan ürün atlanır.
- Sıra: önce web (0.3.1 zip indirilebilir), sonra backend.

