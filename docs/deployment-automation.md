# Yerel deployment paneli ve PowerShell pipeline

GitHub üzerinden çalıştırmak için [GitHub Actions deployment kılavuzuna](deployment-github-actions.md) bakın. Yerel panel alternatif olarak korunmuştur.

Bu araç mevcut Ubuntu VPS + PostgreSQL + systemd + Nginx yerleşimini kullanır. Docker, GitHub Actions veya uygulama içinde yönetim endpoint’i gerektirmez. Panel ayrı bir yerel geliştirici aracıdır; müşterilere açık uygulama paketine girmez.

## Başlatma

Gerekenler: Windows, PowerShell 7 (`pwsh`), Python 3.10+, Git/OpenSSH, Java 21 ve projenin kilitli bağımlılıklarını derleyebilen Node/npm. `python`, `pwsh`, `git`, `node`, `npm.cmd`, `ssh`, `scp` PATH üzerinde olmalıdır.

Proje kökünde:

```powershell
.\scripts\Start-DeployUI.ps1
```

Yalnız mevcut PowerShell oturumunun yürütme ilkesi engelliyorsa, güvendiğiniz bu yerel başlatıcıyı süreç bazında çalıştırın; sistem politikasını değiştirmeyin:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Start-DeployUI.ps1
```

Alternatif: `scripts/Start-DeployUI.cmd` dosyasına çift tıklayın. Başlatıcı, PATH'te yoksa standart PowerShell 7 kurulumunu ve mevcut yerel geliştirme runtime'ını da kontrol eder.

Panel `http://127.0.0.1:9177` adresinde açılır. Yardımcı süreç görünmez pencerede çalışır. Aynı proje için ikinci başlatma mevcut paneli açar. Başka proje/uygulama aynı portu kullanıyorsa yeniden kullanmaz; `-Port 9178` gibi ayrı bir port seçin. `-NoBrowser` yalnız tarayıcı açılmasını engeller.

Panel sadece loopback dinler. Oturum nonce’u, Host/Origin denetimi, CSRF başlığı, frame/CORS kısıtları vardır. Serbest PowerShell komutu, parola veya özel anahtar kabul eden bir alan yoktur. Tarayıcıyı kapatmak çalışan işlemi iptal etmez. Çalışırken yardımcı süreci sonlandırmayın. İşlem yokken “Paneli kapat” ile yardımcı süreç güvenli şekilde kapatılabilir. Loglar `.deploy/ui/history/` altında tutulur; port/PID gerekirse Windows Görev Yöneticisi veya PowerShell ile belirlenebilir.

## Panel akışı

1. **Yeni paket hazırla:** Varsayılan “Son commit” seçimi, mevcut dalın HEAD commit’ini doğrudan Git nesnelerinden ayrı bir klasöre çıkarır. IntelliJ changelist’leri, staged/unstaged düzenlemeler ve yeni dosyalar pakete girmez; yerel dosyalar ve Git index’i değişmez. Maven backend JAR, eklenti ZIP’i, TypeScript/Vite frontend derlemesi üretir. Aktif geliştirme sunucusunun `node_modules` klasörüne veya kaynak dosyalarına yazmaz. Production API adresi açıkça `https://uygulama.vetly.com.tr` olarak ayarlanır.
2. **Hazır paket:** Release ID, commit, kaynak seçimi, eklenti sürümü, migration sayısı ve yerel değişiklik bilgisini inceleyin. Paketler `.deploy/releases/<release-id>/` altındadır.
3. **Yalnız kontrol et:** Uzak `main` ile Git commit uyumu, anahtarlı SSH, dosya SHA-256, gerçek JAR migration kataloğu, canlı Flyway geçmişi, boş alan, systemd yerleşimi ve gerekli migration ön koşulları kontrol edilir. Staging/root-owned paket kopyası yazılır; çalışan JAR/frontend değişmez, servis yeniden başlamaz, DB yedeği veya schema değişikliği yapılmaz.
4. **Kontrol ederek yayınla:** Seçilen paket için kontroller yeniden yapılır. Yedek alınır ve arşiv listesi okunur. Gerekirse V96 için btree_gist kurulur. Backend atomik değiştirilir; 180 saniyeye kadar readiness beklenir, beklenen migration’ların tamamlandığı kontrol edilir. Frontend dosyaları yayınlanır, index en son atomik değiştirilir. HTTPS index, referans asset’ler, eklenti JSON/ZIP ve dış readiness doğrulanır.

