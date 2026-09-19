# Kiracı İzolasyonu Sertleştirme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 44 JPA entity'sinden 27'sinde `tenantId` alanı yok; bunların çoğu `findById(id)` ile hiçbir kiracı kontrolü olmadan okunuyor — doğrulanmış, çalışan bir güvenlik açığı. Bu tur, 16 entity'ye `tenant_id` kolonu + Hibernate 6.6'nın yerleşik `@TenantId` anotasyonunu ekleyerek JPQL/Criteria/türetilmiş sorgulara (ve `findById`'a) **otomatik** kiracı filtresi koyar; gerçek güvence, 16 entity'nin hepsi için kiracılar arası sızıntının olmadığını gerçek bir Postgres'e karşı kanıtlayan `TenantIsolationTest` paketidir.

**Architecture:** Yeni `platform/tenancy/TenantContextIdentifierResolver`, Hibernate'in her Session açılışında çağırdığı `CurrentTenantIdentifierResolver`'ı mevcut `TenantContext` (request-scoped `ThreadLocal`, `JwtAuthenticationFilter` tarafından kurulur) üzerine bağlar. `TenancyHibernateConfig`, resolver'ı `HibernatePropertiesCustomizer` ile Hibernate'e verir. Her entity'ye tek satırlık `@org.hibernate.annotations.TenantId` alanı eklenir; kolon, üst (zaten kiracı-kapsamlı) kayıttan Flyway migration'ıyla geriye doldurulur. Normal bir kimlik-doğrulanmış HTTP isteği dışında çalışan ama `tenantId`'yi zaten parametre olarak taşıyan kod (`AppointmentReminderScheduler`, `TenantAdminPortAdapter`) "köprüleme kuralı"yla `TenantContext`'i geçici kurar. `NotificationLog` bilinçli olarak kapsam dışı bırakılır ve tek gerçek risk taşıyan erişim yoluna (`RetryNotificationUseCase`) elle tenant kontrolü konur.

**Tech Stack:** Spring Boot 3.5.16 / Java 21, Hibernate ORM 6.6.53.Final, PostgreSQL 16 + Flyway, Spring Modulith 1.4.12, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-17-kiraci-izolasyonu-sertlestirme-design.md`

## Global Constraints

- **Modül sınırları:** Modüller arası erişim SADECE hedef modülün `domain` paketindeki `*Port`/`*LookupPort` arayüzleri üzerinden yapılır. `com.vetos.platform.tenancy` paketi `@NamedInterface("tenancy")` ile işaretlidir ve her modülden import edilebilir (bkz. `platform/tenancy/package-info.java`). Her task sonunda `ApplicationModulesTest` çalıştırılır.
- **Entity anotasyonu (spec §3, birebir):** Her `@TenantId`'li entity'de tek alan eklenir ve — mevcut 17 kiracı-kapsamlı entity'nin (`Owner`, `Invoice`, `Branch`, `LabResult`, `ImagingRecord`, `NotificationLog`...) hepsinde olduğu gibi — **`id` alanının hemen altına** yerleştirilir:
  ```java
  @org.hibernate.annotations.TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;
  ```
- **Factory konvansiyonu:** Mevcut kiracı-kapsamlı entity'lerin statik factory metotlarının HEPSİ `tenantId`'yi **ilk parametre** olarak alır (`Owner.register(tenantId, ...)`, `Invoice.createDraft(tenantId, ...)`, `LabResult.request(tenantId, ...)`, `AiJob.create(tenantId, ...)`). Yeni `@TenantId`'li entity'lerin factory'leri de aynı şekilde `tenantId`'yi yeni ilk parametre olarak alır; **her mevcut çağrı yeri** (`application` katmanı + mevcut birim testleri) güncellenir.
- **Hibernate `TenantIdGeneration` kuralı (doğrulandı — `org.hibernate.generator.internal.TenantIdGeneration`):** `@TenantId` alanına elle atanan değer, Session'ın tenant identifier'ından FARKLIYSA persist sırasında `PropertyValueException("assigned tenant id differs from current tenant id")` fırlatılır. Bu yüzden factory'ye geçilen `tenantId`, o anda `TenantContext.current()` ile **aynı** olmak zorundadır. Çağrı yerlerinde ya `TenantContext.current()` ya da zaten elde olan ve ona eşit bir değer (`owner.getTenantId()`, `invoice.getTenantId()`, `command.tenantId()`) kullanılır.
- **Migration şablonu (spec §4):** Her entity için, sıradaki Flyway numarasıyla:
  ```sql
  ALTER TABLE <table> ADD COLUMN tenant_id UUID;
  UPDATE <table> t SET tenant_id = <parent>.tenant_id FROM <parent_table> <parent> WHERE t.<fk_column> = <parent>.id;
  ALTER TABLE <table> ALTER COLUMN tenant_id SET NOT NULL;
  CREATE INDEX idx_<table>_tenant_id ON <table> (tenant_id);
  ```
  Şu an en yüksek migration: **`V35__owner_anonymous_placeholder.sql`**. Bu planda atanan numaralar: Task 1 → `V36`, `V37`; Task 2 → `V38`–`V40`; Task 3 → `V41`, `V42`; Task 4 → `V43`–`V46`; Task 5 → `V47`–`V49`; Task 6 → `V50`, `V51`. Kat 1 (Task 5) migration'ları Kat 0'dan (Task 1/2), Kat 2 (Task 6) Kat 1'den SONRA numaralanmak zorunda — geriye doldurma kaynağı bir önceki katın kolonudur.
- **Köprüleme kuralı (spec §5):** Bir kod parçası `tenantId`'yi parametre olarak taşıyor ama kimlik-doğrulanmış bir HTTP isteği içinde çalışmıyorsa (arka plan işi, platform admin, batch), `@TenantId`'li bir entity'ye dokunmadan hemen önce `TenantContext.set(tenantId)` yapar, iş bitince `finally` içinde `TenantContext.clear()` çağırır. Doğrulanmış 2 yer: `AppointmentReminderScheduler.sendTomorrowReminders()` ve `TenantAdminPortAdapter` (`findBillingContact` + tenant oluşturma akışındaki `StaffUser.register(...)`).
- **`NotificationLog` istisnası (spec §6):** `NotificationLog` bilinçli olarak `@TenantId` DIŞINDA bırakılır — ona giden 2 yoldan biri (`NotificationSendExecutor.attemptSend(logId)`) `@Async`, farklı thread, kendi ürettiğimiz ID'lerle çağrılıyor (saldırgan ID'yi seçemiyor); diğeri (`RetryNotificationUseCase.execute(logId)` ← `POST /notifications/logs/{id}/retry`) tek gerçek risk ve **elle** tenant kontrolüyle kapatılır. Başka kiracının kaydı mevcut `NotificationLogNotFoundException` (404) ile "yok" gibi görünür — var olduğu bile sızdırılmaz.
- **Native sorgular (spec §7 taraması TAMAMLANDI):** Kod tabanındaki tüm `nativeQuery = true` kullanımları — `OwnerJpaRepository` (3 adet), `PatientJpaRepository` (2 adet), `TarbilSyncLogJpaRepository` (1 adet) — **hepsi zaten elle `o.tenant_id = :tenantId` filtreliyor**. `@TenantId` native sorgulara uygulanmaz; bu turda hiçbirine dokunulmaz.
- **Kapsam dışı (spec §2):** Postgres Row-Level Security; `Breed`/`Species`/`Plan`/`DrugCatalog` gibi paylaşımlı referans verisi; `Tenant`, `PlatformAdminUser`, `PlatformPayment`, `TenantSignupRequest`, `BranchWorkingHours`, `StaffShiftTemplate`, `TarbilSyncLog`.
- **Test istisnası (spec §8):** Bu kod tabanında `@DataJpaTest`/`@SpringBootTest` YOK; tüm testler saf Mockito birim testi. `TenantIsolationTest`, doğası gereği gerçek bir veritabanı bağlantısı ve Hibernate Session'ı gerektirdiği için bu kuralın **bilinçli ve dar** bir istisnasıdır. Testcontainers bağımlılıkları `pom.xml`'de var ama kullanılmıyor — **bu turda da kullanılmayacak**. Test, `application.yml`'in zaten varsayılan olarak işaret ettiği datasource'a (`DB_HOST:localhost`, `DB_PORT:5433`, `DB_NAME:myvet`, `DB_USER:myvet`, `DB_PASSWORD:myvet` — yani `backend/docker-compose.yml`'deki Postgres) bağlanır; CI de (`.github/workflows/ci.yml`) tam olarak bu amaçla aynı ayarlarla bir Postgres servis konteyneri sağlıyor.

### Hibernate 6.6 doğrulaması (spec §3'ün "implementasyon sırasında doğrulanacak" notunun cevabı)

Spec §3, konfigürasyon satırlarının birebir doğruluğunu garanti etmediğini açıkça söylüyor. Hibernate 6.6.53.Final bytecode'u üzerinden doğrulanan 4 gerçek — planın tasarımını bunlar belirliyor:

1. **`AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER` sabiti mevcut** (`org.hibernate.cfg.MultiTenancySettings`'ten miras, değeri `"hibernate.tenant_identifier_resolver"`). Spec §3'teki config satırı derlenir ve doğrudur.
2. **Spring Boot 3.5.16 bir `CurrentTenantIdentifierResolver` bean'ini otomatik kaydetmiyor** (`HibernateJpaConfiguration` constructor'ında böyle bir `ObjectProvider` yok) — yani `TenancyHibernateConfig`'teki `HibernatePropertiesCustomizer` **zorunlu**.
3. **`applyToLoadById = true`** — `TenantIdBinder`, `_tenantId` filtresini `new FilterDefinition("_tenantId", "", …, false, true)` ile kaydediyor; son parametre `applyToLoadById`. Yani filtre `findById`'a da uygulanır. Bu turun temel güvenlik iddiası doğrulanmıştır.
4. **Resolver HER Session açılışında çağrılır** — `SessionFactoryImpl$SessionBuilderImpl` constructor'ı, hangi entity'ye dokunulacağından bağımsız olarak `resolveCurrentTenantIdentifier()`'ı çağırır. Devamında `AbstractSharedSessionContract.setUpMultitenancy()` şunu yapar:
   ```java
   if (factory.getDefinedFilterNames().contains("_tenantId")) {
       Object tenantId = getTenantIdentifierValue();
       if (tenantId == null) throw new HibernateException("SessionFactory configured for multi-tenancy, but no tenant identifier specified");
       if (resolver == null || !resolver.isRoot(tenantId)) influencers.enableFilter("_tenantId").setParameter("tenantId", tenantId);
   }
   ```

**Sonuç ve bu planın tek bilinçli sapması:** Spec §3'teki resolver, `TenantContext` boşken `IllegalStateException` fırlatıyor. (4) nedeniyle bu, `@TenantId`'li ilk entity eklendiği anda **uygulamanın tamamını kilitler**: `/api/v1/auth/login` (`LoginUseCase.findByEmail` — tenant henüz bilinmiyor, köprüleme YAPILAMAZ), tüm `/api/v1/public/**` uçları, ayrı filtre zincirindeki tüm platform-admin uçları, `@Scheduled` işler, `@Async` `NotificationSendExecutor` ve açılıştaki platform-admin bootstrap. Spec'in kendi tercihi ("sessizce tüm kiracıları dönmek yerine gürültülü başarısızlık") Hibernate'in bu davranışı karşısında uygulanabilir değil.

Bu yüzden Task 1, spec §3'ün kodunu **önce birebir yazar**, davranışı **gerçekten doğrular** (spec'in istediği şey budur), sonra Hibernate'in kendi mekanizmasıyla düzeltir: `isRoot(...)`. `TenantContext` boşken resolver, `ROOT_TENANT_ID` sentinel'ini döner ve `isRoot` bunu `true` işaretler → `_tenantId` filtresi o Session'da **etkinleştirilmez**. Bu, TenantContext'siz yolların bugünkü davranışını (filtresiz) **aynen korur**; kimlik-doğrulanmış her istek (yani ispatlanmış 2 açığın da bulunduğu yüzey) otomatik filtreli hale gelir. Kayıt (persist) tarafında da tutarlıdır: root Session'da `TenantIdGeneration`, entity'ye elle atanmış `tenantId` değerini olduğu gibi kullanır — factory konvansiyonu bu değeri zaten garanti eder.

---

## Task 1: Hibernate resolver + config + `Patient` + `ConsentRecord` + `TenantIsolationTest` deseni

**Files:**
- Create: `backend/src/main/resources/db/migration/V36__patients_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V37__consent_records_tenant_id.sql`
- Create: `backend/src/main/java/com/vetos/platform/tenancy/TenantContextIdentifierResolver.java`
- Create: `backend/src/main/java/com/vetos/platform/tenancy/TenancyHibernateConfig.java`
- Modify: `backend/src/main/java/com/vetos/platform/tenancy/TenantContext.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/Patient.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/ConsentRecord.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/application/RegisterPatientUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/application/RecordConsentUseCase.java`
- Test: `backend/src/test/java/com/vetos/TenantScopedTestSupport.java` (yeni dosya)
- Test: `backend/src/test/java/com/vetos/TenantIsolationTest.java` (yeni dosya)

**Interfaces:**
- Produces: `TenantContextIdentifierResolver` (+ `public static final UUID ROOT_TENANT_ID`), `TenancyHibernateConfig`, `TenantContext.currentOrNull(): UUID`
- Produces: `TenantScopedTestSupport.asTenant(UUID, Supplier<T>): T` / `asTenantVoid(UUID, Runnable)` — Task 2-6 bunu extend eder
- Produces: `TenantIsolationTest` + içindeki `TenantFixture` / `createTenantFixture(String)` / `purgeTenant(UUID)` — Task 2-6 bunları genişletir
- Produces: `Patient.register(UUID tenantId, UUID ownerId, UUID speciesId, UUID breedId, String name, Sex sex, LocalDate birthDate)`
- Produces: `ConsentRecord.grant(UUID tenantId, UUID ownerId, ConsentType consentType, String ipAddress)`

- [ ] **Step 1: `V36` migration'ını oluştur**

`backend/src/main/resources/db/migration/V36__patients_tenant_id.sql`:
```sql
-- Kat 0: patients.tenant_id, owners.tenant_id'den (owner_id uzerinden) geriye doldurulur.
-- Hibernate @TenantId ile otomatik kiraci filtresi icin -- bkz.
-- docs/superpowers/specs/2026-09-17-kiraci-izolasyonu-sertlestirme-design.md S4.
ALTER TABLE patients ADD COLUMN tenant_id UUID;
UPDATE patients t SET tenant_id = o.tenant_id FROM owners o WHERE t.owner_id = o.id;
ALTER TABLE patients ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_patients_tenant_id ON patients (tenant_id);
```

- [ ] **Step 2: `V37` migration'ını oluştur**

`backend/src/main/resources/db/migration/V37__consent_records_tenant_id.sql`:
```sql
-- Kat 0: consent_records.tenant_id, owners.tenant_id'den (owner_id uzerinden) geriye doldurulur.
ALTER TABLE consent_records ADD COLUMN tenant_id UUID;
UPDATE consent_records t SET tenant_id = o.tenant_id FROM owners o WHERE t.owner_id = o.id;
ALTER TABLE consent_records ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_consent_records_tenant_id ON consent_records (tenant_id);
```

- [ ] **Step 3: `TenantContext`'e `currentOrNull()` ekle**

`backend/src/main/java/com/vetos/platform/tenancy/TenantContext.java` — mevcut `current()` metodunun hemen altına ekle (mevcut `current()` ve `set()`/`clear()` DEĞİŞMEZ):
```java
    /**
     * current() ile ayni degeri doner ama context bossa firlatmak yerine null
     * doner. SADECE "kiraci var mi?" sorusunu sormasi gereken altyapi kodu
     * icin (TenantContextIdentifierResolver, koprulme birim testleri) --
     * use-case katmani her zaman current() kullanmalidir.
     */
    public static UUID currentOrNull() {
        return CURRENT_TENANT.get();
    }
