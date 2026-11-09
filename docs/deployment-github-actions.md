# GitHub Actions ile production deployment

Bu akış GitHub’daki `main` dalından çalışır. Yerel panel veya bilgisayarın açık olması gerekmez. Uygulama yine mevcut VPS’te çalışır; GitHub Pages kullanılmaz. Kod ve workflow dosyaları commit/push edilmeden GitHub’da görünmez. Bu çalışma sırasında commit/push veya gerçek yayın yapılmadı.

## Nasıl çalışır?

Repository → **Actions → Production Deploy → Run workflow**. Branch **main** olmalıdır.

- **Check (varsayılan):** Uygulama CI testleri, paket derlemeleri ve VPS ön kontrolleri çalışır. Paket VPS staging alanına yüklenir ve root-owned alana doğrulanarak alınır. Aktif uygulama değiştirilmez; servis yeniden başlatılmaz; DB yedeği, extension kurulumu veya migration uygulaması yapılmaz.
- **Deploy:** Aynı test/derleme/doğrulama adımlarından sonra DB ve dosya yedekleri alınır; backend yayınlanır; readiness ve migration sonuçları doğrulanır; frontend yayınlanır; HTTPS kontrolleri tamamlanır.

Akış: main kontrolü → mevcut CI (backend, frontend, eklenti, E2E ve Docker derlemesi) → deployment regresyon testleri → production paketleme → production ortamı → paket/commit/hash doğrulaması → SSH → ön kontrol → seçilen Check/Deploy işlemi.

CI başarısızsa paketleme/yayın başlamaz. CI, aynı workflow run’ının commit’ini test eder; başarılı eski bir CI sonucuna güvenilmez. Paketleme sırasında Maven testleri tekrar çalıştırılmaz (`-DskipTests`); CI işi zorunludur. CI mevcut test altyapısını kullanır; bu değişiklikte bütün uygulama testleri lokalde yeniden çalıştırılmadı.

**Commit sabittir:** Run workflow anındaki `main` commit SHA’sı kullanılır. Çalışma sırasında gelen sonraki push’lar pakete eklenmez. Yayın öncesi uzak main yeniden kontrol edilir; paket yeni main değişikliklerini içermiyorsa işlem durur. Yeniden çalıştırılan eski run eski commit’i kullanır; güncel sürüm için yeni Run workflow başlatın.

İşler aynı hazırlanmış paketi artifact ile aktarır. Commit, temiz kaynak modu, manifest SHA-256 ve payload hash’leri hem indirme sonrasında hem yayın öncesinde doğrulanır. Kaynak snapshot, Graphify, .env ve anahtar filtrelerini korur. Artifact’ler 7 gün tutulur; application kaynak kopyası ve SSH anahtarı yüklenmez.

Aynı production concurrency grubunda ikinci yayın aktif yayını iptal etmez. GitHub’ın bekleyen run yönetimi bütün talepler için FIFO kuyruk garantisi vermez. Yayın sırasında Cancel workflow kullanmayın. SSH veya runner kesintisinde işlemin sonucu belirsiz olabilir; aşağıdaki durum kontrolünü yapmadan tekrar başlatmayın.

## Bir kez yapılacak kurulum

### 1. Dosyaları GitHub’a gönderin

Aşağıdaki yeni/değişen dosyalar yayın commit’inde bulunmalı:

- `.github/workflows/production-deploy.yml`
- `.github/workflows/ci.yml` (`workflow_call` ve SHA ile sabitlenmiş action sürümleri)
- `scripts/deploy.ps1`
- `scripts/deploy/release.py`, `server.py`, `github_actions.py`, `test_pipeline.py`, `.gitattributes`
- Yerel paneli de saklayacaksanız mevcut `scripts/Start-DeployUI.*` ve diğer `scripts/deploy/` dosyaları
- Güncellenen deployment belgeleri ve `.gitignore`

