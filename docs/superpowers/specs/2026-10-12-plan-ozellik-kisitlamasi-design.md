# Plan Özellik Kısıtlaması (Faz 2) — Tasarım

**Tarih:** 2026-10-12
**Yol haritası:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` → Faz 2
**Durum:** Onaylandı (sohbette, bölüm bölüm)

## 1. Amaç

Admin panelindeki Paket Yönetimi ekranında her paket için işaretlenen **"Modül Erişimi"** kutuları (`plan_feature_flags`, commit `40604c6`) bugün yalnızca kayıt amaçlı; kliniklerde hiçbir etkisi yok. Bu iş, o işaretleri gerçekten uygular: bir paketin açmadığı modül, o paketteki kliniklerde backend'de ve ön yüzde kapanır. Admin işareti değiştirdiğinde etkisi anında görülür.

Ek olarak:
- Modül listesi tek yerde (backend) tutulur; admin ekranı listeyi ve Türkçe etiketleri backend'den alır.
- Kısıtlama genel bir ayarla kademeli açılır (varsayılan kapalı), böylece mevcut paketlerin işaretleri düzenlenmeden ödeme yapan klinikler kilitlenmez.

## 2. Kullanıcı kararları (sohbetten)

| Konu | Karar |
|---|---|
| Deneme (TRIAL) klinikleri | Tüm özellikler açık. TRIAL için paket kaydı aranmaz. |
| Kapalı modülün eski verisi | **Tamamen kapalı** — okuma dahil tüm uçlar 403; menüden gizlenir. |
| Başka modülün olayıyla tetiklenen otomatik işler | Sessizce atlanır (bilgi logu). İstisna: "stoktan ver" reçetesi reddedilir (bkz. 4.4). |
| Bayrakların okunduğu yer | İstek anında veritabanından (seçenek A); önbellek yok, değişiklik anında etkili. |
| Plan kodu var ama paket kaydı yok | Tüm özellikler kapalı + uyarı logu (ücretli özellik yanlışlıkla açılmaz). |
| Modül listesi | Tek kaynak backend; admin ekranı oradan çeker. |
| Kademeli geçiş | `features.enforcement.enabled`, varsayılan `false`. |
| Git | Commit yok; tüm değişiklikler changelist'te kalır, push yok. |

## 3. Kapsam dışı

- Kiracı bazında özel açma/kapama (paketten bağımsız istisna).
- Kısıtlamanın admin panelinden düğmeyle açılması (ortam değişkeni + yeniden başlatma yeterli).
- Önbellek (Faz 6).
- Yeni modül eklemenin tamamen koddan bağımsız olması: yeni modül her zaman uçlarına etiket konmasını gerektirir; hedef yalnızca listeyi **tek yere** indirmek.

## 4. Tasarım

### 4.1 Ortak altyapı: `com.vetos.platform.feature` (yeni paket, `@NamedInterface("feature")`)

- **`Feature` (enum):** `AI_ASSISTANT, IMAGING, LAB_INTEGRATION, BOARDING, INVENTORY, E_FATURA`. Her değer Türkçe etiketini taşır (`label()`): mevcut `PlanManagementPage.tsx` `FEATURE_FLAG_LABELS` metinleri aynen.
  - `platformadmin/domain/PlanFeatureFlag` silinir; `Plan.enabledFeatures` tipi `Set<Feature>` olur. `@Enumerated(STRING)` değerleri aynı → **migration yok**.
  - `PlanResponse`, `UpdatePlanRequest`, `UpdatePlanCommand` ve testleri yeni tipi kullanır; JSON sözleşmesi değişmez (aynı string'ler).
- **`FeatureAccessPort` (arayüz):**
  - `boolean isEnabled(UUID tenantId, Feature feature)`
  - `Set<Feature> enabledFeatures(UUID tenantId)`
  - `boolean enforcementActive()`
- **`@RequiresFeature(Feature value)`:** sınıf veya metot üzerinde; metot etiketi sınıf etiketinin yerine geçer.
- **`FeatureNotInPlanForbiddenException extends DomainException`:** kod `FEATURE_NOT_IN_PLAN`, mesaj "Bu ozellik planinizda yok". Sınıf adı `ForbiddenException` ile bittiği için mevcut `GlobalExceptionHandler.resolveStatus` kuralıyla **403** döner.
- **`FeatureGateInterceptor` (`HandlerInterceptor`) + `FeatureWebConfig` (`WebMvcConfigurer`):**
  - Handler `HandlerMethod` değilse veya etiket yoksa geçer.
  - Kiracı: `TenantContext.currentOrNull()`. Etiketli uçta kiracı yoksa reddeder (403).
  - `isEnabled` false ise `FeatureNotInPlanForbiddenException` fırlatır.
  - Platform admin zinciri ve public uçlar etiketsiz olduğu için etkilenmez.

### 4.2 Bayrakları okuyan taraf: `platformadmin` içinde `PlanFeatureAccessAdapter implements FeatureAccessPort`

Sıra:
1. `features.enforcement.enabled=false` → tüm özellikler açık (`EnumSet.allOf`).
2. `TenantAdminPort.getOverview(tenantId).planCode()` okunur.
3. `"TRIAL"` → tüm özellikler açık.
4. `PlanRepository` ile koda göre paket aranır; bulunursa `enabledFeatures`.
5. Bulunamazsa → boş küme + `log.warn("Plan bulunamadi, tum ozellikler kapali: tenantId={}, planCode={}")`.

Önbellek yok. Paketin `active=false` olması bayrakları etkilemez (pasif paket = satıştan kalkmış; mevcut aboneler korunur).

`application.yml`:
```yaml
features:
  enforcement:
    # Plan "Modul Erisimi" isaretlerinin kliniklere uygulanmasi. Mevcut paketlerin
    # isaretleri admin panelinden duzenlenmeden ACILMAMALI (bos isaretli paketteki
    # odeme yapan klinikler kilitlenir).
    enabled: ${FEATURES_ENFORCEMENT_ENABLED:false}
