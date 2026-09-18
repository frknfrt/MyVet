# Dağıtım & CI/CD Temeli — Tasarım Dokümanı

**Tarih:** 2026-09-18
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** Repo kökü (`.github/workflows`), `backend/pom.xml`, `backend/src/main/resources/application.yml`

## 1. Bağlam ve Amaç

Repo GitHub'da (`frknfrt/MyVet`) ama `.github/workflows` yok — hiçbir otomatik test/derleme akışı kurulu değil. Bu oturum boyunca her değişiklikten sonra testleri/typecheck'i elle çalıştırdım; bu, insan hatasına açık ve PR'lara güvence vermiyor.

**Kullanıcıyla netleşen kapsam kararı:** Prodüksiyon barındırma platformu **henüz belli değil** (Render şu an sadece test ortamı). Bu yüzden bu tur, platformdan bağımsız kısma odaklanıyor — gerçek "deploy et" adımı platform netleşince, bu turun bıraktığı temiz bir ek noktadan eklenecek. E2E testleri (Playwright) bu turda CI'a dahil edilmiyor — kullanıcı sadece hızlı testleri istedi.

**Doğrulanan mevcut durum:**
- Mevcut backend test paketinin (~200 test) **hiçbiri** gerçek bir veritabanı gerektirmiyor — hepsi Mockito birim testi veya Spring Modulith'in statik bytecode analizi (`ApplicationModulesTest`, `@SpringBootTest` hiç kullanılmıyor).
- **Ama** onaylanmış "Kiracı İzolasyonu Sertleştirme" tasarımındaki (`2026-09-17-kiraci-izolasyonu-sertlestirme-design.md`) `TenantIsolationTest`, bu kod tabanındaki **ilk** gerçek-veritabanı-gerektiren test olacak. CI, bu değişiklik gelmeden önce buna hazır kurulmalı — yoksa o tur implemente edilir edilmez CI kırılır.
- `application-prod.yml` zaten iyi bir disiplin gösteriyor: eksik bir prod env değişkeninde uygulama sessizce yanlış bir varsayılana düşmek yerine başlamayı reddediyor.
- Sağlık kontrolü (health check) uç noktası yok — Spring Boot Actuator projede yok. Hangi platforma gidilirse gidilsin, zero-downtime deploy'un "yeni instance hazır mı" diye sorabileceği bir uç noktaya ihtiyacı olacak.
- `backend/Dockerfile` zaten var ve makul (çok aşamalı derleme, küçük JRE imajı, Render gibi düşük bellekli ortamlar için heap sınırı) — CI'da sadece bozulmadığının doğrulanması yeterli, yeniden yazılmasına gerek yok.

## 2. Kapsam

**Bu turda yapılacak:**
- `.github/workflows/ci.yml` — her PR'da ve `main`'e push'ta: backend testleri (Postgres service container ile) + frontend typecheck/build.
- `backend`'e Spring Boot Actuator, sadece `/actuator/health` açık (platform-bağımsız, ileride hangi platform seçilirse seçilsin gerekecek).
- Docker imajının CI'da build edilip doğrulanması (henüz hiçbir registry'ye push edilmiyor).
- `main` dalı için branch protection kuralı (repo ayarı, kod değil — CI yeşil olmadan merge engellenir).

