# Kiracı İzolasyonu Sertleştirme — Tasarım Dokümanı

**Tarih:** 2026-09-17
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `platform/tenancy` (yeni), + `tenant_id` eklenen 16 entity'nin ait olduğu modüller (`patient`, `tenant`, `inventory`, `billing`, `encounter`, `notification`, `imaging`, `lab`, `ai`)

## 1. Bağlam ve Amaç

Proje bir SaaS, kiracı (klinik) izolasyonu en kritik güvenlik varsayımı. Denetimde iki ciddi bulgu çıktı:

1. **`docs/api-conventions.md`, projenin kendi dokümanında, doğru olmayan bir iddia içeriyor:** "JWT'den çözülen `tenantId`, `TenantContext` üzerinden tüm repository sorgularına **otomatik** filtre olarak eklenir (Hibernate `@Filter` veya sorgu interceptor ile)." Kod tabanında `@Filter`/`@FilterDef`/Postgres Row-Level Security **hiç yok**. Gerçek durum: her use-case, kendi repository sorgusuna `tenantId`'yi **elle** geçirmek zorunda (`TenantContext.current()` üzerinden okunuyor ama sorguya elle iletiliyor).
2. **44 JPA entity'sinden sadece 17'sinde bir `tenantId` alanı var.** Geri kalan 27'nin çoğu `findById(id)` ile, hiçbir kiracı kontrolü olmadan okunuyor. Bu **doğrulanmış, çalışan bir güvenlik açığı** — iki somut örnek:
   - `RecordStockMovementUseCase.execute()` → `inventoryItemRepository.findById(inventoryItemId)` → dönen kaydın çağıranın kiracısına ait olup olmadığı hiç kontrol edilmiyor (Hızlı Satış code review'unda da bağımsız olarak bulunmuştu).
   - `POST /notifications/logs/{id}/retry` → `RetryNotificationUseCase.execute(id)` → aynı şekilde kontrolsüz.

Bu doküman, bu sınıf açığı **tek tek yama yaparak değil, mimari seviyede** kapatmayı hedefliyor.

**Kullanıcıyla netleşen kapsam kararı:** Postgres Row-Level Security (RLS) değil, **Hibernate 6.6'nın yerleşik `@TenantId` mekanizması** kullanılacak — ORM seviyesinde otomatik filtre (JPQL/Criteria/`findById` dahil), native SQL sorgular hariç (onlar zaten elle filtrelidir/filtrelenmelidir). RLS, ihtiyaç doğarsa ayrı bir tur olarak değerlendirilebilir, bu turda yok.

## 2. Kapsam

**Bu turda yapılacak:**
- `platform/tenancy` içine `CurrentTenantIdentifierResolver` implementasyonu + Hibernate'e bağlayan config.
- 16 entity'ye `tenant_id` kolonu (migration + üst kayıttan geriye doldurma) ve `@TenantId` anotasyonu.
- "TenantContext köprüleme kuralı" — arka plan/platform-admin gibi normal bir HTTP isteği dışında çalışan ve tenantId'yi zaten elinde (parametre olarak) taşıyan kodun, `@TenantId`'li bir entity'ye dokunmadan önce `TenantContext`'i geçici olarak kurması. Bilinen 2 somut yer: `AppointmentReminderScheduler`, `TenantAdminPortAdapter` (StaffUser erişimi).
- `NotificationLog`'un **bilinçli olarak `@TenantId` dışında bırakılması** + `RetryNotificationUseCase`'e elle tenant kontrolü.
- Kapsamlı bir "kiracılar arası izolasyon" entegrasyon test paketi — asıl güvence budur (bkz. §6).

**Kapsam dışı (bilinçli olarak):**
- **Postgres Row-Level Security** — kullanıcıyla konuşuldu, bu turda yok.
- **Breed, Species, Plan gibi paylaşımlı referans verisi** — bilinçli olarak tenant'sız kalıyor, dokunulmuyor.
- **Tenant, PlatformAdminUser, PlatformPayment, TenantSignupRequest, BranchWorkingHours, StaffShiftTemplate, TarbilSyncLog** — platform seviyesi veya doğrudan tenant'a ait olmayan entity'ler, bu turun dışında. (`TarbilSyncLog`'un aslında tenant başına olması gerekip gerekmediği ayrı bir soru — bu turda dokunulmuyor, not düşülüyor.)
- **Diğer olası platform-admin/arka-plan erişim yolları** — `TenantAdminPortAdapter` ve `AppointmentReminderScheduler` doğrulanmış 2 örnek; implementasyon sırasında benzer bir "tenantId parametre olarak var ama TenantContext yok" deseni başka yerde bulunursa aynı köprüleme kuralı uygulanır (§5).

## 3. Hibernate `@TenantId` Mekanizması

`platform/tenancy/TenantContextIdentifierResolver.java` (yeni):

```java
package com.vetos.platform.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hibernate'in her @TenantId'li entity sorgusundan once cagirdigi resolver.
 * TenantContext.current() bos ise (arka plan is, platform admin, herkese
 * acik uc nokta) IllegalStateException firlatir -- bu KASITLI: sessizce
 * tum kiracilari donmek yerine gurultulu basarisizlik tercih edilir.
 * Bu durumla normal karsilasacak kod (AppointmentReminderScheduler,
 * TenantAdminPortAdapter) TenantContext'i elle kurar (bkz. tasarim
 * dokumaninin "koprulme kurali" bolumu).
 */
@Component
public class TenantContextIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.current();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
```

Hibernate'e bağlayan config (`platform/tenancy/TenancyHibernateConfig.java`):
```java
@Configuration
class TenancyHibernateConfig {
    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(TenantContextIdentifierResolver resolver) {
        return props -> props.put(org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
```

**Not (implementasyon sırasında doğrulanacak):** Hibernate 6.6'nın tam yapılandırma detayları (yukarıdaki property adının/sabitinin tam karşılığı, `@TenantId` için ek bir `hibernate.multiTenancy` ayarına ihtiyaç olup olmadığı) resmi Hibernate 6.6 referans dokümantasyonuyla teyit edilmeli — bu belge YAKLAŞIMI onaylıyor, konfigürasyon satırlarının birebir doğruluğunu garanti etmiyor. Bu yüzden §6'daki kiracılar-arası entegrasyon testi bu turun **gerçek kabul kriteri**: konfigürasyon yanlışsa test kırmızı çıkar, prosa güvenilmez.

Her `@TenantId`'li entity'de tek satır değişiklik:
```java
@org.hibernate.annotations.TenantId
@Column(name = "tenant_id", nullable = false, updatable = false)
private UUID tenantId;
```

## 4. Entity Kapsamı ve Katmanlı Geri-Doldurma Sırası

16 entity, bağımlılık sırasına göre 3 katman. Migration'lar bu sırayla (sıradaki Flyway V-numarasından başlayarak, implementasyon anında atanır) numaralandırılmalı — Kat 1, Kat 0'ın backfill'i tamamlanmadan çalışamaz.

| Kat | Entity | `tenant_id` kaynağı |
|---|---|---|
| 0 | `Patient` | `owners.tenant_id` (via `owner_id`) |
| 0 | `StaffUser` | `branches.tenant_id` (via `branch_id`) |
| 0 | `InventoryItem` | `branches.tenant_id` (via `branch_id`) |
| 0 | `CashRegisterSession` | `branches.tenant_id` (via `branch_id`) |
| 0 | `InvoiceLine` | `invoices.tenant_id` (via `invoice_id`) |
| 0 | `Payment` | `invoices.tenant_id` (via `invoice_id`) |
| 0 | `ConsentRecord` | `owners.tenant_id` (via `owner_id`) |
| 0 | `ImagingRecordFile` | `imaging_records.tenant_id` (via `imaging_record_id`) |
| 0 | `LabResultFile` | `lab_results.tenant_id` (via `lab_result_id`) |
| 0 | `LabResultItem` | `lab_results.tenant_id` (via `lab_result_id`) |
| 0 | `AiJobDecision` | `ai_jobs.tenant_id` (via `ai_job_id`) |
| 1 | `Encounter` | `patients.tenant_id` (via `patient_id`) — Kat 0'daki `Patient` migration'ından sonra |
| 1 | `Prescription` | `patients.tenant_id` (via `patient_id`, `Prescription` zaten `patient_id`'yi doğrudan taşıyor — `Encounter` ara adımına gerek yok) |
| 1 | `StockMovement` | `inventory_items.tenant_id` (via `inventory_item_id`) — Kat 0'daki `InventoryItem`'dan sonra |
| 2 | `EncounterInventoryUsage` | `encounters.tenant_id` (via `encounter_id`) — Kat 1'deki `Encounter`'dan sonra |
| 2 | `PrescriptionItem` | `prescriptions.tenant_id` (via `prescription_id`) — Kat 1'deki `Prescription`'dan sonra |

Her migration şablonu (örnek, `Patient` için):
```sql
ALTER TABLE patients ADD COLUMN tenant_id UUID;
UPDATE patients p SET tenant_id = o.tenant_id FROM owners o WHERE p.owner_id = o.id;
ALTER TABLE patients ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_patients_tenant_id ON patients (tenant_id);
```

## 5. TenantContext Köprüleme Kuralı

**Kural:** Bir kod parçası `tenantId`'yi zaten elinde (metot parametresi olarak) taşıyorsa ama normal bir kimlik-doğrulanmış HTTP isteği içinde çalışmıyorsa (arka plan işi, platform admin, batch iş), `@TenantId`'li bir entity'ye dokunmadan hemen önce `TenantContext.set(tenantId)` yapmalı, iş bitince `finally` içinde `TenantContext.clear()` çağırmalıdır. Aksi halde `TenantContextIdentifierResolver` `IllegalStateException` fırlatır.

**Doğrulanmış 2 yer:**

1. **`AppointmentReminderScheduler.sendTomorrowReminders()`** — tenant listesi üzerinde dönüyor, her biri için `sendAppointmentRemindersUseCase.execute(tenantId, ...)` çağırıyor (bu use-case artık `@TenantId`'li `Encounter`'a dokunmasa da, ilerideki `Patient`/`Owner` erişimleri için gerekli). Döngü şöyle olmalı:
   ```java
   for (UUID tenantId : tenantLookupPort.findActiveTenantIds()) {
       TenantContext.set(tenantId);
       try {
           int queued = sendAppointmentRemindersUseCase.execute(tenantId, tomorrowStart, tomorrowEnd);
           ...
       } finally {
           TenantContext.clear();
       }
   }
   ```
   (Bölüm 4'teki Bildirim Teslim Sertleştirme dokümanındaki advisory-lock değişikliğiyle **birlikte** uygulanmalı — aynı metodu iki ayrı sebeple değiştiriyoruz, çakışmamaları için implementasyon planında tek görev olarak ele alınmalı.)

2. **`TenantAdminPortAdapter`** — `findBillingContact(tenantId)` (→ `StaffUser` okur) ve tenant oluşturma akışındaki `StaffUser.register(...)` (→ `StaffUser` yazar) çağrıları, platform admin isteğinden geliyor, `TenantContext` kurulu değil. Her ikisi de zaten `tenantId`'yi parametre olarak alıyor — aynı `set/try/finally/clear` deseni bu iki metoda da eklenmeli.

**İmplementasyon sırasında yapılacak tarama:** Yukarıdaki 2 yer doğrulandı; `TenantAdminPort`'un diğer metotları ve varsa başka cross-tenant portlar (`grep -rn "TenantContext.set"` zaten var olanları, `grep -rln "tenantId" application/*.java` parametre alan ama controller'dan çağrılmayanları bulmak için) aynı mantıkla taranmalı.

## 6. `NotificationLog` İstisnası

`NotificationLog` **bilinçli olarak `@TenantId` kapsamı dışında bırakılıyor** — ona giden 2 erişim yolu:

- **`NotificationSendExecutor.attemptSend(logId)`** — sadece uygulama içinden, kendi ürettiğimiz ID'lerle çağrılıyor (`@Async`, farklı thread, `TenantContext` zaten taşınmıyor). Saldırgan bu ID'yi hiç seçemiyor, kontrole gerek yok.
- **`RetryNotificationUseCase.execute(logId)`** ← `POST /notifications/logs/{id}/retry` — **tek gerçek risk burası**, `id` istemciden geliyor ve hiç kontrol edilmiyor.

Düzeltme (elle, `@TenantId`'den bağımsız):
```java
// RetryNotificationUseCase — imza degisiyor
@Transactional
public void execute(UUID tenantId, UUID logId) {
    NotificationLog log = notificationLogRepository.findById(logId)
        .filter(l -> l.getTenantId().equals(tenantId))
        .orElseThrow(() -> new NotificationLogNotFoundException(logId));
    ...
}
```
```java
// NotificationsController
@PostMapping("/logs/{id}/retry")
public void retry(@PathVariable UUID id) {
    retryNotificationUseCase.execute(TenantContext.current(), id);
}
```
Başka bir kiracının kaydı, mevcut `NotificationLogNotFoundException` (404) ile "yok" gibi görünür — var olduğu bile sızdırılmaz.

## 7. Native Sorgular

`@TenantId`, yalnızca JPQL/Criteria/türetilmiş (`findByXxx`) sorgulara uygulanır — `@Query(nativeQuery = true)` ile yazılmış sorgular bunun **dışında kalır**, otomatik filtrelenmez. Bugüne kadar kod tabanındaki native sorgular (`OwnerJpaRepository.findAnonymousPlaceholder`) zaten elle `tenantId` alıyor — bu turda dokunulmuyor. Bildirim Teslim Sertleştirme dokümanındaki yeni `claimDueForRetry` native sorgusu **bilinçli olarak kiracılar arası** (tüm kiracıların gecikmiş retry'larını tek seferde toplar) — `NotificationLog`'un zaten `@TenantId` dışında bırakılmasıyla tutarlı, ekstra bir işlem gerekmiyor.

**İmplementasyon sırasında yapılacak tarama:** Kod tabanındaki tüm `nativeQuery = true` kullanımları listelenip her birinin ya (a) zaten elle `tenantId` filtrelediği ya da (b) `claimDueForRetry` gibi bilinçli olarak kiracılar arası olduğu doğrulanmalı.

## 8. Test Stratejisi

**Asıl güvence — kiracılar arası izolasyon entegrasyon testi** (yeni, örn. `TenantIsolationTest`, gerçek Postgres'e karşı — bu kod tabanında `@DataJpaTest` yok ama bu test, doğası gereği gerçek bir veritabanı bağlantısı ve Hibernate session'ı gerektiriyor, bu yüzden mevcut "repository/entity için ayrı test yazılmayan" kuralının **bilinçli bir istisnası**):
- 2 ayrı tenant (A, B) ve her biri için 16 entity'den birer örnek oluşturulur.
- Her entity tipi için: `TenantContext.set(tenantA)` iken B'nin kaydını `findById` ile çekmeye çalış → boş/`Optional.empty()` (ya da JPQL `findByTenantId` çağrısında B'nin kaydının hiç görünmediği) doğrulanır.
- Bu, prosa/konfigürasyon detaylarının doğruluğuna güvenmek yerine **gerçek davranışı** doğrulayan tek yer — implementasyon planında bu test paketinin YEŞİL olması, tüm 16 entity için "tamamlandı" kabul kriteri.

**Diğer testler:**
- `RetryNotificationUseCase` — başka bir tenant'ın `logId`'siyle çağrıldığında `NotificationLogNotFoundException` fırlattığını doğrulayan birim test (mock repository).
- `AppointmentReminderScheduler` / `TenantAdminPortAdapter` köprüleme — `TenantContext.current()`'ın doğru değerle set edildiğini ve `finally`'de temizlendiğini doğrulayan birim test (spy/mock ile, gerçek Hibernate olmadan).
- Mevcut `ApplicationModulesTest` — yeni `platform/tenancy` sınıflarının modül sınırlarını bozmadığının doğrulanması.

## 9. Açık Sorular

Yok — tasarım kullanıcı onayından geçti. İmplementasyon sırasında doğrulanacak/taranacak maddeler (Hibernate config detayları, diğer cross-tenant erişim yolları, tüm native sorgu listesi) yukarıda ilgili bölümlerde açıkça işaretlendi.