Bazı deployment dosyaları önceki çalışmalardan hâlâ untracked olabilir; yalnız workflow dosyasını commit etmek yeterli değildir. `.deploy/` altındaki paketler, özel anahtarlar ve `.env` dosyalarını commit etmeyin. Graphify değişikliklerini daha önce istediğiniz gibi ayrı yerel changelist’te tutabilirsiniz.

### 2. Production ortamı

GitHub → repository **Settings → Environments → New environment → production**.

Planınızın ve repository görünürlüğünün desteklediği ölçüde deployment branch kuralını yalnız **main** için ayarlayın; isterseniz required reviewer ekleyin. Workflow’daki main kontrolü ayrıca her koşulda çalışır. Onay kuralı varsa Check modu da production job’ına girdiği için onay bekler. GitHub ortam koruma özellikleri özel repository’lerde plana göre değişir.

Actions kullanımının repository’de açık olması gerekir. Workflow token’ı yalnız `contents: read` ister. Application/payment/AI secrets GitHub’a kopyalanmaz; VPS’teki mevcut servis ortamında kalır.

### 3. Ayrı SSH anahtarı

**Yerel PowerShell’de** aşağıdaki dosyanın varlığını kontrol edin:

```powershell
Test-Path "$env:USERPROFILE\.ssh\vetly_github_actions"
```

False ise yeni anahtar oluşturun:

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\vetly_github_actions" -C "vetly-github-actions"
```

Bu anahtar yalnız otomasyon içindir; parola sorularında Enter ile boş bırakın. Var olan kişisel anahtarın üzerine yazmayın. Özel anahtarı sohbete göndermeyin veya repoya eklemeyin.

Public anahtarı mevcut şifreli SSH erişiminizle VPS’e ekleyin:

```powershell
$publicKey = 'restrict ' + (Get-Content "$env:USERPROFILE\.ssh\vetly_github_actions.pub" -Raw).Trim()
$publicKey | ssh furkan@70.40.139.40 "umask 077; mkdir -p ~/.ssh; cat >> ~/.ssh/authorized_keys; chmod 700 ~/.ssh; chmod 600 ~/.ssh/authorized_keys"
```

`restrict` port/agent/X11 yönlendirmelerini ve PTY’yi kapatır; pipeline’ın SSH komutu ve scp/SFTP erişimi devam eder. Bu anahtar furkan hesabına bağlıdır; sunucudaki mevcut hesap yetkilerini devralır. Sudo yetkisi aşağıdaki sabit root yardımcısıyla sınırlı olmalıdır.

### 4. Sunucu kimliği

**Güvenilir VPS konsolunda** sunucunun public host key satırını alın:

```bash
awk '{print "70.40.139.40 " $1 " " $2}' /etc/ssh/ssh_host_ed25519_key.pub
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

İlk komutun çıktısı `70.40.139.40 ssh-ed25519 AAAA...` biçimindedir. Host key özel anahtarı paylaşılmaz. Workflow her çalışmada otomatik ssh-keyscan yapmaz; saklanan sunucu kimliğini zorunlu doğrular.

### 5. GitHub Secrets

**Settings → Environments → production → Environment secrets** altında iki secret ekleyin:

| Secret | Değer |
|---|---|
| `VETLY_SSH_PRIVATE_KEY` | Yeni `vetly_github_actions` özel anahtar dosyasının tamamı; BEGIN/END satırları dahil |
| `VETLY_SSH_KNOWN_HOSTS` | Yukarıdaki güvenilir konsoldan alınan `70.40.139.40 ssh-ed25519 AAAA...` satırı |

Özel anahtarı terminalde yazdırmadan GitHub alanına yapıştırmak için yerelde panoya alabilirsiniz:

```powershell
Get-Content "$env:USERPROFILE\.ssh\vetly_github_actions" -Raw | Set-Clipboard
```

Kaydettikten sonra panoyu temizleyin:

```powershell
Set-Clipboard -Value ''
```