```

- [ ] **Step 4: Resolver'ı spec §3'teki haliyle BİREBİR oluştur**

`backend/src/main/java/com/vetos/platform/tenancy/TenantContextIdentifierResolver.java`:
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

- [ ] **Step 5: `TenancyHibernateConfig`'i spec §3'teki haliyle oluştur**

`backend/src/main/java/com/vetos/platform/tenancy/TenancyHibernateConfig.java`:
```java
package com.vetos.platform.tenancy;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resolver'i Hibernate'e baglar. Spring Boot 3.5 bir
 * CurrentTenantIdentifierResolver bean'ini KENDILIGINDEN Hibernate'e
 * gecirmez (HibernateJpaConfiguration'da boyle bir ObjectProvider yok) --
 * bu customizer olmadan @TenantId filtresi hic devreye girmez.
 * @TenantId (discriminator tabanli multi-tenancy) icin ayrica bir
 * hibernate.multiTenancy ayari GEREKMEZ; o ayar yalnizca ayri
 * sema/veritabani modlari (MultiTenantConnectionProvider) icindir.
 */
@Configuration
class TenancyHibernateConfig {

    @Bean
    HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(TenantContextIdentifierResolver resolver) {
        return props -> props.put(org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
```

- [ ] **Step 6: `Patient`'a `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/patient/domain/Patient.java` — `id` alanının hemen altına yeni alanı ekle (mevcut 17 kiracı-kapsamlı entity'deki yerleşim konvansiyonu):
```java
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
```

Aynı dosyadaki `register(...)` factory'sini güncelle:
```java
    public static Patient register(
        UUID tenantId, UUID ownerId, UUID speciesId, UUID breedId, String name, Sex sex, LocalDate birthDate
    ) {
        Patient patient = new Patient();
        patient.tenantId = tenantId;
        patient.ownerId = ownerId;
        patient.speciesId = speciesId;
        patient.breedId = breedId;
        patient.name = name;
        patient.sex = sex;
        patient.birthDate = birthDate;
        patient.neutered = false;
        patient.status = PatientStatus.ACTIVE;
        patient.createdAt = Instant.now();
        return patient;
    }
```

- [ ] **Step 7: `ConsentRecord`'a `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/patient/domain/ConsentRecord.java` — `id` alanının hemen altına:
```java
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
```

`grant(...)` factory'si:
```java
    public static ConsentRecord grant(UUID tenantId, UUID ownerId, ConsentType consentType, String ipAddress) {
        ConsentRecord record = new ConsentRecord();
        record.tenantId = tenantId;
        record.ownerId = ownerId;
        record.consentType = consentType;
        record.granted = true;
        record.ipAddress = ipAddress;
        record.grantedAt = Instant.now();
        return record;
    }
```

- [ ] **Step 8: `Patient.register` çağrı yerini güncelle (1 yer)**

`backend/src/main/java/com/vetos/modules/patient/application/RegisterPatientUseCase.java` — `owner` zaten elde, `owner.getTenantId()` kullanılır (bu, `TenantContext.current()` ile aynı değerdir; `Owner` mevcut kiracı-kapsamlı entity'lerden):
```java
        Patient patient = Patient.register(
            owner.getTenantId(), owner.getId(), command.speciesId(), command.breedId(),
            command.name(), command.sex(), command.birthDate()
        );
```

- [ ] **Step 9: `ConsentRecord.grant` çağrı yerini güncelle (1 yer)**

`backend/src/main/java/com/vetos/modules/patient/application/RecordConsentUseCase.java` — `execute` metodu, `findById` sonucunu şu an ATIYOR; `owner` değişkenine alıp `getTenantId()` kullan:
```java
    @Transactional
    public UUID execute(RecordConsentCommand command) {
        Owner owner = ownerRepository.findById(command.ownerId())
            .orElseThrow(() -> new OwnerNotFoundException(command.ownerId()));

        ConsentRecord record = ConsentRecord.grant(
            owner.getTenantId(), command.ownerId(), command.consentType(), command.ipAddress()
        );
        return consentRecordRepository.save(record).getId();
    }
```

(Dosyanın üstünde `import com.vetos.modules.patient.domain.Owner;` yoksa ekle.)

- [ ] **Step 10: `TenantScopedTestSupport` temel test sınıfını oluştur**

`backend/src/test/java/com/vetos/TenantScopedTestSupport.java`:
```java
package com.vetos;

import com.vetos.platform.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * TenantIsolationTest ve gelecekteki benzer testler icin: her "X kiracisi
 * olarak" islemi kendi TAZE Hibernate Session'inda calistirir (REQUIRES_NEW).
 *
 * NEDEN BOYLE: Hibernate'in CurrentTenantIdentifierResolver'i,
 * SessionFactoryImpl$SessionBuilderImpl constructor'inda -- yani Session
 * acilirken -- BIR KEZ cagrilir ve degeri o Session/transaction boyunca
 * sabitlenir. Bu yuzden test metoduna @Transactional koyup metodun ortasinda
 * TenantContext'i degistirmek Hibernate'in tenant identifier'i YENIDEN
 * okumasini SAGLAMAZ; test yanlislikla yesil (ya da anlamsiz) cikar.
 * Cozum: her farkli kiracidan islem, TenantContext o transaction
 * BASLAMADAN HEMEN ONCE set edilerek, kendi REQUIRES_NEW transaction'inda
 * calisir. Bunun dogal sonucu, test metotlarinin @Transactional
 * auto-rollback'ine GUVENEMEMESIdir -- temizlik elle yapilir
 * (bkz. TenantIsolationTest.purgeTenant).
 *
 * Alternatif olarak @Autowired EntityManagerFactory ile elle
 * createEntityManager/begin/commit yapilabilirdi; TransactionTemplate
 * tercih edildi cunku Spring Data repository'leri (testin dogruladigi
 * gercek uretim yolu) transaction'a boylece dogal olarak katiliyor ve
 * EntityManager yasam dongusunu elle yonetmek gerekmiyor.
 */
abstract class TenantScopedTestSupport {

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Verilen kiraci kimligiyle, TAZE bir Session/transaction icinde calistirir. */
    protected <T> T asTenant(UUID tenantId, Supplier<T> work) {
        TenantContext.set(tenantId);
        try {
            return newTransaction().execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    protected void asTenantVoid(UUID tenantId, Runnable work) {
        asTenant(tenantId, () -> {
            work.run();
            return null;
        });
    }

    /**
     * TenantContext KURULMADAN, taze bir transaction icinde calistirir --
     * login, /api/v1/public/**, platform admin ve @Scheduled islerin
     * gercek durumunu taklit eder (root Session, _tenantId filtresi kapali).
     */
    protected <T> T inRootSession(Supplier<T> work) {
        TenantContext.clear();
        return newTransaction().execute(status -> work.get());
    }

    protected void inRootSessionVoid(Runnable work) {
        inRootSession(() -> {
            work.run();
            return null;
        });
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }
}
```

- [ ] **Step 11: `TenantIsolationTest`'i 2 entity + 1 root-session senaryosuyla oluştur**

`backend/src/test/java/com/vetos/TenantIsolationTest.java`:
```java
package com.vetos;

import com.vetos.modules.patient.domain.ConsentRecord;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import com.vetos.modules.patient.domain.ConsentType;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.SpeciesRepository;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bu turun GERCEK kabul kriteri (spec S8): Hibernate @TenantId
 * konfigurasyonunun prosa olarak degil, gercek davranis olarak dogru
 * oldugunu kanitlar. Her entity icin: B kiracisinin kaydi, A kiracisi
 * context'indeyken GORUNMEMELI.
 *
 * CALISTIRMA SARTI -- CANLI POSTGRES: Bu kod tabaninda baska hicbir
 * @SpringBootTest/@DataJpaTest yok (tum testler saf Mockito). Bu sinif,
 * gercek bir veritabani baglantisi ve Hibernate Session'i gerektirdigi
 * icin o kuralin BILINCLI ve DAR bir istisnasidir. Lokal calistirmadan
 * once:  cd backend && docker-compose up -d
 * Baglanti bilgisi application.yml'deki varsayilanlardan gelir
 * (localhost:5433/myvet). CI ayni ayarlarla bir Postgres servis
 * konteyneri saglar (.github/workflows/ci.yml). Testcontainers
 * BILINCLI olarak KULLANILMIYOR.
 *
 * Test metotlari @Transactional DEGIL (bkz. TenantScopedTestSupport
 * javadoc'u) -- olusturulan satirlar @AfterEach icinde elle silinir.
 */
@SpringBootTest
class TenantIsolationTest extends TenantScopedTestSupport {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ConsentRecordRepository consentRecordRepository;

    @PersistenceContext private EntityManager entityManager;

    private final List<UUID> createdTenantIds = new ArrayList<>();

    /** Bir kiraci ve onun altindaki ortak ust kayitlar. */
    private record TenantFixture(UUID tenantId, UUID branchId, UUID ownerId) {}

    private TenantFixture createTenantFixture(String label) {
        UUID tenantId = inRootSession(() -> tenantRepository.save(Tenant.register(label, null)).getId());
        createdTenantIds.add(tenantId);
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, label + " Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> ownerRepository.save(
            Owner.register(tenantId, label + " Sahip", "05551234567", null, null)
        ).getId());
        return new TenantFixture(tenantId, branchId, ownerId);
    }

    private UUID anySpeciesId() {
        // species, kiraci-bagimsiz paylasimli referans verisi (V2'de tohumlanir).
        return inRootSession(() -> speciesRepository.findAll().get(0).getId());
    }

    @AfterEach
    void purgeCreatedTenants() {
        createdTenantIds.forEach(this::purgeTenant);
        createdTenantIds.clear();
    }

    /**
     * Test satirlarini FK sirasina gore siler. Native SQL, @TenantId
     * filtresinin disindadir (spec S7) ve root Session'da calisir --
     * TenantContext kurulu degil. Tablolarin bir kismi ilerideki
     * task'lara kadar bos kalir; bos tabloya DELETE zararsizdir.
     */
    private void purgeTenant(UUID tenantId) {
        inRootSessionVoid(() -> {
            String b = "(SELECT id FROM branches WHERE tenant_id = :t)";
            String o = "(SELECT id FROM owners WHERE tenant_id = :t)";
            String p = "(SELECT id FROM patients WHERE owner_id IN " + o + ")";
            String e = "(SELECT id FROM encounters WHERE patient_id IN " + p + ")";
            String ii = "(SELECT id FROM inventory_items WHERE branch_id IN " + b + ")";
            String inv = "(SELECT id FROM invoices WHERE tenant_id = :t)";
            String lr = "(SELECT id FROM lab_results WHERE tenant_id = :t)";
            String ir = "(SELECT id FROM imaging_records WHERE tenant_id = :t)";
            String aj = "(SELECT id FROM ai_jobs WHERE tenant_id = :t)";
            String pr = "(SELECT id FROM prescriptions WHERE patient_id IN " + p + ")";

            List<String> statements = List.of(
                "DELETE FROM prescription_items WHERE prescription_id IN " + pr,
                "DELETE FROM prescriptions WHERE patient_id IN " + p,
                "DELETE FROM encounter_inventory_usage WHERE encounter_id IN " + e,
                "DELETE FROM stock_movements WHERE inventory_item_id IN " + ii,
                "DELETE FROM ai_job_decisions WHERE ai_job_id IN " + aj,
                "DELETE FROM ai_jobs WHERE tenant_id = :t",
                "DELETE FROM lab_result_items WHERE lab_result_id IN " + lr,
                "DELETE FROM lab_result_files WHERE lab_result_id IN " + lr,
                "DELETE FROM lab_results WHERE tenant_id = :t",
                "DELETE FROM imaging_record_files WHERE imaging_record_id IN " + ir,
                "DELETE FROM imaging_records WHERE tenant_id = :t",
                "DELETE FROM payments WHERE invoice_id IN " + inv,
                "DELETE FROM invoice_lines WHERE invoice_id IN " + inv,
                "DELETE FROM invoices WHERE tenant_id = :t",
                "DELETE FROM encounters WHERE patient_id IN " + p,
                "DELETE FROM patients WHERE owner_id IN " + o,
                "DELETE FROM consent_records WHERE owner_id IN " + o,
                "DELETE FROM owners WHERE tenant_id = :t",
                "DELETE FROM cash_register_sessions WHERE branch_id IN " + b,
                "DELETE FROM inventory_items WHERE branch_id IN " + b,
                "DELETE FROM staff_users WHERE branch_id IN " + b,
                "DELETE FROM branches WHERE tenant_id = :t",
                "DELETE FROM tenants WHERE id = :t"
            );
            statements.forEach(sql ->
                entityManager.createNativeQuery(sql).setParameter("t", tenantId).executeUpdate()
            );
        });
    }

    // --- Task 1 ---

    @Test
    void patient_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID speciesId = anySpeciesId();

        UUID patientBId = asTenant(b.tenantId(), () -> patientRepository.save(
            Patient.register(b.tenantId(), b.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)
        ).getId());

        Optional<Patient> seenFromTenantA = asTenant(a.tenantId(), () -> patientRepository.findById(patientBId));

        assertThat(seenFromTenantA).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> patientRepository.findById(patientBId))).isPresent();
    }

    @Test
    void consentRecord_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        UUID consentBId = asTenant(b.tenantId(), () -> consentRecordRepository.save(
            ConsentRecord.grant(b.tenantId(), b.ownerId(), ConsentType.KVKK_ACIK_RIZA, "127.0.0.1")
        ).getId());

        Optional<ConsentRecord> seenFromTenantA = asTenant(a.tenantId(), () -> consentRecordRepository.findById(consentBId));

        assertThat(seenFromTenantA).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> consentRecordRepository.findById(consentBId))).isPresent();
    }

    @Test
    void rootSession_worksAndSeesAllTenants_whenNoTenantContext() {
        // Login (/api/v1/auth/login), /api/v1/public/**, platform admin,
        // @Scheduled isler ve @Async NotificationSendExecutor TenantContext
        // OLMADAN calisir. Hibernate, @TenantId'li EN AZ BIR entity varsa HER
        // Session acilisinda bir tenant identifier ister -- bu yuzden resolver
        // bu durumda ROOT_TENANT_ID doner ve isRoot() ile _tenantId filtresini
        // devre disi birakir. Bu test o davranisi kilitler: aksi halde
        // uygulamanin tamami kilitlenir (bkz. Global Constraints).
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID speciesId = anySpeciesId();
        UUID patientBId = asTenant(b.tenantId(), () -> patientRepository.save(
            Patient.register(b.tenantId(), b.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)
        ).getId());

        Optional<Patient> seenFromRoot = inRootSession(() -> patientRepository.findById(patientBId));

        assertThat(seenFromRoot).isPresent();
    }
}
```

- [ ] **Step 12: Postgres'i başlat ve testi çalıştır — spec §3 resolver'ının davranışını DOĞRULA (RED)**

```bash
cd backend && docker-compose up -d
./mvnw -q -Dtest=TenantIsolationTest test
```
Expected: **FAIL**. `rootSession_worksAndSeesAllTenants_whenNoTenantContext` ve `@AfterEach purgeCreatedTenants` (root Session kullanan her yer), `TenantContext.current()`'tan gelen `IllegalStateException: Aktif tenant context yok - kimlik dogrulanmis bir istek disinda cagrildi` ile patlar. `createTenantFixture`'daki `inRootSession(... tenantRepository.save ...)` zaten ilk satırda patlayacağı için `patient_*`/`consentRecord_*` testleri de aynı hatayla düşer.

Bu, spec §3'ün "konfigürasyon satırlarının birebir doğruluğunu garanti etmiyor... test kırmızı çıkarsa prosaya güvenilmez" uyarısının **tam olarak gerçekleşmesidir**: resolver her Session açılışında çağrıldığı için "context yoksa fırlat" yaklaşımı login/public/platform-admin/scheduler yollarının hepsini kilitler.

- [ ] **Step 13: Resolver'ı `isRoot(...)` ile düzelt (GREEN yolu)**

`backend/src/main/java/com/vetos/platform/tenancy/TenantContextIdentifierResolver.java` — tam dosya:
```java
package com.vetos.platform.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hibernate'in @TenantId filtresini besleyen resolver.
 *
 * ONEMLI (tasarim dokumanindan SAPMA, gerekcesi asagida): tasarim dokumani
 * S3, TenantContext bos oldugunda IllegalStateException firlatmayi
 * ongoruyordu ("sessizce tum kiracilari donmek yerine gurultulu
 * basarisizlik"). Hibernate 6.6'da bu UYGULANABILIR DEGIL:
 * SessionFactoryImpl$SessionBuilderImpl constructor'i, hangi entity'ye
 * dokunulacagindan BAGIMSIZ olarak HER Session acilisinda
 * resolveCurrentTenantIdentifier() cagirir; devaminda
 * AbstractSharedSessionContract.setUpMultitenancy(), @TenantId'li en az bir
 * entity varsa null tenant identifier'i HibernateException ile reddeder.
 * Yani firlatmak; /api/v1/auth/login'i (LoginUseCase e-postadan arar, tenant
 * HENUZ BILINMIYOR -- koprulme yapilamaz), tum /api/v1/public/** uclarini,
 * ayri filtre zincirindeki platform-admin uclarini, @Scheduled isleri,
 * @Async NotificationSendExecutor'u ve acilistaki platform-admin
 * bootstrap'ini komple kilitlerdi.
 *
 * Cozum, Hibernate'in kendi mekanizmasi: context bosken ROOT_TENANT_ID
 * sentinel'i donulur ve isRoot() bunu isaretler -- Hibernate o Session'da
 * _tenantId filtresini HIC etkinlestirmez (root/superuser Session). Bu,
 * TenantContext'siz yollarin BUGUNKU davranisini (filtresiz) aynen korur;
 * kimlik-dogrulanmis her istek -- yani ispatlanmis iki acigin da bulundugu
 * yuzey -- otomatik filtreli hale gelir. Root Session'da kayit (persist)
 * da tutarlidir: TenantIdGeneration, entity'ye elle atanmis tenantId'yi
 * oldugu gibi kullanir; factory konvansiyonu (tenantId ilk parametre) bu
 * degeri her zaman garanti eder.
 *
 * Arka plan/platform-admin kodunun DOGRU davranisi yine de TenantContext'i
 * elle kurmaktir (koprulme kurali, tasarim dokumani S5) -- root Session bir
 * emniyet subabidir, izolasyondan muafiyet ruhsati degil.
 */