```

### 4.3 Kapatılan uçlar

| Özellik | Sınıf etiketi | Metot etiketi |
|---|---|---|
| AI_ASSISTANT | `/api/v1/ai` controller'ı | |
| IMAGING | `/api/v1/imaging-records` | |
| LAB_INTEGRATION | `/api/v1/lab-results` | |
| BOARDING | `/api/v1/boarding-rooms`, `/api/v1/boarding-stays` | |
| INVENTORY | `/api/v1/inventory-items`, `/api/v1/inventory/pending-deductions` | `TarbilController`: `GET /stock-sync`, `POST /stock-sync/{snapshotId}/apply`, `POST`/`GET /encounters/{encounterId}/deduction`; `TarbilExtensionController`: `POST /stock-snapshots`, `POST /stock-snapshots/compare` |
| E_FATURA | `/api/v1/efatura` | |

Etiketlenmeyenler: `/api/v1/public/**` geri dönüş uçları (iyzico, FaturaEntegratör), TARBİL hastalık bildirimi / reçete doldurma uçları.

Modulith: `platform::feature` şu modüllerin `allowedDependencies` listesine eklenir: `ai`, `imaging`, `lab`, `boarding`, `inventory`, `integration.efatura`, `integration.tarbil`, `platformadmin`, `tenant`.

### 4.4 Otomatik işler (senkron olay dinleyicileri; istek içinde çalıştıkları için `TenantContext` dolu)

| Dinleyici | Özellik kapalıysa |
|---|---|
| `EncounterFinalizedEventListener` (inventory) | Atla, INFO log |
| `VaccinationStockEventListener` (kayıt + iptal) | Atla, INFO log |
| `InvoiceIssuedEventListener` (efatura) | Atla, INFO log (GİB'e kuyruk yok) |
| `PrescriptionStockEventListener` — `onIssued` | Reçetede stok kalemi varsa `FeatureNotInPlanForbiddenException` (işlem geri alınır, reçete kaydedilmez); stok kalemi yoksa normal |
| `PrescriptionStockEventListener` — `onCancelled` | Atla (stok düşülmemişti) |

Not: Dinleyicide kiracı `TenantContext.current()` ile alınır; olay nesnesi kiracı taşıyorsa o tercih edilir.

### 4.5 Admin paneli

- **`GET /api/v1/platform-admin/plans/feature-options`** (platform admin yetkisi) → `{ enforcementActive: boolean, options: [{ code, label }] }` (enum sırasıyla).
- `PlanManagementPage.tsx`: `FEATURE_FLAG_OPTIONS` ve `FEATURE_FLAG_LABELS` silinir; kutular bu uçtan çizilir. `platformAdminApi.ts` içindeki `PlanFeatureFlag` tipi `string` olur.
- `enforcementActive=false` iken sayfanın üstünde uyarı: "Modül kısıtlaması henüz aktif değil — işaretler şu an kliniklere uygulanmıyor."

### 4.6 Klinik ön yüzü

- **`GET /api/v1/subscriptions/features`** (tenant modülü, giriş yapmış tüm personel) → `{ enforcementActive, enabledFeatures: string[] }`. (`/subscriptions/current` yalnız ADMIN olduğu için ayrı uç.)
- **`FeatureContext`** (`frontend/src/auth/` yanında): oturum açılınca bir kez yükler; `hasFeature(code)`. Yüklenemezse veya yüklenirken her şeyi **açık** varsayar (backend zaten korur; ön yüz yalnız görünürlük).
- **Menü (`navConfig.tsx`):** `NavItemConfig`'e `feature?: string`; Laboratuvar→LAB_INTEGRATION, Görüntüleme→IMAGING, Konaklama→BOARDING, Stok→INVENTORY. Kapalıysa öğe çizilmez.
- **Rotalar (`App.tsx`):** ilgili rotalar `<FeatureRoute feature=...>` ile sarılır; kapalıysa "Bu modül paketinizde yok. Paketinizi yükseltmek için Ayarlar › Abonelik" sayfası.
- **Sayfa içi parçalar:**
  - Muayene: AI öneri düğmeleri (AI_ASSISTANT), `PrescriptionCard` "Kliniğin stoğundan ver" kutusu ve `MaterialsUsedCard` (INVENTORY).
  - Aşı: `VaccineStockPicker` (INVENTORY).
  - Finans: e-fatura bölümü (E_FATURA).
- **403 `FEATURE_NOT_IN_PLAN`:** `ApiError.errorCode` ile tanınır; ilgili ekranda anlaşılır mesaj gösterilir, uygulama çökmez.

## 5. Hata ve kenar durumları

- Etiketli uçta kiracı yok → 403 (reddet).
- Paket kaydı yok → hepsi kapalı + uyarı logu; genel ayar kapalıyken etkisiz.
- Admin paketteki işareti kaldırır → sonraki istek anında 403.
- Klinik paketini değiştirir (`updateSubscription`) → sonraki istek yeni paketle değerlendirilir.
- Genel ayar kapalı → davranış bugünküyle birebir aynı (regresyon yok).

## 6. Test

- `PlanFeatureAccessAdapterTest`: TRIAL→hepsi; paket işaretleri aynen; paket yok→boş + log; ayar kapalı→hepsi.
- `FeatureGateInterceptorTest` (standalone MockMvc): etiketsiz geçer; açık geçer; kapalı 403 `FEATURE_NOT_IN_PLAN`; metot etiketi sınıfı ezer; kiracı yok 403.
- Dinleyici testleri: stok kapalı → muayene/aşı düşümü yok; e-fatura kapalı → kuyruk yok; stok kapalı + stok kalemli reçete → istisna; stoksuz reçete → normal.
- Entegrasyon (MockMvc, mevcut test altyapısı): kapalı paketli klinik `GET /api/v1/lab-results` → 403; iyzico callback etkilenmez; `/subscriptions/features` doğru liste; `/platform-admin/plans/feature-options` 6 seçenek.
- `ApplicationModulesTest` yeşil.
- Ön yüz: `npm run build`; elle kontrol listesi (menü gizleme, doğrudan adres sayfası, reçete kutusu, admin uyarısı).

## 7. Devreye alma

1. Kod canlıya çıkar (ayar kapalı, davranış değişmez).
2. Admin, Paket Yönetimi'nde her paketin "Modül Erişimi" kutularını düzenler.
3. VPS'te `FEATURES_ENFORCEMENT_ENABLED=true`, backend yeniden başlatılır.
4. Admin ekranındaki uyarı kaybolur; kliniklerde kısıtlama başlar.
