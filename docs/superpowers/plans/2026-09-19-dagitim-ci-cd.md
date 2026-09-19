# Dağıtım & CI/CD Temeli Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GitHub Actions ile backend+frontend için otomatik hızlı test gate'i kurmak, bir sağlık kontrolü (health check) uç noktası eklemek, ve `main` dalını CI yeşil olmadan merge edilemeyecek şekilde korumak.

**Architecture:** `.github/workflows/ci.yml` iki bağımsız job çalıştırır (`backend`: Postgres service container + `mvn test` + Docker build doğrulaması; `frontend`: `npm run build`). Spring Boot Actuator, platformdan bağımsız bir `/actuator/health` uç noktası ekler. Gerçek deploy adımı bu planın kapsamında değil (prod platformu henüz netleşmedi) — workflow, ileride tek bir yeni job eklenerek genişletilebilecek şekilde bırakılıyor.

**Tech Stack:** GitHub Actions, Spring Boot 3.5 Actuator, Maven, Vite/npm.

**Spec:** `docs/superpowers/specs/2026-09-18-dagitim-ci-cd-design.md`

## Global Constraints

- Postgres service container'ı `docker-compose.yml`'deki kimlik bilgileriyle birebir eşleşir: db=`myvet`, user=`myvet`, password=`myvet`, port=`5433`.
- `frontend/package.json`'daki `build` script'i zaten `tsc -b && vite build` — ayrı bir typecheck adımına gerek yok, sadece `npm run build` çalıştırılır.
- Health uç noktası `show-details: never` ile açılır — kimlik doğrulaması olmadan herkese açık olacağı için iç detay (DB bağlantı dizesi vb.) sızdırmaz.
- Bu kod tabanında pure Spring config/wiring sınıfları için ayrı birim test yazılmaz (bkz. `SecurityConfig`'in kendisinin test edilmemesi) — Actuator eklemesi de bu kurala uyar, doğrulama gerçek bir HTTP isteğiyle (manuel) yapılır.
- `main`'e push ve GitHub repo ayarı (branch protection) değişiklikleri, kullanıcının açık onayını gerektiren eylemlerdir — Task 3'te ayrıca işaretlendi, otomatik yapılmaz.

---

## Task 1: Spring Boot Actuator — Sağlık Kontrolü Uç Noktası

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Üretir: `GET /actuator/health` → `{"status":"UP"}` (kimlik doğrulaması gerektirmez, `SecurityConfig`'de zaten `permitAll` — bkz. `.requestMatchers("/actuator/health").permitAll()`, mevcut kodda zaten var).

- [ ] **Step 1: `pom.xml`'e Actuator bağımlılığını ekle**

`backend/pom.xml` — `spring-boot-starter-validation` bağımlılığından hemen sonra (satır 53'ten sonra), diğer boş satırdan önce ekle:
```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```
(Versiyon belirtilmiyor — `spring-boot-starter-parent` BOM'u zaten yönetiyor, diğer `spring-boot-starter-*` bağımlılıklarıyla aynı desen.)

- [ ] **Step 2: `application.yml`'e `management` bölümünü ekle**

`backend/src/main/resources/application.yml` — `spring:` bloğunun bittiği yere (mevcut `servlet.multipart` alt bloğundan sonra, `app:` bölümünden önce), yeni bir üst seviye anahtar olarak ekle:
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

- [ ] **Step 3: Backend'in derlendiğini doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: hatasız derleme.

- [ ] **Step 4: Uç noktayı yerelde manuel doğrula**

Docker Compose Postgres'i başlat (henüz çalışmıyorsa): `cd backend && docker-compose up -d`
Backend'i başlat: `cd backend && ./mvnw spring-boot:run` (ayrı bir terminalde/arka planda)
Başka bir terminalde: `curl -s http://localhost:8080/actuator/health`
Expected: `{"status":"UP"}` (başka detay yok — `show-details: never` ayarı bunu doğruluyor).
Backend'i durdur (Ctrl+C ya da işlemi sonlandır).

- [ ] **Step 5: Mevcut backend test paketinin hâlâ geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: tüm testler PASS (yeni bağımlılık mevcut hiçbir testi bozmamalı).

- [ ] **Step 6: Commit**

```bash
cd backend && git add pom.xml src/main/resources/application.yml
git commit -m "feat: Actuator saglik kontrolu ucnoktasi ekle"
```

---

## Task 2: GitHub Actions CI Workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Üretir: `backend` ve `frontend` adında iki GitHub Actions job'ı — Task 3'teki branch protection kuralı bu isimleri "required status check" olarak kullanacak.

- [ ] **Step 1: Workflow dosyasını oluştur**

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
      - name: Docker imajini dogrula
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

- [ ] **Step 2: YAML söz dizimini yerel olarak doğrula**

Run (Python zaten kuruluysa; değilse bu adımı atla, Step 3'teki gerçek Actions çalışması zaten doğrulayacak):
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))" 2>/dev/null && echo "YAML gecerli" || echo "python3/PyYAML yok, bu adim atlandi"
```
Expected: "YAML gecerli" ya da atlama mesajı (hata değil).

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: GitHub Actions ile backend+frontend hizli test gate'i ekle"
```

**Not:** Bu workflow'un gerçekten çalıştığını görmek (GitHub Actions sekmesinde yeşil/kırmızı sonuç) için commit'lerin GitHub'a push edilmesi gerekiyor — bu, Task 3'te kullanıcının açık onayıyla yapılır.

---

## Task 3: Push, CI Doğrulama, Branch Protection

**Files:** Yok (repo ayarı + doğrulama — kod değişikliği içermiyor).

**Interfaces:**
- Consumes: Task 2'nin ürettiği `backend`/`frontend` job isimleri.

- [ ] **Step 1: Kullanıcıdan push için açık onay iste**

Bu adım koddan önce **durur ve sorar** — repoya push etmek paylaşılan/uzak bir duruma etki eden bir eylem (bu projenin genel güvenlik ilkeleri gereği otomatik yapılamaz). Sor: "Task 1-2'deki commit'leri (ve bu oturumda birikmiş diğer commit'leri) `origin/main`'e push edebilir miyim, CI'ın gerçekten çalıştığını görmek için?"

- [ ] **Step 2: Onay sonrası push et**

Kullanıcı onaylarsa:
```bash
git push origin main
```

- [ ] **Step 3: GitHub Actions sonucunu doğrula**

`gh run list --limit 1` (ya da GitHub arayüzünden Actions sekmesi) ile son çalıştırmanın hem `backend` hem `frontend` job'larının **yeşil (success)** bittiğini doğrula.
Expected: iki job da `success`.

Eğer kırmızıysa: `gh run view --log-failed` ile hatayı incele, düzelt, yeni bir commit'le tekrar push et — bu adım tekrarlanır, bir sonraki step'e geçilmez.

- [ ] **Step 4: Kullanıcıdan branch protection için açık onay iste**

Bu adım da **durur ve sorar** — GitHub repo ayarlarını değiştirmek, kod dışı, paylaşılan bir yapılandırma değişikliği. Sor: "`main` dalına, `backend` ve `frontend` CI job'larını zorunlu kılan bir branch protection kuralı ekleyebilir miyim? Bu, bu iki job yeşil olmadan merge butonunu kilitler."

- [ ] **Step 5: Onay sonrası branch protection kuralını uygula**

Kullanıcı onaylarsa:
```bash
gh api repos/frknfrt/MyVet/branches/main/protection \
  --method PUT \
  -f required_status_checks[strict]=true \
  -f 'required_status_checks[contexts][]=backend' \
  -f 'required_status_checks[contexts][]=frontend' \
  -F enforce_admins=false \
  -F required_pull_request_reviews=null \
  -F restrictions=null
```

- [ ] **Step 6: Doğrula**

Run: `gh api repos/frknfrt/MyVet/branches/main/protection --jq '.required_status_checks.contexts'`
Expected: `["backend", "frontend"]` içeren bir liste.