@Component
public class TenantContextIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /**
     * "Kiraci yok" sentinel'i. gen_random_uuid() bu degeri hicbir zaman
     * uretmez, dolayisiyla gercek bir kiraciyla cakisamaz.
     */
    public static final UUID ROOT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.currentOrNull();
        return tenantId != null ? tenantId : ROOT_TENANT_ID;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return ROOT_TENANT_ID.equals(tenantId);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
```

- [ ] **Step 14: Testi çalıştırıp geçtiğini doğrula (GREEN)**

Run: `cd backend && ./mvnw -q -Dtest=TenantIsolationTest test`
Expected: **PASS (3 test)**. `patient_isIsolatedAcrossTenants` ve `consentRecord_isIsolatedAcrossTenants` boş `Optional` döndüğünü doğrular — yani `findById` gerçekten filtreleniyor (Hibernate `_tenantId` filtresi `applyToLoadById = true`).

- [ ] **Step 15: Tüm test paketini ve modül sınırlarını doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — mevcut tüm birim testleri (`OwnerTest`, `LoginUseCaseTest`, `ApplicationModulesTest` dahil) + 3 yeni izolasyon testi. `Patient.register`/`ConsentRecord.grant` çağrı yerlerinin hiçbiri testlerde kullanılmıyor (grep ile doğrulandı), bu yüzden mevcut testlerde derleme kırılması beklenmiyor.

- [ ] **Step 16: Uygulamayı ayağa kaldırıp login akışının bozulmadığını doğrula**

```bash
cd backend && ./mvnw spring-boot:run
# baska bir terminalde:
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"<mevcut-bir-klinik-admini>","password":"<sifre>"}'
```
Expected: loglarda `Migrating schema "public" to version "36 - patients tenant id"` ve `"37 - consent records tenant id"`; login 200 + token döner (root Session çalışıyor). 401/500 dönerse Step 13 doğru uygulanmamıştır.

- [ ] **Step 17: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V36__patients_tenant_id.sql src/main/resources/db/migration/V37__consent_records_tenant_id.sql src/main/java/com/vetos/platform/tenancy/ src/main/java/com/vetos/modules/patient/domain/Patient.java src/main/java/com/vetos/modules/patient/domain/ConsentRecord.java src/main/java/com/vetos/modules/patient/application/RegisterPatientUseCase.java src/main/java/com/vetos/modules/patient/application/RecordConsentUseCase.java src/test/java/com/vetos/TenantScopedTestSupport.java src/test/java/com/vetos/TenantIsolationTest.java
git commit -m "feat: Hibernate @TenantId altyapisi + Patient/ConsentRecord kiraci izolasyonu"
```

---

## Task 2: Branch-kaynakli Kat 0 — `StaffUser` + `InventoryItem` + `CashRegisterSession`

Üçü de `branches.tenant_id`'den `branch_id` üzerinden doldurulur — aynı migration şekli, tek task'ta gruplanır.

**Files:**
- Create: `backend/src/main/resources/db/migration/V38__staff_users_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V39__inventory_items_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V40__cash_register_sessions_tenant_id.sql`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/StaffUser.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/InventoryItem.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/domain/CashRegisterSession.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/application/CreateStaffUserUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/application/AcceptStaffInviteUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/application/CreateInventoryItemUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/OpenCashRegisterUseCase.java`
- Test: `backend/src/test/java/com/vetos/TenantIsolationTest.java` (genişlet)
- Test: `backend/src/test/java/com/vetos/modules/tenant/application/LoginUseCaseTest.java` (çağrı yeri güncellemesi)
- Test: `backend/src/test/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapterTest.java` (çağrı yeri güncellemesi)

**Interfaces:**
- Consumes: `TenantScopedTestSupport`, `TenantIsolationTest.createTenantFixture`/`purgeTenant` (Task 1)
- Produces: `StaffUser.register(UUID tenantId, UUID branchId, String fullName, String email, String passwordHash, StaffRole role)`
- Produces: `InventoryItem.create(UUID tenantId, UUID branchId, String name, String category, String skuBarcode, int initialQuantity, int reorderThreshold, LocalDate expiryDate, String lotNumber, BigDecimal unitCost)`
- Produces: `CashRegisterSession.open(UUID tenantId, UUID branchId, UUID staffId, BigDecimal openingBalance, String notes)`
- Produces: `TenantFixture.staffUserId()` — Task 4/5 bunu kullanır

- [ ] **Step 1: 3 migration'ı oluştur**

`backend/src/main/resources/db/migration/V38__staff_users_tenant_id.sql`:
```sql
-- Kat 0: staff_users.tenant_id, branches.tenant_id'den (branch_id uzerinden).
ALTER TABLE staff_users ADD COLUMN tenant_id UUID;
UPDATE staff_users t SET tenant_id = b.tenant_id FROM branches b WHERE t.branch_id = b.id;
ALTER TABLE staff_users ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_staff_users_tenant_id ON staff_users (tenant_id);
```

`backend/src/main/resources/db/migration/V39__inventory_items_tenant_id.sql`:
```sql
-- Kat 0: inventory_items.tenant_id, branches.tenant_id'den (branch_id uzerinden).
ALTER TABLE inventory_items ADD COLUMN tenant_id UUID;
UPDATE inventory_items t SET tenant_id = b.tenant_id FROM branches b WHERE t.branch_id = b.id;
ALTER TABLE inventory_items ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_inventory_items_tenant_id ON inventory_items (tenant_id);
```

`backend/src/main/resources/db/migration/V40__cash_register_sessions_tenant_id.sql`:
```sql
-- Kat 0: cash_register_sessions.tenant_id, branches.tenant_id'den (branch_id uzerinden).
ALTER TABLE cash_register_sessions ADD COLUMN tenant_id UUID;
UPDATE cash_register_sessions t SET tenant_id = b.tenant_id FROM branches b WHERE t.branch_id = b.id;
ALTER TABLE cash_register_sessions ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_cash_register_sessions_tenant_id ON cash_register_sessions (tenant_id);
```

- [ ] **Step 2: `StaffUser`'a `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/tenant/domain/StaffUser.java` — `id` alanının hemen altına:
```java
    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
```
ve:
```java
    public static StaffUser register(
        UUID tenantId, UUID branchId, String fullName, String email, String passwordHash, StaffRole role
    ) {
        StaffUser staffUser = new StaffUser();
        staffUser.tenantId = tenantId;
        staffUser.branchId = branchId;
        staffUser.fullName = fullName;
        staffUser.email = email;
        staffUser.passwordHash = passwordHash;
        staffUser.role = role;
        staffUser.twoFactorEnabled = false;
        staffUser.active = true;
        staffUser.createdAt = Instant.now();
        return staffUser;
    }
```

- [ ] **Step 3: `InventoryItem`'a `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/inventory/domain/InventoryItem.java` — `id` alanının hemen altına aynı 3 satırlık alan; factory:
```java
    public static InventoryItem create(
        UUID tenantId, UUID branchId, String name, String category, String skuBarcode,
        int initialQuantity, int reorderThreshold, LocalDate expiryDate, String lotNumber, BigDecimal unitCost
    ) {
        InventoryItem item = new InventoryItem();
        item.tenantId = tenantId;
        item.branchId = branchId;
        item.name = name;
        item.category = category;
        item.skuBarcode = skuBarcode;
        item.quantityOnHand = initialQuantity;
        item.reorderThreshold = reorderThreshold;
        item.expiryDate = expiryDate;
        item.lotNumber = lotNumber;
        item.unitCost = unitCost;
        return item;
    }
```

- [ ] **Step 4: `CashRegisterSession`'a `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/billing/domain/CashRegisterSession.java` — `id` alanının hemen altına aynı 3 satırlık alan; factory:
```java
    public static CashRegisterSession open(
        UUID tenantId, UUID branchId, UUID staffId, BigDecimal openingBalance, String notes
    ) {
        CashRegisterSession session = new CashRegisterSession();
        session.tenantId = tenantId;
        session.branchId = branchId;
        session.openedByStaffId = staffId;
        session.openingBalance = openingBalance;
        session.openedAt = Instant.now();
        session.status = CashRegisterStatus.OPEN;
        session.notes = notes;
        return session;
    }
```

- [ ] **Step 5: `StaffUser.register` çağrı yerlerini güncelle (3 yer)**

1. `backend/src/main/java/com/vetos/modules/tenant/application/CreateStaffUserUseCase.java` — kimlik-doğrulanmış istek, `TenantContext.current()`:
```java
        StaffUser staffUser = StaffUser.register(
            TenantContext.current(), command.branchId(), command.fullName(), command.email(),
            passwordEncoder.encode(command.password()), command.role()
        );
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