Secrets yalnız production işinin SSH hazırlama adımına aktarılır. Anahtar runner’ın geçici dizininde erişimi sınırlandırılmış dosyada tutulur; yapılandırma `-F` ile hem ssh hem scp’ye verilir. İş sonunda `always()` ile silinir. GitHub runner tamamen kesilirse bu adım garanti değildir; hosted runner’ın geçici ortam temizliği geçerlidir.

### 6. VPS yardımcısı ve sınırlı sudo

[Mevcut kurulum kılavuzundaki](deployment-automation.md#2-root-owned-yardımcı) root-owned yardımcı kurulumunu uygulayın veya güncelleyin. `server.py` ve `release.py` birlikte kurulmalı; LF satır sonları korunmalıdır. Kurulu ve repodaki dosya hash’leri farklıysa yayın durur. Pipeline root kodunu kendisi güncellemez.

Konumlar:

- Kod: `/usr/local/lib/vetly-deploy/server.py` ve `release.py`
- Root-owned durum/yedek alanı: `/var/lib/vetly-deploy`
- Sudo kuralı: `furkan ALL=(root) NOPASSWD: /usr/local/lib/vetly-deploy/server.py`
- Backend: `/opt/vetly/app.jar`, `vetly-backend`
- Frontend: `/var/www/vetly`
- Veritabanı: `vetly`

Tüm sudo veya python3 için genel NOPASSWD vermeyin. Gerekli root-owned dizin izinleri ve migration ön koşulları önceki kılavuzda açıklanmıştır. VPS firewall’u GitHub-hosted runner’ın SSH erişimini kabul etmelidir; yalnız kişisel IP’nize izin veriliyorsa ağ erişimi ayrıca düzenlenmelidir. Firewall ayarlarını bu çalışma değiştirmedi.

## İlk çalıştırma

1. Commit/push ve yukarıdaki kurulumdan sonra [Actions](https://github.com/frknfrt/MyVet/actions) sayfasını açın.
2. **Production Deploy → Run workflow → main → Check** seçin.
3. CI, paketleme ve VPS kontrollerinin tamamlandığını; run özetindeki commit/paket kimliğini inceleyin.
4. Gerçek yayın için yeni bir run’da **Deploy** seçin. Bu yeni run güncel main’i yeniden test eder ve yeni paket üretir; önceki Check paketini otomatik yayınlamaz.
5. Varsa ortam onayını verin. Aşamaları loglardan, özetleri run Summary ekranından izleyin. `production-*` paketleri ve `production-result-*` sonuç/günlükleri Artifacts bölümündedir.

## Kesinti ve kurtarma

SSH, runner veya workflow iptalinde manuel VPS erişimiyle kontrol edin:

```bash
sudo /usr/local/lib/vetly-deploy/server.py status --release RELEASE_ID
systemctl is-active vetly-backend
curl -fsS http://127.0.0.1:8081/actuator/health/readiness
```

Migration varsa eski JAR’ın otomatik geri dönüşü kısıtlıdır. DB otomatik restore edilmez. Önceki pipeline’ın yedekleme/geri alma sınırları korunur; ayrıntılar [yerel pipeline kılavuzunda](deployment-automation.md#hata-ve-geri-alma).

## Doğrulama ve sınırlar

Bu değişiklik için 36 yerel deployment testi, PowerShell/Python sözdizimi ve actionlint kontrolü geçti. Gerçek OpenSSH ile üretilen geçici test anahtarı ve config yerelde doğrulandı; ağ bağlantısı yapılmadı. Yanlış manifest hash’i SSH öncesinde reddedildi.

GitHub üzerinde ilk run, repository/environment secrets kurulumu ve gerçek VPS Check/Deploy henüz yapılmadı. Uygulamanın tam CI süiti ilk GitHub run’ında zorunlu olarak çalışacak. Mevcut CI testindeki hata yayın aşamasını bloke eder. Kullanılan GitHub-hosted runner süreleri ve artifact depolaması hesabın Actions kotasına tabidir.

Başvuru: [GitHub deploy akışları](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments), [environment kurulum ve plan sınırları](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments), [action SHA sabitleme](https://docs.github.com/en/actions/reference/security/secure-use).