Bir hata sonraki aşamayı durdurur. Bir yerel pipeline ve bir sunucu yayını aynı anda çalışabilir; ikinci işlem reddedilir. İşlem geçmişi JSON ve okunabilir log olarak tutulur. Aynı release tekrar uygulanmaz; başarısız uygulama düzeltildikten sonra yeni paket hazırlayın.

### Kaynak ve test sınırı

- Commit/push/merge veya otomatik git pull yapılmaz. Kontrolde yalnız `git fetch origin main` ve ancestry kontrolü vardır. Uzak main değişikliklerini içermeyen paket reddedilir.
- Varsayılan **Son commit** modunda commitlenmemiş uygulama değişiklikleri hata oluşturmaz; paketin dışında bırakılır. Yalnız mevcut dalın son commit’indeki kod yayınlanır. İstenen düzeltmelerin bu commit’te bulunduğunu kontrol edin. IntelliJ changelist adları Git tarafından filtre olarak kullanılmaz. Checkout, stash veya reset çalıştırılmaz.
- Güncel yerel kodu paketlemek için **Çalışma klasörü** seçin. Commitlenmemiş değişiklik varsa “Commitlenmemiş uygulama değişikliklerini dahil et” izni ayrıca gerekir. Dosya hash’leri ve dirty bilgisi kaydedilir. Kontrol/yayın için hazır paket kartındaki “Seçili paketin yerel değişikliklerini kabul ediyorum” seçeneği ayrıca işaretlenmelidir.
- Kaynak seçimi yalnız yeni hazırlanan paketleri etkiler. Önceden hazırlanmış yerel değişiklikli paketler değişmez; bunların yerine Son commit ile yeni paket hazırlayın. Eski manifestler çalışma klasörü paketi olarak gösterilir.
- Kaynak kapsamı yalnız backend/frontend/extension dosyalarıdır. Graphify, `.env*`, özel anahtarlar, node_modules ve eski derleme çıktıları dışlanır. `frontend/.env.production` yerine sabit production API adresi süreç ortamından verilir. Bu filtre, kaynak içine yanlışlıkla yazılmış bütün secret’ları tespit eden bir tarayıcı değildir.
- **Maven package `-DskipTests` ile çalışır.** Bu işlem tam uygulama test süitinin geçtiğini iddia etmez ve yerel/production veritabanında test başlatmaz. Uygulamaya uygun testleri yayın öncesi ayrıca çalıştırın. Manifest bu ayrımı kaydeder. TypeScript/Vite ve eklenti derlemesi zorunludur.

## İlk VPS kurulumu — bir kez, kullanıcı tarafından

Pipeline şifreli SSH veya interaktif sudo penceresi açmaz. Başlangıçta sizin mevcut SSH erişiminizle aşağıdaki kurulum yapılmalıdır. Özel anahtar, `backend.env` veya sudo parolasını paylaşmayın.

### 1. SSH anahtarı

Mevcut uygun anahtarınız varsa onu kullanın. Yeni anahtar gerekiyorsa önce aynı dosyanın olmadığını kontrol edin; var olan anahtarı ezmeyin:

```powershell
Test-Path "$env:USERPROFILE\.ssh\vetly_deploy"
```