2. `backend/src/main/java/com/vetos/modules/tenant/application/AcceptStaffInviteUseCase.java` — **herkese açık** uç (`/api/v1/public/staff-invites`), TenantContext YOK; ama davet kaydı `tenantId` taşıyor (root Session'da elle atanan değer aynen yazılır):
```java
        StaffUser staffUser = StaffUser.register(
            invite.getTenantId(), invite.getBranchId(), invite.getFullName(), invite.getEmail(),
            passwordEncoder.encode(command.password()), invite.getRole()
        );
```

3. `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java:131` (`createTenant` içinde) — şimdilik sadece parametreyi ekle; köprüleme Task 7'de gelir:
```java
        StaffUser admin = staffUserJpaRepository.save(
            StaffUser.register(tenant.getId(), branch.getId(), adminFullName, adminEmail, passwordHash, StaffRole.ADMIN)
        );
```

- [ ] **Step 6: `InventoryItem.create` ve `CashRegisterSession.open` çağrı yerlerini güncelle (2 yer)**

`backend/src/main/java/com/vetos/modules/inventory/application/CreateInventoryItemUseCase.java`:
```java
        InventoryItem item = InventoryItem.create(
            TenantContext.current(), command.branchId(), command.name(), command.category(), command.skuBarcode(),
            command.initialQuantity(), command.reorderThreshold(), command.expiryDate(),
            command.lotNumber(), command.unitCost()
        );
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

`backend/src/main/java/com/vetos/modules/billing/application/OpenCashRegisterUseCase.java`:
```java
        return cashRegisterSessionRepository.save(
            CashRegisterSession.open(TenantContext.current(), branchId, staffId, openingBalance, notes)
        ).getId();
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

- [ ] **Step 7: Mevcut birim testlerindeki çağrı yerlerini güncelle (5 yer)**

`backend/src/test/java/com/vetos/modules/tenant/application/LoginUseCaseTest.java` — 3 çağrı:
- satır ~35 (`should_throwTenantSuspendedForbiddenException_when_tenantIsSuspended`) ve ~55 (`should_succeed_when_tenantIsActive`): `tenantId` değişkeni zaten var →
  ```java
        StaffUser staffUser = StaffUser.register(tenantId, branchId, "Dr. Test", "test@example.com", "hash", StaffRole.VET);
  ```
- satır ~75 (`should_throwInvalidCredentials_when_passwordDoesNotMatch`):
  ```java
        StaffUser staffUser = StaffUser.register(UUID.randomUUID(), UUID.randomUUID(), "Dr. Test", "test@example.com", "hash", StaffRole.VET);
  ```

`backend/src/test/java/com/vetos/modules/inventory/infrastructure/StockDeductionAdapterTest.java` — 2 çağrı (satır ~35 ve ~54), ikisinde de ilk parametre olarak yeni bir `UUID.randomUUID()` eklenir:
```java
        InventoryItem item = InventoryItem.create(
            UUID.randomUUID(), UUID.randomUUID(), "Mama", "Gida", null, 2, 1, null, null, BigDecimal.TEN
        );
```
```java
        InventoryItem item = InventoryItem.create(
            UUID.randomUUID(), UUID.randomUUID(), "Mama", "Gida", null, 10, 1, null, null, BigDecimal.TEN
        );
```

- [ ] **Step 8: `TenantIsolationTest`'i genişlet**

`backend/src/test/java/com/vetos/TenantIsolationTest.java`:

1. Yeni `@Autowired` alanları ekle:
```java
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private CashRegisterSessionRepository cashRegisterSessionRepository;
```
(importlar: `com.vetos.modules.tenant.domain.StaffUserRepository`, `com.vetos.modules.tenant.domain.StaffRole`, `com.vetos.modules.tenant.domain.StaffUser`, `com.vetos.modules.inventory.domain.InventoryItem`, `com.vetos.modules.inventory.domain.InventoryItemRepository`, `com.vetos.modules.billing.domain.CashRegisterSession`, `com.vetos.modules.billing.domain.CashRegisterSessionRepository`, `java.math.BigDecimal`)

2. `TenantFixture`'a `staffUserId` ekle ve `createTenantFixture`'ı genişlet:
```java
    private record TenantFixture(UUID tenantId, UUID branchId, UUID ownerId, UUID staffUserId) {}

    private TenantFixture createTenantFixture(String label) {
        UUID tenantId = inRootSession(() -> tenantRepository.save(Tenant.register(label, null)).getId());
        createdTenantIds.add(tenantId);
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, label + " Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> ownerRepository.save(
            Owner.register(tenantId, label + " Sahip", "05551234567", null, null)
        ).getId());
        // staff_users.email GLOBAL unique -- her fixture benzersiz bir e-posta almali.
        UUID staffUserId = asTenant(tenantId, () -> staffUserRepository.save(StaffUser.register(
            tenantId, branchId, "Dr. " + label, "izolasyon-" + UUID.randomUUID() + "@test.local", "hash", StaffRole.VET
        )).getId());
        return new TenantFixture(tenantId, branchId, ownerId, staffUserId);
    }
```

3. 4 yeni test metodu ekle:
```java
    // --- Task 2 ---

    @Test
    void staffUser_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        assertThat(asTenant(a.tenantId(), () -> staffUserRepository.findById(b.staffUserId()))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> staffUserRepository.findById(b.staffUserId()))).isPresent();
    }

    @Test
    void staffUser_isVisibleToRootSession_soLoginKeepsWorking() {
        // LoginUseCase.findByEmail, tenant HENUZ BILINMEDEN calisir -- koprulme
        // imkansiz. Bu yuzden root Session'da StaffUser gorunur kalmali.
        TenantFixture b = createTenantFixture("Izolasyon B");
        String email = inRootSession(() -> staffUserRepository.findById(b.staffUserId()).orElseThrow().getEmail());

        assertThat(inRootSession(() -> staffUserRepository.findByEmail(email))).isPresent();
    }

    @Test
    void inventoryItem_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        UUID itemBId = asTenant(b.tenantId(), () -> inventoryItemRepository.save(InventoryItem.create(
            b.tenantId(), b.branchId(), "Mama", "Gida", null, 10, 1, null, null, BigDecimal.TEN
        )).getId());

        assertThat(asTenant(a.tenantId(), () -> inventoryItemRepository.findById(itemBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> inventoryItemRepository.findById(itemBId))).isPresent();
    }

    @Test
    void cashRegisterSession_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        UUID sessionBId = asTenant(b.tenantId(), () -> cashRegisterSessionRepository.save(
            CashRegisterSession.open(b.tenantId(), b.branchId(), b.staffUserId(), BigDecimal.valueOf(100), null)
        ).getId());

        assertThat(asTenant(a.tenantId(), () -> cashRegisterSessionRepository.findById(sessionBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> cashRegisterSessionRepository.findById(sessionBId))).isPresent();
    }
```

- [ ] **Step 9: Testleri çalıştır**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — `TenantIsolationTest` 7 test, `LoginUseCaseTest`/`StockDeductionAdapterTest` güncellenmiş imzalarla geçer, `ApplicationModulesTest` PASS.

- [ ] **Step 10: Login akışını uçtan uca doğrula**

```bash
cd backend && ./mvnw spring-boot:run
curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"<mevcut-bir-klinik-admini>","password":"<sifre>"}'
```
Expected: loglarda `"38 - staff users tenant id"`, `"39 - inventory items tenant id"`, `"40 - cash register sessions tenant id"` migration'ları; login 200 + token. (StaffUser artık `@TenantId`'li — bu curl, root Session'ın login için gerçekten çalıştığının canlı kanıtıdır.)

- [ ] **Step 11: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V38__staff_users_tenant_id.sql src/main/resources/db/migration/V39__inventory_items_tenant_id.sql src/main/resources/db/migration/V40__cash_register_sessions_tenant_id.sql src/main/java/com/vetos/modules/tenant src/main/java/com/vetos/modules/inventory src/main/java/com/vetos/modules/billing src/test/java/com/vetos
git commit -m "feat: StaffUser/InventoryItem/CashRegisterSession kiraci izolasyonu"
```

---

## Task 3: Invoice-kaynakli Kat 0 — `InvoiceLine` + `Payment`

**Files:**
- Create: `backend/src/main/resources/db/migration/V41__invoice_lines_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V42__payments_tenant_id.sql`
- Modify: `backend/src/main/java/com/vetos/modules/billing/domain/InvoiceLine.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/domain/Payment.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/AddInvoiceLineUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/AutoCaptureEncounterChargeUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/CreateBoardingStayInvoiceUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/RecordPaymentUseCase.java`
- Test: `backend/src/test/java/com/vetos/TenantIsolationTest.java` (genişlet)
- Test: `backend/src/test/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutorTest.java` (çağrı yeri güncellemesi)

**Interfaces:**
- Consumes: `TenantIsolationTest.createTenantFixture` (Task 2 hali — `staffUserId` dahil)
- Produces: `InvoiceLine.create(UUID tenantId, UUID invoiceId, String description, int quantity, BigDecimal unitPrice, UUID serviceTypeId, UUID inventoryItemId, InvoiceLineSource source)` (kısa overload)
- Produces: `InvoiceLine.create(UUID tenantId, UUID invoiceId, String description, int quantity, BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal vatRate, UUID serviceTypeId, UUID inventoryItemId, InvoiceLineSource source)` (uzun overload)
- Produces: `Payment.record(UUID tenantId, UUID invoiceId, PaymentMethod method, BigDecimal amount, String pspRef)`

- [ ] **Step 1: 2 migration'ı oluştur**

`backend/src/main/resources/db/migration/V41__invoice_lines_tenant_id.sql`:
```sql
-- Kat 0: invoice_lines.tenant_id, invoices.tenant_id'den (invoice_id uzerinden).
ALTER TABLE invoice_lines ADD COLUMN tenant_id UUID;
UPDATE invoice_lines t SET tenant_id = i.tenant_id FROM invoices i WHERE t.invoice_id = i.id;
ALTER TABLE invoice_lines ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_invoice_lines_tenant_id ON invoice_lines (tenant_id);
```

`backend/src/main/resources/db/migration/V42__payments_tenant_id.sql`:
```sql
-- Kat 0: payments.tenant_id, invoices.tenant_id'den (invoice_id uzerinden).
ALTER TABLE payments ADD COLUMN tenant_id UUID;
UPDATE payments t SET tenant_id = i.tenant_id FROM invoices i WHERE t.invoice_id = i.id;
ALTER TABLE payments ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_payments_tenant_id ON payments (tenant_id);
```

- [ ] **Step 2: `InvoiceLine`'a `@TenantId` alanını ve her İKİ overload'a parametreyi ekle**

`backend/src/main/java/com/vetos/modules/billing/domain/InvoiceLine.java` — `id` alanının hemen altına standart 3 satırlık alan; sonra iki factory:
```java
    public static InvoiceLine create(
        UUID tenantId, UUID invoiceId, String description, int quantity, BigDecimal unitPrice,
        UUID serviceTypeId, UUID inventoryItemId, InvoiceLineSource source
    ) {
        return create(tenantId, invoiceId, description, quantity, unitPrice, BigDecimal.ZERO, BigDecimal.ZERO, serviceTypeId, inventoryItemId, source);
    }

    public static InvoiceLine create(
        UUID tenantId, UUID invoiceId, String description, int quantity, BigDecimal unitPrice,
        BigDecimal discountAmount, BigDecimal vatRate,
        UUID serviceTypeId, UUID inventoryItemId, InvoiceLineSource source
    ) {
        InvoiceLine line = new InvoiceLine();
        line.tenantId = tenantId;
        line.invoiceId = invoiceId;
        line.description = description;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        line.discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        line.vatRate = vatRate == null ? BigDecimal.ZERO : vatRate;

        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).subtract(line.discountAmount);
        line.vatAmount = subtotal.multiply(line.vatRate).divide(BigDecimal.valueOf(100));
        line.lineTotal = subtotal.add(line.vatAmount);

        line.serviceTypeId = serviceTypeId;
        line.inventoryItemId = inventoryItemId;
        line.source = source;
        return line;
    }
```

- [ ] **Step 3: `Payment`'a `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/billing/domain/Payment.java` — `id` alanının hemen altına standart alan; factory:
```java
    public static Payment record(UUID tenantId, UUID invoiceId, PaymentMethod method, BigDecimal amount, String pspRef) {
        Payment payment = new Payment();
        payment.tenantId = tenantId;
        payment.invoiceId = invoiceId;
        payment.method = method;
        payment.amount = amount;
        payment.pspRef = pspRef;
        payment.paidAt = Instant.now();
        return payment;
    }
```

- [ ] **Step 4: `InvoiceLine.create` çağrı yerlerini güncelle (3 yer)**

Üçünde de `Invoice` nesnesi zaten elde — `invoice.getTenantId()` kullanılır (bu `TenantContext.current()` ile aynıdır ve `CreateBoardingStayInvoiceUseCase` gibi `tenantId`'yi parametre alan event akışlarında da doğrudur).

1. `backend/src/main/java/com/vetos/modules/billing/application/AddInvoiceLineUseCase.java`:
```java
        InvoiceLine line = invoiceLineRepository.save(InvoiceLine.create(
            invoice.getTenantId(), invoice.getId(), command.description(), command.quantity(), command.unitPrice(),
            command.discountAmount(), command.vatRate(),
            command.serviceTypeId(), command.inventoryItemId(), InvoiceLineSource.MANUAL
        ));
```

2. `backend/src/main/java/com/vetos/modules/billing/application/AutoCaptureEncounterChargeUseCase.java`:
```java
        invoiceLineRepository.save(InvoiceLine.create(
            invoice.getTenantId(), invoice.getId(), "Muayene ucreti (tutari guncelleyin)", 1, BigDecimal.ZERO,
            null, null, InvoiceLineSource.AUTO_CHARGE_CAPTURE
        ));
```

3. `backend/src/main/java/com/vetos/modules/billing/application/CreateBoardingStayInvoiceUseCase.java`:
```java
        InvoiceLine line = invoiceLineRepository.save(InvoiceLine.create(
            invoice.getTenantId(), invoice.getId(), "Konaklama Bedeli (" + roomLabel + ")", (int) nights, unitPrice,
            null, null, InvoiceLineSource.AUTO_CHARGE_CAPTURE
        ));