**Kapsam dışı (bilinçli olarak):**
- **Gerçek deploy adımı** (registry'ye push, platforma tetikleme, deploy-sonrası smoke test, rollback) — platform netleşmeden tasarlanamaz. Bu turun bıraktığı workflow dosyası, ileride tek bir yeni job eklenerek genişletilebilecek şekilde temiz bırakılıyor (bkz. §5).
- **E2E testleri (Playwright) CI'da** — kullanıcı bu turda istemedi, ayrı bir tur olabilir.
- **Frontend'in nereye deploy edileceği** (Render Static Site, Vercel, Netlify, vb.) — backend platformuyla birlikte netleşecek bir karar, bu turda yok.
- **Staging/production ortam ayrımı, branch stratejisi** — repo şu an trunk-based çalışıyor (`main` + kısa ömürlü feature branch/worktree'ler, merge sonrası silinen) — bu turun CI tetikleyicileri bu varsayıma göre kuruluyor (her PR + `main`'e her push), ayrı bir `develop` dalı eklenmiyor.
- **Testcontainers'a geçiş** — `pom.xml`'de `spring-boot-testcontainers`/`org.testcontainers` bağımlılıkları tanımlı ama **hiçbir testte kullanılmıyor** (muhtemelen ileride kullanılmak üzere önceden eklenmiş). Bu, mevcut Postgres-service-container yaklaşımıyla çakışmıyor, ama ayrı bir temizlik fırsatı — bu turda dokunulmuyor.

## 3. GitHub Actions Workflow

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: myvet
          POSTGRES_USER: myvet
          POSTGRES_PASSWORD: myvet
        ports:
          - 5433:5432
        options: >-
          --health-cmd="pg_isready -U myvet"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Backend testleri
        working-directory: backend
        run: ./mvnw -B test
        env:
          DB_HOST: localhost
          DB_PORT: 5433
          DB_NAME: myvet
          DB_USER: myvet
          DB_PASSWORD: myvet
      - name: Docker imajını dogrula
        working-directory: backend
        run: docker build -t myvet-backend:ci .

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - working-directory: frontend
        run: npm ci
      - name: Typecheck + build
        working-directory: frontend
        run: npm run build
```

**Notlar:**
- Postgres service container `docker-compose.yml`'deki kimlik bilgileriyle (`myvet`/`myvet`/`myvet`, port 5433) birebir eşleşiyor — yerel geliştirme deneyimiyle tutarlı, ayrı bir "CI'a özel" DB yapılandırması icat edilmiyor.
- `mvn test` şu an bu Postgres'i hiç kullanmıyor (bkz. §1) ama servis container'ı zararsız/bedelsiz — A implemente olunca otomatik hazır olacak.
- `frontend`'in `npm run build` script'i zaten `tsc -b && vite build` (typecheck + gerçek build tek komutta) — ayrı bir typecheck adımına gerek yok.
- İki job (`backend`, `frontend`) birbirinden bağımsız, paralel çalışır — toplam CI süresi daha kısa.

## 4. Health Check Endpoint

`pom.xml`'e `spring-boot-starter-actuator` eklenir. `application.yml`'e:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```
`show-details: never` — sağlık uç noktası kimlik doğrulaması olmadan herkese açık olacağı için (platformun ping atabilmesi gerekiyor), veritabanı bağlantı dizesi gibi iç detayları sızdırmaması için bilinçli bir seçim.

## 5. Deploy Job İçin Bırakılan Genişleme Noktası

Bu workflow dosyasına şimdi boş bir "deploy" job'ı **eklenmiyor** (spekülatif iskelet kod istenmiyor — bkz. proje genel ilkeleri). Platform netleştiğinde, eklenecek olan:
```yaml
  deploy:
    needs: [backend, frontend]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - ... # platforma özel adımlar
```
şeklinde, mevcut `backend`/`frontend` job'larına **hiç dokunmadan** eklenebilecek, izole bir ek olacak. Bu turun asıl katkısı, bu eklemenin küçük ve risksiz olmasını sağlayan temiz bir temel bırakmak.

## 6. Branch Protection

Repo ayarı (kod değil): `main` dalında, `backend` ve `frontend` CI job'ları **zorunlu durum kontrolü (required status check)** olarak işaretlenir — bu ikisi yeşil olmadan merge butonu aktif olmaz. Implementasyon sırasında `gh` CLI (`gh api repos/frknfrt/MyVet/branches/main/protection ...`) veya GitHub arayüzünden yapılabilir; kullanıcı onayı gerektiren bir repo-ayarı değişikliği olduğu için implementasyon planında ayrı, açıkça onay istenen bir adım olarak işaretlenmeli.

## 7. Test Stratejisi

Bu turun "testi" workflow'un kendisinin doğru çalıştığını görmek: bir deneme PR'ı açılıp hem `backend` hem `frontend` job'larının yeşil bittiği, kasıtlı olarak bozulan bir testin (örn. geçici bir `assertTrue(false)`) `backend` job'ını kırdığı ve merge butonunu (branch protection kurulduktan sonra) engellediği doğrulanır. Bu, geleneksel birim test kapsamına girmiyor — CI altyapısının kendisi bu turun "ürünü".

## 8. Açık Sorular

Yok — tasarım kullanıcı onayından geçti. Deploy platformu netleştiğinde §5'teki genişleme noktası kullanılarak ayrı bir (çok daha küçük) tasarım/plan turu yapılacak.