Sonuç `False` ise:

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\vetly_deploy" -C "vetly-deploy"
```

Parolalı anahtar için Windows OpenSSH agent’i çalışır durumda tutup `ssh-add` ile anahtarı açın. Panel özel anahtar parolasını saklamaz.

```powershell
ssh-add "$env:USERPROFILE\.ssh\vetly_deploy"
```

Public anahtarı mevcut erişiminizle ekleyin:

```powershell
Get-Content "$env:USERPROFILE\.ssh\vetly_deploy.pub" | ssh furkan@70.40.139.40 "umask 077; mkdir -p ~/.ssh; cat >> ~/.ssh/authorized_keys; chmod 700 ~/.ssh; chmod 600 ~/.ssh/authorized_keys"
```

`$env:USERPROFILE\.ssh\config` içine mevcut ayarları silmeden bu hedef için ekleyin:

```text
Host 70.40.139.40
    User furkan
    IdentityFile ~/.ssh/vetly_deploy
    IdentitiesOnly yes
```

Sunucu fingerprint’ini güvenilir VPS konsolundan doğrulayın. `StrictHostKeyChecking=no` kullanmayın. İlk tanıma için normal SSH ile bağlandıktan sonra aşağıdaki anahtarlı testin şifresiz tamamlanması gerekir:

```powershell
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes furkan@70.40.139.40 true
```

### 2. Root-owned yardımcı

Windows PowerShell, proje kökü:

```powershell
scp scripts/deploy/server.py furkan@70.40.139.40:~/vetly-deploy-server.py
scp scripts/deploy/release.py furkan@70.40.139.40:~/vetly-deploy-release.py
```

VPS terminalinde tek tek:

```bash
sudo install -d -o root -g root -m 0755 /usr/local/lib/vetly-deploy
sudo install -o root -g root -m 0755 ~/vetly-deploy-server.py /usr/local/lib/vetly-deploy/server.py
sudo install -o root -g root -m 0644 ~/vetly-deploy-release.py /usr/local/lib/vetly-deploy/release.py
sudo python3 -m py_compile /usr/local/lib/vetly-deploy/server.py /usr/local/lib/vetly-deploy/release.py
sudo install -d -o root -g root -m 0700 /var/lib/vetly-deploy
```

Helper dosyaları LF satır sonlu olmalıdır; repodaki `.gitattributes` bunu korur. Yardımcı ayrı bir systemd servisi değildir; yalnız pipeline çağrısı sırasında çalışır. `/opt/vetly` altına veya deploy kullanıcısının değiştirebileceği bir yere sudo ile çalıştırılacak kod koymayın. Yardımcı kodu ve üst dizinleri root’a ait ve grup/diğer kullanıcıların yazmasına kapalı olmalıdır; kod import edilmeden önce doğrulanır.

Sınırlı sudo kuralını `visudo` ile ekleyin:

```bash
sudo visudo -f /etc/sudoers.d/vetly-deploy
```

Dosyanın içeriği:

```text
furkan ALL=(root) NOPASSWD: /usr/local/lib/vetly-deploy/server.py
```

Ardından:

```bash
sudo chmod 0440 /etc/sudoers.d/vetly-deploy
sudo visudo -c
```

`NOPASSWD: ALL`, `NOPASSWD: python3`, kullanıcı tarafından değiştirilebilir `.py/.sh` dosyaları veya geniş `cp/mv/systemctl` yetkileri vermeyin. Yardımcı yalnız sabit hedefleri ve `check/apply/status` işlemlerini destekler; serbest DB adı, shell komutu veya hedef dizin kabul etmez.

Mevcut `/var/www/vetly` ve yayınlanacak alt dizinler root-owned ve grup/diğer kullanıcılara yazmaya kapalı olmalıdır; standart izinler dizinlerde 0755, dosyalarda 0644’tür. Yardımcı bunu doğrular. Yeni root-owned durum/yedek alanı `/var/lib/vetly-deploy` altında tutulur; uygulama kullanıcısı burada dosya değiştiremez.

Sunucu yardımcı kodu değiştiğinde iki dosyayı da aynı şekilde tekrar kurun. Her paketten root yetkisiyle yeni script çalıştırılmaz. Kontrol/yayın çağrısında yerel ve kurulu iki yardımcı dosyanın SHA-256 değerleri eşleştirilir; eski yardımcı sürümüyle yayın yapılmaz.

## Sunucudaki dosyalar

| Konum | Amaç |
|---|---|
| `/home/furkan/vetly-deploy/incoming/<id>` | Kullanıcının SSH ile yüklediği staging |
| `/var/lib/vetly-deploy/releases/<id>` | Root-owned doğrulanmış paket ve sonuç JSON’u |
| `/var/lib/vetly-deploy/backups/<id>` | DB dump + eski JAR + eski frontend; özel dizin |
| `/var/lib/vetly-deploy/.deploy.lock` | Eşzamanlı yayın kilidi |
| `/opt/vetly/app.jar` | systemd’nin çalıştırdığı JAR |
| `/var/www/vetly` | Nginx statik dosyaları; eski hashed asset’ler korunur |

Hedef kullanıcı `furkan`, uygulama kullanıcısı `vetly`, service `vetly-backend`, DB `vetly` ve alan adı sabittir. Farklı sunucuya uyarlamak için yardımcı ve PowerShell profilini kod üzerinden gözden geçirip tekrar kurun. Panelden hedef veya komut değiştirilemez.

## Hata ve geri alma

- Yedek veya ön kontrol hatasında aktif uygulama değiştirilmez.
- Backend/front kontrolleri başarısız, başlangıçta bekleyen migration yok ve migration geçmişi aynıysa eski JAR otomatik geri alınabilir; başarısız yayın başarılı gösterilmez.
- Frontend değişmişse eski frontend dosyaları/index geri yüklenir; yeni hashed asset’ler silinmez.
- **Başlangıçta bekleyen migration varsa veya migration geçmişi değiştiyse eski JAR otomatik geri yüklenmez. Veritabanı hiçbir durumda otomatik geri yüklenmez.** Migration kaydı değişmemiş olsa bile olası kısmi şema değişikliğini yalnız bu kayıtla kesin dışlayamayız. Sonuç manuel inceleme gerektiğini belirtir. Eski şemaya dönüş gerekiyorsa yayın sonrası yazılan veriler de dikkate alınarak ayrı kurtarma kararı verilmelidir.
- Yedekler otomatik silinmez ve bu araç sunucu dışı günlük yedek yerine geçmez. Root-owned yedekleri ayrı saklama politikasına göre yönetin. Disk yetersizse pipeline durur.

SSH/ağ bağlantısı yayın sırasında kesilirse sonuç belirsiz olabilir; otomatik tekrar başlatmayın. Önce sunucu sonucunu, systemd durumunu ve readiness’i kontrol edin. Bu ilk sürüm uzak işi systemd-run ile bağlantıdan bağımsız bir job’a dönüştürmez.

Belirli yayın sonucunu VPS’te tekrar okuyabilirsiniz:

```bash
sudo /usr/local/lib/vetly-deploy/server.py status --release RELEASE_ID
```

## Arayüzsüz kullanım ve yerel testler

```powershell
pwsh -File scripts/deploy.ps1 -Mode Prepare # Varsayılan: son commit
pwsh -File scripts/deploy.ps1 -Mode Prepare -SourceMode working-tree -AllowDirty # Yerel kodu açıkça dahil et
pwsh -File scripts/deploy.ps1 -Mode Check -ReleaseId RELEASE_ID
pwsh -File scripts/deploy.ps1 -Mode Deploy -ReleaseId RELEASE_ID
```

`Deploy` release belirtilmeden CLI’dan çağrılırsa yeni paket hazırlayıp kontrol ederek yayınlar. Panel ise önce hazırlanmış bir paketin seçilmesini ister.

```powershell
python -m unittest discover -s scripts/deploy -p test_pipeline.py -v
```

Testler yalnız geçici Git/artifact dizinleri ve sahte runtime kullanır; VPS, production systemd veya kullanıcı veritabanına bağlanmaz. Paket/manifest bozulması, migration uyumsuzluğu, arşiv path traversal, yedek hatası, readiness, geri alma sınırı, tek iş kilidi ve panel API güvenlik sınırlarını kapsar. Gerçek Ubuntu/sudo/SSH kurulumu ilk kullanımda ayrıca doğrulanmalıdır.