```

- [ ] **Step 5: `Payment.record` çağrı yerini güncelle (1 yer)**

`backend/src/main/java/com/vetos/modules/billing/application/RecordPaymentUseCase.java`:
```java
        Payment payment = paymentRepository.save(Payment.record(
            invoice.getTenantId(), invoice.getId(), command.method(), command.amount(), command.pspRef()
        ));
```

- [ ] **Step 6: Mevcut birim testindeki çağrı yerini güncelle (1 yer)**

`backend/src/test/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutorTest.java` (~satır 98):
```java
        InvoiceLine line = InvoiceLine.create(
            UUID.randomUUID(), invoiceId, "muayene", 1, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("20"), null, null, InvoiceLineSource.MANUAL
        );
```

- [ ] **Step 7: `TenantIsolationTest`'i genişlet**

1. Yeni `@Autowired` alanları:
```java
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceLineRepository invoiceLineRepository;
    @Autowired private PaymentRepository paymentRepository;
```
(importlar: `com.vetos.modules.billing.domain.Invoice`, `InvoiceRepository`, `InvoiceLine`, `InvoiceLineRepository`, `InvoiceLineSource`, `Payment`, `PaymentRepository`, `PaymentMethod`)

2. Fatura üreten küçük bir yardımcı + 2 test:
```java
    // --- Task 3 ---

    private UUID createInvoice(TenantFixture f) {
        return asTenant(f.tenantId(), () -> invoiceRepository.save(
            Invoice.createDraft(f.tenantId(), f.branchId(), f.ownerId(), null, f.staffUserId())
        ).getId());
    }

    @Test
    void invoiceLine_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID invoiceBId = createInvoice(b);

        asTenantVoid(b.tenantId(), () -> invoiceLineRepository.save(InvoiceLine.create(
            b.tenantId(), invoiceBId, "Muayene", 1, BigDecimal.valueOf(500), null, null, InvoiceLineSource.MANUAL
        )));

        // InvoiceLineRepository'de findById yok -- mevcut turetilmis sorgu
        // (findByInvoiceId) uzerinden dogrulanir; @TenantId turetilmis
        // sorgulara da uygulanir.
        assertThat(asTenant(a.tenantId(), () -> invoiceLineRepository.findByInvoiceId(invoiceBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> invoiceLineRepository.findByInvoiceId(invoiceBId))).hasSize(1);
    }

    @Test
    void payment_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID invoiceBId = createInvoice(b);

        asTenantVoid(b.tenantId(), () -> paymentRepository.save(Payment.record(
            b.tenantId(), invoiceBId, PaymentMethod.CASH, BigDecimal.valueOf(500), null
        )));

        assertThat(asTenant(a.tenantId(), () -> paymentRepository.findByInvoiceId(invoiceBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> paymentRepository.findByInvoiceId(invoiceBId))).hasSize(1);
    }
```

- [ ] **Step 8: Testleri çalıştır**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — `TenantIsolationTest` 9 test; `EInvoiceSubmissionExecutorTest`, `AddInvoiceLineUseCaseTest`, `CompleteQuickSaleUseCaseTest`, `ApplicationModulesTest` PASS.

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V41__invoice_lines_tenant_id.sql src/main/resources/db/migration/V42__payments_tenant_id.sql src/main/java/com/vetos/modules/billing src/test/java/com/vetos
git commit -m "feat: InvoiceLine/Payment kiraci izolasyonu"
```

---

## Task 4: Kat 0'ın tamamlanması — `ImagingRecordFile` + `LabResultFile` + `LabResultItem` + `AiJobDecision`

Dördü de zaten kiracı-kapsamlı bir üst kayıttan (`imaging_records`, `lab_results`, `ai_jobs`) doldurulur — tek migration şekli.

**Files:**
- Create: `backend/src/main/resources/db/migration/V43__imaging_record_files_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V44__lab_result_files_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V45__lab_result_items_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V46__ai_job_decisions_tenant_id.sql`
- Modify: `backend/src/main/java/com/vetos/modules/imaging/domain/ImagingRecordFile.java`
- Modify: `backend/src/main/java/com/vetos/modules/lab/domain/LabResultFile.java`
- Modify: `backend/src/main/java/com/vetos/modules/lab/domain/LabResultItem.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/domain/AiJobDecision.java`
- Modify: `backend/src/main/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/lab/application/UploadLabResultFileUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/lab/application/CompleteLabResultUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCase.java`
- Test: `backend/src/test/java/com/vetos/TenantIsolationTest.java` (genişlet)
- Test: `backend/src/test/java/com/vetos/modules/ai/domain/AiJobDecisionTest.java` (7 çağrı yeri)
- Test: `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCaseTest.java` (1 çağrı yeri)
- Test: `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobFeedbackUseCaseTest.java` (2 çağrı yeri)

**Interfaces:**
- Consumes: `TenantIsolationTest.createTenantFixture`, `Patient.register` (Task 1)
- Produces: `ImagingRecordFile.create(UUID tenantId, UUID imagingRecordId, String fileName, String contentType, byte[] content)`
- Produces: `LabResultFile.create(UUID tenantId, UUID labResultId, String fileName, String contentType, byte[] content)`
- Produces: `LabResultItem.create(UUID tenantId, UUID labResultId, String parameterName, String value, String unit, String referenceRange, LabValueFlag flag)`
- Produces: `AiJobDecision.createPending(UUID tenantId, UUID aiJobId)`

- [ ] **Step 1: 4 migration'ı oluştur**

`backend/src/main/resources/db/migration/V43__imaging_record_files_tenant_id.sql`:
```sql
-- Kat 0: imaging_record_files.tenant_id, imaging_records.tenant_id'den.
ALTER TABLE imaging_record_files ADD COLUMN tenant_id UUID;
UPDATE imaging_record_files t SET tenant_id = r.tenant_id FROM imaging_records r WHERE t.imaging_record_id = r.id;
ALTER TABLE imaging_record_files ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_imaging_record_files_tenant_id ON imaging_record_files (tenant_id);
```

`backend/src/main/resources/db/migration/V44__lab_result_files_tenant_id.sql`:
```sql
-- Kat 0: lab_result_files.tenant_id, lab_results.tenant_id'den.
ALTER TABLE lab_result_files ADD COLUMN tenant_id UUID;
UPDATE lab_result_files t SET tenant_id = r.tenant_id FROM lab_results r WHERE t.lab_result_id = r.id;
ALTER TABLE lab_result_files ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_lab_result_files_tenant_id ON lab_result_files (tenant_id);
```

`backend/src/main/resources/db/migration/V45__lab_result_items_tenant_id.sql`:
```sql
-- Kat 0: lab_result_items.tenant_id, lab_results.tenant_id'den.
ALTER TABLE lab_result_items ADD COLUMN tenant_id UUID;
UPDATE lab_result_items t SET tenant_id = r.tenant_id FROM lab_results r WHERE t.lab_result_id = r.id;
ALTER TABLE lab_result_items ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_lab_result_items_tenant_id ON lab_result_items (tenant_id);
```

`backend/src/main/resources/db/migration/V46__ai_job_decisions_tenant_id.sql`:
```sql
-- Kat 0: ai_job_decisions.tenant_id, ai_jobs.tenant_id'den.
ALTER TABLE ai_job_decisions ADD COLUMN tenant_id UUID;
UPDATE ai_job_decisions t SET tenant_id = j.tenant_id FROM ai_jobs j WHERE t.ai_job_id = j.id;
ALTER TABLE ai_job_decisions ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_ai_job_decisions_tenant_id ON ai_job_decisions (tenant_id);
```

- [ ] **Step 2: 4 entity'ye `@TenantId` alanını ve factory parametresini ekle**

Dördünde de `id` alanının hemen altına standart 3 satırlık alan; factory imzaları:

`backend/src/main/java/com/vetos/modules/imaging/domain/ImagingRecordFile.java`:
```java
    public static ImagingRecordFile create(
        UUID tenantId, UUID imagingRecordId, String fileName, String contentType, byte[] content
    ) {
        ImagingRecordFile file = new ImagingRecordFile();
        file.tenantId = tenantId;
        file.imagingRecordId = imagingRecordId;
        file.fileName = fileName;
        file.contentType = contentType;
        file.fileSize = content.length;
        file.content = content;
        file.uploadedAt = Instant.now();
        return file;
    }
```

`backend/src/main/java/com/vetos/modules/lab/domain/LabResultFile.java`:
```java
    public static LabResultFile create(
        UUID tenantId, UUID labResultId, String fileName, String contentType, byte[] content
    ) {
        LabResultFile file = new LabResultFile();
        file.tenantId = tenantId;
        file.labResultId = labResultId;
        file.fileName = fileName;
        file.contentType = contentType;
        file.fileSize = content.length;
        file.content = content;
        file.uploadedAt = Instant.now();
        return file;
    }
```

`backend/src/main/java/com/vetos/modules/lab/domain/LabResultItem.java`:
```java
    public static LabResultItem create(
        UUID tenantId, UUID labResultId, String parameterName, String value, String unit, String referenceRange, LabValueFlag flag
    ) {
        LabResultItem item = new LabResultItem();
        item.tenantId = tenantId;
        item.labResultId = labResultId;
        item.parameterName = parameterName;
        item.value = value;
        item.unit = unit;
        item.referenceRange = referenceRange;
        item.flag = flag;
        return item;
    }
```

`backend/src/main/java/com/vetos/modules/ai/domain/AiJobDecision.java` (bu sınıfta diğer alanlar `@Column(name=...)` kullanmıyor — yine de kolon adı açıkça yazılır, spec §3 şablonu birebir uygulanır):
```java
    public static AiJobDecision createPending(UUID tenantId, UUID aiJobId) {
        AiJobDecision d = new AiJobDecision();
        d.tenantId = tenantId;
        d.aiJobId = aiJobId;
        return d;
    }
```

- [ ] **Step 3: `ImagingRecordFile.create` çağrı yerini güncelle (1 yer)**

`backend/src/main/java/com/vetos/modules/imaging/application/UploadImagingRecordFileUseCase.java` — `findById` sonucu şu an atılıyor; değişkene al:
```java
    @Transactional
    public UUID execute(UUID imagingRecordId, String fileName, String contentType, byte[] content) {
        ImagingRecord record = imagingRecordRepository.findById(imagingRecordId)
            .orElseThrow(() -> new ImagingRecordNotFoundException(imagingRecordId));

        ImagingRecordFile file = ImagingRecordFile.create(
            record.getTenantId(), imagingRecordId, fileName, contentType, content
        );
        return imagingRecordFileRepository.save(file).getId();
    }
```
(`import com.vetos.modules.imaging.domain.ImagingRecord;` ekle.)

- [ ] **Step 4: `LabResultFile.create` ve `LabResultItem.create` çağrı yerlerini güncelle (2 yer)**

`backend/src/main/java/com/vetos/modules/lab/application/UploadLabResultFileUseCase.java`:
```java
    @Transactional
    public UUID execute(UUID labResultId, String fileName, String contentType, byte[] content) {
        LabResult result = labResultRepository.findById(labResultId)
            .orElseThrow(() -> new LabResultNotFoundException(labResultId));

        LabResultFile file = LabResultFile.create(
            result.getTenantId(), labResultId, fileName, contentType, content
        );
        return labResultFileRepository.save(file).getId();
    }
```
(`import com.vetos.modules.lab.domain.LabResult;` ekle.)

`backend/src/main/java/com/vetos/modules/lab/application/CompleteLabResultUseCase.java`:
```java
        command.items().forEach(item -> labResultItemRepository.save(
            LabResultItem.create(
                result.getTenantId(), result.getId(), item.parameterName(), item.value(),
                item.unit(), item.referenceRange(), item.flag()
            )
        ));
```

- [ ] **Step 5: `AiJobDecision.createPending` çağrı yerini güncelle (1 yer)**

`backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCase.java` — `command.tenantId()` zaten mevcut ve `TenantContext.current()` ile aynıdır:
```java
        AiJobDecision decision = aiJobDecisionRepository.findByAiJobId(command.aiJobId())
            .orElseGet(() -> AiJobDecision.createPending(command.tenantId(), command.aiJobId()));
```

- [ ] **Step 6: Mevcut birim testlerindeki çağrı yerlerini güncelle (10 yer)**

Hepsinde ilk parametre olarak `UUID.randomUUID()` eklenir (saf Mockito testleri — gerçek Hibernate yok, değer önemsiz):
- `backend/src/test/java/com/vetos/modules/ai/domain/AiJobDecisionTest.java` satır 18, 30, 40, 49, 60, 68, 78 → `AiJobDecision.createPending(UUID.randomUUID(), UUID.randomUUID())`
- `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCaseTest.java` satır 82 → `AiJobDecision.createPending(UUID.randomUUID(), aiJobId)`
- `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobFeedbackUseCaseTest.java` satır 34 ve 56 → `AiJobDecision.createPending(UUID.randomUUID(), aiJobId)`

- [ ] **Step 7: `TenantIsolationTest`'i genişlet**

1. Yeni `@Autowired` alanları:
```java
    @Autowired private ImagingRecordRepository imagingRecordRepository;
    @Autowired private ImagingRecordFileRepository imagingRecordFileRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private LabResultFileRepository labResultFileRepository;
    @Autowired private LabResultItemRepository labResultItemRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private AiJobDecisionRepository aiJobDecisionRepository;
    @Autowired private EncounterRepository encounterRepository;
```
(importlar: `imaging.domain.{ImagingModality, ImagingRecord, ImagingRecordFile, ImagingRecordFileRepository, ImagingRecordRepository}`, `lab.domain.{LabResult, LabResultFile, LabResultFileRepository, LabResultItem, LabResultItemRepository, LabResultRepository, LabValueFlag}`, `ai.domain.{AiJob, AiJobDecision, AiJobDecisionRepository, AiJobRepository, AiTaskType}`, `encounter.domain.{Encounter, EncounterRepository}`)

2. Ortak hasta/muayene yardımcıları + 4 test:
```java
    // --- Task 4 ---

    private UUID createPatient(TenantFixture f) {
        UUID speciesId = anySpeciesId();
        return asTenant(f.tenantId(), () -> patientRepository.save(
            Patient.register(f.tenantId(), f.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)
        ).getId());
    }

    // NOT: Encounter Task 5'te @TenantId aliyor -- o task bu cagriyi
    // Encounter.start(tenantId, ...) olarak gunceller.
    private UUID createEncounter(TenantFixture f, UUID patientId) {
        return asTenant(f.tenantId(), () -> encounterRepository.save(
            Encounter.start(patientId, f.staffUserId(), null, null)
        ).getId());
    }

    @Test
    void imagingRecordFile_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);

        UUID fileBId = asTenant(b.tenantId(), () -> {
            ImagingRecord record = imagingRecordRepository.save(ImagingRecord.request(
                b.tenantId(), patientBId, b.staffUserId(), ImagingModality.XRAY, "Toraks", null
            ));
            return imagingRecordFileRepository.save(ImagingRecordFile.create(
                b.tenantId(), record.getId(), "film.png", "image/png", new byte[] {1, 2, 3}
            )).getId();
        });

        assertThat(asTenant(a.tenantId(), () -> imagingRecordFileRepository.findById(fileBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> imagingRecordFileRepository.findById(fileBId))).isPresent();
    }

    @Test
    void labResultFile_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);

        UUID fileBId = asTenant(b.tenantId(), () -> {
            LabResult result = labResultRepository.save(LabResult.request(
                b.tenantId(), patientBId, b.staffUserId(), "Hemogram", null
            ));
            return labResultFileRepository.save(LabResultFile.create(
                b.tenantId(), result.getId(), "sonuc.pdf", "application/pdf", new byte[] {1, 2, 3}
            )).getId();
        });

        assertThat(asTenant(a.tenantId(), () -> labResultFileRepository.findById(fileBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> labResultFileRepository.findById(fileBId))).isPresent();
    }

    @Test
    void labResultItem_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);

        UUID labResultBId = asTenant(b.tenantId(), () -> {
            LabResult result = labResultRepository.save(LabResult.request(
                b.tenantId(), patientBId, b.staffUserId(), "Hemogram", null
            ));
            labResultItemRepository.save(LabResultItem.create(
                b.tenantId(), result.getId(), "WBC", "12.3", "10^3/uL", "6-17", LabValueFlag.NORMAL
            ));
            return result.getId();
        });

        assertThat(asTenant(a.tenantId(), () -> labResultItemRepository.findByLabResultId(labResultBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> labResultItemRepository.findByLabResultId(labResultBId))).hasSize(1);
    }

    @Test
    void aiJobDecision_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);
        UUID encounterBId = createEncounter(b, patientBId);

        UUID aiJobBId = asTenant(b.tenantId(), () -> {
            AiJob job = aiJobRepository.save(AiJob.create(
                b.tenantId(), AiTaskType.DIAGNOSIS_SUGGESTION, encounterBId,
                "oneri metni", "test-model", "v1", b.staffUserId()
            ));
            aiJobDecisionRepository.save(AiJobDecision.createPending(b.tenantId(), job.getId()));
            return job.getId();
        });

        assertThat(asTenant(a.tenantId(), () -> aiJobDecisionRepository.findByAiJobId(aiJobBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> aiJobDecisionRepository.findByAiJobId(aiJobBId))).isPresent();
    }
```

- [ ] **Step 8: Testleri çalıştır — Kat 0 tamamlandı (11 entity)**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — `TenantIsolationTest` 13 test; `AiJobDecisionTest`, `RecordAiJobDecisionUseCaseTest`, `RecordAiJobFeedbackUseCaseTest`, `ApplicationModulesTest` PASS. Bu adımın sonunda spec §4'teki **Kat 0'ın 11 entity'sinin hepsi** izolasyon testiyle kapsanmış olur.

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V43__imaging_record_files_tenant_id.sql src/main/resources/db/migration/V44__lab_result_files_tenant_id.sql src/main/resources/db/migration/V45__lab_result_items_tenant_id.sql src/main/resources/db/migration/V46__ai_job_decisions_tenant_id.sql src/main/java/com/vetos/modules/imaging src/main/java/com/vetos/modules/lab src/main/java/com/vetos/modules/ai src/test/java/com/vetos
git commit -m "feat: ImagingRecordFile/LabResultFile/LabResultItem/AiJobDecision kiraci izolasyonu (Kat 0 tamam)"
```

---

## Task 5: Kat 1 — `Encounter` + `Prescription` + `StockMovement`

**Bağımlılık:** `encounters`/`prescriptions` backfill'i `patients.tenant_id`'yi (Task 1, `V36`), `stock_movements` backfill'i `inventory_items.tenant_id`'yi (Task 2, `V39`) kullanır — bu yüzden migration numaraları `V47`+ olmak zorunda.

**Files:**
- Create: `backend/src/main/resources/db/migration/V47__encounters_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V48__prescriptions_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V49__stock_movements_tenant_id.sql`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/Encounter.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/Prescription.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/domain/StockMovement.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/StartEncounterUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/application/RecordStockMovementUseCase.java`
- Test: `backend/src/test/java/com/vetos/TenantIsolationTest.java` (genişlet + Task 4'teki `createEncounter` güncellemesi)
- Test: `backend/src/test/java/com/vetos/modules/encounter/domain/EncounterTest.java` (2 çağrı yeri)
- Test: `backend/src/test/java/com/vetos/modules/encounter/application/UpdatePhysicalExamUseCaseTest.java` (1 çağrı yeri)
- Test: `backend/src/test/java/com/vetos/modules/encounter/infrastructure/persistence/EncounterLookupAdapterTest.java` (1 çağrı yeri)

**Interfaces:**
- Consumes: `patients.tenant_id` (Task 1 `V36`), `inventory_items.tenant_id` (Task 2 `V39`), `TenantIsolationTest.createPatient` (Task 4)
- Produces: `Encounter.start(UUID tenantId, UUID patientId, UUID staffUserId, UUID appointmentId, String templateUsed)`
- Produces: `Prescription.issue(UUID tenantId, UUID patientId, UUID encounterId, UUID prescribingStaffId, boolean controlledSubstance)`
- Produces: `StockMovement.record(UUID tenantId, UUID inventoryItemId, StockMovementType movementType, int quantity, StockReferenceType referenceType, UUID referenceId)`

- [ ] **Step 1: 3 migration'ı oluştur**

`backend/src/main/resources/db/migration/V47__encounters_tenant_id.sql`:
```sql
-- Kat 1: encounters.tenant_id, patients.tenant_id'den (patient_id uzerinden).
-- patients.tenant_id V36'da dolduruldu -- bu migration ondan SONRA calismali.
ALTER TABLE encounters ADD COLUMN tenant_id UUID;
UPDATE encounters t SET tenant_id = p.tenant_id FROM patients p WHERE t.patient_id = p.id;
ALTER TABLE encounters ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_encounters_tenant_id ON encounters (tenant_id);
```

`backend/src/main/resources/db/migration/V48__prescriptions_tenant_id.sql`:
```sql
-- Kat 1: prescriptions.tenant_id, patients.tenant_id'den. Prescription zaten
-- patient_id'yi DOGRUDAN tasiyor -- Encounter ara adimina gerek yok (spec S4).
ALTER TABLE prescriptions ADD COLUMN tenant_id UUID;
UPDATE prescriptions t SET tenant_id = p.tenant_id FROM patients p WHERE t.patient_id = p.id;
ALTER TABLE prescriptions ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_prescriptions_tenant_id ON prescriptions (tenant_id);
```

`backend/src/main/resources/db/migration/V49__stock_movements_tenant_id.sql`:
```sql
-- Kat 1: stock_movements.tenant_id, inventory_items.tenant_id'den.
-- inventory_items.tenant_id V39'da dolduruldu.
ALTER TABLE stock_movements ADD COLUMN tenant_id UUID;
UPDATE stock_movements t SET tenant_id = i.tenant_id FROM inventory_items i WHERE t.inventory_item_id = i.id;
ALTER TABLE stock_movements ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_stock_movements_tenant_id ON stock_movements (tenant_id);
```

- [ ] **Step 2: 3 entity'ye `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/encounter/domain/Encounter.java` — `id` alanının hemen altına standart alan; factory:
```java
    public static Encounter start(
        UUID tenantId, UUID patientId, UUID staffUserId, UUID appointmentId, String templateUsed
    ) {
        Encounter encounter = new Encounter();
        encounter.tenantId = tenantId;
        encounter.patientId = patientId;
        encounter.staffUserId = staffUserId;
        encounter.appointmentId = appointmentId;
        encounter.templateUsed = templateUsed;
        encounter.encounterDate = Instant.now();
        encounter.status = EncounterStatus.DRAFT;
        encounter.aiGenerated = false;
        return encounter;
    }
```

`backend/src/main/java/com/vetos/modules/encounter/domain/Prescription.java`:
```java
    public static Prescription issue(
        UUID tenantId, UUID patientId, UUID encounterId, UUID prescribingStaffId, boolean controlledSubstance
    ) {
        Prescription prescription = new Prescription();
        prescription.tenantId = tenantId;
        prescription.patientId = patientId;
        prescription.encounterId = encounterId;
        prescription.prescribingStaffId = prescribingStaffId;
        prescription.issuedDate = LocalDate.now();
        prescription.status = PrescriptionStatus.ACTIVE;
        prescription.controlledSubstance = controlledSubstance;
        return prescription;
    }
```

`backend/src/main/java/com/vetos/modules/inventory/domain/StockMovement.java`:
```java
    public static StockMovement record(
        UUID tenantId, UUID inventoryItemId, StockMovementType movementType, int quantity,
        StockReferenceType referenceType, UUID referenceId
    ) {
        StockMovement movement = new StockMovement();
        movement.tenantId = tenantId;
        movement.inventoryItemId = inventoryItemId;
        movement.movementType = movementType;
        movement.quantity = quantity;
        movement.referenceType = referenceType;
        movement.referenceId = referenceId;
        movement.createdAt = Instant.now();
        return movement;
    }
```

- [ ] **Step 3: Çağrı yerlerini güncelle (3 yer)**

1. `backend/src/main/java/com/vetos/modules/encounter/application/StartEncounterUseCase.java`:
```java
        Encounter encounter = Encounter.start(
            TenantContext.current(), command.patientId(), command.staffUserId(),
            command.appointmentId(), command.templateUsed()
        );
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

2. `backend/src/main/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCase.java` — `Prescription.issue` çağrısı (aynı dosyadaki `PrescriptionItem.add` Task 6'da güncellenecek):
```java
        Prescription prescription = prescriptionRepository.save(Prescription.issue(
            TenantContext.current(), command.patientId(), command.encounterId(),
            command.prescribingStaffId(), command.controlledSubstance()
        ));
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

3. `backend/src/main/java/com/vetos/modules/inventory/application/RecordStockMovementUseCase.java` — `item` zaten elde; artık `item.getTenantId()` var. (Denetimde bulunan asıl açık burasıydı: `findById` çağrısı bu task'tan sonra Hibernate tarafından otomatik filtrelenir, yani başka kiracının kalemi zaten `InventoryItemNotFoundException` verir.)
```java
        return stockMovementRepository.save(
            StockMovement.record(item.getTenantId(), inventoryItemId, type, quantity, referenceType, referenceId)
        ).getId();
```

- [ ] **Step 4: Mevcut birim testlerindeki `Encounter.start` çağrı yerlerini güncelle (4 yer)**

Hepsinde ilk parametre olarak `UUID.randomUUID()` eklenir:
- `backend/src/test/java/com/vetos/modules/encounter/domain/EncounterTest.java` satır 14 ve 27 → `Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null)`
- `backend/src/test/java/com/vetos/modules/encounter/application/UpdatePhysicalExamUseCaseTest.java` satır 32 → `Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null)`
- `backend/src/test/java/com/vetos/modules/encounter/infrastructure/persistence/EncounterLookupAdapterTest.java` satır 37 → `Encounter.start(UUID.randomUUID(), patientId, UUID.randomUUID(), null, null)`

- [ ] **Step 5: `TenantIsolationTest`'i genişlet**

1. Task 4'te eklenen `createEncounter` yardımcısını yeni imzaya güncelle ve NOT satırını sil:
```java
    private UUID createEncounter(TenantFixture f, UUID patientId) {
        return asTenant(f.tenantId(), () -> encounterRepository.save(
            Encounter.start(f.tenantId(), patientId, f.staffUserId(), null, null)
        ).getId());
    }
```

2. Yeni `@Autowired` alanları:
```java
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
```
(importlar: `encounter.domain.{Prescription, PrescriptionRepository}`, `inventory.domain.{StockMovement, StockMovementRepository, StockMovementType, StockReferenceType}`)

3. Stok kalemi yardımcısı + 3 test:
```java
    // --- Task 5 ---

    private UUID createInventoryItem(TenantFixture f) {
        return asTenant(f.tenantId(), () -> inventoryItemRepository.save(InventoryItem.create(
            f.tenantId(), f.branchId(), "Mama", "Gida", null, 10, 1, null, null, BigDecimal.TEN
        )).getId());
    }

    @Test
    void encounter_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID encounterBId = createEncounter(b, createPatient(b));

        assertThat(asTenant(a.tenantId(), () -> encounterRepository.findById(encounterBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> encounterRepository.findById(encounterBId))).isPresent();
    }

    @Test
    void prescription_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);
        UUID encounterBId = createEncounter(b, patientBId);

        UUID prescriptionBId = asTenant(b.tenantId(), () -> prescriptionRepository.save(
            Prescription.issue(b.tenantId(), patientBId, encounterBId, b.staffUserId(), false)
        ).getId());

        assertThat(asTenant(a.tenantId(), () -> prescriptionRepository.findById(prescriptionBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> prescriptionRepository.findById(prescriptionBId))).isPresent();
    }

    @Test
    void stockMovement_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID itemBId = createInventoryItem(b);

        asTenantVoid(b.tenantId(), () -> stockMovementRepository.save(StockMovement.record(
            b.tenantId(), itemBId, StockMovementType.IN, 5, StockReferenceType.MANUAL, null
        )));

        assertThat(asTenant(a.tenantId(), () -> stockMovementRepository.findByInventoryItemId(itemBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> stockMovementRepository.findByInventoryItemId(itemBId))).hasSize(1);
    }
```

- [ ] **Step 6: Testleri çalıştır**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — `TenantIsolationTest` 16 test; `EncounterTest`, `UpdatePhysicalExamUseCaseTest`, `EncounterLookupAdapterTest`, `StockDeductionAdapterTest`, `ApplicationModulesTest` PASS.

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V47__encounters_tenant_id.sql src/main/resources/db/migration/V48__prescriptions_tenant_id.sql src/main/resources/db/migration/V49__stock_movements_tenant_id.sql src/main/java/com/vetos/modules/encounter src/main/java/com/vetos/modules/inventory src/test/java/com/vetos
git commit -m "feat: Encounter/Prescription/StockMovement kiraci izolasyonu (Kat 1)"
```

---

## Task 6: Kat 2 — `EncounterInventoryUsage` + `PrescriptionItem`

**Bağımlılık:** backfill kaynakları `encounters.tenant_id` (`V47`) ve `prescriptions.tenant_id` (`V48`) — migration numaraları `V50`+ olmak zorunda.

**Files:**
- Create: `backend/src/main/resources/db/migration/V50__encounter_inventory_usage_tenant_id.sql`
- Create: `backend/src/main/resources/db/migration/V51__prescription_items_tenant_id.sql`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/EncounterInventoryUsage.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/PrescriptionItem.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/RecordInventoryUsageUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCase.java`
- Test: `backend/src/test/java/com/vetos/TenantIsolationTest.java` (genişlet — 16 entity tamamlanır)

**Interfaces:**
- Consumes: `encounters.tenant_id` (Task 5 `V47`), `prescriptions.tenant_id` (Task 5 `V48`), `TenantIsolationTest.createEncounter` (Task 5 hali)
- Produces: `EncounterInventoryUsage.record(UUID tenantId, UUID encounterId, UUID inventoryItemId, int quantity)`
- Produces: `PrescriptionItem.add(UUID tenantId, UUID prescriptionId, UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route)`

- [ ] **Step 1: 2 migration'ı oluştur**

`backend/src/main/resources/db/migration/V50__encounter_inventory_usage_tenant_id.sql`:
```sql
-- Kat 2: encounter_inventory_usage.tenant_id, encounters.tenant_id'den.
-- encounters.tenant_id V47'de dolduruldu -- bu migration ondan SONRA calismali.
ALTER TABLE encounter_inventory_usage ADD COLUMN tenant_id UUID;
UPDATE encounter_inventory_usage t SET tenant_id = e.tenant_id FROM encounters e WHERE t.encounter_id = e.id;
ALTER TABLE encounter_inventory_usage ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_encounter_inventory_usage_tenant_id ON encounter_inventory_usage (tenant_id);
```

`backend/src/main/resources/db/migration/V51__prescription_items_tenant_id.sql`:
```sql
-- Kat 2: prescription_items.tenant_id, prescriptions.tenant_id'den.
-- prescriptions.tenant_id V48'de dolduruldu.
ALTER TABLE prescription_items ADD COLUMN tenant_id UUID;
UPDATE prescription_items t SET tenant_id = p.tenant_id FROM prescriptions p WHERE t.prescription_id = p.id;
ALTER TABLE prescription_items ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_prescription_items_tenant_id ON prescription_items (tenant_id);
```

- [ ] **Step 2: 2 entity'ye `@TenantId` alanını ve factory parametresini ekle**

`backend/src/main/java/com/vetos/modules/encounter/domain/EncounterInventoryUsage.java` — `id` alanının hemen altına standart alan; factory:
```java
    public static EncounterInventoryUsage record(UUID tenantId, UUID encounterId, UUID inventoryItemId, int quantity) {
        EncounterInventoryUsage usage = new EncounterInventoryUsage();
        usage.tenantId = tenantId;
        usage.encounterId = encounterId;
        usage.inventoryItemId = inventoryItemId;
        usage.quantity = quantity;
        return usage;
    }
```

`backend/src/main/java/com/vetos/modules/encounter/domain/PrescriptionItem.java`:
```java
    public static PrescriptionItem add(
        UUID tenantId, UUID prescriptionId, UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route
    ) {
        PrescriptionItem item = new PrescriptionItem();
        item.tenantId = tenantId;
        item.prescriptionId = prescriptionId;
        item.drugId = drugId;
        item.dosage = dosage;
        item.frequency = frequency;
        item.durationDays = durationDays;
        item.route = route;
        return item;
    }
```

- [ ] **Step 3: Çağrı yerlerini güncelle (2 yer)**

1. `backend/src/main/java/com/vetos/modules/encounter/application/RecordInventoryUsageUseCase.java`:
```java
    @Transactional
    public UUID execute(UUID encounterId, UUID inventoryItemId, int quantity) {
        return encounterInventoryUsageRepository.save(
            EncounterInventoryUsage.record(TenantContext.current(), encounterId, inventoryItemId, quantity)
        ).getId();
    }
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

2. `backend/src/main/java/com/vetos/modules/encounter/application/IssuePrescriptionUseCase.java` — `prescription` zaten elde, `getTenantId()` kullanılır:
```java
        command.items().forEach(item -> prescriptionItemRepository.save(PrescriptionItem.add(
            prescription.getTenantId(), prescription.getId(), item.drugId(), item.dosage(),
            item.frequency(), item.durationDays(), item.route()
        )));
```

- [ ] **Step 4: `TenantIsolationTest`'i genişlet — 16 entity tamam**

1. Yeni `@Autowired` alanları:
```java
    @Autowired private EncounterInventoryUsageRepository encounterInventoryUsageRepository;
    @Autowired private PrescriptionItemRepository prescriptionItemRepository;
    @Autowired private DrugCatalogRepository drugCatalogRepository;
```
(importlar: `encounter.domain.{DrugCatalogRepository, DrugRoute, EncounterInventoryUsage, EncounterInventoryUsageRepository, PrescriptionItem, PrescriptionItemRepository}`)

2. 2 test:
```java
    // --- Task 6 ---

    @Test
    void encounterInventoryUsage_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID encounterBId = createEncounter(b, createPatient(b));
        UUID itemBId = createInventoryItem(b);

        asTenantVoid(b.tenantId(), () -> encounterInventoryUsageRepository.save(
            EncounterInventoryUsage.record(b.tenantId(), encounterBId, itemBId, 2)
        ));

        assertThat(asTenant(a.tenantId(), () -> encounterInventoryUsageRepository.findByEncounterId(encounterBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> encounterInventoryUsageRepository.findByEncounterId(encounterBId))).hasSize(1);
    }

    @Test
    void prescriptionItem_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);
        UUID encounterBId = createEncounter(b, patientBId);
        // drug_catalog kiraci-bagimsiz paylasimli referans verisi (V4'te tohumlanir).
        UUID drugId = inRootSession(() -> drugCatalogRepository.findAll().get(0).getId());

        UUID prescriptionBId = asTenant(b.tenantId(), () -> {
            Prescription prescription = prescriptionRepository.save(
                Prescription.issue(b.tenantId(), patientBId, encounterBId, b.staffUserId(), false)
            );
            prescriptionItemRepository.save(PrescriptionItem.add(
                b.tenantId(), prescription.getId(), drugId, "1x1", "gunde 1", 5, DrugRoute.ORAL
            ));
            return prescription.getId();
        });

        assertThat(asTenant(a.tenantId(), () -> prescriptionItemRepository.findByPrescriptionId(prescriptionBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> prescriptionItemRepository.findByPrescriptionId(prescriptionBId))).hasSize(1);
    }
```

- [ ] **Step 5: Tüm izolasyon paketini çalıştır — spec §8 kabul kriteri**

Run: `cd backend && ./mvnw -q -Dtest=TenantIsolationTest test`
Expected: **PASS (18 test)** — 16 entity için birer izolasyon testi + `staffUser_isVisibleToRootSession_soLoginKeepsWorking` + `rootSession_worksAndSeesAllTenants_whenNoTenantContext`. Spec §8'in "tüm 16 entity için tamamlandı" kabul kriteri bu adımda sağlanır.

Run: `cd backend && ./mvnw -q test`
Expected: PASS — tüm paket.

- [ ] **Step 6: Commit**

```bash
cd backend && git add src/main/resources/db/migration/V50__encounter_inventory_usage_tenant_id.sql src/main/resources/db/migration/V51__prescription_items_tenant_id.sql src/main/java/com/vetos/modules/encounter src/test/java/com/vetos/TenantIsolationTest.java
git commit -m "feat: EncounterInventoryUsage/PrescriptionItem kiraci izolasyonu (16 entity tamam)"
```

---

## Task 7: TenantContext köprüleme kuralı — `AppointmentReminderScheduler` + `TenantAdminPortAdapter`

**Bağımlılık:** Task 1-6. Köprüleme, `@TenantId`'li entity'ler var olduktan sonra anlam kazanır (`StaffUser` Task 2'de anotasyonlandı).

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderScheduler.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderSchedulerTest.java` (yeni dosya)
- Test: `backend/src/test/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapterTest.java` (yeni dosya)

**Interfaces:**
- Consumes: `TenantContext.set`/`clear`/`currentOrNull` (Task 1), `@TenantId`'li `StaffUser` (Task 2)
- Produces: davranış değişikliği yok (imza değişmez) — sadece `TenantContext` köprülemesi

**Not:** Spec §5, bu değişikliğin "Bildirim Teslim Sertleştirme" dokümanındaki advisory-lock değişikliğiyle birlikte ele alınmasını istiyor. Kod tabanında bugün `sendTomorrowReminders()` içinde advisory-lock YOK ve `docs/superpowers/plans/` altında böyle bir plan bulunmuyor — bu yüzden metot mevcut haliyle değiştirilir; advisory-lock turu geldiğinde aynı metodun tek bir sürümünü görecektir.

- [ ] **Step 1: Başarısız scheduler testini yaz**

`backend/src/test/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderSchedulerTest.java`:
```java
package com.vetos.modules.notification.infrastructure.scheduling;

import com.vetos.modules.notification.application.SendAppointmentRemindersUseCase;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Koprulme kurali (tasarim dokumani S5): arka plan isi, @TenantId'li bir
 * entity'ye dokunmadan once TenantContext'i kurar, finally'de temizler.
 * Gercek Hibernate yok -- sadece TenantContext'in dogru deger ile
 * kuruldugu ve her tur sonunda temizlendigi dogrulanir.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentReminderSchedulerTest {

    @Mock private TenantLookupPort tenantLookupPort;
    @Mock private SendAppointmentRemindersUseCase sendAppointmentRemindersUseCase;

    @Test
    void should_setTenantContext_forEachTenant_and_clearAfterwards() {
        AppointmentReminderScheduler scheduler =
            new AppointmentReminderScheduler(tenantLookupPort, sendAppointmentRemindersUseCase);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        List<UUID> seenInsideUseCase = new ArrayList<>();
        when(tenantLookupPort.findActiveTenantIds()).thenReturn(List.of(tenantA, tenantB));
        when(sendAppointmentRemindersUseCase.execute(any(), any(Instant.class), any(Instant.class)))
            .thenAnswer(invocation -> {
                seenInsideUseCase.add(TenantContext.current());
                return 0;
            });

        scheduler.sendTomorrowReminders();

        assertThat(seenInsideUseCase).containsExactly(tenantA, tenantB);
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void should_clearTenantContext_evenWhenUseCaseThrows() {
        AppointmentReminderScheduler scheduler =
            new AppointmentReminderScheduler(tenantLookupPort, sendAppointmentRemindersUseCase);
        UUID tenantA = UUID.randomUUID();
        when(tenantLookupPort.findActiveTenantIds()).thenReturn(List.of(tenantA));
        when(sendAppointmentRemindersUseCase.execute(eq(tenantA), any(Instant.class), any(Instant.class)))
            .thenThrow(new RuntimeException("gonderim patladi"));

        scheduler.sendTomorrowReminders();

        // Mevcut try/catch hatayi yutar; koprulme yine de temizlemis olmali --
        // aksi halde ThreadLocal, scheduler thread'inde SIZAR ve bir sonraki
        // is yanlis kiracinin verisini gorur.
        assertThat(TenantContext.currentOrNull()).isNull();
    }
}
```

- [ ] **Step 2: Testi çalıştırıp başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=AppointmentReminderSchedulerTest test`
Expected: FAIL — `should_setTenantContext_forEachTenant_and_clearAfterwards`, `IllegalStateException: Aktif tenant context yok...` ile düşer (use-case içinde `TenantContext.current()` çağrıldığında context kurulu değil). (Sınıf `package-private` olduğu için test aynı pakette — yukarıdaki gibi.)

- [ ] **Step 3: `AppointmentReminderScheduler`'a köprülemeyi ekle**

`backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderScheduler.java` — `sendTomorrowReminders()` döngüsü (spec §5'teki desen; `TenantContext.set` try'ın DIŞINDA, `clear()` `finally`'de — mevcut `catch (Exception e)` korunur):
```java
    @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
    public void sendTomorrowReminders() {
        Instant tomorrowStart = LocalDate.now(ISTANBUL).plusDays(1).atStartOfDay(ISTANBUL).toInstant();
        Instant tomorrowEnd = tomorrowStart.plus(1, ChronoUnit.DAYS);

        for (UUID tenantId : tenantLookupPort.findActiveTenantIds()) {
            // Koprulme kurali: bu is bir HTTP istegi icinde calismiyor, ama
            // tenantId elimizde. @TenantId'li entity'lere (Patient, Encounter...)
            // dokunmadan once TenantContext kurulmali -- aksi halde sorgu root
            // Session'da, yani FILTRESIZ calisir.
            TenantContext.set(tenantId);
            try {
                int queued = sendAppointmentRemindersUseCase.execute(tenantId, tomorrowStart, tomorrowEnd);
                if (queued > 0) {
                    log.info("Randevu hatirlatmasi kuyruklandi: tenantId={}, adet={}", tenantId, queued);
                }
            } catch (Exception e) {
                log.error("Randevu hatirlatma isi basarisiz: tenantId={}", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

- [ ] **Step 4: Scheduler testini çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=AppointmentReminderSchedulerTest test`
Expected: PASS (2 test)

- [ ] **Step 5: Başarısız `TenantAdminPortAdapter` testini yaz**

`backend/src/test/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapterTest.java`:
```java
package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.StaffInviteRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.Subscription;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.platform.event.DomainEventPublisher;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Koprulme kurali (tasarim dokumani S5): platform admin istekleri
 * TenantContext OLMADAN gelir; StaffUser (@TenantId'li) okuyan/yazan
 * cagrilar TenantContext'i elle kurmali, finally'de temizlemeli.
 * Gercek Hibernate yok -- mock repository'ler icinde TenantContext'in
 * degeri yakalanir.
 */
@ExtendWith(MockitoExtension.class)
class TenantAdminPortAdapterTest {

    @Mock private TenantJpaRepository tenantJpaRepository;
    @Mock private SubscriptionJpaRepository subscriptionJpaRepository;
    @Mock private BranchJpaRepository branchJpaRepository;
    @Mock private StaffUserJpaRepository staffUserJpaRepository;
    @Mock private StaffInviteRepository staffInviteRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DomainEventPublisher eventPublisher;

    private TenantAdminPortAdapter adapter() {
        return new TenantAdminPortAdapter(
            tenantJpaRepository, subscriptionJpaRepository, branchJpaRepository,
            staffUserJpaRepository, staffInviteRepository, passwordEncoder, eventPublisher
        );
    }

    private static Branch branchWithId(UUID tenantId, UUID branchId) {
        Branch branch = Branch.create(tenantId, "Merkez");
        ReflectionTestUtils.setField(branch, "id", branchId);
        return branch;
    }

    private static Tenant tenantWithId(UUID tenantId) {
        Tenant tenant = Tenant.register("Test Klinik", null);
        ReflectionTestUtils.setField(tenant, "id", tenantId);
        return tenant;
    }

    @Test
    void findBillingContactEmail_setsTenantContext_andClearsIt() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        AtomicReference<UUID> seen = new AtomicReference<>();
        when(branchJpaRepository.findByTenantId(tenantId)).thenReturn(List.of(branchWithId(tenantId, branchId)));
        when(staffUserJpaRepository.findByBranchIdInAndRole(anyList(), any(StaffRole.class)))
            .thenAnswer(invocation -> {
                seen.set(TenantContext.current());
                return List.of(StaffUser.register(tenantId, branchId, "Admin", "a@b.c", "hash", StaffRole.ADMIN));
            });

        adapter().findBillingContactEmail(tenantId);

        assertThat(seen.get()).isEqualTo(tenantId);
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void createTenant_setsTenantContext_onlyAroundStaffUserWrite() {
        UUID tenantId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        List<UUID> contextDuringEmailCheck = new ArrayList<>();
        AtomicReference<UUID> contextDuringSave = new AtomicReference<>();

        when(staffUserJpaRepository.existsByEmail("admin@klinik.test")).thenAnswer(invocation -> {
            // Bu sorgu GLOBAL olmali: staff_users.email tum kiracilarda
            // essiz. Kiraci filtreli bir Session'da calisirsa baska
            // kiracidaki ayni e-posta gorunmez ve unique constraint
            // ihlali 500 olarak patlar. Bu yuzden koprulme buraya DEGIL,
            // sadece StaffUser.register/save cagrisina uygulanir.
            contextDuringEmailCheck.add(TenantContext.currentOrNull());
            return false;
        });
        when(tenantJpaRepository.save(any(Tenant.class))).thenReturn(tenantWithId(tenantId));
        when(branchJpaRepository.save(any(Branch.class))).thenReturn(branchWithId(tenantId, branchId));
        when(subscriptionJpaRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode("sifre")).thenReturn("hash");
        when(staffUserJpaRepository.save(any(StaffUser.class))).thenAnswer(invocation -> {
            contextDuringSave.set(TenantContext.current());
            return invocation.getArgument(0);
        });

        adapter().createTenant(
            "Test Klinik", null, "Merkez", "Adres", "Ankara",
            "Admin", "admin@klinik.test", "sifre"
        );

        assertThat(contextDuringEmailCheck).containsExactly((UUID) null);
        assertThat(contextDuringSave.get()).isEqualTo(tenantId);
        assertThat(TenantContext.currentOrNull()).isNull();
    }
}
```

- [ ] **Step 6: Testi çalıştırıp başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=TenantAdminPortAdapterTest test`
Expected: FAIL — her iki testte de mock içindeki `TenantContext.current()` `IllegalStateException` fırlatır (köprüleme henüz yok).

- [ ] **Step 7: `TenantAdminPortAdapter`'a köprülemeyi ekle (2 yer)**

`backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java`:

1. Private `findBillingContact` metodu — `StaffUser` okur:
```java
    // Koprulme kurali (tasarim dokumani S5): platform admin istegi, TenantContext
    // kurulu DEGIL. StaffUser artik @TenantId'li -- filtreyi devreye sokmak icin
    // context elle kurulur.
    private Optional<StaffUser> findBillingContact(UUID tenantId) {
        List<UUID> branchIds = branchJpaRepository.findByTenantId(tenantId).stream().map(Branch::getId).toList();
        if (branchIds.isEmpty()) {
            return Optional.empty();
        }
        TenantContext.set(tenantId);
        try {
            return staffUserJpaRepository.findByBranchIdInAndRole(branchIds, StaffRole.ADMIN).stream().findFirst();
        } finally {
            TenantContext.clear();
        }
    }
```

2. `createTenant` içindeki `StaffUser.register(...)`/`save(...)` çağrısı — köprüleme **DAR** tutulur; `existsByEmail` bilinçli olarak dışarıda kalır:
```java
        String passwordHash = passwordEncoder.encode(adminPassword);
        // Koprulme kurali: sadece StaffUser yazimini sarar. existsByEmail
        // yukarida, KAPSAM DISINDA kalir -- staff_users.email tum kiracilarda
        // essiz oldugu icin o kontrol GLOBAL calismak zorunda.
        final UUID newTenantId = tenant.getId();
        final UUID newBranchId = branch.getId();
        StaffUser admin;
        TenantContext.set(newTenantId);
        try {
            admin = staffUserJpaRepository.save(
                StaffUser.register(newTenantId, newBranchId, adminFullName, adminEmail, passwordHash, StaffRole.ADMIN)
            );
        } finally {
            TenantContext.clear();
        }

        eventPublisher.publish(new ClinicRegisteredEvent(tenant.getId(), branch.getId(), admin.getId()));

        return tenant.getId();
```
(`import com.vetos.platform.tenancy.TenantContext;` ekle.)

- [ ] **Step 8: Testleri çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=AppointmentReminderSchedulerTest,TenantAdminPortAdapterTest test`
Expected: PASS (4 test)

Run: `cd backend && ./mvnw -q test`
Expected: PASS — tüm paket (`TenantIsolationTest` 18 test dahil), `ApplicationModulesTest` PASS.

- [ ] **Step 9: Ek tarama — spec §5'in istediği "başka köprüleme noktası var mı?" kontrolü**

```bash
cd backend && grep -rn "TenantContext.set" --include=*.java src/main/java
```
Expected: tam olarak 3 sonuç — `platform/security/JwtAuthenticationFilter.java`, `modules/notification/infrastructure/scheduling/AppointmentReminderScheduler.java`, `modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java` (2 satır).

```bash
cd backend && grep -rn "TenantAdminPort\b" --include=*.java src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java
```
`TenantAdminPort`'un diğer metotlarını gözden geçir: `listAll`, `getOverview`, `updateSubscription`, `suspend`, `activate`, `listSubscriptionsDueOnOrBefore`, `advanceRenewal`, `updateBillingStatus`, `createTenantForPaidSignup`, `createAdminInviteForPaidSignup`, `isEmailRegistered`. Bunların hiçbiri `@TenantId`'li bir entity'ye YAZMIYOR (`Tenant`, `Subscription`, `Branch`, `StaffInvite` bu turun kapsamı dışında) — `toOverview` içindeki `staffUserJpaRepository.countByBranchIdIn(branchIds)` bir `StaffUser` OKUMASIdır ama `branchIds` zaten o kiracının şubeleriyle sınırlıdır, root Session'da doğru sonucu verir; köprüleme eklemek davranışı değiştirmez. `isEmailRegistered` ve `createTenantForPaidSignup` içindeki e-posta kontrolleri bilinçli olarak GLOBAL kalır (Step 7'deki gerekçe). Sonuç: spec §5'te sayılan 2 yer dışında ek köprüleme noktası yok.

- [ ] **Step 10: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderScheduler.java src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java src/test/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderSchedulerTest.java src/test/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapterTest.java
git commit -m "feat: TenantContext koprulme kurali (scheduler + platform admin adaptoru)"
```

---

## Task 8: `NotificationLog` istisnası — `RetryNotificationUseCase` tenant kontrolü

**Bağımlılık:** Task 1-7'den bağımsız (`NotificationLog` bilinçli olarak `@TenantId` DIŞINDA). İlgisiz bir temizlik olduğu için en sona sıralanmıştır.

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/notification/application/RetryNotificationUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/api/NotificationsController.java`
- Test: `backend/src/test/java/com/vetos/modules/notification/application/RetryNotificationUseCaseTest.java` (yeni dosya)

**Interfaces:**
- Produces: `RetryNotificationUseCase.execute(UUID tenantId, UUID logId): void` (imza değişiyor — eski tek parametreli sürüm KALDIRILIR)
- Consumes: `TenantContext.current()` (`NotificationsController.retry` içinde)

- [ ] **Step 1: Başarısız regresyon testini yaz**

`backend/src/test/java/com/vetos/modules/notification/application/RetryNotificationUseCaseTest.java`:
```java
package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.notification.domain.exception.NotificationLogNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationLog bilincli olarak @TenantId DISINDA (tasarim dokumani S6).
 * Tek gercek risk POST /notifications/logs/{id}/retry -- id istemciden
 * geliyor. Baska kiracinin kaydi 404 ile "yok" gibi gorunmeli, var oldugu
 * bile sizdirilmamali.
 */
@ExtendWith(MockitoExtension.class)
class RetryNotificationUseCaseTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendExecutor notificationSendExecutor;

    private NotificationLog aLog(UUID tenantId, UUID logId) {
        NotificationLog log = NotificationLog.queue(
            tenantId, UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER,
            "05551234567", "mesaj", null, null
        );
        ReflectionTestUtils.setField(log, "id", logId);
        return log;
    }

    @Test
    void should_throwNotFound_when_logBelongsToAnotherTenant() {
        RetryNotificationUseCase useCase =
            new RetryNotificationUseCase(notificationLogRepository, notificationSendExecutor);
        UUID callerTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(aLog(foreignTenantId, logId)));

        assertThatThrownBy(() -> useCase.execute(callerTenantId, logId))
            .isInstanceOf(NotificationLogNotFoundException.class);

        verify(notificationLogRepository, never()).save(any());
        verify(notificationSendExecutor, never()).attemptSend(any());
    }

    @Test
    void should_retry_when_logBelongsToCallerTenant() {
        RetryNotificationUseCase useCase =
            new RetryNotificationUseCase(notificationLogRepository, notificationSendExecutor);
        UUID tenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        NotificationLog log = aLog(tenantId, logId);
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(log));

        useCase.execute(tenantId, logId);

        verify(notificationLogRepository).save(log);
        verify(notificationSendExecutor).attemptSend(logId);
    }
}
```

(`NotificationType.APPOINTMENT_REMINDER` sabitinin gerçek adını `modules/notification/domain/NotificationType.java`'dan doğrula; farklıysa oradaki ilk sabiti kullan.)

- [ ] **Step 2: Testi çalıştırıp derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q -Dtest=RetryNotificationUseCaseTest test`
Expected: derleme hatası — `execute(UUID, UUID)` henüz yok (mevcut imza `execute(UUID logId)`).

- [ ] **Step 3: `RetryNotificationUseCase` imzasını değiştir**

`backend/src/main/java/com/vetos/modules/notification/application/RetryNotificationUseCase.java` — `execute` metodu (spec §6):
```java
    /**
     * NotificationLog @TenantId DISINDA tutuluyor (tasarim dokumani S6) --
     * bu yuzden kiraci kontrolu ELLE yapilir. Baska kiracinin kaydi,
     * mevcut NotificationLogNotFoundException (404) ile "yok" gibi gorunur;
     * var oldugu bile sizdirilmaz.
     */
    @Transactional
    public void execute(UUID tenantId, UUID logId) {
        NotificationLog log = notificationLogRepository.findById(logId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new NotificationLogNotFoundException(logId));
        log.markRetrying();
        notificationLogRepository.save(log);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationSendExecutor.attemptSend(logId);
                }
            });
        } else {
            notificationSendExecutor.attemptSend(logId);
        }
    }
```

- [ ] **Step 4: `NotificationsController.retry`'ı güncelle**

`backend/src/main/java/com/vetos/modules/notification/api/NotificationsController.java` — diğer metotlarla aynı desen (`TenantContext.current()` zaten bu sınıfta kullanılıyor ve import edilmiş):
```java
    @PostMapping("/logs/{id}/retry")
    public void retry(@PathVariable UUID id) {
        retryNotificationUseCase.execute(TenantContext.current(), id);
    }
```

- [ ] **Step 5: Testi çalıştırıp geçtiğini doğrula**

Run: `cd backend && ./mvnw -q -Dtest=RetryNotificationUseCaseTest test`
Expected: PASS (2 test)

- [ ] **Step 6: `execute(UUID)` çağıran başka yer kalmadığını doğrula**

```bash
cd backend && grep -rn "retryNotificationUseCase\|RetryNotificationUseCase" --include=*.java src/main src/test
```
Expected: sadece `NotificationsController` (alan tanımı + import + `retry` metodu), `RetryNotificationUseCase`'in kendisi ve yeni test sınıfı.

- [ ] **Step 7: Tüm paketi ve uygulamayı doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS — tüm paket; `TenantIsolationTest` 18 test, `ApplicationModulesTest` PASS.

```bash
cd backend && ./mvnw spring-boot:run
# Klinik A'nin token'i ile, Klinik B'ye ait bir bildirim log id'si:
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://localhost:8080/api/v1/notifications/logs/<klinik-B-log-id>/retry \
  -H 'Authorization: Bearer <klinik-A-token>'
```
Expected: `404` (önceden 200 dönüyordu ve başka kiracının bildirimini yeniden gönderiyordu).

- [ ] **Step 8: Commit**

```bash
cd backend && git add src/main/java/com/vetos/modules/notification/application/RetryNotificationUseCase.java src/main/java/com/vetos/modules/notification/api/NotificationsController.java src/test/java/com/vetos/modules/notification/application/RetryNotificationUseCaseTest.java
git commit -m "fix: RetryNotificationUseCase'e elle kiraci kontrolu (NotificationLog istisnasi)"
```
