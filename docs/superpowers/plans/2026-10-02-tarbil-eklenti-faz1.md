# TARBİL Eklentisi — Faz 1 Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** TARBİL ekran bilgisine bağlı olmayan her şeyi kurmak: sahte senkronu kaldırıp gerçek aktarım durumu (bekliyor/gönderildi/bildirilmeyecek), eklenti eşleştirme + anahtarı, eklenti API'si, Vetly web ekranları ve eklentinin altyapısı (arka plan, yan panel, sayfa içi kart). Faz 1 sonunda hekim aşıyı TARBİL'e elle girip eklentiden/Vetly'den "gönderildi" olarak işaretleyebilir; otomatik form doldurma Faz 2'dir.

**Architecture:** Vetly backend (`integration/tarbil`) tek gerçek kaynaktır; eklenti yalnız kendi anahtarıyla `/api/v1/tarbil-extension/**` uçlarına erişir. Eklenti anahtarı, `platform/security`'de tanımlanan `ExtensionTokenAuthenticator` portunu kullanan ayrı bir `SecurityFilterChain` ile doğrulanır; portun uygulaması `tarbil` modülündedir. Eklenti MV3 + TypeScript + Vite; Vetly API'ye yalnızca arka plan service worker'ı konuşur.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Modulith, Flyway, PostgreSQL, JUnit 5 + Mockito + AssertJ; React 18 + TypeScript + Vite 5; Chrome Extension Manifest V3, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`

## Global Constraints

- Mimari kural: `tarbil` modülü `patient`, `encounter`, `tenant` verisine YALNIZCA `*LookupPort` arayüzleriyle erişir; başka modülün repository/entity'sini import etmez (`docs/architecture.md`). `ApplicationModulesTest` her task sonunda yeşil kalmalı.
- Kod ve test isimlendirmesi `docs/coding-conventions.md` ve mevcut desen: use-case `XxxUseCase.execute(...)`, test `should_<sonuç>_when_<koşul>`, Mockito + AssertJ.
- Hata tipleri `DomainException` alt sınıfı; HTTP kodu sınıf adı sonekiyle belirlenir (`...NotFoundException` → 404, `...ConflictException` → 409, `...UnauthorizedException` → 401, diğer → 422).
- `tarbil_*` tabloları Hibernate `@TenantId` DIŞINDADIR (mevcut `tarbil_sync_log` kararıyla aynı); her sorgu `tenant_id`'yi elle filtreler.
- Eşleştirme kodu: 8 karakter, `XXXX-XXXX`, 10 dakika geçerli, tek kullanımlık. Kod ve anahtar yalnızca SHA-256 hex özeti olarak saklanır.
- Eklenti anahtarı biçimi: `vtx_` + 43 karakter base64url (32 rastgele bayt).
- Eklentiye sahip TC/adres/telefon gönderilmez.
- Eklenti üretim derlemesinde konsola hasta/çip bilgisi yazmaz.
- Frontend: renkler yalnız `var(--color-*)` token'larıyla; buton varyantları `docs/design-system.md` (sayfada en fazla 1 `primary`); TARBİL durumları AI mavisi KULLANMAZ.
- Frontend doğrulaması `npm run build` (tsc dahil); frontend'de birim test altyapısı yok, eklenmez.
- Backend doğrulaması `./mvnw test` + çalışan instance'a karşı curl (CLAUDE.md).

## Spec'ten Bilinçli Sapmalar (kod incelemesi sonrası)

1. **Testcontainers kullanılmaz.** Spec §9 "Testcontainers" diyor; ama repo bunu bilinçli olarak kullanmıyor (`TenantIsolationTest` javadoc'u): canlı docker-compose Postgres'e bağlanan dar bir `@SpringBootTest` istisnası var. Güvenlik entegrasyon testi aynı deseni izler.
2. **`GET /api/v1/tarbil-extension/submissions/by-vaccination/{vaccinationRecordId}` eklenir.** Vetly'deki "TARBİL'e aktar" butonu yalnızca aşı kaydı id'sini bilir; eklentinin bundan kuyruk kaydını bulması gerekir.
3. **Eklenti izinlerine `alarms` eklenir.** Çevrimdışı onay kuyruğunun dakikada bir boşaltılması için (spec §8).
4. **`StaffUserLookupPort`'a `isActive(UUID)` eklenir.** Pasifleştirilen personelin eklenti anahtarı da reddedilmeli (spec'te açıkça yoktu; güvenlik açığı olarak eklendi).

## Review Focus

1. **Aşı kuyruğa girdikten sonra Vetly'de iptal edilir/silinir** → bekleyenlerde görünmez, tekil sorgu 404 döner. Test: Task 6 `should_skipCancelledAndMissingVaccinations_when_listingPending`, `should_throwNotFound_when_vaccinationCancelled`.
2. **Aynı aşı için olay iki kez yayınlanır** (kayıt + sonradan "yapıldı") → tek satır. Test: Task 2 `should_notCreateSecondRow_when_vaccinationAlreadyQueued`.
3. **Anahtar iptal edilir ya da personel pasifleştirilir; eklenti açıkken istek atar** → 401, eklenti eşleştirme ekranına döner. Test: Task 4 `should_reject_when_staffInactive`, Task 5 `should_returnUnauthenticated_when_tokenRevoked`, Task 10 `should_clearTokenAndThrow_when_401`.
4. **Hekim TARBİL'e kaydeder ama Vetly'ye ulaşılamıyor** → onay yerelde saklanır, bağlantı gelince gönderilir; iki kez gönderilse de tek kayıt. Test: Task 6 `should_returnExistingWithoutChange_when_alreadySubmitted`, Task 10 `should_keepEntry_when_flushFails`.
5. **Aşı adı varyantları** ("Kuduz  Aşısı ", "KUDUZ AŞISI", "İ/ı") aynı eşleştirmeye düşmeli. Test: Task 3 `should_normalizeTurkishCaseAndWhitespace`.

---

## Dosya Yapısı

**Backend — `backend/src/main/java/com/vetos/`**

| Dosya | Sorumluluk |
|---|---|
| `modules/encounter/domain/VaccinationTarbilView.java` (yeni) | TARBİL için aşı görünümü (record) |
| `modules/encounter/domain/VaccinationLookupPort.java` (değişir) | `findForTarbil(UUID)` eklenir |
| `modules/encounter/infrastructure/persistence/VaccinationLookupAdapter.java` (değişir) | Uygulama |
| `modules/patient/domain/PatientTarbilProfile.java` (yeni) | TARBİL için hasta profili (record) |
| `modules/patient/domain/PatientLookupPort.java` (değişir) | `findTarbilProfile(UUID)` eklenir |
| `modules/patient/infrastructure/persistence/PatientLookupAdapter.java` (değişir) | Uygulama |
| `modules/tenant/domain/StaffUserLookupPort.java` (değişir) | `isActive(UUID)` eklenir |
| `modules/tenant/infrastructure/persistence/StaffUserLookupAdapter.java` (değişir) | Uygulama |
| `platform/security/ExtensionTokenAuthenticator.java` (yeni) | Port: ham anahtar → `AuthenticatedStaffUser` |
| `platform/security/ExtensionTokenAuthenticationFilter.java` (yeni) | `Bearer vtx_…` doğrulama filtresi |
| `platform/security/TarbilExtensionSecurityConfig.java` (yeni) | `@Order(2)` zincir, `/api/v1/tarbil-extension/**` |
| `platform/web/RateLimitFilter.java` (değişir) | `/api/v1/tarbil-extension/pair` için ayrı bucket |
| `modules/integration/tarbil/package-info.java` (değişir) | `allowedDependencies` genişler |
| `modules/integration/tarbil/domain/TarbilSyncLog.java` (yeniden yazılır) | Aşı aktarım kaydı + durum geçişleri |
| `modules/integration/tarbil/domain/TarbilSyncStatus.java` (değişir) | `PENDING, SUBMITTED, DISMISSED` |
| `modules/integration/tarbil/domain/TarbilConfirmationMethod.java` (yeni) | `AUTO, MANUAL` |
| `modules/integration/tarbil/domain/TarbilSyncLogRepository.java` (değişir) | Sorgular |
| `modules/integration/tarbil/domain/TarbilValueMapping*.java` (yeni) | Öğrenilen eşleştirme entity + repo + `TarbilMappingKind` |
| `modules/integration/tarbil/domain/TarbilExtensionToken*.java` (yeni) | Anahtar entity + repo |
| `modules/integration/tarbil/domain/VaccineKeyNormalizer.java` (yeni) | Aşı adı → eşleştirme anahtarı |
| `modules/integration/tarbil/domain/ExtensionSecrets.java` (yeni) | Kod/anahtar üretimi + SHA-256 |
| `modules/integration/tarbil/domain/exception/*.java` (yeni) | Hata tipleri |
| `modules/integration/tarbil/application/*.java` | Use-case'ler (aşağıda task'larda) |
| `modules/integration/tarbil/infrastructure/security/TarbilExtensionTokenAuthenticatorAdapter.java` (yeni) | Port uygulaması |
| `modules/integration/tarbil/infrastructure/persistence/*` | JPA repo + adapter'lar |
| `modules/integration/tarbil/api/TarbilController.java` (değişir) | Web uçları |
| `modules/integration/tarbil/api/TarbilExtensionController.java` (yeni) | Eklenti uçları |
| `resources/db/migration/V59__tarbil_submission_rework.sql` | `tarbil_sync_log` yeniden şekillenir |
| `resources/db/migration/V60__tarbil_value_mapping.sql` | Yeni tablo |
| `resources/db/migration/V61__tarbil_extension_token.sql` | Yeni tablo |

**Silinenler:** `MockTarbilAdapter`, `TarbilSyncPort`, `TarbilSyncRequest`, `TarbilSyncOutcome`, `TarbilSyncExecutor`, `TarbilRetryScheduler`, `RetryDueTarbilSyncsUseCase`, `RetryTarbilSyncUseCase`, `PatientIdentificationUpdatedEventListener` ve testleri `RetryDueTarbilSyncsUseCaseTest`, `RetryTarbilSyncUseCaseTest`, `TarbilSyncExecutorBackoffTest`.

**Frontend — `frontend/src/`:** `api/tarbilApi.ts` (değişir), `pages/settings/tarbilStatus.tsx` (değişir), `pages/settings/IntegrationsPanel.tsx` (yeniden yazılır), `pages/settings/TarbilExtensionCard.tsx` (yeni), `pages/settings/TarbilMappingsCard.tsx` (yeni), `lib/tarbilExtension.ts` (yeni), `pages/vaccinations/VaccinationsPage.tsx` (değişir), `vite-env.d.ts` (değişir).

**Eklenti — `extension/`:** `package.json`, `tsconfig.json`, `vite.config.ts`, `vite.content.config.ts`, `vitest.config.ts`, `public/manifest.json`, `scripts/extension-id.mjs`, `sidepanel.html`, `src/shared/{types,messages,config}.ts`, `src/background/{index,tokenStore,vetlyApi,confirmationOutbox,router,chromeStorage}.ts`, `src/sidepanel/{main,App,PairingView,PendingList,SubmissionCard,useBackground}.tsx`, `src/tarbil/content.ts`, testler `src/**/*.test.ts`.

---

### Task 1: Lookup portlarını TARBİL ihtiyacı için genişlet

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/encounter/domain/VaccinationTarbilView.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/VaccinationLookupPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/infrastructure/persistence/VaccinationLookupAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/patient/domain/PatientTarbilProfile.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/PatientLookupPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/PatientLookupAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/StaffUserLookupPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/StaffUserLookupAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/encounter/infrastructure/persistence/VaccinationLookupAdapterTest.java`
- Test: `backend/src/test/java/com/vetos/modules/patient/infrastructure/persistence/PatientLookupAdapterTest.java`
- Test: `backend/src/test/java/com/vetos/modules/tenant/infrastructure/persistence/StaffUserLookupAdapterTest.java`

**Interfaces:**
- Produces:
  - `record VaccinationTarbilView(UUID id, UUID tenantId, UUID patientId, String vaccineName, String lotNumber, LocalDate administeredDate, VaccinationStatus status)`
  - `Optional<VaccinationTarbilView> VaccinationLookupPort.findForTarbil(UUID vaccinationRecordId)`
  - `record PatientTarbilProfile(UUID id, String name, String microchipNumber, UUID speciesId, String speciesName, String breedName, Sex sex, LocalDate birthDate)`
  - `Optional<PatientTarbilProfile> PatientLookupPort.findTarbilProfile(UUID patientId)` (hasta yoksa veya aktif kiracıya ait değilse boş)
  - `boolean StaffUserLookupPort.isActive(UUID staffUserId)` (yoksa `false`)

- [ ] **Step 1: Failing testleri yaz**

`VaccinationLookupAdapterTest.java`:
```java
package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaccinationLookupAdapterTest {

    @Mock private VaccinationRecordJpaRepository jpaRepository;

    @Test
    void should_mapAllFields_when_findForTarbil() {
        UUID id = UUID.randomUUID();
        VaccinationRecord record = mock(VaccinationRecord.class);
        when(record.getId()).thenReturn(id);
        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        when(record.getTenantId()).thenReturn(tenantId);
        when(record.getPatientId()).thenReturn(patientId);
        when(record.getVaccineName()).thenReturn("Kuduz");
        when(record.getLotNumber()).thenReturn("L-42");
        when(record.getAdministeredDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(record.getStatus()).thenReturn(VaccinationStatus.ADMINISTERED);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(record));

        Optional<VaccinationTarbilView> view = new VaccinationLookupAdapter(jpaRepository).findForTarbil(id);

        assertThat(view).contains(new VaccinationTarbilView(
            id, tenantId, patientId, "Kuduz", "L-42", LocalDate.of(2026, 10, 1), VaccinationStatus.ADMINISTERED));
    }

    @Test
    void should_returnEmpty_when_recordMissing() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(new VaccinationLookupAdapter(jpaRepository).findForTarbil(id)).isEmpty();
    }
}
```

`PatientLookupAdapterTest.java`:
```java
package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.Breed;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.Species;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientLookupAdapterTest {

    @Mock private PatientJpaRepository patientJpaRepository;
    @Mock private SpeciesJpaRepository speciesJpaRepository;
    @Mock private BreedJpaRepository breedJpaRepository;

    @Test
    void should_buildProfileWithSpeciesAndBreedNames_when_patientExists() {
        UUID patientId = UUID.randomUUID();
        UUID speciesId = UUID.randomUUID();
        UUID breedId = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getName()).thenReturn("Pamuk");
        when(patient.getMicrochipNumber()).thenReturn("900123456789012");
        when(patient.getSpeciesId()).thenReturn(speciesId);
        when(patient.getBreedId()).thenReturn(breedId);
        when(patient.getSex()).thenReturn(Sex.FEMALE);
        when(patient.getBirthDate()).thenReturn(LocalDate.of(2023, 5, 1));
        Species species = mock(Species.class);
        when(species.getName()).thenReturn("Kedi");
        Breed breed = mock(Breed.class);
        when(breed.getName()).thenReturn("Van Kedisi");
        when(patientJpaRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(speciesJpaRepository.findById(speciesId)).thenReturn(Optional.of(species));
        when(breedJpaRepository.findById(breedId)).thenReturn(Optional.of(breed));

        Optional<PatientTarbilProfile> profile =
            new PatientLookupAdapter(patientJpaRepository, speciesJpaRepository, breedJpaRepository).findTarbilProfile(patientId);

        assertThat(profile).contains(new PatientTarbilProfile(
            patientId, "Pamuk", "900123456789012", speciesId, "Kedi", "Van Kedisi", Sex.FEMALE, LocalDate.of(2023, 5, 1)));
    }

    @Test
    void should_returnEmpty_when_patientMissing() {
        UUID patientId = UUID.randomUUID();
        when(patientJpaRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThat(new PatientLookupAdapter(patientJpaRepository, speciesJpaRepository, breedJpaRepository)
            .findTarbilProfile(patientId)).isEmpty();
    }
}
```

`StaffUserLookupAdapterTest.java`:
```java
package com.vetos.modules.tenant.infrastructure.persistence;

import com.vetos.modules.tenant.domain.StaffUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffUserLookupAdapterTest {

    @Mock private StaffUserJpaRepository jpaRepository;

    @Test
    void should_returnActiveFlag_when_staffExists() {
        UUID id = UUID.randomUUID();
        StaffUser staffUser = mock(StaffUser.class);
        when(staffUser.isActive()).thenReturn(false);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(staffUser));

        assertThat(new StaffUserLookupAdapter(jpaRepository).isActive(id)).isFalse();
    }

    @Test
    void should_returnFalse_when_staffMissing() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(new StaffUserLookupAdapter(jpaRepository).isActive(id)).isFalse();
    }
}
```

- [ ] **Step 2: Testlerin derlenmediğini/başarısız olduğunu gör**

Run: `cd backend && ./mvnw test -Dtest='VaccinationLookupAdapterTest,PatientLookupAdapterTest,StaffUserLookupAdapterTest'`
Expected: Derleme hatası (`VaccinationTarbilView`, `PatientTarbilProfile`, `findTarbilProfile`, `isActive` yok).

- [ ] **Step 3: Uygulamayı yaz**

`VaccinationTarbilView.java`:
```java
package com.vetos.modules.encounter.domain;

import java.time.LocalDate;
import java.util.UUID;

/** integration/tarbil modulunun asi aktarimi icin ihtiyac duydugu, salt-okunur gorunum. */
public record VaccinationTarbilView(
    UUID id, UUID tenantId, UUID patientId, String vaccineName, String lotNumber,
    LocalDate administeredDate, VaccinationStatus status
) {}
```

`VaccinationLookupPort.java` — arayüze ekle (mevcut `findDueForReminder` kalır, gerekli import'lar: `java.util.Optional`, `java.util.UUID`):
```java
    /** integration/tarbil icin -- kiraci kontrolu cagiranin sorumlulugundadir (tenantId gorunumde). */
    Optional<VaccinationTarbilView> findForTarbil(UUID vaccinationRecordId);
```

`VaccinationLookupAdapter.java` — sınıfa ekle (import `com.vetos.modules.encounter.domain.VaccinationTarbilView`, `java.util.Optional`):
```java
    @Override
    public Optional<VaccinationTarbilView> findForTarbil(UUID vaccinationRecordId) {
        return jpaRepository.findById(vaccinationRecordId).map(r -> new VaccinationTarbilView(
            r.getId(), r.getTenantId(), r.getPatientId(), r.getVaccineName(), r.getLotNumber(),
            r.getAdministeredDate(), r.getStatus()
        ));
    }
```

`PatientTarbilProfile.java`:
```java
package com.vetos.modules.patient.domain;

import java.time.LocalDate;
import java.util.UUID;

/** integration/tarbil icin hasta profili -- sahip kisisel verisi (TC/adres/telefon) BILINCLI olarak yok. */
public record PatientTarbilProfile(
    UUID id, String name, String microchipNumber, UUID speciesId, String speciesName,
    String breedName, Sex sex, LocalDate birthDate
) {}
```

`PatientLookupPort.java` — ekle (import `java.util.Optional`):
```java
    /** Hasta yoksa (veya aktif kiracinin @TenantId filtresine takiliyorsa) bos doner -- firlatmaz. */
    Optional<PatientTarbilProfile> findTarbilProfile(UUID patientId);
```

`PatientLookupAdapter.java` — `BreedJpaRepository` alanını ekle ve metodu yaz:
```java
    private final PatientJpaRepository jpaRepository;
    private final SpeciesJpaRepository speciesJpaRepository;
    private final BreedJpaRepository breedJpaRepository;

    @Override
    public Optional<PatientTarbilProfile> findTarbilProfile(UUID patientId) {
        return jpaRepository.findById(patientId).map(p -> new PatientTarbilProfile(
            p.getId(), p.getName(), p.getMicrochipNumber(), p.getSpeciesId(),
            speciesJpaRepository.findById(p.getSpeciesId()).map(Species::getName).orElse(null),
            p.getBreedId() == null ? null : breedJpaRepository.findById(p.getBreedId()).map(Breed::getName).orElse(null),
            p.getSex(), p.getBirthDate()
        ));
    }
```
(import `com.vetos.modules.patient.domain.Breed`, `PatientTarbilProfile`, `java.util.Optional`. `BreedJpaRepository` aynı paketteyse import gerekmez.)

`StaffUserLookupPort.java` — ekle:
```java
    /** Kayit yoksa false -- eklenti anahtari dogrulamasi pasif personeli reddetmek icin kullanir. */
    boolean isActive(UUID staffUserId);
```

`StaffUserLookupAdapter.java` — ekle:
```java
    @Override
    public boolean isActive(UUID staffUserId) {
        return jpaRepository.findById(staffUserId).map(StaffUser::isActive).orElse(false);
    }
```
(`StaffUser`'da `active` alanının getter'ı Lombok ile `isActive()`; yoksa `StaffUser`'a `public boolean isActive() { return active; }` ekle.)

- [ ] **Step 4: Testleri ve modül kontrolünü çalıştır**

Run: `cd backend && ./mvnw test -Dtest='VaccinationLookupAdapterTest,PatientLookupAdapterTest,StaffUserLookupAdapterTest,ApplicationModulesTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/encounter backend/src/main/java/com/vetos/modules/patient backend/src/main/java/com/vetos/modules/tenant backend/src/test/java/com/vetos/modules/encounter backend/src/test/java/com/vetos/modules/patient backend/src/test/java/com/vetos/modules/tenant
git commit -m "feat: TARBIL aktarimi icin asi/hasta/personel lookup portlarini genislet"
```

---

### Task 2: Sahte senkronu kaldır, aşı aktarım kaydını yeniden şekillendir

**Files:**
- Delete: `.../tarbil/infrastructure/adapter/MockTarbilAdapter.java`, `.../tarbil/domain/TarbilSyncPort.java`, `.../tarbil/domain/TarbilSyncRequest.java`, `.../tarbil/domain/TarbilSyncOutcome.java`, `.../tarbil/application/TarbilSyncExecutor.java`, `.../tarbil/infrastructure/scheduling/TarbilRetryScheduler.java`, `.../tarbil/application/RetryDueTarbilSyncsUseCase.java`, `.../tarbil/application/RetryTarbilSyncUseCase.java`, `.../tarbil/infrastructure/event/PatientIdentificationUpdatedEventListener.java`
- Delete tests: `.../tarbil/application/RetryDueTarbilSyncsUseCaseTest.java`, `.../tarbil/application/RetryTarbilSyncUseCaseTest.java`, `.../tarbil/application/TarbilSyncExecutorBackoffTest.java`
- Rewrite: `.../tarbil/domain/TarbilSyncLog.java`, `.../tarbil/domain/TarbilSyncStatus.java`, `.../tarbil/domain/TarbilSyncLogRepository.java`, `.../tarbil/infrastructure/persistence/TarbilSyncLogJpaRepository.java`, `.../tarbil/infrastructure/persistence/TarbilSyncLogRepositoryAdapter.java`, `.../tarbil/application/QueueTarbilSyncUseCase.java`, `.../tarbil/infrastructure/event/VaccinationRecordedEventListener.java`, `.../tarbil/application/GetTarbilStatusSummaryUseCase.java`, `.../tarbil/application/dto/TarbilStatusSummary.java`, `.../tarbil/application/ListTarbilSyncLogsUseCase.java`, `.../tarbil/application/dto/TarbilSyncLogSummary.java`, `.../tarbil/api/dto/TarbilStatusResponse.java`, `.../tarbil/api/dto/TarbilSyncLogResponse.java`, `.../tarbil/api/TarbilController.java`
- Create: `.../tarbil/domain/TarbilConfirmationMethod.java`, `.../tarbil/domain/exception/TarbilSubmissionStateConflictException.java`, `backend/src/main/resources/db/migration/V59__tarbil_submission_rework.sql`
- Modify: `.../tarbil/package-info.java`
- Test: `.../tarbil/domain/TarbilSyncLogTest.java` (yeniden yazılır), `.../tarbil/application/QueueTarbilSyncUseCaseTest.java` (yeni)

(`...` = `backend/src/main/java/com/vetos/modules/integration` veya `backend/src/test/java/com/vetos/modules/integration`.)

**Interfaces:**
- Consumes: Task 1 portları (sadece package-info izinleri bu task'ta açılır).
- Produces:
  - `enum TarbilSyncStatus { PENDING, SUBMITTED, DISMISSED }`
  - `enum TarbilConfirmationMethod { AUTO, MANUAL }`
  - `TarbilSyncLog.queueVaccination(UUID tenantId, UUID patientId, UUID vaccinationRecordId)`
  - `boolean TarbilSyncLog.markSubmitted(UUID staffId, TarbilConfirmationMethod method, String tarbilReference, Instant now)` — zaten `SUBMITTED` ise `false` döner, hiçbir alanı değiştirmez; `DISMISSED` ise `TarbilSubmissionStateConflictException`
  - `void TarbilSyncLog.dismiss(UUID staffId, String reason, Instant now)` — yalnız `PENDING`'den; aksi halde conflict
  - `void TarbilSyncLog.restore()` — yalnız `DISMISSED`'tan `PENDING`'e; aksi halde conflict
  - Getter'lar: `getId, getTenantId, getPatientId, getVaccinationRecordId, getStatus, getQueuedAt, getSubmittedAt, getSubmittedByStaffId, getConfirmationMethod, getTarbilReference, getDismissedReason, getDismissedAt, getDismissedByStaffId`
  - `TarbilSyncLogRepository`: `save`, `findById(UUID)`, `findByTenantId(UUID)`, `findByTenantIdAndStatus(UUID, TarbilSyncStatus)`, `findByVaccinationRecordId(UUID)`
  - `UUID QueueTarbilSyncUseCase.queueVaccination(UUID patientId, UUID vaccinationRecordId)` — idempotent
  - `record TarbilStatusSummary(long pendingCount, long submittedCount, long dismissedCount, Instant lastSubmittedAt)`
  - `record TarbilSyncLogSummary(UUID id, UUID patientId, String patientName, String vaccineName, LocalDate administeredDate, TarbilSyncStatus status, Instant queuedAt, Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference, String dismissedReason)`

- [ ] **Step 1: Domain testini yeniden yaz**

`TarbilSyncLogTest.java` (dosyanın tamamı):
```java
package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionStateConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarbilSyncLogTest {

    private final UUID staffId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-02T10:00:00Z");

    private TarbilSyncLog aLog() {
        return TarbilSyncLog.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void should_startPending_when_queued() {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), vaccinationId);

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        assertThat(log.getVaccinationRecordId()).isEqualTo(vaccinationId);
        assertThat(log.getQueuedAt()).isNotNull();
    }

    @Test
    void should_recordSubmission_when_markSubmittedFromPending() {
        TarbilSyncLog log = aLog();

        boolean changed = log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", now);

        assertThat(changed).isTrue();
        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        assertThat(log.getSubmittedByStaffId()).isEqualTo(staffId);
        assertThat(log.getConfirmationMethod()).isEqualTo(TarbilConfirmationMethod.AUTO);
        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        assertThat(log.getSubmittedAt()).isEqualTo(now);
    }

    @Test
    void should_keepFirstSubmission_when_markSubmittedTwice() {
        TarbilSyncLog log = aLog();
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", now);

        boolean changed = log.markSubmitted(UUID.randomUUID(), TarbilConfirmationMethod.MANUAL, null, now.plusSeconds(60));

        assertThat(changed).isFalse();
        assertThat(log.getConfirmationMethod()).isEqualTo(TarbilConfirmationMethod.AUTO);
        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        assertThat(log.getSubmittedAt()).isEqualTo(now);
    }

    @Test
    void should_throwConflict_when_markSubmittedWhileDismissed() {
        TarbilSyncLog log = aLog();
        log.dismiss(staffId, "Bildirim gerekmiyor", now);

        assertThatThrownBy(() -> log.markSubmitted(staffId, TarbilConfirmationMethod.MANUAL, null, now))
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }

    @Test
    void should_dismissAndRestore_when_pending() {
        TarbilSyncLog log = aLog();

        log.dismiss(staffId, "Karma asi", now);
        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.DISMISSED);
        assertThat(log.getDismissedReason()).isEqualTo("Karma asi");

        log.restore();
        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        assertThat(log.getDismissedReason()).isNull();
        assertThat(log.getDismissedAt()).isNull();
    }

    @Test
    void should_throwConflict_when_dismissingSubmitted() {
        TarbilSyncLog log = aLog();
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, null, now);

        assertThatThrownBy(() -> log.dismiss(staffId, "x", now))
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }

    @Test
    void should_throwConflict_when_restoringPending() {
        assertThatThrownBy(() -> aLog().restore())
            .isInstanceOf(TarbilSubmissionStateConflictException.class);
    }
}
```

`QueueTarbilSyncUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueTarbilSyncUseCaseTest {

    @Mock private TarbilSyncLogRepository repository;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach void setTenant() { TenantContext.set(tenantId); }
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void should_createPendingRow_when_vaccinationNotQueued() {
        UUID patientId = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        when(repository.findByVaccinationRecordId(vaccinationId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new QueueTarbilSyncUseCase(repository).queueVaccination(patientId, vaccinationId);

        ArgumentCaptor<TarbilSyncLog> captor = ArgumentCaptor.forClass(TarbilSyncLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getVaccinationRecordId()).isEqualTo(vaccinationId);
    }

    @Test
    void should_notCreateSecondRow_when_vaccinationAlreadyQueued() {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSyncLog existing = TarbilSyncLog.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId);
        when(repository.findByVaccinationRecordId(vaccinationId)).thenReturn(Optional.of(existing));

        new QueueTarbilSyncUseCase(repository).queueVaccination(UUID.randomUUID(), vaccinationId);

        verify(repository, never()).save(any());
    }
}
```

- [ ] **Step 2: Testlerin başarısız olduğunu gör**

Run: `cd backend && ./mvnw test -Dtest='TarbilSyncLogTest,QueueTarbilSyncUseCaseTest'`
Expected: Derleme hatası (`queueVaccination`, `TarbilConfirmationMethod`, `TarbilSubmissionStateConflictException` yok).

- [ ] **Step 3: Eski retry/mock dosyalarını sil**

```bash
cd backend
git rm src/main/java/com/vetos/modules/integration/tarbil/infrastructure/adapter/MockTarbilAdapter.java \
  src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncPort.java \
  src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncRequest.java \
  src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncOutcome.java \
  src/main/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutor.java \
  src/main/java/com/vetos/modules/integration/tarbil/infrastructure/scheduling/TarbilRetryScheduler.java \
  src/main/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCase.java \
  src/main/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCase.java \
  src/main/java/com/vetos/modules/integration/tarbil/infrastructure/event/PatientIdentificationUpdatedEventListener.java \
  src/test/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCaseTest.java \
  src/test/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCaseTest.java \
  src/test/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutorBackoffTest.java
```

- [ ] **Step 4: Domain ve exception**

`TarbilSyncStatus.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

/** Sunucu hicbir kaydi kendi basina SUBMITTED yapmaz -- yalnizca eklenti/hekim onayiyla. */
public enum TarbilSyncStatus { PENDING, SUBMITTED, DISMISSED }
```

`TarbilConfirmationMethod.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

/** AUTO: eklenti TARBIL basari mesajini yakaladi. MANUAL: hekim elle isaretledi. */
public enum TarbilConfirmationMethod { AUTO, MANUAL }
```

`exception/TarbilSubmissionStateConflictException.java`:
```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.platform.exception.DomainException;

public class TarbilSubmissionStateConflictException extends DomainException {
    public TarbilSubmissionStateConflictException(TarbilSyncStatus current, String action) {
        super("TARBIL_SUBMISSION_STATE_CONFLICT", "TARBIL kaydi '" + current + "' durumundayken " + action + " yapilamaz");
    }
}
```

`TarbilSyncLog.java` (dosyanın tamamı):
```java
package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionStateConflictException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Bir asinin TARBIL'e aktarim durumu. Sunucu TARBIL'e hicbir sey gondermez;
 * gonderimi hekim TARBIL arayuzunde yapar, eklenti (ya da hekim elle) bunu
 * buraya bildirir. Bkz. docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md.
 */
@Entity
@Table(name = "tarbil_sync_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "vaccination_record_id", nullable = false, unique = true)
    private UUID vaccinationRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_type", nullable = false)
    private TarbilSyncType syncType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TarbilSyncStatus status;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by_staff_id")
    private UUID submittedByStaffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_method")
    private TarbilConfirmationMethod confirmationMethod;

    @Column(name = "tarbil_reference")
    private String tarbilReference;

    @Column(name = "dismissed_reason")
    private String dismissedReason;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "dismissed_by_staff_id")
    private UUID dismissedByStaffId;

    public static TarbilSyncLog queueVaccination(UUID tenantId, UUID patientId, UUID vaccinationRecordId) {
        TarbilSyncLog log = new TarbilSyncLog();
        log.tenantId = tenantId;
        log.patientId = patientId;
        log.vaccinationRecordId = vaccinationRecordId;
        log.syncType = TarbilSyncType.VACCINATION;
        log.status = TarbilSyncStatus.PENDING;
        log.queuedAt = Instant.now();
        return log;
    }

    /** @return true ise durum degisti; false ise zaten SUBMITTED'di (idempotent, ilk kayit korunur). */
    public boolean markSubmitted(UUID staffId, TarbilConfirmationMethod method, String reference, Instant now) {
        if (status == TarbilSyncStatus.SUBMITTED) {
            return false;
        }
        if (status == TarbilSyncStatus.DISMISSED) {
            throw new TarbilSubmissionStateConflictException(status, "gonderildi isaretlemesi");
        }
        this.status = TarbilSyncStatus.SUBMITTED;
        this.submittedByStaffId = staffId;
        this.confirmationMethod = method;
        this.tarbilReference = reference;
        this.submittedAt = now;
        return true;
    }

    public void dismiss(UUID staffId, String reason, Instant now) {
        if (status != TarbilSyncStatus.PENDING) {
            throw new TarbilSubmissionStateConflictException(status, "bildirilmeyecek isaretlemesi");
        }
        this.status = TarbilSyncStatus.DISMISSED;
        this.dismissedReason = reason;
        this.dismissedByStaffId = staffId;
        this.dismissedAt = now;
    }

    public void restore() {
        if (status != TarbilSyncStatus.DISMISSED) {
            throw new TarbilSubmissionStateConflictException(status, "geri alma");
        }
        this.status = TarbilSyncStatus.PENDING;
        this.dismissedReason = null;
        this.dismissedByStaffId = null;
        this.dismissedAt = null;
    }
}
```

- [ ] **Step 5: Repository**

`TarbilSyncLogRepository.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilSyncLogRepository {
    TarbilSyncLog save(TarbilSyncLog log);
    Optional<TarbilSyncLog> findById(UUID id);
    List<TarbilSyncLog> findByTenantId(UUID tenantId);
    List<TarbilSyncLog> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSyncLog> findByVaccinationRecordId(UUID vaccinationRecordId);
}
```

`TarbilSyncLogJpaRepository.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilSyncLogJpaRepository extends JpaRepository<TarbilSyncLog, UUID> {
    List<TarbilSyncLog> findByTenantId(UUID tenantId);
    List<TarbilSyncLog> findByTenantIdAndStatusOrderByQueuedAtAsc(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSyncLog> findByVaccinationRecordId(UUID vaccinationRecordId);
}
```

`TarbilSyncLogRepositoryAdapter.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilSyncLogRepositoryAdapter implements TarbilSyncLogRepository {

    private final TarbilSyncLogJpaRepository jpaRepository;

    @Override public TarbilSyncLog save(TarbilSyncLog log) { return jpaRepository.save(log); }
    @Override public Optional<TarbilSyncLog> findById(UUID id) { return jpaRepository.findById(id); }
    @Override public List<TarbilSyncLog> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<TarbilSyncLog> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status) {
        return jpaRepository.findByTenantIdAndStatusOrderByQueuedAtAsc(tenantId, status);
    }

    @Override
    public Optional<TarbilSyncLog> findByVaccinationRecordId(UUID vaccinationRecordId) {
        return jpaRepository.findByVaccinationRecordId(vaccinationRecordId);
    }
}
```

- [ ] **Step 6: Kuyruklama ve olay dinleyici**

`QueueTarbilSyncUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Asiyi "TARBIL'e aktarilmayi bekliyor" olarak kuyruga ekler. Disariya hicbir sey
 * gondermez. Ayni asi icin ikinci cagri (kayit + sonradan "yapildi") yeni satir
 * olusturmaz.
 */
@Service
@RequiredArgsConstructor
public class QueueTarbilSyncUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;

    @Transactional
    public UUID queueVaccination(UUID patientId, UUID vaccinationRecordId) {
        // Cagiran: VaccinationRecordedEventListener -- kimligi dogrulanmis bir istek
        // icindeki senkron @EventListener, TenantContext kurulu.
        return tarbilSyncLogRepository.findByVaccinationRecordId(vaccinationRecordId)
            .map(TarbilSyncLog::getId)
            .orElseGet(() -> tarbilSyncLogRepository.save(
                TarbilSyncLog.queueVaccination(TenantContext.current(), patientId, vaccinationRecordId)
            ).getId());
    }
}
```

`VaccinationRecordedEventListener.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.event;

import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.integration.tarbil.application.QueueTarbilSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class VaccinationRecordedEventListener {

    private final QueueTarbilSyncUseCase queueTarbilSyncUseCase;

    @EventListener
    void onVaccinationRecorded(VaccinationRecordedEvent event) {
        queueTarbilSyncUseCase.queueVaccination(event.patientId(), event.vaccinationRecordId());
    }
}
```

- [ ] **Step 7: Durum özeti ve log listesi**

`dto/TarbilStatusSummary.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

import java.time.Instant;

public record TarbilStatusSummary(long pendingCount, long submittedCount, long dismissedCount, Instant lastSubmittedAt) {}
```

`GetTarbilStatusSummaryUseCase.java` — `execute` gövdesi:
```java
    @Transactional(readOnly = true)
    public TarbilStatusSummary execute(UUID tenantId) {
        List<TarbilSyncLog> logs = tarbilSyncLogRepository.findByTenantId(tenantId);
        long pending = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.PENDING).count();
        long submitted = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.SUBMITTED).count();
        long dismissed = logs.stream().filter(l -> l.getStatus() == TarbilSyncStatus.DISMISSED).count();
        Instant lastSubmittedAt = logs.stream()
            .map(TarbilSyncLog::getSubmittedAt)
            .filter(java.util.Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);
        return new TarbilStatusSummary(pending, submitted, dismissed, lastSubmittedAt);
    }
```

`dto/TarbilSyncLogSummary.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TarbilSyncLogSummary(
    UUID id, UUID patientId, String patientName, String vaccineName, LocalDate administeredDate,
    TarbilSyncStatus status, Instant queuedAt, Instant submittedAt,
    TarbilConfirmationMethod confirmationMethod, String tarbilReference, String dismissedReason
) {}
```

`ListTarbilSyncLogsUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSyncLogSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListTarbilSyncLogsUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final PatientLookupPort patientLookupPort;
    private final VaccinationLookupPort vaccinationLookupPort;

    @Transactional(readOnly = true)
    public List<TarbilSyncLogSummary> execute(UUID tenantId) {
        return tarbilSyncLogRepository.findByTenantId(tenantId).stream()
            .map(log -> {
                var vaccination = vaccinationLookupPort.findForTarbil(log.getVaccinationRecordId());
                return new TarbilSyncLogSummary(
                    log.getId(), log.getPatientId(),
                    patientLookupPort.findTarbilProfile(log.getPatientId()).map(PatientTarbilProfile::name).orElse("—"),
                    vaccination.map(VaccinationTarbilView::vaccineName).orElse("—"),
                    vaccination.map(VaccinationTarbilView::administeredDate).orElse(null),
                    log.getStatus(), log.getQueuedAt(), log.getSubmittedAt(),
                    log.getConfirmationMethod(), log.getTarbilReference(), log.getDismissedReason()
                );
            })
            .sorted(Comparator.comparing(TarbilSyncLogSummary::queuedAt).reversed())
            .toList();
    }
}
```

`api/dto/TarbilStatusResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilStatusSummary;

import java.time.Instant;

public record TarbilStatusResponse(long pendingCount, long submittedCount, long dismissedCount, Instant lastSubmittedAt) {
    public static TarbilStatusResponse from(TarbilStatusSummary s) {
        return new TarbilStatusResponse(s.pendingCount(), s.submittedCount(), s.dismissedCount(), s.lastSubmittedAt());
    }
}
```

`api/dto/TarbilSyncLogResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSyncLogSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TarbilSyncLogResponse(
    UUID id, UUID patientId, String patientName, String vaccineName, LocalDate administeredDate,
    TarbilSyncStatus status, Instant queuedAt, Instant submittedAt,
    TarbilConfirmationMethod confirmationMethod, String tarbilReference, String dismissedReason
) {
    public static TarbilSyncLogResponse from(TarbilSyncLogSummary s) {
        return new TarbilSyncLogResponse(s.id(), s.patientId(), s.patientName(), s.vaccineName(), s.administeredDate(),
            s.status(), s.queuedAt(), s.submittedAt(), s.confirmationMethod(), s.tarbilReference(), s.dismissedReason());
    }
}
```

`TarbilController.java` — `RetryTarbilSyncUseCase` alanını ve `retry` metodunu sil; sınıf seviyesindeki `@PreAuthorize("hasRole('ADMIN')")` satırını `@PreAuthorize("hasAnyRole('ADMIN','VET')")` yap. (Yeni uçlar Task 7'de.)

`package-info.java`:
```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration: TARBIL",
    allowedDependencies = {
        "modules.patient::domain", "modules.encounter::domain", "modules.encounter::domain.event",
        "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception"
    }
)
package com.vetos.modules.integration.tarbil;
```

- [ ] **Step 8: Migration**

`V59__tarbil_submission_rework.sql`:
```sql
-- TARBIL eklentisi Faz 1 (bkz. docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md S5.1-5.2).
-- MockTarbilAdapter'in SYNCED/FAILED isaretledigi kayitlarin HICBIRI TARBIL'e gercekten
-- gitmedi -- hepsi "bekliyor"a doner. Kimliklendirme/tedavi kuyruklamasi durduruldu;
-- o turlerdeki satirlar da hic gonderilmedigi icin silinmesi bilgi kaybi degil.
DELETE FROM tarbil_sync_log WHERE sync_type <> 'VACCINATION';
UPDATE tarbil_sync_log SET status = 'PENDING' WHERE status IN ('SYNCED', 'FAILED');

ALTER TABLE tarbil_sync_log ADD COLUMN vaccination_record_id UUID;
UPDATE tarbil_sync_log t
SET vaccination_record_id = v.id
FROM vaccination_records v
WHERE v.patient_id = t.patient_id
  AND t.payload IS NOT NULL
  AND v.vaccine_name = (t.payload::jsonb ->> 'vaccineName')
  AND v.administered_date = (t.payload::jsonb ->> 'administeredDate')::date;
-- Esletirilemeyen (kaydi silinmis) ve ayni asiya dusen mukerrer satirlar atilir.
DELETE FROM tarbil_sync_log WHERE vaccination_record_id IS NULL;
DELETE FROM tarbil_sync_log a USING tarbil_sync_log b
WHERE a.vaccination_record_id = b.vaccination_record_id AND a.id > b.id;
ALTER TABLE tarbil_sync_log ALTER COLUMN vaccination_record_id SET NOT NULL;
CREATE UNIQUE INDEX uq_tarbil_sync_log_vaccination_record_id ON tarbil_sync_log (vaccination_record_id);

ALTER TABLE tarbil_sync_log RENAME COLUMN attempted_at TO queued_at;
ALTER TABLE tarbil_sync_log ADD COLUMN submitted_at TIMESTAMPTZ;
ALTER TABLE tarbil_sync_log ADD COLUMN submitted_by_staff_id UUID;
ALTER TABLE tarbil_sync_log ADD COLUMN confirmation_method TEXT;
ALTER TABLE tarbil_sync_log ADD COLUMN tarbil_reference TEXT;
ALTER TABLE tarbil_sync_log ADD COLUMN dismissed_reason TEXT;
ALTER TABLE tarbil_sync_log ADD COLUMN dismissed_at TIMESTAMPTZ;
ALTER TABLE tarbil_sync_log ADD COLUMN dismissed_by_staff_id UUID;

DROP INDEX IF EXISTS idx_tarbil_sync_log_due_retry;
ALTER TABLE tarbil_sync_log DROP COLUMN payload;
ALTER TABLE tarbil_sync_log DROP COLUMN attempt_count;
ALTER TABLE tarbil_sync_log DROP COLUMN next_retry_at;
CREATE INDEX idx_tarbil_sync_log_tenant_status ON tarbil_sync_log (tenant_id, status);
```

- [ ] **Step 9: Testleri çalıştır**

Run: `cd backend && ./mvnw test`
Expected: PASS (tüm testler; `ApplicationModulesTest` dahil). `TarbilSyncPort`'a başka bir yerden referans kalmışsa derleme hatası verir — `grep -rn "TarbilSyncPort\|SYNCED\|attemptSync" src` ile kalıntıları temizle.

- [ ] **Step 10: Migration'ı canlı DB'de doğrula**

Run: `cd backend && docker-compose up -d && ./mvnw spring-boot:run` (başka terminalde), açılış loglarında `Migrating schema "public" to version "59 - tarbil submission rework"` ve hatasız `Started` görülmeli. Durdur.

- [ ] **Step 11: Commit**

```bash
git add -A backend
git commit -m "feat(tarbil): sahte senkronu kaldir, asi aktarim kaydini PENDING/SUBMITTED/DISMISSED olarak yeniden sekillendir"
```

---

### Task 3: Öğrenilen eşleştirmeler (`tarbil_value_mapping`)

**Files:**
- Create: `.../tarbil/domain/TarbilMappingKind.java`, `.../tarbil/domain/TarbilValueMapping.java`, `.../tarbil/domain/TarbilValueMappingRepository.java`, `.../tarbil/domain/VaccineKeyNormalizer.java`, `.../tarbil/domain/exception/TarbilValueMappingNotFoundException.java`, `.../tarbil/domain/exception/InvalidTarbilMappingException.java`
- Create: `.../tarbil/infrastructure/persistence/TarbilValueMappingJpaRepository.java`, `.../tarbil/infrastructure/persistence/TarbilValueMappingRepositoryAdapter.java`
- Create: `.../tarbil/application/LearnTarbilMappingUseCase.java`, `.../tarbil/application/ListTarbilMappingsUseCase.java`, `.../tarbil/application/DeleteTarbilMappingUseCase.java`, `.../tarbil/application/dto/TarbilMappingSummary.java`
- Create: `backend/src/main/resources/db/migration/V60__tarbil_value_mapping.sql`
- Test: `.../tarbil/domain/VaccineKeyNormalizerTest.java`, `.../tarbil/application/LearnTarbilMappingUseCaseTest.java`, `.../tarbil/application/DeleteTarbilMappingUseCaseTest.java`

**Interfaces:**
- Produces:
  - `enum TarbilMappingKind { VACCINE, SPECIES }`
  - `static String VaccineKeyNormalizer.normalize(String vaccineName)` — trim, ardışık boşlukları teke indir, `Locale.forLanguageTag("tr")` ile küçük harf; `null`/boş → `""`
  - `TarbilValueMapping.create(UUID tenantId, TarbilMappingKind kind, String vetlyKey, String tarbilFieldsJson, UUID staffId, Instant now)`, `update(String tarbilFieldsJson, UUID staffId, Instant now)`, getter'lar `getId, getTenantId, getKind, getVetlyKey, getTarbilFields, getLearnedByStaffId, getUpdatedAt`
  - `TarbilValueMappingRepository`: `save`, `findById`, `findByTenantIdAndKindAndVetlyKey(UUID, TarbilMappingKind, String)`, `findByTenantId(UUID)`, `delete(TarbilValueMapping)`
  - `void LearnTarbilMappingUseCase.execute(UUID tenantId, UUID staffId, TarbilMappingKind kind, String rawKey, String tarbilFieldsJson)` — `VACCINE` anahtarını normalize eder; `tarbilFieldsJson` geçerli bir JSON nesnesi ve ≤ 4096 karakter değilse `InvalidTarbilMappingException` (422)
  - `List<TarbilMappingSummary> ListTarbilMappingsUseCase.execute(UUID tenantId)`; `record TarbilMappingSummary(UUID id, TarbilMappingKind kind, String vetlyKey, String tarbilFields, Instant updatedAt)`
  - `void DeleteTarbilMappingUseCase.execute(UUID tenantId, UUID mappingId)` — başka kiracıya aitse `TarbilValueMappingNotFoundException`

- [ ] **Step 1: Failing testleri yaz**

`VaccineKeyNormalizerTest.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VaccineKeyNormalizerTest {

    @Test
    void should_normalizeTurkishCaseAndWhitespace() {
        assertThat(VaccineKeyNormalizer.normalize("  KUDUZ   AŞISI ")).isEqualTo("kuduz aşısı");
        assertThat(VaccineKeyNormalizer.normalize("Kuduz Aşısı")).isEqualTo("kuduz aşısı");
        assertThat(VaccineKeyNormalizer.normalize("İÇ PARAZİT")).isEqualTo("iç parazit");
        assertThat(VaccineKeyNormalizer.normalize("ISIRGAN")).isEqualTo("ısırgan");
    }

    @Test
    void should_returnEmpty_when_nullOrBlank() {
        assertThat(VaccineKeyNormalizer.normalize(null)).isEmpty();
        assertThat(VaccineKeyNormalizer.normalize("   ")).isEmpty();
    }
}
```

`LearnTarbilMappingUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilMappingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearnTarbilMappingUseCaseTest {

    @Mock private TarbilValueMappingRepository repository;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private LearnTarbilMappingUseCase useCase() {
        return new LearnTarbilMappingUseCase(repository, new ObjectMapper());
    }

    @Test
    void should_createWithNormalizedKey_when_vaccineMappingNew() {
        when(repository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.VACCINE, "kuduz aşısı"))
            .thenReturn(Optional.empty());

        useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, " Kuduz  Aşısı", "{\"vaccine\":{\"value\":\"g1\",\"text\":\"Rabisin\"}}");

        ArgumentCaptor<TarbilValueMapping> captor = ArgumentCaptor.forClass(TarbilValueMapping.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getVetlyKey()).isEqualTo("kuduz aşısı");
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getLearnedByStaffId()).isEqualTo(staffId);
    }

    @Test
    void should_overwriteFields_when_mappingExists() {
        TarbilValueMapping existing = TarbilValueMapping.create(
            tenantId, TarbilMappingKind.VACCINE, "kuduz aşısı", "{\"old\":1}", UUID.randomUUID(), Instant.now());
        when(repository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.VACCINE, "kuduz aşısı"))
            .thenReturn(Optional.of(existing));

        useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz Aşısı", "{\"new\":2}");

        assertThat(existing.getTarbilFields()).isEqualTo("{\"new\":2}");
        assertThat(existing.getLearnedByStaffId()).isEqualTo(staffId);
        verify(repository).save(existing);
    }

    @Test
    void should_reject_when_fieldsNotJsonObject() {
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz", "[1,2]"))
            .isInstanceOf(InvalidTarbilMappingException.class);
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz", "not json"))
            .isInstanceOf(InvalidTarbilMappingException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_reject_when_fieldsTooLarge() {
        String big = "{\"x\":\"" + "a".repeat(5000) + "\"}";
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "Kuduz", big))
            .isInstanceOf(InvalidTarbilMappingException.class);
    }

    @Test
    void should_reject_when_keyBlank() {
        assertThatThrownBy(() -> useCase().execute(tenantId, staffId, TarbilMappingKind.VACCINE, "   ", "{}"))
            .isInstanceOf(InvalidTarbilMappingException.class);
    }
}
```

`DeleteTarbilMappingUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilValueMappingNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteTarbilMappingUseCaseTest {

    @Mock private TarbilValueMappingRepository repository;

    @Test
    void should_throwNotFound_when_mappingBelongsToAnotherTenant() {
        UUID id = UUID.randomUUID();
        TarbilValueMapping foreign = TarbilValueMapping.create(
            UUID.randomUUID(), TarbilMappingKind.VACCINE, "kuduz", "{}", UUID.randomUUID(), Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> new DeleteTarbilMappingUseCase(repository).execute(UUID.randomUUID(), id))
            .isInstanceOf(TarbilValueMappingNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void should_delete_when_mappingBelongsToTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        TarbilValueMapping own = TarbilValueMapping.create(
            tenantId, TarbilMappingKind.VACCINE, "kuduz", "{}", UUID.randomUUID(), Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(own));

        new DeleteTarbilMappingUseCase(repository).execute(tenantId, id);

        verify(repository).delete(own);
    }
}
```

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd backend && ./mvnw test -Dtest='VaccineKeyNormalizerTest,LearnTarbilMappingUseCaseTest,DeleteTarbilMappingUseCaseTest'`
Expected: Derleme hatası.

- [ ] **Step 3: Domain, exception, persistence**

`TarbilMappingKind.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

/** VACCINE: anahtar normalize asi adi. SPECIES: anahtar Vetly speciesId (UUID metni). */
public enum TarbilMappingKind { VACCINE, SPECIES }
```

`VaccineKeyNormalizer.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.Locale;

/** "  KUDUZ   AŞISI " ve "Kuduz Aşısı" ayni esletirmeye dusmeli -- Turkce I/ı kurali dahil. */
public final class VaccineKeyNormalizer {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private VaccineKeyNormalizer() {
    }

    public static String normalize(String vaccineName) {
        if (vaccineName == null) {
            return "";
        }
        return vaccineName.trim().replaceAll("\\s+", " ").toLowerCase(TR);
    }
}
```

`TarbilValueMapping.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Hekimin TARBIL'de ilk kez elle sectigi degerlerden ogrenilen esletirme (klinik bazli).
 * Hicbir deger onceden uydurulmaz (CLAUDE.md "Yapma" kurali).
 */
@Entity
@Table(name = "tarbil_value_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilValueMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TarbilMappingKind kind;

    @Column(name = "vetly_key", nullable = false)
    private String vetlyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tarbil_fields", nullable = false, columnDefinition = "jsonb")
    private String tarbilFields;

    @Column(name = "learned_by_staff_id", nullable = false)
    private UUID learnedByStaffId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TarbilValueMapping create(
        UUID tenantId, TarbilMappingKind kind, String vetlyKey, String tarbilFieldsJson, UUID staffId, Instant now
    ) {
        TarbilValueMapping m = new TarbilValueMapping();
        m.tenantId = tenantId;
        m.kind = kind;
        m.vetlyKey = vetlyKey;
        m.tarbilFields = tarbilFieldsJson;
        m.learnedByStaffId = staffId;
        m.updatedAt = now;
        return m;
    }

    public void update(String tarbilFieldsJson, UUID staffId, Instant now) {
        this.tarbilFields = tarbilFieldsJson;
        this.learnedByStaffId = staffId;
        this.updatedAt = now;
    }
}
```

`TarbilValueMappingRepository.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilValueMappingRepository {
    TarbilValueMapping save(TarbilValueMapping mapping);
    Optional<TarbilValueMapping> findById(UUID id);
    Optional<TarbilValueMapping> findByTenantIdAndKindAndVetlyKey(UUID tenantId, TarbilMappingKind kind, String vetlyKey);
    List<TarbilValueMapping> findByTenantId(UUID tenantId);
    void delete(TarbilValueMapping mapping);
}
```

`exception/TarbilValueMappingNotFoundException.java`:
```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilValueMappingNotFoundException extends DomainException {
    public TarbilValueMappingNotFoundException(UUID id) {
        super("TARBIL_VALUE_MAPPING_NOT_FOUND", "TARBIL esletirmesi bulunamadi: " + id);
    }
}
```

`exception/InvalidTarbilMappingException.java`:
```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidTarbilMappingException extends DomainException {
    public InvalidTarbilMappingException(String reason) {
        super("INVALID_TARBIL_MAPPING", "Gecersiz TARBIL esletirmesi: " + reason);
    }
}
```

`TarbilValueMappingJpaRepository.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilValueMappingJpaRepository extends JpaRepository<TarbilValueMapping, UUID> {
    Optional<TarbilValueMapping> findByTenantIdAndKindAndVetlyKey(UUID tenantId, TarbilMappingKind kind, String vetlyKey);
    List<TarbilValueMapping> findByTenantIdOrderByKindAscVetlyKeyAsc(UUID tenantId);
}
```

`TarbilValueMappingRepositoryAdapter.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilValueMappingRepositoryAdapter implements TarbilValueMappingRepository {

    private final TarbilValueMappingJpaRepository jpaRepository;

    @Override public TarbilValueMapping save(TarbilValueMapping m) { return jpaRepository.save(m); }
    @Override public Optional<TarbilValueMapping> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public Optional<TarbilValueMapping> findByTenantIdAndKindAndVetlyKey(UUID tenantId, TarbilMappingKind kind, String key) {
        return jpaRepository.findByTenantIdAndKindAndVetlyKey(tenantId, kind, key);
    }

    @Override
    public List<TarbilValueMapping> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByKindAscVetlyKeyAsc(tenantId);
    }

    @Override public void delete(TarbilValueMapping m) { jpaRepository.delete(m); }
}
```

- [ ] **Step 4: Use-case'ler**

`LearnTarbilMappingUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.VaccineKeyNormalizer;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilMappingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearnTarbilMappingUseCase {

    static final int MAX_FIELDS_JSON_LENGTH = 4096;

    private final TarbilValueMappingRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void execute(UUID tenantId, UUID staffId, TarbilMappingKind kind, String rawKey, String tarbilFieldsJson) {
        String key = kind == TarbilMappingKind.VACCINE ? VaccineKeyNormalizer.normalize(rawKey) : (rawKey == null ? "" : rawKey.trim());
        if (key.isEmpty()) {
            throw new InvalidTarbilMappingException("anahtar bos");
        }
        if (tarbilFieldsJson == null || tarbilFieldsJson.length() > MAX_FIELDS_JSON_LENGTH) {
            throw new InvalidTarbilMappingException("alanlar bos ya da cok buyuk");
        }
        try {
            JsonNode node = objectMapper.readTree(tarbilFieldsJson);
            if (node == null || !node.isObject()) {
                throw new InvalidTarbilMappingException("alanlar bir JSON nesnesi olmali");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new InvalidTarbilMappingException("alanlar gecerli JSON degil");
        }
        Instant now = Instant.now();
        TarbilValueMapping mapping = repository.findByTenantIdAndKindAndVetlyKey(tenantId, kind, key)
            .map(existing -> {
                existing.update(tarbilFieldsJson, staffId, now);
                return existing;
            })
            .orElseGet(() -> TarbilValueMapping.create(tenantId, kind, key, tarbilFieldsJson, staffId, now));
        repository.save(mapping);
    }
}
```

`dto/TarbilMappingSummary.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;

import java.time.Instant;
import java.util.UUID;

public record TarbilMappingSummary(UUID id, TarbilMappingKind kind, String vetlyKey, String tarbilFields, Instant updatedAt) {}
```

`ListTarbilMappingsUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilMappingSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListTarbilMappingsUseCase {

    private final TarbilValueMappingRepository repository;

    @Transactional(readOnly = true)
    public List<TarbilMappingSummary> execute(UUID tenantId) {
        return repository.findByTenantId(tenantId).stream()
            .map(m -> new TarbilMappingSummary(m.getId(), m.getKind(), m.getVetlyKey(), m.getTarbilFields(), m.getUpdatedAt()))
            .toList();
    }
}
```

`DeleteTarbilMappingUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilValueMappingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteTarbilMappingUseCase {

    private final TarbilValueMappingRepository repository;

    @Transactional
    public void execute(UUID tenantId, UUID mappingId) {
        TarbilValueMapping mapping = repository.findById(mappingId)
            .filter(m -> m.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilValueMappingNotFoundException(mappingId));
        repository.delete(mapping);
    }
}
```

- [ ] **Step 5: Migration**

`V60__tarbil_value_mapping.sql`:
```sql
-- Hekimin TARBIL'de ilk kez elle sectigi degerlerden ogrenilen esletirmeler (klinik bazli).
-- @TenantId DISINDA (tarbil_sync_log ile ayni karar) -- tenant_id elle filtrelenir.
CREATE TABLE tarbil_value_mapping (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    kind                TEXT NOT NULL,
    vetly_key           TEXT NOT NULL,
    tarbil_fields       JSONB NOT NULL,
    learned_by_staff_id UUID NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_tarbil_value_mapping UNIQUE (tenant_id, kind, vetly_key)
);
```

- [ ] **Step 6: Testleri çalıştır**

Run: `cd backend && ./mvnw test -Dtest='VaccineKeyNormalizerTest,LearnTarbilMappingUseCaseTest,DeleteTarbilMappingUseCaseTest,ApplicationModulesTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A backend
git commit -m "feat(tarbil): klinik bazli ogrenilen TARBIL deger esletirmeleri"
```

---

### Task 4: Eklenti anahtarı — eşleştirme, doğrulama, iptal

**Files:**
- Create: `.../tarbil/domain/ExtensionSecrets.java`, `.../tarbil/domain/TarbilExtensionToken.java`, `.../tarbil/domain/TarbilExtensionTokenRepository.java`
- Create: `.../tarbil/domain/exception/InvalidPairingCodeUnauthorizedException.java`, `.../tarbil/domain/exception/TarbilExtensionTokenNotFoundException.java`
- Create: `.../tarbil/infrastructure/persistence/TarbilExtensionTokenJpaRepository.java`, `.../tarbil/infrastructure/persistence/TarbilExtensionTokenRepositoryAdapter.java`
- Create: `.../tarbil/application/CreatePairingCodeUseCase.java`, `.../tarbil/application/PairExtensionUseCase.java`, `.../tarbil/application/ListExtensionTokensUseCase.java`, `.../tarbil/application/RevokeExtensionTokenUseCase.java`, `.../tarbil/application/AuthenticateExtensionTokenUseCase.java`, `.../tarbil/application/dto/ExtensionTokenSummary.java`, `.../tarbil/application/dto/PairingCode.java`, `.../tarbil/application/dto/ExtensionIdentity.java`
- Create: `backend/src/main/resources/db/migration/V61__tarbil_extension_token.sql`
- Test: `.../tarbil/domain/ExtensionSecretsTest.java`, `.../tarbil/domain/TarbilExtensionTokenTest.java`, `.../tarbil/application/PairExtensionUseCaseTest.java`, `.../tarbil/application/AuthenticateExtensionTokenUseCaseTest.java`, `.../tarbil/application/RevokeExtensionTokenUseCaseTest.java`

**Interfaces:**
- Consumes: `StaffUserLookupPort.isActive(UUID)` (Task 1)
- Produces:
  - `ExtensionSecrets.newPairingCode()` → `String` biçim `XXXX-XXXX`, alfabe `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (31 karakter; `0/O`, `1/I/L` yok); `ExtensionSecrets.newToken()` → `"vtx_" + base64url(32 bayt, padding yok)`; `ExtensionSecrets.sha256Hex(String)`; `ExtensionSecrets.normalizePairingCode(String)` → büyük harfe çevirir, `-` ve boşlukları siler, ortadan tekrar `-` koyar
  - `TarbilExtensionToken.issuePairing(UUID tenantId, UUID staffId, String codeHash, Instant expiresAt, Instant now)`; `void pair(String tokenHash, String label, Instant now)` (kod süresi dolmuşsa ya da zaten eşleşmişse `InvalidPairingCodeUnauthorizedException`); `void revoke(Instant now)`; `boolean isUsable()` (eşleşmiş ve iptal edilmemiş); `void touch(Instant now)` (son kullanım 5 dakikadan eskiyse günceller)
  - `TarbilExtensionTokenRepository`: `save`, `findById`, `findByPairingCodeHash(String)`, `findByTokenHash(String)`, `findByTenantId(UUID)`, `findByTenantIdAndStaffUserId(UUID, UUID)`
  - `record PairingCode(String code, Instant expiresAt)`; `PairingCode CreatePairingCodeUseCase.execute(UUID tenantId, UUID staffId)`
  - `String PairExtensionUseCase.execute(String rawCode, String label)` → ham anahtar (yalnızca bu yanıtta)
  - `record ExtensionIdentity(UUID tokenId, UUID tenantId, UUID staffUserId)`; `Optional<ExtensionIdentity> AuthenticateExtensionTokenUseCase.execute(String rawToken)` — iptal/eşleşmemiş/personel pasif → boş
  - `record ExtensionTokenSummary(UUID id, UUID staffUserId, String staffName, String label, Instant pairedAt, Instant lastUsedAt, Instant revokedAt)`; `List<ExtensionTokenSummary> ListExtensionTokensUseCase.execute(UUID tenantId, UUID callerStaffId, boolean callerIsAdmin)` — yalnız eşleşmiş anahtarlar; admin değilse yalnız kendi anahtarları
  - `void RevokeExtensionTokenUseCase.execute(UUID tenantId, UUID callerStaffId, boolean callerIsAdmin, UUID tokenId)` — başka kiracı ya da (admin değilken) başka personel → `TarbilExtensionTokenNotFoundException`

- [ ] **Step 1: Failing testleri yaz**

`ExtensionSecretsTest.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionSecretsTest {

    @Test
    void should_generateFormattedPairingCode_withoutAmbiguousCharacters() {
        for (int i = 0; i < 200; i++) {
            assertThat(ExtensionSecrets.newPairingCode()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{4}");
        }
    }

    @Test
    void should_generateUniqueTokensWithPrefix() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = ExtensionSecrets.newToken();
            assertThat(token).startsWith("vtx_").hasSize(47);
            tokens.add(token);
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void should_normalizeUserTypedCode() {
        assertThat(ExtensionSecrets.normalizePairingCode(" k7qm 2xpa ")).isEqualTo("K7QM-2XPA");
        assertThat(ExtensionSecrets.normalizePairingCode("k7qm-2xpa")).isEqualTo("K7QM-2XPA");
    }

    @Test
    void should_hashDeterministically() {
        assertThat(ExtensionSecrets.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
```

`TarbilExtensionTokenTest.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarbilExtensionTokenTest {

    private final Instant now = Instant.parse("2026-10-02T10:00:00Z");

    private TarbilExtensionToken pending() {
        return TarbilExtensionToken.issuePairing(UUID.randomUUID(), UUID.randomUUID(), "codehash", now.plus(Duration.ofMinutes(10)), now);
    }

    @Test
    void should_becomeUsableAndClearCode_when_pairedBeforeExpiry() {
        TarbilExtensionToken token = pending();

        token.pair("tokenhash", "Muayene 1", now.plus(Duration.ofMinutes(5)));

        assertThat(token.isUsable()).isTrue();
        assertThat(token.getTokenHash()).isEqualTo("tokenhash");
        assertThat(token.getPairingCodeHash()).isNull();
        assertThat(token.getLabel()).isEqualTo("Muayene 1");
    }

    @Test
    void should_reject_when_codeExpired() {
        TarbilExtensionToken token = pending();

        assertThatThrownBy(() -> token.pair("tokenhash", "x", now.plus(Duration.ofMinutes(11))))
            .isInstanceOf(InvalidPairingCodeUnauthorizedException.class);
        assertThat(token.isUsable()).isFalse();
    }

    @Test
    void should_reject_when_pairedTwice() {
        TarbilExtensionToken token = pending();
        token.pair("tokenhash", "x", now);

        assertThatThrownBy(() -> token.pair("other", "y", now))
            .isInstanceOf(InvalidPairingCodeUnauthorizedException.class);
    }

    @Test
    void should_notBeUsable_when_revoked() {
        TarbilExtensionToken token = pending();
        token.pair("tokenhash", "x", now);

        token.revoke(now);

        assertThat(token.isUsable()).isFalse();
    }

    @Test
    void should_updateLastUsedOnlyAfterFiveMinutes() {
        TarbilExtensionToken token = pending();
        token.pair("tokenhash", "x", now);

        token.touch(now.plusSeconds(60));
        assertThat(token.getLastUsedAt()).isEqualTo(now.plusSeconds(60));
        token.touch(now.plusSeconds(120));
        assertThat(token.getLastUsedAt()).isEqualTo(now.plusSeconds(60));
        token.touch(now.plusSeconds(60 + 301));
        assertThat(token.getLastUsedAt()).isEqualTo(now.plusSeconds(361));
    }
}
```

`PairExtensionUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairExtensionUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;

    @Test
    void should_returnRawTokenAndStoreOnlyHash_when_codeValid() {
        TarbilExtensionToken pending = TarbilExtensionToken.issuePairing(
            UUID.randomUUID(), UUID.randomUUID(), ExtensionSecrets.sha256Hex("K7QM-2XPA"),
            Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        when(repository.findByPairingCodeHash(ExtensionSecrets.sha256Hex("K7QM-2XPA"))).thenReturn(Optional.of(pending));

        String raw = new PairExtensionUseCase(repository).execute("k7qm 2xpa", "Muayene 1");

        assertThat(raw).startsWith("vtx_");
        assertThat(pending.getTokenHash()).isEqualTo(ExtensionSecrets.sha256Hex(raw));
        verify(repository).save(pending);
    }

    @Test
    void should_throwUnauthorized_when_codeUnknown() {
        when(repository.findByPairingCodeHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PairExtensionUseCase(repository).execute("AAAA-BBBB", "x"))
            .isInstanceOf(InvalidPairingCodeUnauthorizedException.class);
    }
}
```

`AuthenticateExtensionTokenUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionIdentity;
import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateExtensionTokenUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;
    @Mock private StaffUserLookupPort staffUserLookupPort;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private TarbilExtensionToken paired(String raw) {
        TarbilExtensionToken t = TarbilExtensionToken.issuePairing(
            tenantId, staffId, "c", Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        t.pair(ExtensionSecrets.sha256Hex(raw), "x", Instant.now());
        return t;
    }

    @Test
    void should_returnIdentity_when_tokenValidAndStaffActive() {
        TarbilExtensionToken token = paired("vtx_abc");
        when(repository.findByTokenHash(ExtensionSecrets.sha256Hex("vtx_abc"))).thenReturn(Optional.of(token));
        when(staffUserLookupPort.isActive(staffId)).thenReturn(true);

        Optional<ExtensionIdentity> identity = new AuthenticateExtensionTokenUseCase(repository, staffUserLookupPort).execute("vtx_abc");

        assertThat(identity).isPresent();
        assertThat(identity.get().tenantId()).isEqualTo(tenantId);
        assertThat(identity.get().staffUserId()).isEqualTo(staffId);
    }

    @Test
    void should_reject_when_tokenRevoked() {
        TarbilExtensionToken token = paired("vtx_abc");
        token.revoke(Instant.now());
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThat(new AuthenticateExtensionTokenUseCase(repository, staffUserLookupPort).execute("vtx_abc")).isEmpty();
    }

    @Test
    void should_reject_when_staffInactive() {
        TarbilExtensionToken token = paired("vtx_abc");
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(staffUserLookupPort.isActive(staffId)).thenReturn(false);

        assertThat(new AuthenticateExtensionTokenUseCase(repository, staffUserLookupPort).execute("vtx_abc")).isEmpty();
    }

    @Test
    void should_reject_when_tokenUnknownOrMalformed() {
        assertThat(new AuthenticateExtensionTokenUseCase(repository, staffUserLookupPort).execute("not-a-token")).isEmpty();
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThat(new AuthenticateExtensionTokenUseCase(repository, staffUserLookupPort).execute("vtx_unknown")).isEmpty();
    }
}
```

`RevokeExtensionTokenUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilExtensionTokenNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeExtensionTokenUseCaseTest {

    @Mock private TarbilExtensionTokenRepository repository;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID ownerStaffId = UUID.randomUUID();

    private TarbilExtensionToken token() {
        TarbilExtensionToken t = TarbilExtensionToken.issuePairing(
            tenantId, ownerStaffId, "c", Instant.now().plus(Duration.ofMinutes(10)), Instant.now());
        t.pair("h", "x", Instant.now());
        return t;
    }

    @Test
    void should_revoke_when_callerOwnsToken() {
        UUID id = UUID.randomUUID();
        TarbilExtensionToken t = token();
        when(repository.findById(id)).thenReturn(Optional.of(t));

        new RevokeExtensionTokenUseCase(repository).execute(tenantId, ownerStaffId, false, id);

        assertThat(t.isUsable()).isFalse();
    }

    @Test
    void should_throwNotFound_when_nonAdminRevokesOthersToken() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token()));

        assertThatThrownBy(() -> new RevokeExtensionTokenUseCase(repository).execute(tenantId, UUID.randomUUID(), false, id))
            .isInstanceOf(TarbilExtensionTokenNotFoundException.class);
    }

    @Test
    void should_revoke_when_adminRevokesOthersToken() {
        UUID id = UUID.randomUUID();
        TarbilExtensionToken t = token();
        when(repository.findById(id)).thenReturn(Optional.of(t));

        new RevokeExtensionTokenUseCase(repository).execute(tenantId, UUID.randomUUID(), true, id);

        assertThat(t.isUsable()).isFalse();
    }

    @Test
    void should_throwNotFound_when_tokenInAnotherTenant() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(token()));

        assertThatThrownBy(() -> new RevokeExtensionTokenUseCase(repository).execute(UUID.randomUUID(), ownerStaffId, true, id))
            .isInstanceOf(TarbilExtensionTokenNotFoundException.class);
    }
}
```

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd backend && ./mvnw test -Dtest='ExtensionSecretsTest,TarbilExtensionTokenTest,PairExtensionUseCaseTest,AuthenticateExtensionTokenUseCaseTest,RevokeExtensionTokenUseCaseTest'`
Expected: Derleme hatası.

- [ ] **Step 3: Domain**

`ExtensionSecrets.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Eslestirme kodu ve eklenti anahtari uretimi. Ikisi de veritabaninda yalnizca SHA-256 ozeti olarak durur. */
public final class ExtensionSecrets {

    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ExtensionSecrets() {
    }

    public static String newPairingCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "vtx_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String normalizePairingCode(String raw) {
        String compact = raw == null ? "" : raw.toUpperCase().replaceAll("[\\s-]", "");
        return compact.length() == 8 ? compact.substring(0, 4) + "-" + compact.substring(4) : compact;
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

`exception/InvalidPairingCodeUnauthorizedException.java`:
```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidPairingCodeUnauthorizedException extends DomainException {
    public InvalidPairingCodeUnauthorizedException() {
        super("INVALID_PAIRING_CODE", "Eslestirme kodu gecersiz ya da suresi dolmus");
    }
}
```

`exception/TarbilExtensionTokenNotFoundException.java`:
```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilExtensionTokenNotFoundException extends DomainException {
    public TarbilExtensionTokenNotFoundException(UUID id) {
        super("TARBIL_EXTENSION_TOKEN_NOT_FOUND", "Eklenti baglantisi bulunamadi: " + id);
    }
}
```

`TarbilExtensionToken.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Hekime ozel TARBIL eklentisi anahtari. Yasam dongusu: issuePairing (kod) -> pair (anahtar)
 * -> revoke. Kod ve anahtar yalnizca SHA-256 ozeti olarak saklanir.
 * @TenantId DISINDA: kimlik dogrulama asamasinda kiraci henuz bilinmiyor.
 */
@Entity
@Table(name = "tarbil_extension_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilExtensionToken {

    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "staff_user_id", nullable = false)
    private UUID staffUserId;

    private String label;

    @Column(name = "token_hash", unique = true)
    private String tokenHash;

    @Column(name = "pairing_code_hash", unique = true)
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at", nullable = false)
    private Instant pairingExpiresAt;

    @Column(name = "paired_at")
    private Instant pairedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static TarbilExtensionToken issuePairing(UUID tenantId, UUID staffId, String codeHash, Instant expiresAt, Instant now) {
        TarbilExtensionToken t = new TarbilExtensionToken();
        t.tenantId = tenantId;
        t.staffUserId = staffId;
        t.pairingCodeHash = codeHash;
        t.pairingExpiresAt = expiresAt;
        t.createdAt = now;
        return t;
    }

    public void pair(String tokenHash, String label, Instant now) {
        if (pairedAt != null || revokedAt != null || pairingCodeHash == null || now.isAfter(pairingExpiresAt)) {
            throw new InvalidPairingCodeUnauthorizedException();
        }
        this.tokenHash = tokenHash;
        this.label = label;
        this.pairedAt = now;
        this.pairingCodeHash = null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
            this.pairingCodeHash = null;
        }
    }

    public boolean isUsable() {
        return pairedAt != null && revokedAt == null && tokenHash != null;
    }

    public void touch(Instant now) {
        if (lastUsedAt == null || Duration.between(lastUsedAt, now).compareTo(TOUCH_INTERVAL) > 0) {
            this.lastUsedAt = now;
        }
    }
}
```

`TarbilExtensionTokenRepository.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilExtensionTokenRepository {
    TarbilExtensionToken save(TarbilExtensionToken token);
    Optional<TarbilExtensionToken> findById(UUID id);
    Optional<TarbilExtensionToken> findByPairingCodeHash(String hash);
    Optional<TarbilExtensionToken> findByTokenHash(String hash);
    List<TarbilExtensionToken> findByTenantId(UUID tenantId);
}
```

`TarbilExtensionTokenJpaRepository.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilExtensionTokenJpaRepository extends JpaRepository<TarbilExtensionToken, UUID> {
    Optional<TarbilExtensionToken> findByPairingCodeHash(String hash);
    Optional<TarbilExtensionToken> findByTokenHash(String hash);
    List<TarbilExtensionToken> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
```

`TarbilExtensionTokenRepositoryAdapter.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilExtensionTokenRepositoryAdapter implements TarbilExtensionTokenRepository {

    private final TarbilExtensionTokenJpaRepository jpaRepository;

    @Override public TarbilExtensionToken save(TarbilExtensionToken t) { return jpaRepository.save(t); }
    @Override public Optional<TarbilExtensionToken> findById(UUID id) { return jpaRepository.findById(id); }
    @Override public Optional<TarbilExtensionToken> findByPairingCodeHash(String h) { return jpaRepository.findByPairingCodeHash(h); }
    @Override public Optional<TarbilExtensionToken> findByTokenHash(String h) { return jpaRepository.findByTokenHash(h); }
    @Override public List<TarbilExtensionToken> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); }
}
```

- [ ] **Step 4: Use-case'ler ve DTO'lar**

`dto/PairingCode.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

import java.time.Instant;

public record PairingCode(String code, Instant expiresAt) {}
```

`dto/ExtensionIdentity.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

import java.util.UUID;

public record ExtensionIdentity(UUID tokenId, UUID tenantId, UUID staffUserId) {}
```

`dto/ExtensionTokenSummary.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

import java.time.Instant;
import java.util.UUID;

public record ExtensionTokenSummary(
    UUID id, UUID staffUserId, String staffName, String label, Instant pairedAt, Instant lastUsedAt, Instant revokedAt
) {}
```

`CreatePairingCodeUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.PairingCode;
import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreatePairingCodeUseCase {

    static final Duration CODE_TTL = Duration.ofMinutes(10);

    private final TarbilExtensionTokenRepository repository;

    @Transactional
    public PairingCode execute(UUID tenantId, UUID staffId) {
        String code = ExtensionSecrets.newPairingCode();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_TTL);
        repository.save(TarbilExtensionToken.issuePairing(tenantId, staffId, ExtensionSecrets.sha256Hex(code), expiresAt, now));
        return new PairingCode(code, expiresAt);
    }
}
```

`PairExtensionUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidPairingCodeUnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PairExtensionUseCase {

    private final TarbilExtensionTokenRepository repository;

    /** @return ham anahtar -- yalnizca bu yanitta bir kez dondurulur, veritabaninda sadece ozeti durur. */
    @Transactional
    public String execute(String rawCode, String label) {
        String codeHash = ExtensionSecrets.sha256Hex(ExtensionSecrets.normalizePairingCode(rawCode));
        TarbilExtensionToken token = repository.findByPairingCodeHash(codeHash)
            .orElseThrow(InvalidPairingCodeUnauthorizedException::new);
        String rawToken = ExtensionSecrets.newToken();
        String safeLabel = label == null || label.isBlank() ? "Eklenti" : label.trim();
        token.pair(ExtensionSecrets.sha256Hex(rawToken), safeLabel.length() > 60 ? safeLabel.substring(0, 60) : safeLabel, Instant.now());
        repository.save(token);
        return rawToken;
    }
}
```

`AuthenticateExtensionTokenUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionIdentity;
import com.vetos.modules.integration.tarbil.domain.ExtensionSecrets;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticateExtensionTokenUseCase {

    private final TarbilExtensionTokenRepository repository;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional
    public Optional<ExtensionIdentity> execute(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("vtx_")) {
            return Optional.empty();
        }
        Optional<TarbilExtensionToken> found = repository.findByTokenHash(ExtensionSecrets.sha256Hex(rawToken))
            .filter(TarbilExtensionToken::isUsable)
            .filter(t -> staffUserLookupPort.isActive(t.getStaffUserId()));
        found.ifPresent(t -> {
            t.touch(Instant.now());
            repository.save(t);
        });
        return found.map(t -> new ExtensionIdentity(t.getId(), t.getTenantId(), t.getStaffUserId()));
    }
}
```

`ListExtensionTokensUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionTokenSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListExtensionTokensUseCase {

    private final TarbilExtensionTokenRepository repository;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public List<ExtensionTokenSummary> execute(UUID tenantId, UUID callerStaffId, boolean callerIsAdmin) {
        return repository.findByTenantId(tenantId).stream()
            .filter(t -> t.getPairedAt() != null)
            .filter(t -> callerIsAdmin || t.getStaffUserId().equals(callerStaffId))
            .map(t -> new ExtensionTokenSummary(
                t.getId(), t.getStaffUserId(), staffUserLookupPort.findSummaryById(t.getStaffUserId()).fullName(),
                t.getLabel(), t.getPairedAt(), t.getLastUsedAt(), t.getRevokedAt()))
            .toList();
    }
}
```

`RevokeExtensionTokenUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilExtensionToken;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilExtensionTokenNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevokeExtensionTokenUseCase {

    private final TarbilExtensionTokenRepository repository;

    @Transactional
    public void execute(UUID tenantId, UUID callerStaffId, boolean callerIsAdmin, UUID tokenId) {
        TarbilExtensionToken token = repository.findById(tokenId)
            .filter(t -> t.getTenantId().equals(tenantId))
            .filter(t -> callerIsAdmin || t.getStaffUserId().equals(callerStaffId))
            .orElseThrow(() -> new TarbilExtensionTokenNotFoundException(tokenId));
        token.revoke(Instant.now());
        repository.save(token);
    }
}
```

- [ ] **Step 5: Migration**

`V61__tarbil_extension_token.sql`:
```sql
-- Hekime ozel TARBIL eklentisi anahtarlari. Kod ve anahtar yalnizca SHA-256 ozeti olarak durur.
-- @TenantId DISINDA: kimlik dogrulama sirasinda kiraci henuz bilinmiyor (anahtar ozetiyle global arama).
CREATE TABLE tarbil_extension_token (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    staff_user_id      UUID NOT NULL,
    label              TEXT,
    token_hash         TEXT UNIQUE,
    pairing_code_hash  TEXT UNIQUE,
    pairing_expires_at TIMESTAMPTZ NOT NULL,
    paired_at          TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    last_used_at       TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ
);
CREATE INDEX idx_tarbil_extension_token_tenant ON tarbil_extension_token (tenant_id);
```

- [ ] **Step 6: Testleri çalıştır**

Run: `cd backend && ./mvnw test -Dtest='ExtensionSecretsTest,TarbilExtensionTokenTest,PairExtensionUseCaseTest,AuthenticateExtensionTokenUseCaseTest,RevokeExtensionTokenUseCaseTest,ApplicationModulesTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A backend
git commit -m "feat(tarbil): eklenti eslestirme kodu ve iptal edilebilir anahtar"
```

---

### Task 5: Eklenti anahtarı için ayrı güvenlik zinciri

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/security/ExtensionTokenAuthenticator.java`
- Create: `backend/src/main/java/com/vetos/platform/security/ExtensionTokenAuthenticationFilter.java`
- Create: `backend/src/main/java/com/vetos/platform/security/TarbilExtensionSecurityConfig.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/security/TarbilExtensionTokenAuthenticatorAdapter.java`
- Modify: `backend/src/main/java/com/vetos/platform/web/RateLimitFilter.java`
- Test: `backend/src/test/java/com/vetos/platform/security/ExtensionTokenAuthenticationFilterTest.java`

**Interfaces:**
- Consumes: `AuthenticateExtensionTokenUseCase.execute(String)` (Task 4)
- Produces:
  - `interface ExtensionTokenAuthenticator { Optional<AuthenticatedStaffUser> authenticate(String rawToken); }` (platform/security)
  - Eklenti isteğinde principal: `AuthenticatedStaffUser(staffUserId, tenantId, List.of(), "TARBIL_EXTENSION")`, yetki `ROLE_TARBIL_EXTENSION`; `TenantContext` istek süresince kurulur
  - `/api/v1/tarbil-extension/pair` herkese açık ve hız sınırlı (5 dakikada 10, ayrı bucket); diğer `/api/v1/tarbil-extension/**` uçları `ROLE_TARBIL_EXTENSION` ister

- [ ] **Step 1: Failing filtre testini yaz**

`ExtensionTokenAuthenticationFilterTest.java`:
```java
package com.vetos.platform.security;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionTokenAuthenticationFilterTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    @Test
    void should_setPrincipalAndTenant_when_tokenValid() throws Exception {
        ExtensionTokenAuthenticator authenticator = raw -> "vtx_ok".equals(raw)
            ? Optional.of(new AuthenticatedStaffUser(staffId, tenantId, List.of(), "TARBIL_EXTENSION"))
            : Optional.empty();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer vtx_ok");
        AtomicReference<Authentication> seenAuth = new AtomicReference<>();
        AtomicReference<UUID> seenTenant = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            seenAuth.set(SecurityContextHolder.getContext().getAuthentication());
            seenTenant.set(TenantContext.currentOrNull());
        };

        new ExtensionTokenAuthenticationFilter(authenticator).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seenAuth.get()).isNotNull();
        assertThat(seenAuth.get().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_TARBIL_EXTENSION");
        assertThat(seenTenant.get()).isEqualTo(tenantId);
        assertThat(TenantContext.currentOrNull()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void should_returnUnauthenticated_when_tokenRevoked() throws Exception {
        ExtensionTokenAuthenticator authenticator = raw -> Optional.empty();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer vtx_revoked");
        AtomicReference<Authentication> seenAuth = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenAuth.set(SecurityContextHolder.getContext().getAuthentication());

        new ExtensionTokenAuthenticationFilter(authenticator).doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(seenAuth.get()).isNull();
    }

    @Test
    void should_ignoreJwtShapedBearer() throws Exception {
        ExtensionTokenAuthenticator authenticator = raw -> {
            throw new AssertionError("JWT bir eklenti anahtari olarak denenmemeli");
        };
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.x.y");
        AtomicReference<Authentication> seenAuth = new AtomicReference<>();

        new ExtensionTokenAuthenticationFilter(authenticator)
            .doFilter(request, new MockHttpServletResponse(), (req, res) -> seenAuth.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(seenAuth.get()).isNull();
    }
}
```

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd backend && ./mvnw test -Dtest=ExtensionTokenAuthenticationFilterTest`
Expected: Derleme hatası.

- [ ] **Step 3: Port, filtre, zincir**

`ExtensionTokenAuthenticator.java`:
```java
package com.vetos.platform.security;

import java.util.Optional;

/**
 * TARBIL eklentisi anahtarini dogrulayan port. platform/security bir modul
 * bagimliligi alamayacagi icin arayuz burada, uygulamasi integration/tarbil
 * modulunde (TarbilExtensionTokenAuthenticatorAdapter).
 */
public interface ExtensionTokenAuthenticator {
    Optional<AuthenticatedStaffUser> authenticate(String rawToken);
}
```

`ExtensionTokenAuthenticationFilter.java`:
```java
package com.vetos.platform.security;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JwtAuthenticationFilter'in eklenti anahtari esdegeri. Yalnizca "vtx_" onekli
 * anahtarlari dener -- JWT burada kimlik dogrulamaz, eklenti anahtari da ana
 * zincirde dogrulanmaz (iki zincir birbirine kapali).
 */
@RequiredArgsConstructor
public class ExtensionTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_PREFIX = "vtx_";
    public static final String ROLE = "TARBIL_EXTENSION";

    private final ExtensionTokenAuthenticator authenticator;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith(BEARER_PREFIX + TOKEN_PREFIX)) {
                authenticator.authenticate(header.substring(BEARER_PREFIX.length())).ifPresent(principal -> {
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))
                    ));
                    TenantContext.set(principal.tenantId());
                    MDC.put("tenantId", principal.tenantId().toString());
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
            SecurityContextHolder.clearContext();
        }
    }
}
```

`TarbilExtensionSecurityConfig.java`:
```java
package com.vetos.platform.security;

import com.vetos.platform.web.RateLimitFilter;
import com.vetos.platform.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Ucuncu, bagimsiz SecurityFilterChain -- sadece /api/v1/tarbil-extension/**.
 * PlatformAdminSecurityConfig ile ayni desen; SecurityConfig degistirilmedi.
 * CORS kapali: eklenti istekleri host_permissions'li service worker'dan gelir.
 */
@Configuration
@RequiredArgsConstructor
public class TarbilExtensionSecurityConfig {

    private final ExtensionTokenAuthenticator extensionTokenAuthenticator;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    @Bean
    @Order(2)
    public SecurityFilterChain tarbilExtensionSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/tarbil-extension/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/tarbil-extension/pair").permitAll()
                .anyRequest().hasRole(ExtensionTokenAuthenticationFilter.ROLE)
            )
            // Kayit sirasi PlatformAdminSecurityConfig ile ayni gerekceyle.
            .addFilterBefore(new ExtensionTokenAuthenticationFilter(extensionTokenAuthenticator), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RateLimitFilter(), ExtensionTokenAuthenticationFilter.class)
            .addFilterBefore(new RequestIdFilter(), RateLimitFilter.class);
        return http.build();
    }
}
```

`RateLimitFilter.java` — bucket alanlarına ekle:
```java
    private final Map<String, Bucket> extensionPairBuckets = new ConcurrentHashMap<>();
```
ve `else if (path.startsWith("/api/v1/public/"))` dalından ÖNCE ekle:
```java
        } else if (path.equals("/api/v1/tarbil-extension/pair")) {
            // Eslestirme kodu deneme saldirisina karsi -- personel girisi kadar siki, ayri havuz.
            buckets = extensionPairBuckets;
            limit = Bandwidth.builder().capacity(10).refillIntervally(10, Duration.ofMinutes(5)).build();
```

`TarbilExtensionTokenAuthenticatorAdapter.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.security;

import com.vetos.modules.integration.tarbil.application.AuthenticateExtensionTokenUseCase;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.security.ExtensionTokenAuthenticationFilter;
import com.vetos.platform.security.ExtensionTokenAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class TarbilExtensionTokenAuthenticatorAdapter implements ExtensionTokenAuthenticator {

    private final AuthenticateExtensionTokenUseCase authenticateExtensionTokenUseCase;

    @Override
    public Optional<AuthenticatedStaffUser> authenticate(String rawToken) {
        return authenticateExtensionTokenUseCase.execute(rawToken).map(identity -> new AuthenticatedStaffUser(
            identity.staffUserId(), identity.tenantId(), List.of(), ExtensionTokenAuthenticationFilter.ROLE
        ));
    }
}
```

- [ ] **Step 4: Testleri çalıştır**

Run: `cd backend && ./mvnw test -Dtest='ExtensionTokenAuthenticationFilterTest,ApplicationModulesTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A backend
git commit -m "feat(security): TARBIL eklenti anahtari icin ayri SecurityFilterChain"
```

---

### Task 6: Eklenti use-case'leri (bekleyenler, tekil kayıt, gönderildi, bildirilmeyecek)

**Files:**
- Create: `.../tarbil/application/dto/TarbilSubmissionView.java`, `.../tarbil/application/TarbilSubmissionAssembler.java`
- Create: `.../tarbil/application/ListPendingSubmissionsUseCase.java`, `.../tarbil/application/GetSubmissionUseCase.java`, `.../tarbil/application/MarkSubmittedUseCase.java`, `.../tarbil/application/DismissSubmissionUseCase.java`, `.../tarbil/application/RestoreSubmissionUseCase.java`, `.../tarbil/application/GetExtensionProfileUseCase.java`, `.../tarbil/application/dto/ExtensionProfile.java`
- Create: `.../tarbil/domain/exception/TarbilSubmissionNotFoundException.java`
- Delete: `.../tarbil/domain/exception/TarbilSyncLogNotFoundException.java` (yerini `TarbilSubmissionNotFoundException` alır; referanslarını güncelle)
- Test: `.../tarbil/application/ListPendingSubmissionsUseCaseTest.java`, `.../tarbil/application/GetSubmissionUseCaseTest.java`, `.../tarbil/application/MarkSubmittedUseCaseTest.java`

**Interfaces:**
- Consumes: Task 1 portları, Task 2 `TarbilSyncLog`/repo, Task 3 mapping repo + `VaccineKeyNormalizer`, `TenantLookupPort.findTenantName`, `StaffUserLookupPort.findSummaryById`
- Produces:
  - `record TarbilSubmissionView(UUID id, UUID vaccinationRecordId, TarbilSyncStatus status, String patientName, String microchipNumber, UUID speciesId, String speciesName, String breedName, String sex, LocalDate birthDate, String vaccineName, String lotNumber, LocalDate administeredDate, Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference, String vaccineKey, String vaccineMappingJson, String speciesMappingJson)` — `sex` enum adı (`MALE/FEMALE/UNKNOWN`) ya da `null`; mapping JSON'ları yoksa `null`
  - `List<TarbilSubmissionView> ListPendingSubmissionsUseCase.execute(UUID tenantId)` — iptal edilmiş (`CANCELLED`) ya da kaydı bulunamayan aşılar atlanır
  - `TarbilSubmissionView GetSubmissionUseCase.byId(UUID tenantId, UUID submissionId)`, `byVaccination(UUID tenantId, UUID vaccinationRecordId)` — bulunamazsa/başka kiracıya aitse/aşı iptal/silinmişse `TarbilSubmissionNotFoundException`
  - `TarbilSubmissionView MarkSubmittedUseCase.execute(UUID tenantId, UUID staffId, UUID submissionId, TarbilConfirmationMethod method, String tarbilReference)` — idempotent
  - `void DismissSubmissionUseCase.execute(UUID tenantId, UUID staffId, UUID submissionId, String reason)`; `void RestoreSubmissionUseCase.execute(UUID tenantId, UUID submissionId)`
  - `record ExtensionProfile(String clinicName, String staffName)`; `ExtensionProfile GetExtensionProfileUseCase.execute(UUID tenantId, UUID staffId)`

- [ ] **Step 1: Failing testleri yaz**

`ListPendingSubmissionsUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.*;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import com.vetos.modules.patient.domain.Sex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPendingSubmissionsUseCaseTest {

    @Mock private TarbilSyncLogRepository syncLogRepository;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private final UUID tenantId = UUID.randomUUID();

    private ListPendingSubmissionsUseCase useCase() {
        return new ListPendingSubmissionsUseCase(syncLogRepository,
            new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository));
    }

    @Test
    void should_includeMappingsAndPatientData_when_pendingVaccinationExists() {
        UUID patientId = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        UUID speciesId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queueVaccination(tenantId, patientId, vaccinationId);
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(log));
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, patientId, "Kuduz Aşısı", "L-1", LocalDate.of(2026, 10, 1), VaccinationStatus.ADMINISTERED)));
        when(patientLookupPort.findTarbilProfile(patientId)).thenReturn(Optional.of(new PatientTarbilProfile(
            patientId, "Pamuk", null, speciesId, "Kedi", null, Sex.FEMALE, null)));
        TarbilValueMapping vaccineMapping = TarbilValueMapping.create(
            tenantId, TarbilMappingKind.VACCINE, "kuduz aşısı", "{\"vaccine\":{}}", UUID.randomUUID(), Instant.now());
        when(mappingRepository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.VACCINE, "kuduz aşısı"))
            .thenReturn(Optional.of(vaccineMapping));
        when(mappingRepository.findByTenantIdAndKindAndVetlyKey(eq(tenantId), eq(TarbilMappingKind.SPECIES), any()))
            .thenReturn(Optional.empty());

        List<TarbilSubmissionView> result = useCase().execute(tenantId);

        assertThat(result).hasSize(1);
        TarbilSubmissionView view = result.get(0);
        assertThat(view.patientName()).isEqualTo("Pamuk");
        assertThat(view.microchipNumber()).isNull();
        assertThat(view.sex()).isEqualTo("FEMALE");
        assertThat(view.vaccineKey()).isEqualTo("kuduz aşısı");
        assertThat(view.vaccineMappingJson()).isEqualTo("{\"vaccine\":{}}");
        assertThat(view.speciesMappingJson()).isNull();
    }

    @Test
    void should_skipCancelledAndMissingVaccinations_when_listingPending() {
        UUID cancelledId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(
            TarbilSyncLog.queueVaccination(tenantId, patientId, cancelledId),
            TarbilSyncLog.queueVaccination(tenantId, patientId, missingId)));
        when(vaccinationLookupPort.findForTarbil(cancelledId)).thenReturn(Optional.of(new VaccinationTarbilView(
            cancelledId, tenantId, patientId, "Karma", null, LocalDate.now(), VaccinationStatus.CANCELLED)));
        when(vaccinationLookupPort.findForTarbil(missingId)).thenReturn(Optional.empty());

        assertThat(useCase().execute(tenantId)).isEmpty();
    }
}
```

`GetSubmissionUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import com.vetos.modules.patient.domain.PatientLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSubmissionUseCaseTest {

    @Mock private TarbilSyncLogRepository syncLogRepository;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private GetSubmissionUseCase useCase() {
        return new GetSubmissionUseCase(syncLogRepository,
            new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository));
    }

    @Test
    void should_throwNotFound_when_submissionInAnotherTenant() {
        UUID id = UUID.randomUUID();
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(
            TarbilSyncLog.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> useCase().byId(UUID.randomUUID(), id)).isInstanceOf(TarbilSubmissionNotFoundException.class);
    }

    @Test
    void should_throwNotFound_when_vaccinationCancelled() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(
            TarbilSyncLog.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId)));
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, UUID.randomUUID(), "Kuduz", null, LocalDate.now(), VaccinationStatus.CANCELLED)));

        assertThatThrownBy(() -> useCase().byId(tenantId, id)).isInstanceOf(TarbilSubmissionNotFoundException.class);
    }

    @Test
    void should_throwNotFound_when_noRowForVaccination() {
        UUID vaccinationId = UUID.randomUUID();
        when(syncLogRepository.findByVaccinationRecordId(vaccinationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().byVaccination(UUID.randomUUID(), vaccinationId))
            .isInstanceOf(TarbilSubmissionNotFoundException.class);
    }
}
```

`MarkSubmittedUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.domain.*;
import com.vetos.modules.patient.domain.PatientLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkSubmittedUseCaseTest {

    @Mock private TarbilSyncLogRepository syncLogRepository;
    @Mock private TarbilValueMappingRepository mappingRepository;
    @Mock private VaccinationLookupPort vaccinationLookupPort;
    @Mock private PatientLookupPort patientLookupPort;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private MarkSubmittedUseCase useCase() {
        return new MarkSubmittedUseCase(syncLogRepository,
            new TarbilSubmissionAssembler(vaccinationLookupPort, patientLookupPort, mappingRepository));
    }

    private TarbilSyncLog pendingWithVaccination(UUID id) {
        UUID vaccinationId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queueVaccination(tenantId, UUID.randomUUID(), vaccinationId);
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(log));
        lenient().when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, log.getPatientId(), "Kuduz", null, LocalDate.now(), VaccinationStatus.ADMINISTERED)));
        lenient().when(patientLookupPort.findTarbilProfile(any())).thenReturn(Optional.empty());
        return log;
    }

    @Test
    void should_markAndSave_when_pending() {
        UUID id = UUID.randomUUID();
        TarbilSyncLog log = pendingWithVaccination(id);

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.AUTO, "TRB-9");

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.SUBMITTED);
        verify(syncLogRepository).save(log);
    }

    @Test
    void should_returnExistingWithoutChange_when_alreadySubmitted() {
        UUID id = UUID.randomUUID();
        TarbilSyncLog log = pendingWithVaccination(id);
        log.markSubmitted(staffId, TarbilConfirmationMethod.AUTO, "TRB-1", Instant.now());

        useCase().execute(tenantId, staffId, id, TarbilConfirmationMethod.MANUAL, null);

        assertThat(log.getTarbilReference()).isEqualTo("TRB-1");
        verify(syncLogRepository, never()).save(any());
    }
}
```

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd backend && ./mvnw test -Dtest='ListPendingSubmissionsUseCaseTest,GetSubmissionUseCaseTest,MarkSubmittedUseCaseTest'`
Expected: Derleme hatası.

- [ ] **Step 3: Exception, DTO, assembler**

`TarbilSyncLogNotFoundException.java`'ı sil (`git rm`), yerine `exception/TarbilSubmissionNotFoundException.java`:
```java
package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilSubmissionNotFoundException extends DomainException {
    public TarbilSubmissionNotFoundException(UUID id) {
        super("TARBIL_SUBMISSION_NOT_FOUND", "TARBIL aktarim kaydi bulunamadi: " + id);
    }
}
```

`dto/TarbilSubmissionView.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Eklentiye giden aktarim verisi -- sahip TC/adres/telefon BILINCLI olarak yok (spec S4). */
public record TarbilSubmissionView(
    UUID id, UUID vaccinationRecordId, TarbilSyncStatus status,
    String patientName, String microchipNumber, UUID speciesId, String speciesName, String breedName,
    String sex, LocalDate birthDate,
    String vaccineName, String lotNumber, LocalDate administeredDate,
    Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference,
    String vaccineKey, String vaccineMappingJson, String speciesMappingJson
) {}
```

`TarbilSubmissionAssembler.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.*;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** TarbilSyncLog + asi + hasta + esletirmeleri tek gorunumde birlestirir. Asi iptal/silinmisse bos doner. */
@Component
@RequiredArgsConstructor
class TarbilSubmissionAssembler {

    private final VaccinationLookupPort vaccinationLookupPort;
    private final PatientLookupPort patientLookupPort;
    private final TarbilValueMappingRepository mappingRepository;

    Optional<TarbilSubmissionView> assemble(TarbilSyncLog log) {
        Optional<VaccinationTarbilView> vaccination = vaccinationLookupPort.findForTarbil(log.getVaccinationRecordId())
            .filter(v -> v.tenantId().equals(log.getTenantId()))
            .filter(v -> v.status() != VaccinationStatus.CANCELLED);
        if (vaccination.isEmpty()) {
            return Optional.empty();
        }
        VaccinationTarbilView v = vaccination.get();
        Optional<PatientTarbilProfile> patient = patientLookupPort.findTarbilProfile(log.getPatientId());
        String vaccineKey = VaccineKeyNormalizer.normalize(v.vaccineName());
        String vaccineMapping = mappingRepository
            .findByTenantIdAndKindAndVetlyKey(log.getTenantId(), TarbilMappingKind.VACCINE, vaccineKey)
            .map(TarbilValueMapping::getTarbilFields).orElse(null);
        String speciesMapping = patient.map(PatientTarbilProfile::speciesId)
            .flatMap(sid -> mappingRepository.findByTenantIdAndKindAndVetlyKey(log.getTenantId(), TarbilMappingKind.SPECIES, sid.toString()))
            .map(TarbilValueMapping::getTarbilFields).orElse(null);
        return Optional.of(new TarbilSubmissionView(
            log.getId(), log.getVaccinationRecordId(), log.getStatus(),
            patient.map(PatientTarbilProfile::name).orElse("—"),
            patient.map(PatientTarbilProfile::microchipNumber).orElse(null),
            patient.map(PatientTarbilProfile::speciesId).orElse(null),
            patient.map(PatientTarbilProfile::speciesName).orElse(null),
            patient.map(PatientTarbilProfile::breedName).orElse(null),
            patient.map(PatientTarbilProfile::sex).map(Enum::name).orElse(null),
            patient.map(PatientTarbilProfile::birthDate).orElse(null),
            v.vaccineName(), v.lotNumber(), v.administeredDate(),
            log.getSubmittedAt(), log.getConfirmationMethod(), log.getTarbilReference(),
            vaccineKey, vaccineMapping, speciesMapping
        ));
    }
}
```

- [ ] **Step 4: Use-case'ler**

`ListPendingSubmissionsUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListPendingSubmissionsUseCase {

    private final TarbilSyncLogRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional(readOnly = true)
    public List<TarbilSubmissionView> execute(UUID tenantId) {
        return syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING).stream()
            .map(assembler::assemble)
            .flatMap(Optional::stream)
            .toList();
    }
}
```

`GetSubmissionUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetSubmissionUseCase {

    private final TarbilSyncLogRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional(readOnly = true)
    public TarbilSubmissionView byId(UUID tenantId, UUID submissionId) {
        return resolve(tenantId, syncLogRepository.findById(submissionId), submissionId);
    }

    @Transactional(readOnly = true)
    public TarbilSubmissionView byVaccination(UUID tenantId, UUID vaccinationRecordId) {
        return resolve(tenantId, syncLogRepository.findByVaccinationRecordId(vaccinationRecordId), vaccinationRecordId);
    }

    private TarbilSubmissionView resolve(UUID tenantId, Optional<TarbilSyncLog> log, UUID requestedId) {
        return log.filter(l -> l.getTenantId().equals(tenantId))
            .flatMap(assembler::assemble)
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(requestedId));
    }
}
```

`MarkSubmittedUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Idempotent: eklentinin cevrimdisi kuyrugu ayni onayi birden fazla gonderebilir. */
@Service
@RequiredArgsConstructor
public class MarkSubmittedUseCase {

    private final TarbilSyncLogRepository syncLogRepository;
    private final TarbilSubmissionAssembler assembler;

    @Transactional
    public TarbilSubmissionView execute(UUID tenantId, UUID staffId, UUID submissionId,
                                        TarbilConfirmationMethod method, String tarbilReference) {
        TarbilSyncLog log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        String reference = tarbilReference == null || tarbilReference.isBlank() ? null : tarbilReference.trim();
        if (log.markSubmitted(staffId, method, reference, Instant.now())) {
            syncLogRepository.save(log);
        }
        return assembler.assemble(log).orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
    }
}
```

`DismissSubmissionUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DismissSubmissionUseCase {

    private final TarbilSyncLogRepository syncLogRepository;

    @Transactional
    public void execute(UUID tenantId, UUID staffId, UUID submissionId, String reason) {
        TarbilSyncLog log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        String safeReason = reason == null || reason.isBlank() ? null : reason.trim();
        log.dismiss(staffId, safeReason, Instant.now());
        syncLogRepository.save(log);
    }
}
```

`RestoreSubmissionUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestoreSubmissionUseCase {

    private final TarbilSyncLogRepository syncLogRepository;

    @Transactional
    public void execute(UUID tenantId, UUID submissionId) {
        TarbilSyncLog log = syncLogRepository.findById(submissionId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSubmissionNotFoundException(submissionId));
        log.restore();
        syncLogRepository.save(log);
    }
}
```

`dto/ExtensionProfile.java`:
```java
package com.vetos.modules.integration.tarbil.application.dto;

public record ExtensionProfile(String clinicName, String staffName) {}
```

`GetExtensionProfileUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionProfile;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetExtensionProfileUseCase {

    private final TenantLookupPort tenantLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public ExtensionProfile execute(UUID tenantId, UUID staffId) {
        return new ExtensionProfile(
            tenantLookupPort.findTenantName(tenantId).orElse("Klinik"),
            staffUserLookupPort.findSummaryById(staffId).fullName()
        );
    }
}
```

- [ ] **Step 5: Testleri çalıştır**

Run: `cd backend && ./mvnw test -Dtest='ListPendingSubmissionsUseCaseTest,GetSubmissionUseCaseTest,MarkSubmittedUseCaseTest,ApplicationModulesTest'`
Expected: PASS. `TarbilSyncLogNotFoundException`'a kalan referans varsa derleme hatası — `grep -rn TarbilSyncLogNotFoundException backend/src` ile temizle.

- [ ] **Step 6: Commit**

```bash
git add -A backend
git commit -m "feat(tarbil): bekleyen/tekil aktarim, gonderildi ve bildirilmeyecek use-case'leri"
```

---

### Task 7: Web ve eklenti controller'ları + güvenlik entegrasyon testi

**Files:**
- Modify: `.../tarbil/api/TarbilController.java`
- Create: `.../tarbil/api/TarbilExtensionController.java`
- Create: `.../tarbil/api/dto/{PairingCodeResponse,ExtensionTokenResponse,TarbilMappingResponse,DismissRequest,PairRequest,PairResponse,MarkSubmittedRequest,TarbilSubmissionResponse,ExtensionProfileResponse}.java`
- Test: `backend/src/test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3, 4, 6 use-case'leri.
- Produces (HTTP):
  - Web (JWT, `ADMIN`/`VET`): `GET /api/v1/tarbil/status`, `GET /api/v1/tarbil/sync-logs`, `POST /api/v1/tarbil/sync-logs/{id}/dismiss` body `{"reason": "..."}` → 204, `POST /api/v1/tarbil/sync-logs/{id}/restore` → 204, `POST /api/v1/tarbil/extension/pairing-codes` → 201 `{"code","expiresAt"}`, `GET /api/v1/tarbil/extension/tokens` → `[{"id","staffUserId","staffName","label","pairedAt","lastUsedAt","revokedAt"}]`, `DELETE /api/v1/tarbil/extension/tokens/{id}` → 204, `GET /api/v1/tarbil/mappings` → `[{"id","kind","vetlyKey","tarbilFields"(nesne),"updatedAt"}]`, `DELETE /api/v1/tarbil/mappings/{id}` → 204
  - Eklenti: `POST /api/v1/tarbil-extension/pair` `{"code","label"}` → `{"token"}`; `GET /me` → `{"clinicName","staffName"}`; `GET /pending` → `TarbilSubmissionResponse[]`; `GET /submissions/{id}`; `GET /submissions/by-vaccination/{vaccinationRecordId}`; `POST /submissions/{id}/submitted` `{"method":"AUTO|MANUAL","tarbilReference":null}` → `TarbilSubmissionResponse`; `POST /submissions/{id}/dismiss` `{"reason"}` → 204; `PUT /mappings/{kind}/{key}` body: herhangi bir JSON nesnesi → 204
  - `TarbilSubmissionResponse` alanları: `TarbilSubmissionView` ile aynı adlar; `vaccineMapping` ve `speciesMapping` JSON nesnesi olarak (`@JsonRawValue`), yoksa `null`

- [ ] **Step 1: Güvenlik entegrasyon testini yaz (canlı Postgres)**

`TarbilExtensionSecurityIntegrationTest.java`:
```java
package com.vetos;

import com.vetos.modules.integration.tarbil.application.CreatePairingCodeUseCase;
import com.vetos.modules.integration.tarbil.application.PairExtensionUseCase;
import com.vetos.modules.integration.tarbil.application.RevokeExtensionTokenUseCase;
import com.vetos.modules.integration.tarbil.application.ListExtensionTokensUseCase;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TenantIsolationTest ile ayni bilincli istisna: canli docker-compose Postgres
 * gerekir (cd backend && docker-compose up -d). Testcontainers KULLANILMIYOR.
 * Dogrular: eklenti anahtari yalniz /api/v1/tarbil-extension/** uclarina erisir,
 * JWT eklenti uclarina erisemez, iptal edilen anahtar reddedilir, A kiracisinin
 * anahtari B kiracisinin kaydini goremez.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TarbilExtensionSecurityIntegrationTest extends TenantScopedTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private CreatePairingCodeUseCase createPairingCodeUseCase;
    @Autowired private PairExtensionUseCase pairExtensionUseCase;
    @Autowired private RevokeExtensionTokenUseCase revokeExtensionTokenUseCase;
    @Autowired private ListExtensionTokensUseCase listExtensionTokensUseCase;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID tenantA;
    private UUID staffA;
    private UUID tenantB;
    private String tokenA;
    private String jwtA;

    @BeforeEach
    void setUp() {
        tenantA = inRootSession(() -> tenantRepository.save(Tenant.register("Klinik A " + UUID.randomUUID(), null)).getId());
        tenantB = inRootSession(() -> tenantRepository.save(Tenant.register("Klinik B " + UUID.randomUUID(), null)).getId());
        staffA = asTenant(tenantA, () -> {
            Branch branch = branchRepository.save(Branch.create(tenantA, "Merkez"));
            return staffUserRepository.save(StaffUser.register(
                tenantA, branch.getId(), "Dr. A", "a-" + UUID.randomUUID() + "@test.local", "x", StaffRole.VET)).getId();
        });
        String code = createPairingCodeUseCase.execute(tenantA, staffA).code();
        tokenA = pairExtensionUseCase.execute(code, "Test PC");
        jwtA = jwtTokenProvider.generate(staffA, tenantA, java.util.List.of(), "VET");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM tarbil_extension_token WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        jdbcTemplate.update("DELETE FROM staff_users WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id IN (?, ?)", tenantA, tenantB);
        jdbcTemplate.update("DELETE FROM tenants WHERE id IN (?, ?)", tenantA, tenantB);
    }

    @Test
    void extensionTokenReachesExtensionEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk());
    }

    @Test
    void extensionTokenCannotReachOtherApis() throws Exception {
        mockMvc.perform(get("/api/v1/patients").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtCannotReachExtensionEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        UUID tokenId = listExtensionTokensUseCase.execute(tenantA, staffA, false).get(0).id();
        revokeExtensionTokenUseCase.execute(tenantA, staffA, false, tokenId);

        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCannotSeeAnotherTenantsSubmission() throws Exception {
        UUID foreignLogId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO tarbil_sync_log (id, tenant_id, patient_id, vaccination_record_id, sync_type, status, queued_at)
            SELECT ?, ?, p.id, ?, 'VACCINATION', 'PENDING', now() FROM patients p LIMIT 1
            """, foreignLogId, tenantB, UUID.randomUUID());
        try {
            mockMvc.perform(get("/api/v1/tarbil-extension/submissions/" + foreignLogId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        } finally {
            jdbcTemplate.update("DELETE FROM tarbil_sync_log WHERE id = ?", foreignLogId);
        }
    }
}
```
**Not:** `Branch.create(...)`, `Tenant.register(...)` ve `JwtTokenProvider.generate(...)` imzalarını `TenantIsolationTest` ve `JwtTokenProvider`'daki gerçek imzalarla eşle (aynı test sınıfı bu nesneleri zaten oluşturuyor — oradaki çağrıyı birebir kopyala). `tokenCannotSeeAnotherTenantsSubmission` testi veritabanında en az bir hasta olmasına dayanır; hasta yoksa `INSERT` 0 satır ekler ve test yine 404 bekler (geçerli kalır), ama anlamlı olması için `docker-compose` DB'sinde smoke verisi bulunmalı.

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd backend && docker-compose up -d && ./mvnw test -Dtest=TarbilExtensionSecurityIntegrationTest`
Expected: `extensionTokenReachesExtensionEndpoints` FAIL (404 — controller yok).

- [ ] **Step 3: DTO'lar**

`api/dto/PairingCodeResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.PairingCode;

import java.time.Instant;

public record PairingCodeResponse(String code, Instant expiresAt) {
    public static PairingCodeResponse from(PairingCode c) { return new PairingCodeResponse(c.code(), c.expiresAt()); }
}
```

`api/dto/ExtensionTokenResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionTokenSummary;

import java.time.Instant;
import java.util.UUID;

public record ExtensionTokenResponse(UUID id, UUID staffUserId, String staffName, String label,
                                     Instant pairedAt, Instant lastUsedAt, Instant revokedAt) {
    public static ExtensionTokenResponse from(ExtensionTokenSummary s) {
        return new ExtensionTokenResponse(s.id(), s.staffUserId(), s.staffName(), s.label(), s.pairedAt(), s.lastUsedAt(), s.revokedAt());
    }
}
```

`api/dto/TarbilMappingResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.vetos.modules.integration.tarbil.application.dto.TarbilMappingSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;

import java.time.Instant;
import java.util.UUID;

public record TarbilMappingResponse(UUID id, TarbilMappingKind kind, String vetlyKey,
                                    @JsonRawValue String tarbilFields, Instant updatedAt) {
    public static TarbilMappingResponse from(TarbilMappingSummary s) {
        return new TarbilMappingResponse(s.id(), s.kind(), s.vetlyKey(), s.tarbilFields(), s.updatedAt());
    }
}
```

`api/dto/DismissRequest.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.Size;

public record DismissRequest(@Size(max = 200) String reason) {}
```

`api/dto/PairRequest.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PairRequest(@NotBlank @Size(max = 20) String code, @Size(max = 60) String label) {}
```

`api/dto/PairResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

public record PairResponse(String token) {}
```

`api/dto/MarkSubmittedRequest.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarkSubmittedRequest(@NotNull TarbilConfirmationMethod method, @Size(max = 100) String tarbilReference) {}
```

`api/dto/ExtensionProfileResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionProfile;

public record ExtensionProfileResponse(String clinicName, String staffName) {
    public static ExtensionProfileResponse from(ExtensionProfile p) { return new ExtensionProfileResponse(p.clinicName(), p.staffName()); }
}
```

`api/dto/TarbilSubmissionResponse.java`:
```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TarbilSubmissionResponse(
    UUID id, UUID vaccinationRecordId, TarbilSyncStatus status,
    String patientName, String microchipNumber, UUID speciesId, String speciesName, String breedName,
    String sex, LocalDate birthDate, String vaccineName, String lotNumber, LocalDate administeredDate,
    Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference,
    String vaccineKey, @JsonRawValue String vaccineMapping, @JsonRawValue String speciesMapping
) {
    public static TarbilSubmissionResponse from(TarbilSubmissionView v) {
        return new TarbilSubmissionResponse(v.id(), v.vaccinationRecordId(), v.status(), v.patientName(), v.microchipNumber(),
            v.speciesId(), v.speciesName(), v.breedName(), v.sex(), v.birthDate(), v.vaccineName(), v.lotNumber(),
            v.administeredDate(), v.submittedAt(), v.confirmationMethod(), v.tarbilReference(),
            v.vaccineKey(), v.vaccineMappingJson(), v.speciesMappingJson());
    }
}
```

- [ ] **Step 4: Controller'lar**

`TarbilController.java` (dosyanın tamamı):
```java
package com.vetos.modules.integration.tarbil.api;

import com.vetos.modules.integration.tarbil.api.dto.*;
import com.vetos.modules.integration.tarbil.application.*;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** api-conventions.md rol matrisi: /tarbil/** -> ADMIN, VET (aktarimi hekimler yapar). */
@RestController
@RequestMapping("/api/v1/tarbil")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','VET')")
public class TarbilController {

    private final GetTarbilStatusSummaryUseCase getTarbilStatusSummaryUseCase;
    private final ListTarbilSyncLogsUseCase listTarbilSyncLogsUseCase;
    private final DismissSubmissionUseCase dismissSubmissionUseCase;
    private final RestoreSubmissionUseCase restoreSubmissionUseCase;
    private final CreatePairingCodeUseCase createPairingCodeUseCase;
    private final ListExtensionTokensUseCase listExtensionTokensUseCase;
    private final RevokeExtensionTokenUseCase revokeExtensionTokenUseCase;
    private final ListTarbilMappingsUseCase listTarbilMappingsUseCase;
    private final DeleteTarbilMappingUseCase deleteTarbilMappingUseCase;

    @GetMapping("/status")
    public TarbilStatusResponse status() {
        return TarbilStatusResponse.from(getTarbilStatusSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/sync-logs")
    public List<TarbilSyncLogResponse> syncLogs() {
        return listTarbilSyncLogsUseCase.execute(TenantContext.current()).stream().map(TarbilSyncLogResponse::from).toList();
    }

    @PostMapping("/sync-logs/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable UUID id, @Valid @RequestBody DismissRequest request,
                        @AuthenticationPrincipal AuthenticatedStaffUser user) {
        dismissSubmissionUseCase.execute(TenantContext.current(), user.staffUserId(), id, request.reason());
    }

    @PostMapping("/sync-logs/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable UUID id) {
        restoreSubmissionUseCase.execute(TenantContext.current(), id);
    }

    @PostMapping("/extension/pairing-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public PairingCodeResponse createPairingCode(@AuthenticationPrincipal AuthenticatedStaffUser user) {
        return PairingCodeResponse.from(createPairingCodeUseCase.execute(TenantContext.current(), user.staffUserId()));
    }

    @GetMapping("/extension/tokens")
    public List<ExtensionTokenResponse> tokens(@AuthenticationPrincipal AuthenticatedStaffUser user) {
        return listExtensionTokensUseCase.execute(TenantContext.current(), user.staffUserId(), "ADMIN".equals(user.role()))
            .stream().map(ExtensionTokenResponse::from).toList();
    }

    @DeleteMapping("/extension/tokens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedStaffUser user) {
        revokeExtensionTokenUseCase.execute(TenantContext.current(), user.staffUserId(), "ADMIN".equals(user.role()), id);
    }

    @GetMapping("/mappings")
    public List<TarbilMappingResponse> mappings() {
        return listTarbilMappingsUseCase.execute(TenantContext.current()).stream().map(TarbilMappingResponse::from).toList();
    }

    @DeleteMapping("/mappings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMapping(@PathVariable UUID id) {
        deleteTarbilMappingUseCase.execute(TenantContext.current(), id);
    }
}
```

`TarbilExtensionController.java`:
```java
package com.vetos.modules.integration.tarbil.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.vetos.modules.integration.tarbil.api.dto.*;
import com.vetos.modules.integration.tarbil.application.*;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Yalnizca eklenti anahtariyla (TarbilExtensionSecurityConfig) erisilir; /pair herkese acik ve hiz sinirli. */
@RestController
@RequestMapping("/api/v1/tarbil-extension")
@RequiredArgsConstructor
public class TarbilExtensionController {

    private final PairExtensionUseCase pairExtensionUseCase;
    private final GetExtensionProfileUseCase getExtensionProfileUseCase;
    private final ListPendingSubmissionsUseCase listPendingSubmissionsUseCase;
    private final GetSubmissionUseCase getSubmissionUseCase;
    private final MarkSubmittedUseCase markSubmittedUseCase;
    private final DismissSubmissionUseCase dismissSubmissionUseCase;
    private final LearnTarbilMappingUseCase learnTarbilMappingUseCase;

    @PostMapping("/pair")
    public PairResponse pair(@Valid @RequestBody PairRequest request) {
        return new PairResponse(pairExtensionUseCase.execute(request.code(), request.label()));
    }

    @GetMapping("/me")
    public ExtensionProfileResponse me(@AuthenticationPrincipal AuthenticatedStaffUser user) {
        return ExtensionProfileResponse.from(getExtensionProfileUseCase.execute(TenantContext.current(), user.staffUserId()));
    }

    @GetMapping("/pending")
    public List<TarbilSubmissionResponse> pending() {
        return listPendingSubmissionsUseCase.execute(TenantContext.current()).stream().map(TarbilSubmissionResponse::from).toList();
    }

    @GetMapping("/submissions/{id}")
    public TarbilSubmissionResponse submission(@PathVariable UUID id) {
        return TarbilSubmissionResponse.from(getSubmissionUseCase.byId(TenantContext.current(), id));
    }

    @GetMapping("/submissions/by-vaccination/{vaccinationRecordId}")
    public TarbilSubmissionResponse submissionByVaccination(@PathVariable UUID vaccinationRecordId) {
        return TarbilSubmissionResponse.from(getSubmissionUseCase.byVaccination(TenantContext.current(), vaccinationRecordId));
    }

    @PostMapping("/submissions/{id}/submitted")
    public TarbilSubmissionResponse submitted(@PathVariable UUID id, @Valid @RequestBody MarkSubmittedRequest request,
                                              @AuthenticationPrincipal AuthenticatedStaffUser user) {
        return TarbilSubmissionResponse.from(markSubmittedUseCase.execute(
            TenantContext.current(), user.staffUserId(), id, request.method(), request.tarbilReference()));
    }

    @PostMapping("/submissions/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable UUID id, @Valid @RequestBody DismissRequest request,
                        @AuthenticationPrincipal AuthenticatedStaffUser user) {
        dismissSubmissionUseCase.execute(TenantContext.current(), user.staffUserId(), id, request.reason());
    }

    @PutMapping("/mappings/{kind}/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void learnMapping(@PathVariable TarbilMappingKind kind, @PathVariable String key, @RequestBody JsonNode fields,
                             @AuthenticationPrincipal AuthenticatedStaffUser user) {
        learnTarbilMappingUseCase.execute(TenantContext.current(), user.staffUserId(), kind, key, fields.toString());
    }
}
```

- [ ] **Step 5: Tüm testleri çalıştır**

Run: `cd backend && ./mvnw test`
Expected: PASS (`TarbilExtensionSecurityIntegrationTest` ve `TenantIsolationTest` canlı Postgres ile).

- [ ] **Step 6: curl ile uçtan uca doğrula**

Backend'i başlat (`./mvnw spring-boot:run`), CLAUDE.md'deki akışla bir VET kullanıcısıyla giriş yapıp JWT al (`$JWT`), sonra:
```bash
CODE=$(curl -s -X POST -H "Authorization: Bearer $JWT" http://localhost:8080/api/v1/tarbil/extension/pairing-codes | jq -r .code)
TOKEN=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"code\":\"$CODE\",\"label\":\"curl\"}" http://localhost:8080/api/v1/tarbil-extension/pair | jq -r .token)
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/tarbil-extension/me
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/tarbil-extension/pending
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/patients   # 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" -d "{\"code\":\"$CODE\"}" http://localhost:8080/api/v1/tarbil-extension/pair   # 401 (kod tek kullanimlik)
```
Bir aşıyı "yapıldı" işaretleyip `pending`'de göründüğünü, `POST /submissions/{id}/submitted {"method":"MANUAL"}` sonrası listeden düştüğünü ve ikinci aynı çağrının 200 ile aynı kaydı döndürdüğünü doğrula.

- [ ] **Step 7: Commit**

```bash
git add -A backend
git commit -m "feat(tarbil): web ve eklenti API uclari + guvenlik entegrasyon testi"
```

---

### Task 8: Frontend — Ayarlar > Entegrasyonlar (durum, eklenti bağlama, eşleştirmeler)

**Files:**
- Modify: `frontend/src/api/tarbilApi.ts`
- Modify: `frontend/src/pages/settings/tarbilStatus.tsx`
- Rewrite: `frontend/src/pages/settings/IntegrationsPanel.tsx`
- Create: `frontend/src/pages/settings/TarbilExtensionCard.tsx`
- Create: `frontend/src/pages/settings/TarbilMappingsCard.tsx`

**Interfaces:**
- Consumes: Task 7 web uçları.
- Produces: `tarbilApi.{status, syncLogs, dismiss(id, reason), restore(id), createPairingCode(), extensionTokens(), revokeExtensionToken(id), mappings(), deleteMapping(id)}`; tipler `TarbilSyncStatus = 'PENDING' | 'SUBMITTED' | 'DISMISSED'`, `TarbilConfirmationMethod = 'AUTO' | 'MANUAL'`.

- [ ] **Step 1: API istemcisi**

`frontend/src/api/tarbilApi.ts` (dosyanın tamamı):
```ts
import { apiClient } from './client';

export type TarbilSyncStatus = 'PENDING' | 'SUBMITTED' | 'DISMISSED';
export type TarbilConfirmationMethod = 'AUTO' | 'MANUAL';
export type TarbilMappingKind = 'VACCINE' | 'SPECIES';

export interface TarbilStatus {
  pendingCount: number;
  submittedCount: number;
  dismissedCount: number;
  lastSubmittedAt: string | null;
}

export interface TarbilSyncLog {
  id: string;
  patientId: string;
  patientName: string;
  vaccineName: string;
  administeredDate: string | null;
  status: TarbilSyncStatus;
  queuedAt: string;
  submittedAt: string | null;
  confirmationMethod: TarbilConfirmationMethod | null;
  tarbilReference: string | null;
  dismissedReason: string | null;
}

export interface PairingCode {
  code: string;
  expiresAt: string;
}

export interface ExtensionToken {
  id: string;
  staffUserId: string;
  staffName: string;
  label: string | null;
  pairedAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

export interface TarbilMapping {
  id: string;
  kind: TarbilMappingKind;
  vetlyKey: string;
  tarbilFields: Record<string, { value?: string; text?: string }>;
  updatedAt: string;
}

export const tarbilApi = {
  status: () => apiClient.get<TarbilStatus>('/api/v1/tarbil/status'),
  syncLogs: () => apiClient.get<TarbilSyncLog[]>('/api/v1/tarbil/sync-logs'),
  dismiss: (id: string, reason: string) => apiClient.post<void>(`/api/v1/tarbil/sync-logs/${id}/dismiss`, { reason }),
  restore: (id: string) => apiClient.post<void>(`/api/v1/tarbil/sync-logs/${id}/restore`),
  createPairingCode: () => apiClient.post<PairingCode>('/api/v1/tarbil/extension/pairing-codes'),
  extensionTokens: () => apiClient.get<ExtensionToken[]>('/api/v1/tarbil/extension/tokens'),
  revokeExtensionToken: (id: string) => apiClient.delete<void>(`/api/v1/tarbil/extension/tokens/${id}`),
  mappings: () => apiClient.get<TarbilMapping[]>('/api/v1/tarbil/mappings'),
  deleteMapping: (id: string) => apiClient.delete<void>(`/api/v1/tarbil/mappings/${id}`),
};
```
(`apiClient.post`'un ikinci argümanı gövde; imzayı `frontend/src/api/client.ts`'teki `apiClient` nesnesinden doğrula. `post` gövde döndürmüyorsa `createPairingCode` için `client.ts`'te JSON döndüren varyantı kullan — `get/post/put/delete` tanımlarına bak.)

- [ ] **Step 2: Durum rozeti**

`frontend/src/pages/settings/tarbilStatus.tsx` (dosyanın tamamı):
```tsx
import { Badge, BadgeTone } from '../../components/ui/Badge';
import { TarbilConfirmationMethod, TarbilSyncStatus } from '../../api/tarbilApi';

const STATUS_CONFIG: Record<TarbilSyncStatus, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'Bekliyor', tone: 'warning' },
  SUBMITTED: { label: 'Gönderildi', tone: 'success' },
  DISMISSED: { label: 'Bildirilmeyecek', tone: 'neutral' },
};

const METHOD_LABELS: Record<TarbilConfirmationMethod, string> = {
  AUTO: 'Eklenti doğruladı',
  MANUAL: 'Elle işaretlendi',
};

export function TarbilSyncStatusBadge({ status }: { status: TarbilSyncStatus }) {
  const { label, tone } = STATUS_CONFIG[status];
  return <Badge tone={tone}>{label}</Badge>;
}

export function confirmationMethodLabel(method: TarbilConfirmationMethod | null) {
  return method ? METHOD_LABELS[method] : '';
}
```
(`BadgeTone`'da `warning` yoksa `components/ui/Badge.tsx`'teki mevcut tonlardan rust/terrakota olanı kullan — `docs/design-system.md`: kural tabanlı uyarılar `warning` tonu, AI mavisi değil.)

- [ ] **Step 3: Eklenti kartı**

`frontend/src/pages/settings/TarbilExtensionCard.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { ExtensionToken, PairingCode, tarbilApi } from '../../api/tarbilApi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import styles from './SettingsPage.module.css';

export function TarbilExtensionCard() {
  const [tokens, setTokens] = useState<ExtensionToken[]>([]);
  const [code, setCode] = useState<PairingCode | null>(null);
  const [busy, setBusy] = useState(false);

  function reload() {
    tarbilApi.extensionTokens().then(setTokens);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleCreateCode() {
    setBusy(true);
    try {
      setCode(await tarbilApi.createPairingCode());
    } finally {
      setBusy(false);
    }
  }

  async function handleRevoke(id: string) {
    setBusy(true);
    try {
      await tarbilApi.revokeExtensionToken(id);
      reload();
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.integrationCard}>
      <div className={styles.integrationHeader}>
        <div>
          <div className={styles.integrationName}>TARBİL Chrome Eklentisi</div>
          <div className={styles.integrationDesc}>
            Eklentiyi bağlamak için kod oluşturun ve eklentinin yan panelinde girin. Kod 10 dakika geçerlidir ve bir kez kullanılabilir.
          </div>
        </div>
        <Button variant="secondary" onClick={handleCreateCode} disabled={busy}>
          Eklentiyi bağla
        </Button>
      </div>

      {code && (
        <div className={styles.lastSynced}>
          Eşleştirme kodu: <strong>{code.code}</strong> — {new Date(code.expiresAt).toLocaleTimeString('tr-TR')} saatine kadar geçerli
        </div>
      )}

      {tokens.length === 0 ? (
        <div className={styles.empty}>Bağlı eklenti yok</div>
      ) : (
        tokens.map((t) => (
          <div key={t.id} className={styles.row}>
            <div>{t.label ?? 'Eklenti'}</div>
            <div className={styles.muted}>{t.staffName}</div>
            <div className={styles.muted}>
              {t.lastUsedAt ? `Son kullanım: ${new Date(t.lastUsedAt).toLocaleString('tr-TR')}` : 'Henüz kullanılmadı'}
            </div>
            <div>{t.revokedAt ? <Badge tone="neutral">İptal edildi</Badge> : <Badge tone="success">Aktif</Badge>}</div>
            <div>
              {!t.revokedAt && (
                <Button variant="danger" onClick={() => handleRevoke(t.id)} disabled={busy}>
                  İptal et
                </Button>
              )}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
```

- [ ] **Step 4: Eşleştirmeler kartı**

`frontend/src/pages/settings/TarbilMappingsCard.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { TarbilMapping, tarbilApi } from '../../api/tarbilApi';
import { Button } from '../../components/ui/Button';
import styles from './SettingsPage.module.css';

const KIND_LABELS = { VACCINE: 'Aşı', SPECIES: 'Tür' } as const;

function describeFields(fields: TarbilMapping['tarbilFields']) {
  return Object.entries(fields)
    .map(([key, v]) => `${key}: ${v.text ?? v.value ?? '?'}`)
    .join(' · ');
}

export function TarbilMappingsCard() {
  const [mappings, setMappings] = useState<TarbilMapping[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  function reload() {
    tarbilApi.mappings().then(setMappings);
  }

  useEffect(() => {
    reload();
  }, []);

  async function handleDelete(id: string) {
    setBusyId(id);
    try {
      await tarbilApi.deleteMapping(id);
      reload();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className={styles.tableCard}>
      <div className={styles.tableHead}>
        <div>Öğrenilen eşleştirme</div>
        <div>Tür</div>
        <div>TARBİL değeri</div>
        <div>Güncellendi</div>
        <div></div>
      </div>
      {mappings.length === 0 ? (
        <div className={styles.empty}>
          Henüz eşleştirme yok. Bir aşıyı ilk kez TARBİL'e aktardığınızda seçtiğiniz değerler burada görünür.
        </div>
      ) : (
        mappings.map((m) => (
          <div key={m.id} className={styles.row}>
            <div>{m.vetlyKey}</div>
            <div className={styles.muted}>{KIND_LABELS[m.kind]}</div>
            <div className={styles.muted}>{describeFields(m.tarbilFields)}</div>
            <div className={styles.muted}>{new Date(m.updatedAt).toLocaleString('tr-TR')}</div>
            <div>
              <Button variant="tertiary" onClick={() => handleDelete(m.id)} disabled={busyId === m.id}>
                Sil
              </Button>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
```

- [ ] **Step 5: Panel**

`frontend/src/pages/settings/IntegrationsPanel.tsx` (dosyanın tamamı):
```tsx
import { useEffect, useState } from 'react';
import { tarbilApi, TarbilStatus, TarbilSyncLog } from '../../api/tarbilApi';
import { Button } from '../../components/ui/Button';
import { confirmationMethodLabel, TarbilSyncStatusBadge } from './tarbilStatus';
import { TarbilExtensionCard } from './TarbilExtensionCard';
import { TarbilMappingsCard } from './TarbilMappingsCard';
import styles from './SettingsPage.module.css';

export function IntegrationsPanel() {
  const [status, setStatus] = useState<TarbilStatus | null>(null);
  const [logs, setLogs] = useState<TarbilSyncLog[]>([]);
  const [busyId, setBusyId] = useState<string | null>(null);

  function reload() {
    tarbilApi.status().then(setStatus);
    tarbilApi.syncLogs().then(setLogs);
  }

  useEffect(() => {
    reload();
  }, []);

  async function run(id: string, action: () => Promise<void>) {
    setBusyId(id);
    try {
      await action();
      reload();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <div className={styles.integrationCard}>
        <div className={styles.integrationHeader}>
          <div>
            <div className={styles.integrationName}>TARBİL</div>
            <div className={styles.integrationDesc}>
              Aşı bildirimleri TARBİL'e hekim tarafından girilir; Vetly yalnızca gerçekten bildirilenleri "Gönderildi" gösterir.
            </div>
          </div>
        </div>

        {status && (
          <>
            <div className={styles.statsRow}>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Bekleyen</div>
                <div className={styles.statValue}>{status.pendingCount}</div>
              </div>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Gönderildi</div>
                <div className={styles.statValue}>{status.submittedCount}</div>
              </div>
              <div className={styles.statCard}>
                <div className={styles.statLabel}>Bildirilmeyecek</div>
                <div className={styles.statValue}>{status.dismissedCount}</div>
              </div>
            </div>
            <div className={styles.lastSynced}>
              Son bildirim:{' '}
              {status.lastSubmittedAt ? new Date(status.lastSubmittedAt).toLocaleString('tr-TR') : 'Henüz yok'}
            </div>
          </>
        )}
      </div>

      <TarbilExtensionCard />

      <div className={styles.tableCard}>
        <div className={styles.tableHead}>
          <div>Hasta</div>
          <div>Aşı</div>
          <div>Durum</div>
          <div>Zaman</div>
          <div></div>
        </div>
        {logs.length === 0 ? (
          <div className={styles.empty}>Kayıt bulunmuyor</div>
        ) : (
          logs.map((log) => (
            <div key={log.id} className={styles.row}>
              <div>{log.patientName}</div>
              <div className={styles.muted}>
                {log.vaccineName}
                {log.administeredDate && ` · ${new Date(log.administeredDate).toLocaleDateString('tr-TR')}`}
              </div>
              <div>
                <TarbilSyncStatusBadge status={log.status} />
                {log.status === 'SUBMITTED' && (
                  <div className={styles.muted}>
                    {confirmationMethodLabel(log.confirmationMethod)}
                    {log.tarbilReference && ` · No: ${log.tarbilReference}`}
                  </div>
                )}
                {log.status === 'DISMISSED' && log.dismissedReason && <div className={styles.muted}>{log.dismissedReason}</div>}
              </div>
              <div className={styles.muted}>
                {new Date(log.submittedAt ?? log.queuedAt).toLocaleString('tr-TR')}
              </div>
              <div>
                {log.status === 'PENDING' && (
                  <Button variant="tertiary" disabled={busyId === log.id}
                    onClick={() => run(log.id, () => tarbilApi.dismiss(log.id, 'Bildirim gerekmiyor'))}>
                    Bildirilmeyecek
                  </Button>
                )}
                {log.status === 'DISMISSED' && (
                  <Button variant="tertiary" disabled={busyId === log.id} onClick={() => run(log.id, () => tarbilApi.restore(log.id))}>
                    Geri al
                  </Button>
                )}
              </div>
            </div>
          ))
        )}
      </div>

      <TarbilMappingsCard />
    </div>
  );
}
```

- [ ] **Step 6: Derle**

Run: `cd frontend && npm run build`
Expected: Hatasız derleme. (`tarbilTypeLabel` başka bir dosyada kullanılıyorsa — `grep -rn tarbilTypeLabel frontend/src` — o kullanım da kaldırılmalı.)

- [ ] **Step 7: Tarayıcıda kontrol**

`npm run dev` + backend; Ayarlar > Entegrasyonlar: üç sayaç, "Eklentiyi bağla" ile kod görünür, bekleyen bir aşıda "Bildirilmeyecek" → durum değişir, "Geri al" → Bekliyor.

- [ ] **Step 8: Commit**

```bash
git add frontend/src
git commit -m "feat(frontend): TARBIL entegrasyon ekranini gercek aktarim durumu, eklenti baglama ve esletirmelerle yenile"
```

---

### Task 9: Eklenti iskeleti — derleme, manifest, sabit kimlik, paylaşılan tipler

**Files:**
- Create: `extension/package.json`, `extension/tsconfig.json`, `extension/vite.config.ts`, `extension/vite.content.config.ts`, `extension/vitest.config.ts`, `extension/public/manifest.json`, `extension/sidepanel.html`, `extension/scripts/extension-id.mjs`, `extension/src/shared/types.ts`, `extension/src/shared/messages.ts`, `extension/src/shared/config.ts`, `extension/src/vite-env.d.ts`, `extension/README.md`, `extension/.gitignore`
- Modify: `.gitignore` (kök)

**Interfaces:**
- Produces:
  - `config.ts`: `export const VETLY_API_BASE: string` (`import.meta.env.VITE_VETLY_API_BASE ?? 'https://uygulama.vetly.com.tr'`), `export const VETLY_APP_ORIGIN: string`
  - `types.ts`: `Submission` (Task 7'deki `TarbilSubmissionResponse` alanları; `vaccineMapping`/`speciesMapping`: `Record<string, unknown> | null`), `ExtensionProfile { clinicName; staffName }`, `ConfirmationMethod = 'AUTO' | 'MANUAL'`
  - `messages.ts`: `type BackgroundRequest` birleşimi — `{type:'GET_STATE'}`, `{type:'PAIR', code, label}`, `{type:'UNPAIR'}`, `{type:'LIST_PENDING'}`, `{type:'GET_SUBMISSION', id}`, `{type:'SET_ACTIVE', id}`, `{type:'GET_ACTIVE'}`, `{type:'MARK_SUBMITTED', id, method, tarbilReference}`, `{type:'DISMISS', id, reason}`; `type BackgroundResponse<T> = {ok:true, data:T} | {ok:false, error:string, code?: 'UNAUTHORIZED'|'OFFLINE'|'NOT_FOUND'|'CONFLICT'|'UNKNOWN'}`; `type ExternalRequest = {type:'PING'} | {type:'SELECT_SUBMISSION', vaccinationRecordId}`; `interface ExtensionState { paired: boolean; profile: ExtensionProfile | null; pendingConfirmations: number }`

- [ ] **Step 1: Paket ve derleme yapılandırması**

`extension/package.json`:
```json
{
  "name": "vetly-tarbil-extension",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "build": "tsc --noEmit && vite build && vite build --config vite.content.config.ts",
    "build:dev": "tsc --noEmit && vite build --mode development && vite build --mode development --config vite.content.config.ts",
    "test": "vitest run",
    "extension-id": "node scripts/extension-id.mjs"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@types/chrome": "^0.0.270",
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.4",
    "vite": "^5.4.1",
    "vitest": "^2.1.0"
  }
}
```

`extension/tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "types": ["chrome", "vite/client"],
    "skipLibCheck": true,
    "isolatedModules": true,
    "lib": ["ES2022", "DOM"]
  },
  "include": ["src", "vite.config.ts", "vite.content.config.ts", "vitest.config.ts"]
}
```

`extension/vite.config.ts`:
```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';

// Yan panel (HTML) + arka plan service worker'i (ES module). Icerik betigi
// ES module olamadigi icin ayri bir IIFE derlemesinde: vite.content.config.ts.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        sidepanel: resolve(__dirname, 'sidepanel.html'),
        background: resolve(__dirname, 'src/background/index.ts'),
      },
      output: {
        entryFileNames: (chunk) => (chunk.name === 'background' ? 'background.js' : 'assets/[name]-[hash].js'),
      },
    },
  },
});
```

`extension/vite.content.config.ts`:
```ts
import { defineConfig } from 'vite';
import { resolve } from 'node:path';

export default defineConfig({
  build: {
    outDir: 'dist',
    emptyOutDir: false,
    lib: {
      entry: resolve(__dirname, 'src/tarbil/content.ts'),
      formats: ['iife'],
      name: 'VetlyTarbilContent',
      fileName: () => 'content.js',
    },
  },
});
```

`extension/vitest.config.ts`:
```ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
});
```

`extension/src/vite-env.d.ts`:
```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_VETLY_API_BASE?: string;
  readonly VITE_VETLY_APP_ORIGIN?: string;
}
```

`extension/.gitignore`:
```
node_modules/
dist/
key.pem
```

Kök `.gitignore`'a ekle:
```
# TARBIL eklentisi
extension/node_modules/
extension/dist/
extension/key.pem
```

- [ ] **Step 2: Sabit eklenti kimliği**

```bash
cd extension
openssl genrsa -out key.pem 2048
openssl rsa -in key.pem -pubout -outform DER | openssl base64 -A > public-key.b64
```
`key.pem` commit EDİLMEZ (paketlenmemiş yüklemede gerekmez; ileride Web Store için saklanır — şifre yöneticisine/yedeğe koy). `public-key.b64` içeriği manifest'teki `key` alanına gider.

`extension/scripts/extension-id.mjs`:
```js
// manifest.json'daki "key" alanindan Chrome eklenti kimligini hesaplar:
// SHA-256(DER public key) -> ilk 16 bayt -> her hex hane 0-f => a-p.
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';

const manifest = JSON.parse(readFileSync(new URL('../public/manifest.json', import.meta.url), 'utf8'));
const der = Buffer.from(manifest.key, 'base64');
const hex = createHash('sha256').update(der).digest('hex').slice(0, 32);
const id = [...hex].map((c) => String.fromCharCode('a'.charCodeAt(0) + parseInt(c, 16))).join('');
console.log(id);
```

- [ ] **Step 3: Manifest ve panel HTML**

`extension/public/manifest.json` (`key` değerine `public-key.b64` içeriğini yapıştır):
```json
{
  "manifest_version": 3,
  "name": "Vetly TARBİL Yardımcısı",
  "version": "0.1.0",
  "description": "Vetly'deki aşı kayıtlarını TARBİL'e aktarmanıza yardım eder. Kaydet'e her zaman siz basarsınız.",
  "key": "BURAYA_public-key.b64_ICERIGI",
  "permissions": ["storage", "sidePanel", "scripting", "alarms"],
  "host_permissions": [
    "https://uygulama.vetly.com.tr/*",
    "https://hbsapp.tarbil.gov.tr/*",
    "http://localhost:8080/*"
  ],
  "background": { "service_worker": "background.js", "type": "module" },
  "side_panel": { "default_path": "sidepanel.html" },
  "action": { "default_title": "Vetly TARBİL" },
  "externally_connectable": {
    "matches": ["https://uygulama.vetly.com.tr/*", "http://localhost:5173/*"]
  },
  "content_scripts": [
    {
      "matches": ["https://hbsapp.tarbil.gov.tr/*"],
      "js": ["content.js"],
      "run_at": "document_idle"
    }
  ]
}
```

`extension/sidepanel.html`:
```html
<!doctype html>
<html lang="tr">
  <head>
    <meta charset="UTF-8" />
    <title>Vetly TARBİL</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/sidepanel/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 4: Paylaşılan tipler**

`extension/src/shared/config.ts`:
```ts
export const VETLY_API_BASE: string = import.meta.env.VITE_VETLY_API_BASE ?? 'https://uygulama.vetly.com.tr';
export const VETLY_APP_ORIGIN: string = import.meta.env.VITE_VETLY_APP_ORIGIN ?? 'https://uygulama.vetly.com.tr';
```

`extension/src/shared/types.ts`:
```ts
export type ConfirmationMethod = 'AUTO' | 'MANUAL';
export type SubmissionStatus = 'PENDING' | 'SUBMITTED' | 'DISMISSED';

export interface Submission {
  id: string;
  vaccinationRecordId: string;
  status: SubmissionStatus;
  patientName: string;
  microchipNumber: string | null;
  speciesId: string | null;
  speciesName: string | null;
  breedName: string | null;
  sex: 'MALE' | 'FEMALE' | 'UNKNOWN' | null;
  birthDate: string | null;
  vaccineName: string;
  lotNumber: string | null;
  administeredDate: string;
  submittedAt: string | null;
  confirmationMethod: ConfirmationMethod | null;
  tarbilReference: string | null;
  vaccineKey: string;
  vaccineMapping: Record<string, unknown> | null;
  speciesMapping: Record<string, unknown> | null;
}

export interface ExtensionProfile {
  clinicName: string;
  staffName: string;
}
```

`extension/src/shared/messages.ts`:
```ts
import type { ConfirmationMethod, ExtensionProfile, Submission } from './types';

export type BackgroundRequest =
  | { type: 'GET_STATE' }
  | { type: 'PAIR'; code: string; label: string }
  | { type: 'UNPAIR' }
  | { type: 'LIST_PENDING' }
  | { type: 'GET_SUBMISSION'; id: string }
  | { type: 'SET_ACTIVE'; id: string }
  | { type: 'GET_ACTIVE' }
  | { type: 'MARK_SUBMITTED'; id: string; method: ConfirmationMethod; tarbilReference: string | null }
  | { type: 'DISMISS'; id: string; reason: string };

export type ErrorCode = 'UNAUTHORIZED' | 'OFFLINE' | 'NOT_FOUND' | 'CONFLICT' | 'UNKNOWN';

export type BackgroundResponse<T> = { ok: true; data: T } | { ok: false; error: string; code: ErrorCode };

export type ExternalRequest = { type: 'PING' } | { type: 'SELECT_SUBMISSION'; vaccinationRecordId: string };

export interface ExtensionState {
  paired: boolean;
  profile: ExtensionProfile | null;
  pendingConfirmations: number;
}

export type { Submission };
```

- [ ] **Step 5: README**

`extension/README.md`:
```markdown
# Vetly TARBİL Yardımcısı (Chrome eklentisi)

Tasarım: `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`.

## Derleme
    npm ci
    npm run build          # prod: https://uygulama.vetly.com.tr
    npm run build:dev      # .env.development'taki VITE_VETLY_API_BASE (ör. http://localhost:8080)

## Kurulum (paketlenmemiş)
Chrome > `chrome://extensions` > Geliştirici modu > "Paketlenmemiş öğe yükle" > `extension/dist`.

## Eklenti kimliği
`npm run extension-id` — çıktı, frontend'deki `VITE_TARBIL_EXTENSION_ID` değeridir.
```

`extension/.env.development`:
```
VITE_VETLY_API_BASE=http://localhost:8080
VITE_VETLY_APP_ORIGIN=http://localhost:5173
```

- [ ] **Step 6: Geçici giriş dosyaları ile derlemeyi doğrula**

Bu task'ta henüz arka plan/panel/içerik kodu yok; derlemenin çalıştığını görmek için boş giriş dosyaları oluştur (Task 10–12 bunların içini doldurur):
`extension/src/background/index.ts`: `export {};`
`extension/src/sidepanel/main.tsx`: `export {};`
`extension/src/tarbil/content.ts`: `export {};`

Run: `cd extension && npm install && npm run build && npm run extension-id`
Expected: `dist/` altında `manifest.json`, `background.js`, `content.js`, `sidepanel.html`; son komut 32 harfli (a–p) bir kimlik yazar. Kimliği not et.

- [ ] **Step 7: Commit**

```bash
git add extension .gitignore
git commit -m "feat(extension): MV3 eklenti iskeleti, sabit kimlik ve paylasilan mesaj tipleri"
```

---

### Task 10: Eklenti arka planı — anahtar, Vetly API istemcisi, çevrimdışı onay kuyruğu, mesaj yönlendirici

**Files:**
- Create: `extension/src/background/chromeStorage.ts`, `extension/src/background/tokenStore.ts`, `extension/src/background/vetlyApi.ts`, `extension/src/background/confirmationOutbox.ts`, `extension/src/background/router.ts`
- Modify: `extension/src/background/index.ts`
- Test: `extension/src/background/vetlyApi.test.ts`, `extension/src/background/confirmationOutbox.test.ts`, `extension/src/background/router.test.ts`

**Interfaces:**
- Consumes: Task 9 tipleri, Task 7 eklenti uçları.
- Produces:
  - `interface KeyValueStore { get<T>(key: string): Promise<T | undefined>; set(key: string, value: unknown): Promise<void>; remove(key: string): Promise<void> }`; `chromeLocalStore`, `chromeSessionStore` (gerçek), `memoryStore()` (test)
  - `createTokenStore(store)` → `{ get(): Promise<string|null>; set(token): Promise<void>; clear(): Promise<void> }`
  - `class ApiError extends Error { code: ErrorCode }`
  - `createVetlyApi({ baseUrl, tokens, fetchFn })` → `{ pair(code,label): Promise<void>; me(); listPending(); getSubmission(id); getByVaccination(vaccinationRecordId); markSubmitted(id, method, ref); dismiss(id, reason) }` — 401'de anahtarı siler ve `ApiError('UNAUTHORIZED')`; ağ hatasında `ApiError('OFFLINE')`; 404 → `NOT_FOUND`; 409 → `CONFLICT`
  - `createConfirmationOutbox(store, api)` → `{ enqueue(entry: {id, method, tarbilReference}): Promise<void>; flush(): Promise<number>; size(): Promise<number> }` — `flush` başarılı/`NOT_FOUND`/`CONFLICT` girdileri siler (tekrar denemenin anlamı yok), `OFFLINE`/`UNKNOWN` olanları tutar, `UNAUTHORIZED`'da durur ve tutar
  - `createRouter({ api, tokens, outbox, session })` → `handle(req: BackgroundRequest): Promise<BackgroundResponse<unknown>>`; `handleExternal(req: ExternalRequest): Promise<BackgroundResponse<unknown>>`. `MARK_SUBMITTED` ağ hatasında outbox'a yazar ve `{ok:true, data:{queued:true}}` döner. Aktif aşı `session` deposunda `activeSubmissionId` anahtarında.

- [ ] **Step 1: Failing testleri yaz**

`extension/src/background/vetlyApi.test.ts`:
```ts
import { describe, expect, it } from 'vitest';
import { createTokenStore, memoryStore } from './chromeStorage';
import { ApiError, createVetlyApi } from './vetlyApi';

function fakeFetch(status: number, body: unknown = {}) {
  const calls: { url: string; init: RequestInit }[] = [];
  const fn = async (url: string, init: RequestInit) => {
    calls.push({ url, init });
    return new Response(status === 204 ? null : JSON.stringify(body), { status });
  };
  return { fn: fn as unknown as typeof fetch, calls };
}

describe('vetlyApi', () => {
  it('sends bearer token and parses json', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const { fn, calls } = fakeFetch(200, []);
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    expect(await api.listPending()).toEqual([]);
    expect(calls[0].url).toBe('https://x/api/v1/tarbil-extension/pending');
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBe('Bearer vtx_abc');
  });

  it('should_clearTokenAndThrow_when_401', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_revoked');
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fakeFetch(401).fn });

    await expect(api.listPending()).rejects.toMatchObject({ code: 'UNAUTHORIZED' });
    expect(await tokens.get()).toBeNull();
  });

  it('maps network failure to OFFLINE', async () => {
    const tokens = createTokenStore(memoryStore());
    await tokens.set('vtx_abc');
    const failing = (async () => {
      throw new TypeError('Failed to fetch');
    }) as unknown as typeof fetch;
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: failing });

    await expect(api.listPending()).rejects.toBeInstanceOf(ApiError);
    await expect(api.listPending()).rejects.toMatchObject({ code: 'OFFLINE' });
  });

  it('stores token on pair without sending authorization', async () => {
    const tokens = createTokenStore(memoryStore());
    const { fn, calls } = fakeFetch(200, { token: 'vtx_new' });
    const api = createVetlyApi({ baseUrl: 'https://x', tokens, fetchFn: fn });

    await api.pair('K7QM-2XPA', 'PC');

    expect(await tokens.get()).toBe('vtx_new');
    expect((calls[0].init.headers as Record<string, string>).Authorization).toBeUndefined();
  });
});
```

`extension/src/background/confirmationOutbox.test.ts`:
```ts
import { describe, expect, it } from 'vitest';
import { memoryStore } from './chromeStorage';
import { createConfirmationOutbox } from './confirmationOutbox';
import { ApiError } from './vetlyApi';

function apiThat(outcomes: Array<'ok' | ApiError['code']>) {
  const calls: string[] = [];
  return {
    calls,
    markSubmitted: async (id: string) => {
      calls.push(id);
      const next = outcomes.shift() ?? 'ok';
      if (next !== 'ok') throw new ApiError(next, next);
      return {} as never;
    },
  };
}

describe('confirmationOutbox', () => {
  it('should_keepEntry_when_flushFails', async () => {
    const api = apiThat(['OFFLINE']);
    const outbox = createConfirmationOutbox(memoryStore(), api);
    await outbox.enqueue({ id: 's1', method: 'AUTO', tarbilReference: null });

    expect(await outbox.flush()).toBe(0);
    expect(await outbox.size()).toBe(1);
  });

  it('removes sent, not-found and conflict entries', async () => {
    const api = apiThat(['ok', 'NOT_FOUND', 'CONFLICT']);
    const outbox = createConfirmationOutbox(memoryStore(), api);
    await outbox.enqueue({ id: 'a', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'b', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'c', method: 'AUTO', tarbilReference: null });

    expect(await outbox.flush()).toBe(1);
    expect(await outbox.size()).toBe(0);
  });

  it('does not duplicate the same submission', async () => {
    const outbox = createConfirmationOutbox(memoryStore(), apiThat([]));
    await outbox.enqueue({ id: 'a', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'a', method: 'MANUAL', tarbilReference: null });

    expect(await outbox.size()).toBe(1);
  });

  it('stops on UNAUTHORIZED and keeps remaining', async () => {
    const api = apiThat(['UNAUTHORIZED']);
    const outbox = createConfirmationOutbox(memoryStore(), api);
    await outbox.enqueue({ id: 'a', method: 'AUTO', tarbilReference: null });
    await outbox.enqueue({ id: 'b', method: 'AUTO', tarbilReference: null });

    await outbox.flush();

    expect(api.calls).toEqual(['a']);
    expect(await outbox.size()).toBe(2);
  });
});
```

`extension/src/background/router.test.ts`:
```ts
import { describe, expect, it } from 'vitest';
import { createTokenStore, memoryStore } from './chromeStorage';
import { createConfirmationOutbox } from './confirmationOutbox';
import { createRouter } from './router';
import { ApiError } from './vetlyApi';

function setup(markSubmitted: () => Promise<unknown>) {
  const tokens = createTokenStore(memoryStore());
  const session = memoryStore();
  const api = {
    markSubmitted,
    getByVaccination: async (vid: string) => ({ id: 'sub-for-' + vid }),
  } as never;
  const outbox = createConfirmationOutbox(memoryStore(), api);
  return { router: createRouter({ api, tokens, outbox, session }), outbox, session };
}

describe('router', () => {
  it('queues confirmation when offline and reports queued', async () => {
    const { router, outbox } = setup(async () => {
      throw new ApiError('OFFLINE', 'offline');
    });

    const res = await router.handle({ type: 'MARK_SUBMITTED', id: 's1', method: 'MANUAL', tarbilReference: null });

    expect(res).toEqual({ ok: true, data: { queued: true } });
    expect(await outbox.size()).toBe(1);
  });

  it('selects active submission from external request by vaccination id', async () => {
    const { router, session } = setup(async () => ({}));

    const res = await router.handleExternal({ type: 'SELECT_SUBMISSION', vaccinationRecordId: 'v1' });

    expect(res.ok).toBe(true);
    expect(await session.get('activeSubmissionId')).toBe('sub-for-v1');
  });

  it('answers ping', async () => {
    const { router } = setup(async () => ({}));
    expect(await router.handleExternal({ type: 'PING' })).toEqual({ ok: true, data: { version: '0.1.0' } });
  });
});
```

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd extension && npm test`
Expected: FAIL (modüller yok).

- [ ] **Step 3: Depolama**

`extension/src/background/chromeStorage.ts`:
```ts
export interface KeyValueStore {
  get<T>(key: string): Promise<T | undefined>;
  set(key: string, value: unknown): Promise<void>;
  remove(key: string): Promise<void>;
}

function chromeArea(area: chrome.storage.StorageArea): KeyValueStore {
  return {
    async get<T>(key: string) {
      const result = await area.get(key);
      return result[key] as T | undefined;
    },
    async set(key, value) {
      await area.set({ [key]: value });
    },
    async remove(key) {
      await area.remove(key);
    },
  };
}

export const chromeLocalStore = (): KeyValueStore => chromeArea(chrome.storage.local);
export const chromeSessionStore = (): KeyValueStore => chromeArea(chrome.storage.session);

export function memoryStore(): KeyValueStore {
  const data = new Map<string, unknown>();
  return {
    async get<T>(key: string) {
      return data.get(key) as T | undefined;
    },
    async set(key, value) {
      data.set(key, structuredClone(value));
    },
    async remove(key) {
      data.delete(key);
    },
  };
}

export function createTokenStore(store: KeyValueStore) {
  const KEY = 'vetlyExtensionToken';
  return {
    async get(): Promise<string | null> {
      return (await store.get<string>(KEY)) ?? null;
    },
    set: (token: string) => store.set(KEY, token),
    clear: () => store.remove(KEY),
  };
}

export type TokenStore = ReturnType<typeof createTokenStore>;
```

- [ ] **Step 4: API istemcisi**

`extension/src/background/vetlyApi.ts`:
```ts
import type { ErrorCode } from '../shared/messages';
import type { ConfirmationMethod, ExtensionProfile, Submission } from '../shared/types';
import type { TokenStore } from './chromeStorage';

export class ApiError extends Error {
  constructor(public readonly code: ErrorCode, message: string) {
    super(message);
  }
}

interface Options {
  baseUrl: string;
  tokens: TokenStore;
  fetchFn?: typeof fetch;
}

export function createVetlyApi({ baseUrl, tokens, fetchFn = fetch }: Options) {
  async function request<T>(path: string, init: RequestInit = {}, auth = true): Promise<T> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (auth) {
      const token = await tokens.get();
      if (!token) throw new ApiError('UNAUTHORIZED', 'Eklenti bağlı değil');
      headers.Authorization = `Bearer ${token}`;
    }
    let response: Response;
    try {
      response = await fetchFn(`${baseUrl}${path}`, { ...init, headers });
    } catch {
      throw new ApiError('OFFLINE', "Vetly'ye ulaşılamıyor");
    }
    if (response.status === 401) {
      if (auth) await tokens.clear();
      throw new ApiError('UNAUTHORIZED', 'Bağlantı geçersiz ya da iptal edilmiş');
    }
    if (response.status === 404) throw new ApiError('NOT_FOUND', 'Kayıt bulunamadı');
    if (response.status === 409) throw new ApiError('CONFLICT', 'Kayıt bu işlem için uygun durumda değil');
    if (!response.ok) throw new ApiError('UNKNOWN', `Beklenmeyen yanıt: ${response.status}`);
    return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
  }

  return {
    async pair(code: string, label: string) {
      const { token } = await request<{ token: string }>(
        '/api/v1/tarbil-extension/pair',
        { method: 'POST', body: JSON.stringify({ code, label }) },
        false,
      );
      await tokens.set(token);
    },
    me: () => request<ExtensionProfile>('/api/v1/tarbil-extension/me'),
    listPending: () => request<Submission[]>('/api/v1/tarbil-extension/pending'),
    getSubmission: (id: string) => request<Submission>(`/api/v1/tarbil-extension/submissions/${id}`),
    getByVaccination: (vaccinationRecordId: string) =>
      request<Submission>(`/api/v1/tarbil-extension/submissions/by-vaccination/${vaccinationRecordId}`),
    markSubmitted: (id: string, method: ConfirmationMethod, tarbilReference: string | null) =>
      request<Submission>(`/api/v1/tarbil-extension/submissions/${id}/submitted`, {
        method: 'POST',
        body: JSON.stringify({ method, tarbilReference }),
      }),
    dismiss: (id: string, reason: string) =>
      request<void>(`/api/v1/tarbil-extension/submissions/${id}/dismiss`, {
        method: 'POST',
        body: JSON.stringify({ reason }),
      }),
  };
}

export type VetlyApi = ReturnType<typeof createVetlyApi>;
```

- [ ] **Step 5: Çevrimdışı onay kuyruğu**

`extension/src/background/confirmationOutbox.ts`:
```ts
import type { ConfirmationMethod } from '../shared/types';
import type { KeyValueStore } from './chromeStorage';
import { ApiError, type VetlyApi } from './vetlyApi';

export interface OutboxEntry {
  id: string;
  method: ConfirmationMethod;
  tarbilReference: string | null;
}

const KEY = 'confirmationOutbox';

/**
 * TARBIL'e kaydedilmis ama Vetly'ye bildirilememis onaylar. Kayip olmamasi icin
 * chrome.storage.local'da tutulur; sunucu ucu idempotent oldugu icin tekrar gonderim guvenli.
 */
export function createConfirmationOutbox(store: KeyValueStore, api: Pick<VetlyApi, 'markSubmitted'>) {
  async function read(): Promise<OutboxEntry[]> {
    return (await store.get<OutboxEntry[]>(KEY)) ?? [];
  }

  return {
    async enqueue(entry: OutboxEntry) {
      const entries = await read();
      if (!entries.some((e) => e.id === entry.id)) {
        await store.set(KEY, [...entries, entry]);
      }
    },
    async size() {
      return (await read()).length;
    },
    async flush(): Promise<number> {
      const entries = await read();
      const remaining: OutboxEntry[] = [];
      let sent = 0;
      for (let i = 0; i < entries.length; i++) {
        const entry = entries[i];
        try {
          await api.markSubmitted(entry.id, entry.method, entry.tarbilReference);
          sent++;
        } catch (e) {
          const code = e instanceof ApiError ? e.code : 'UNKNOWN';
          if (code === 'NOT_FOUND' || code === 'CONFLICT') continue;
          remaining.push(entry);
          if (code === 'UNAUTHORIZED') {
            remaining.push(...entries.slice(i + 1));
            break;
          }
        }
      }
      await store.set(KEY, remaining);
      return sent;
    },
  };
}

export type ConfirmationOutbox = ReturnType<typeof createConfirmationOutbox>;
```

- [ ] **Step 6: Yönlendirici ve servis worker girişi**

`extension/src/background/router.ts`:
```ts
import type { BackgroundRequest, BackgroundResponse, ExtensionState, ExternalRequest } from '../shared/messages';
import type { KeyValueStore, TokenStore } from './chromeStorage';
import type { ConfirmationOutbox } from './confirmationOutbox';
import { ApiError, type VetlyApi } from './vetlyApi';

const ACTIVE_KEY = 'activeSubmissionId';
const VERSION = '0.1.0';

interface Deps {
  api: VetlyApi;
  tokens: TokenStore;
  outbox: ConfirmationOutbox;
  session: KeyValueStore;
}

function fail(e: unknown): BackgroundResponse<never> {
  if (e instanceof ApiError) return { ok: false, error: e.message, code: e.code };
  return { ok: false, error: 'Beklenmeyen hata', code: 'UNKNOWN' };
}

export function createRouter({ api, tokens, outbox, session }: Deps) {
  async function state(): Promise<ExtensionState> {
    const paired = (await tokens.get()) !== null;
    let profile = null;
    if (paired) {
      try {
        profile = await api.me();
      } catch (e) {
        if (!(e instanceof ApiError) || e.code !== 'OFFLINE') throw e;
      }
    }
    return { paired: (await tokens.get()) !== null, profile, pendingConfirmations: await outbox.size() };
  }

  return {
    async handle(req: BackgroundRequest): Promise<BackgroundResponse<unknown>> {
      try {
        switch (req.type) {
          case 'GET_STATE':
            return { ok: true, data: await state() };
          case 'PAIR':
            await api.pair(req.code.trim(), req.label.trim());
            return { ok: true, data: await state() };
          case 'UNPAIR':
            await tokens.clear();
            return { ok: true, data: await state() };
          case 'LIST_PENDING':
            await outbox.flush().catch(() => 0);
            return { ok: true, data: await api.listPending() };
          case 'GET_SUBMISSION':
            return { ok: true, data: await api.getSubmission(req.id) };
          case 'SET_ACTIVE':
            await session.set(ACTIVE_KEY, req.id);
            return { ok: true, data: null };
          case 'GET_ACTIVE': {
            const id = await session.get<string>(ACTIVE_KEY);
            return { ok: true, data: id ? await api.getSubmission(id) : null };
          }
          case 'MARK_SUBMITTED':
            try {
              return { ok: true, data: await api.markSubmitted(req.id, req.method, req.tarbilReference) };
            } catch (e) {
              if (e instanceof ApiError && (e.code === 'OFFLINE' || e.code === 'UNKNOWN')) {
                await outbox.enqueue({ id: req.id, method: req.method, tarbilReference: req.tarbilReference });
                return { ok: true, data: { queued: true } };
              }
              throw e;
            }
          case 'DISMISS':
            await api.dismiss(req.id, req.reason);
            return { ok: true, data: null };
        }
      } catch (e) {
        return fail(e);
      }
    },

    async handleExternal(req: ExternalRequest): Promise<BackgroundResponse<unknown>> {
      try {
        switch (req.type) {
          case 'PING':
            return { ok: true, data: { version: VERSION } };
          case 'SELECT_SUBMISSION': {
            const submission = await api.getByVaccination(req.vaccinationRecordId);
            await session.set(ACTIVE_KEY, submission.id);
            return { ok: true, data: { submissionId: submission.id } };
          }
        }
      } catch (e) {
        return fail(e);
      }
    },
  };
}
```

`extension/src/background/index.ts`:
```ts
import { VETLY_API_BASE, VETLY_APP_ORIGIN } from '../shared/config';
import type { BackgroundRequest, ExternalRequest } from '../shared/messages';
import { chromeLocalStore, chromeSessionStore, createTokenStore } from './chromeStorage';
import { createConfirmationOutbox } from './confirmationOutbox';
import { createRouter } from './router';
import { createVetlyApi } from './vetlyApi';

const tokens = createTokenStore(chromeLocalStore());
const api = createVetlyApi({ baseUrl: VETLY_API_BASE, tokens });
const outbox = createConfirmationOutbox(chromeLocalStore(), api);
const router = createRouter({ api, tokens, outbox, session: chromeSessionStore() });

// Arac cubugu simgesine tiklamak yan paneli acar (kullanici hareketi gerektiren tek yol).
chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => undefined);

chrome.runtime.onMessage.addListener((req: BackgroundRequest, _sender, sendResponse) => {
  router.handle(req).then(sendResponse);
  return true;
});

chrome.runtime.onMessageExternal.addListener((req: ExternalRequest, sender, sendResponse) => {
  if (!sender.origin || !sender.origin.startsWith(VETLY_APP_ORIGIN)) {
    sendResponse({ ok: false, error: 'İzin verilmeyen kaynak', code: 'UNKNOWN' });
    return false;
  }
  router.handleExternal(req).then(sendResponse);
  return true;
});

chrome.alarms.create('flush-confirmations', { periodInMinutes: 1 });
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === 'flush-confirmations') outbox.flush().catch(() => undefined);
});
chrome.runtime.onStartup.addListener(() => {
  outbox.flush().catch(() => undefined);
});
```

- [ ] **Step 7: Testleri ve derlemeyi çalıştır**

Run: `cd extension && npm test && npm run build`
Expected: Tüm Vitest testleri PASS, derleme hatasız.

- [ ] **Step 8: Commit**

```bash
git add extension
git commit -m "feat(extension): arka plan servisi, Vetly API istemcisi ve cevrimdisi onay kuyrugu"
```

---

### Task 11: Eklenti yan paneli — eşleştirme, bekleyenler, gönderildi/bildirilmeyecek

**Files:**
- Create: `extension/src/sidepanel/useBackground.ts`, `extension/src/sidepanel/PairingView.tsx`, `extension/src/sidepanel/PendingList.tsx`, `extension/src/sidepanel/SubmissionCard.tsx`, `extension/src/sidepanel/App.tsx`, `extension/src/sidepanel/panel.css`
- Modify: `extension/src/sidepanel/main.tsx`

**Interfaces:**
- Consumes: Task 10 mesajları.
- Produces: `sendToBackground<T>(req: BackgroundRequest): Promise<BackgroundResponse<T>>`; `SubmissionCard` props `{ submission: Submission; onDone: () => void }` (Task 12 de kullanır).

- [ ] **Step 1: Mesaj yardımcı fonksiyonu**

`extension/src/sidepanel/useBackground.ts`:
```ts
import type { BackgroundRequest, BackgroundResponse } from '../shared/messages';

export function sendToBackground<T>(req: BackgroundRequest): Promise<BackgroundResponse<T>> {
  return chrome.runtime.sendMessage(req);
}
```

- [ ] **Step 2: Bileşenler**

`extension/src/sidepanel/panel.css` (değerler `frontend/src/styles/tokens.css`'ten; eklenti ayrı paket olduğu için token'lar burada yeniden tanımlanır — renkleri oradan birebir kopyala):
```css
:root {
  --color-surface: #ffffff;
  --color-border: #e5e7eb;
  --color-text: #1f2937;
  --color-muted: #6b7280;
  --color-primary-600: #2f6f5e;
  --color-warning-600: #b4532a;
  --color-success-600: #2e7d32;
}
body { margin: 0; font: 14px/1.4 system-ui, sans-serif; color: var(--color-text); background: var(--color-surface); }
.panel { padding: 12px; display: flex; flex-direction: column; gap: 12px; }
.header { display: flex; justify-content: space-between; align-items: baseline; border-bottom: 1px solid var(--color-border); padding-bottom: 8px; }
.muted { color: var(--color-muted); font-size: 12px; }
.card { border: 1px solid var(--color-border); border-radius: 8px; padding: 10px; display: flex; flex-direction: column; gap: 6px; }
.warning { color: var(--color-warning-600); font-size: 12px; }
.success { color: var(--color-success-600); }
.row { display: flex; gap: 6px; flex-wrap: wrap; }
button { border: 1px solid var(--color-border); background: var(--color-surface); border-radius: 6px; padding: 6px 10px; cursor: pointer; }
button.primary { background: var(--color-primary-600); color: #fff; border-color: var(--color-primary-600); }
button:disabled { opacity: 0.5; cursor: default; }
input { border: 1px solid var(--color-border); border-radius: 6px; padding: 6px 8px; width: 100%; box-sizing: border-box; }
.banner { background: #fff7ed; border: 1px solid var(--color-warning-600); border-radius: 6px; padding: 6px 8px; font-size: 12px; }
```
(Not: `#fff7ed` ve `#fff` yalnız bu CSS dosyasının token tanımında/zemin rengi olarak kabul edilir; kurala uygun hale getirmek için tokens.css'te karşılığı varsa onu kullan.)

`extension/src/sidepanel/PairingView.tsx`:
```tsx
import { useState } from 'react';
import type { ExtensionState } from '../shared/messages';
import { sendToBackground } from './useBackground';

export function PairingView({ onPaired, message }: { onPaired: (s: ExtensionState) => void; message?: string }) {
  const [code, setCode] = useState('');
  const [label, setLabel] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError(null);
    const res = await sendToBackground<ExtensionState>({ type: 'PAIR', code, label: label || 'Bu bilgisayar' });
    setBusy(false);
    if (res.ok) onPaired(res.data);
    else setError(res.code === 'UNAUTHORIZED' ? 'Kod geçersiz ya da süresi dolmuş. Vetly\'de yeni kod oluşturun.' : res.error);
  }

  return (
    <div className="panel">
      <h3>Vetly'ye bağlan</h3>
      {message && <div className="banner">{message}</div>}
      <div className="muted">Vetly &gt; Ayarlar &gt; Entegrasyonlar &gt; "Eklentiyi bağla" ile kod oluşturun.</div>
      <input placeholder="Eşleştirme kodu (ör. K7QM-2XPA)" value={code} onChange={(e) => setCode(e.target.value)} />
      <input placeholder="Bu bilgisayarın adı (ör. Muayene 1)" value={label} onChange={(e) => setLabel(e.target.value)} />
      {error && <div className="warning">{error}</div>}
      <button className="primary" disabled={busy || code.trim().length < 8} onClick={submit}>
        Bağlan
      </button>
    </div>
  );
}
```

`extension/src/sidepanel/SubmissionCard.tsx`:
```tsx
import { useState } from 'react';
import type { Submission } from '../shared/types';
import { sendToBackground } from './useBackground';

const SEX = { MALE: 'Erkek', FEMALE: 'Dişi', UNKNOWN: 'Bilinmiyor' } as const;

export function SubmissionCard({ submission: s, onDone }: { submission: Submission; onDone: () => void }) {
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);

  async function markSubmitted() {
    setBusy(true);
    const res = await sendToBackground<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'MANUAL', tarbilReference: null });
    setBusy(false);
    if (res.ok && res.data && 'queued' in res.data) setNote("Vetly'ye ulaşılamadı; bağlantı gelince otomatik bildirilecek.");
    else if (res.ok) onDone();
    else setNote(res.error);
  }

  async function dismiss() {
    setBusy(true);
    const res = await sendToBackground({ type: 'DISMISS', id: s.id, reason: 'Bildirim gerekmiyor' });
    setBusy(false);
    if (res.ok) onDone();
    else setNote(res.error);
  }

  async function setActive() {
    await sendToBackground({ type: 'SET_ACTIVE', id: s.id });
    setNote('Bu aşı TARBİL sekmesindeki Vetly kartında gösterilecek.');
  }

  if (s.status === 'SUBMITTED') {
    return (
      <div className="card">
        <strong>{s.patientName}</strong>
        <div className="success">✓ {new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş.</div>
      </div>
    );
  }

  return (
    <div className="card">
      <strong>{s.patientName}</strong>
      <div className="muted">
        {[s.speciesName, s.breedName, s.sex ? SEX[s.sex] : null].filter(Boolean).join(' · ')}
      </div>
      {s.microchipNumber ? (
        <div>Çip: {s.microchipNumber}</div>
      ) : (
        <div className="warning">Çip numarası yok. Hayvan TARBİL'de kayıtlı değilse önce kimliklendirme gerekir.</div>
      )}
      <div>
        {s.vaccineName}
        {s.lotNumber && ` · Lot ${s.lotNumber}`} · {new Date(s.administeredDate).toLocaleDateString('tr-TR')}
      </div>
      {!s.vaccineMapping && <div className="muted">İlk kez aktarılıyor: aşı ürününü ve hastalığı TARBİL'de siz seçeceksiniz.</div>}
      <div className="row">
        <button onClick={setActive} disabled={busy}>TARBİL kartında göster</button>
        <button className="primary" onClick={markSubmitted} disabled={busy}>Gönderildi olarak işaretle</button>
        <button onClick={dismiss} disabled={busy}>Bildirilmeyecek</button>
      </div>
      {note && <div className="muted">{note}</div>}
    </div>
  );
}
```

`extension/src/sidepanel/PendingList.tsx`:
```tsx
import { useEffect, useState } from 'react';
import type { Submission } from '../shared/types';
import { SubmissionCard } from './SubmissionCard';
import { sendToBackground } from './useBackground';

export function PendingList({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [items, setItems] = useState<Submission[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const res = await sendToBackground<Submission[]>({ type: 'LIST_PENDING' });
    if (res.ok) {
      setItems(res.data);
      setError(null);
    } else if (res.code === 'UNAUTHORIZED') {
      onUnauthorized();
    } else {
      setError(res.code === 'OFFLINE' ? 'Çevrimdışı — Vetly\'ye ulaşılamıyor.' : res.error);
    }
  }

  useEffect(() => {
    load();
  }, []);

  if (error) return <div className="banner">{error} <button onClick={load}>Yenile</button></div>;
  if (!items) return <div className="muted">Yükleniyor…</div>;
  if (items.length === 0) return <div className="muted">TARBİL'e aktarılmayı bekleyen aşı yok.</div>;
  return (
    <>
      <div className="row">
        <span className="muted">{items.length} bekleyen aşı</span>
        <button onClick={load}>Yenile</button>
      </div>
      {items.map((s) => (
        <SubmissionCard key={s.id} submission={s} onDone={load} />
      ))}
    </>
  );
}
```

`extension/src/sidepanel/App.tsx`:
```tsx
import { useEffect, useState } from 'react';
import type { ExtensionState } from '../shared/messages';
import { PairingView } from './PairingView';
import { PendingList } from './PendingList';
import { sendToBackground } from './useBackground';

export function App() {
  const [state, setState] = useState<ExtensionState | null>(null);
  const [message, setMessage] = useState<string | undefined>();

  async function refresh() {
    const res = await sendToBackground<ExtensionState>({ type: 'GET_STATE' });
    if (res.ok) setState(res.data);
  }

  useEffect(() => {
    refresh();
  }, []);

  if (!state) return <div className="panel muted">Yükleniyor…</div>;
  if (!state.paired) return <PairingView onPaired={setState} message={message} />;

  return (
    <div className="panel">
      <div className="header">
        <div>
          <strong>{state.profile?.clinicName ?? 'Vetly'}</strong>
          <div className="muted">{state.profile?.staffName ?? 'Çevrimdışı'}</div>
        </div>
        <button onClick={async () => setState((await sendToBackground<ExtensionState>({ type: 'UNPAIR' })).ok ? { ...state, paired: false } : state)}>
          Bağlantıyı kes
        </button>
      </div>
      {state.pendingConfirmations > 0 && (
        <div className="banner">{state.pendingConfirmations} onay Vetly'ye gönderilmeyi bekliyor (bağlantı gelince otomatik).</div>
      )}
      <PendingList
        onUnauthorized={() => {
          setMessage('Bağlantı iptal edildi ya da geçersiz. Yeniden bağlayın.');
          setState({ ...state, paired: false });
        }}
      />
    </div>
  );
}
```

`extension/src/sidepanel/main.tsx`:
```tsx
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './panel.css';

createRoot(document.getElementById('root')!).render(<App />);
```

- [ ] **Step 3: Derle**

Run: `cd extension && npm run build:dev && npm test`
Expected: Hatasız; testler PASS.

- [ ] **Step 4: Elle doğrula (yerel backend)**

`chrome://extensions` → `extension/dist`'i yükle → araç çubuğu simgesi → yan panel "Vetly'ye bağlan". Vetly'de (localhost:5173) kod oluştur, panelde gir → başlıkta klinik ve hekim adı; bekleyen aşılar listelenir; "Gönderildi olarak işaretle" sonrası listeden düşer ve Vetly Ayarlar > Entegrasyonlar'da "Gönderildi · Elle işaretlendi" görünür. Vetly'de anahtarı iptal et → panelde "Yenile" → eşleştirme ekranı + iptal mesajı. Backend'i durdurup "Gönderildi olarak işaretle" → çevrimdışı notu; backend'i başlat, ≤1 dk sonra kayıt Vetly'de "Gönderildi".

- [ ] **Step 5: Commit**

```bash
git add extension
git commit -m "feat(extension): yan panel -- eslestirme, bekleyen asilar, gonderildi/bildirilmeyecek"
```

---

### Task 12: Vetly'de "TARBİL'e aktar" ve TARBİL sayfasında Vetly kartı

**Files:**
- Create: `frontend/src/lib/tarbilExtension.ts`
- Modify: `frontend/src/vite-env.d.ts`, `frontend/src/pages/vaccinations/VaccinationsPage.tsx`, `frontend/.env.production` (yalnız yeni değişken eklenir)
- Modify: `extension/src/tarbil/content.ts`

**Interfaces:**
- Consumes: Task 10 `ExternalRequest` (`PING`, `SELECT_SUBMISSION`), `BackgroundRequest` (`GET_ACTIVE`, `MARK_SUBMITTED`, `DISMISS`).
- Produces: `frontend/src/lib/tarbilExtension.ts` → `isExtensionAvailable(): Promise<boolean>`, `selectForTarbil(vaccinationRecordId: string): Promise<{ ok: boolean; reason?: 'NOT_INSTALLED' | 'NOT_PAIRED' | 'NOT_FOUND' | 'ERROR' }>`; ortam değişkenleri `VITE_TARBIL_EXTENSION_ID`, `VITE_TARBIL_VACCINE_URL` (Faz 1 varsayılanı `https://hbsapp.tarbil.gov.tr/`; Faz 2 kesin aşı sayfası adresini koyar).

- [ ] **Step 1: Frontend yardımcı modülü**

`frontend/src/vite-env.d.ts`'e ekle:
```ts
interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_TARBIL_EXTENSION_ID?: string;
  readonly VITE_TARBIL_VACCINE_URL?: string;
}
```
(Dosyada `VITE_API_BASE_URL` zaten tanımlıysa yalnız yeni iki satırı mevcut arayüze ekle.)

`frontend/src/lib/tarbilExtension.ts`:
```ts
// Vetly TARBIL eklentisiyle konusma (externally_connectable). Eklenti yoksa
// chrome.runtime tanimsiz ya da sendMessage lastError verir.
type Reply = { ok: true; data: unknown } | { ok: false; error: string; code: string };

const EXTENSION_ID = import.meta.env.VITE_TARBIL_EXTENSION_ID;
export const TARBIL_VACCINE_URL = import.meta.env.VITE_TARBIL_VACCINE_URL ?? 'https://hbsapp.tarbil.gov.tr/';

declare const chrome: {
  runtime?: { sendMessage: (id: string, msg: unknown, cb: (reply: Reply | undefined) => void) => void; lastError?: unknown };
};

function send(msg: unknown): Promise<Reply | undefined> {
  return new Promise((resolve) => {
    if (!EXTENSION_ID || typeof chrome === 'undefined' || !chrome.runtime?.sendMessage) {
      resolve(undefined);
      return;
    }
    try {
      chrome.runtime.sendMessage(EXTENSION_ID, msg, (reply) => {
        resolve(chrome.runtime?.lastError ? undefined : reply);
      });
    } catch {
      resolve(undefined);
    }
  });
}

export async function isExtensionAvailable(): Promise<boolean> {
  const reply = await send({ type: 'PING' });
  return !!reply?.ok;
}

export async function selectForTarbil(
  vaccinationRecordId: string,
): Promise<{ ok: boolean; reason?: 'NOT_INSTALLED' | 'NOT_PAIRED' | 'NOT_FOUND' | 'ERROR' }> {
  const reply = await send({ type: 'SELECT_SUBMISSION', vaccinationRecordId });
  if (!reply) return { ok: false, reason: 'NOT_INSTALLED' };
  if (reply.ok) return { ok: true };
  if (reply.code === 'UNAUTHORIZED') return { ok: false, reason: 'NOT_PAIRED' };
  if (reply.code === 'NOT_FOUND') return { ok: false, reason: 'NOT_FOUND' };
  return { ok: false, reason: 'ERROR' };
}
```

- [ ] **Step 2: Aşı listesine buton**

`VaccinationsPage.tsx` — import'lara ekle:
```tsx
import { selectForTarbil, TARBIL_VACCINE_URL } from '../../lib/tarbilExtension';
```
Bileşen içinde (diğer `useState`'lerin yanına):
```tsx
  const [tarbilNotice, setTarbilNotice] = useState<string | null>(null);

  async function handleSendToTarbil(vaccinationId: string) {
    const result = await selectForTarbil(vaccinationId);
    if (result.ok) {
      window.open(TARBIL_VACCINE_URL, '_blank', 'noopener');
      setTarbilNotice('TARBİL yeni sekmede açıldı; aşı bilgisi sayfadaki Vetly kartında.');
      return;
    }
    setTarbilNotice(
      result.reason === 'NOT_INSTALLED'
        ? 'Vetly TARBİL eklentisi bu tarayıcıda yüklü değil. Kurulum: Ayarlar > Entegrasyonlar.'
        : result.reason === 'NOT_PAIRED'
          ? 'Eklenti Vetly\'ye bağlı değil. Ayarlar > Entegrasyonlar > "Eklentiyi bağla".'
          : result.reason === 'NOT_FOUND'
            ? 'Bu aşı TARBİL kuyruğunda yok (iptal edilmiş ya da henüz uygulanmamış olabilir).'
            : 'Eklentiyle iletişim kurulamadı.',
    );
  }
```
Satır eylemlerinde, `tab === 'planlanan'` bloğunun yanına uygulanmış aşılar için ekle (aynı `styles.actionsCell`/`styles.actionBtn` sınıflarıyla, `canWrite` koşulu altında):
```tsx
              {v.status === 'ADMINISTERED' && canWrite && (
                <div className={styles.actionsCell} onClick={(e) => e.stopPropagation()}>
                  <button type="button" className={styles.actionBtn} onClick={() => handleSendToTarbil(v.id)}>
                    TARBİL'e aktar
                  </button>
                </div>
              )}
```
Sayfanın üst kısmına (başlık altı) bildirim:
```tsx
      {tarbilNotice && <div className={styles.muted}>{tarbilNotice}</div>}
```
(Satır grid'i `rowWithActions`/`rowPlain` sınıflarıyla sütun sayısı belirliyor; uygulanmış aşı satırlarında eylem sütunu görünsün diye className koşulunu `(tab === 'planlanan' && canWrite) || (v.status === 'ADMINISTERED' && canWrite) ? styles.rowWithActions : styles.rowPlain` yap.)

`frontend/.env.production`'a ekle (`<ID>`: Task 9 Step 6'da `npm run extension-id` çıktısı):
```
VITE_TARBIL_EXTENSION_ID=<ID>
VITE_TARBIL_VACCINE_URL=https://hbsapp.tarbil.gov.tr/
```
Yerel geliştirme için `frontend/.env.development.local` (gitignore'da değilse ekle) aynı iki satır.

- [ ] **Step 3: TARBİL sayfasında Vetly kartı (içerik betiği)**

`extension/src/tarbil/content.ts`:
```ts
// Faz 1: TARBIL sayfasinda aktif asiyi gosteren kucuk kart. Form doldurma Faz 2'de.
// Izole dunyada calisir; Shadow DOM ile TARBIL'in CSS'inden yalitilir.
import type { BackgroundRequest, BackgroundResponse } from '../shared/messages';
import type { Submission } from '../shared/types';

function send<T>(req: BackgroundRequest): Promise<BackgroundResponse<T>> {
  return chrome.runtime.sendMessage(req);
}

const host = document.createElement('div');
host.style.cssText = 'position:fixed;right:16px;bottom:16px;z-index:2147483647;';
const root = host.attachShadow({ mode: 'closed' });

const STYLE = `
  .card{font:13px/1.4 system-ui,sans-serif;background:#fff;color:#1f2937;border:1px solid #e5e7eb;border-radius:8px;
        box-shadow:0 4px 16px rgba(0,0,0,.15);padding:10px;width:280px;display:flex;flex-direction:column;gap:6px}
  .muted{color:#6b7280;font-size:12px} .warn{color:#b4532a;font-size:12px} .ok{color:#2e7d32}
  button{border:1px solid #e5e7eb;background:#fff;border-radius:6px;padding:5px 8px;cursor:pointer}
  .row{display:flex;gap:6px;flex-wrap:wrap} .x{margin-left:auto;border:none;background:none;font-size:16px}
`;

function el(html: string): HTMLElement {
  const t = document.createElement('template');
  t.innerHTML = html.trim();
  return t.content.firstElementChild as HTMLElement;
}

function escapeHtml(s: string) {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

async function render() {
  const res = await send<Submission | null>({ type: 'GET_ACTIVE' });
  root.innerHTML = `<style>${STYLE}</style>`;
  if (!res.ok || !res.data) {
    host.remove();
    return;
  }
  const s = res.data;
  if (!host.isConnected) document.body.appendChild(host);

  if (s.status === 'SUBMITTED') {
    root.appendChild(el(`<div class="card"><div class="row"><strong>Vetly</strong><button class="x" data-a="close">×</button></div>
      <div class="ok">✓ ${escapeHtml(s.patientName)} — bu aşı ${new Date(s.submittedAt!).toLocaleDateString('tr-TR')} tarihinde TARBİL'e kaydedilmiş. Tekrar girmeyin.</div></div>`));
  } else {
    root.appendChild(el(`<div class="card">
      <div class="row"><strong>Vetly</strong><button class="x" data-a="close">×</button></div>
      <div><strong>${escapeHtml(s.patientName)}</strong></div>
      ${s.microchipNumber ? `<div>Çip: ${escapeHtml(s.microchipNumber)}</div>` : '<div class="warn">Çip numarası yok.</div>'}
      <div>${escapeHtml(s.vaccineName)} · ${new Date(s.administeredDate).toLocaleDateString('tr-TR')}</div>
      <div class="muted">Otomatik doldurma yakında. Şimdilik bilgileri TARBİL'e girip Kaydet'e basın, ardından işaretleyin.</div>
      <div class="row"><button data-a="submitted">Kaydedildi olarak işaretle</button><button data-a="dismiss">Bildirilmeyecek</button></div>
      <div class="muted" data-note></div></div>`));
  }

  root.querySelectorAll('button').forEach((b) =>
    b.addEventListener('click', async () => {
      const action = (b as HTMLElement).dataset.a;
      const note = root.querySelector('[data-note]');
      if (action === 'close') {
        host.remove();
        return;
      }
      const r =
        action === 'submitted'
          ? await send<{ queued?: boolean }>({ type: 'MARK_SUBMITTED', id: s.id, method: 'MANUAL', tarbilReference: null })
          : await send({ type: 'DISMISS', id: s.id, reason: 'Bildirim gerekmiyor' });
      if (note) note.textContent = r.ok ? 'Vetly güncellendi.' : r.error;
      if (r.ok) setTimeout(render, 1200);
    }),
  );
}

render();
chrome.storage.session.onChanged.addListener((changes) => {
  if ('activeSubmissionId' in changes) render();
});
```
(Not: `chrome.storage.session` içerik betiklerinden varsayılan olarak erişilemez. `background/index.ts`'in başına `chrome.storage.session.setAccessLevel({ accessLevel: 'TRUSTED_AND_UNTRUSTED_CONTEXTS' }).catch(() => undefined);` ekle — bu adımın parçasıdır.)

- [ ] **Step 4: Derle**

Run: `cd extension && npm run build:dev && npm test && cd ../frontend && npm run build`
Expected: Hepsi hatasız.

- [ ] **Step 5: Elle doğrula**

`frontend/.env.development.local`'a eklenti kimliğini koy, `npm run dev`. Eklentiyi yeniden yükle. Aşı Takvimi'nde uygulanmış bir aşıda "TARBİL'e aktar" → TARBİL yeni sekmede açılır, sağ altta Vetly kartı o aşıyla görünür; "Kaydedildi olarak işaretle" → "Vetly güncellendi", kart ✓ durumuna geçer; aynı aşıda tekrar "TARBİL'e aktar" → kart "zaten kaydedilmiş, tekrar girmeyin" uyarısını gösterir. Eklentiyi devre dışı bırak → buton "eklenti yüklü değil" bildirimini gösterir.

- [ ] **Step 6: Commit**

```bash
git add frontend extension
git commit -m "feat: Vetly'den 'TARBIL'e aktar' ve TARBIL sayfasinda Vetly karti"
```

---

### Task 13: Dokümantasyon ve dağıtım

**Files:**
- Modify: `docs/api-conventions.md`, `docs/architecture.md`, `docs/deployment.md`, `docs/implementation-plan.md`

- [ ] **Step 1: `docs/api-conventions.md`**

Rol matrisindeki `/tarbil/**` satırını şu iki satırla değiştir (tablonun sütun sırası mevcut satırla aynı kalsın; mevcut satır `| \`/tarbil/**\` (senkron tetikleme) | ❌ | ❌ | ❌ | ✅ |`):
```
| `/tarbil/**` (durum, bildirilmeyecek/geri al, eklenti bağlama, eşleştirmeler) | ✅ (VET) | ❌ | ❌ | ✅ |
| `/tarbil-extension/**` (yalnız eklenti anahtarı; `/pair` herkese açık + hız sınırlı) | eklenti | eklenti | eklenti | eklenti |
```
(Sütun başlıklarını kontrol et; VET sütunu hangisiyse ✅ orada olmalı.)

- [ ] **Step 2: `docs/architecture.md`**

Güvenlik/auth bölümüne kısa madde ekle:
```
- **Üçüncü SecurityFilterChain — TARBİL eklentisi (`TarbilExtensionSecurityConfig`, `@Order(2)`):** yalnız `/api/v1/tarbil-extension/**`. `vtx_` önekli anahtar `platform/security/ExtensionTokenAuthenticator` portuyla doğrulanır; portun uygulaması `integration/tarbil` modülünde (platform bir modüle bağımlı olamaz). JWT bu zincirde, eklenti anahtarı ana zincirde geçersizdir.
```

- [ ] **Step 3: `docs/deployment.md`**

"Bilinen açık işler" bölümünden önce ekle:
````markdown
## TARBİL eklentisi

Kaynak: `extension/` (tasarım: `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`).

```powershell
cd <repo>\extension
npm ci
npm run build          # dist/ — prod API: https://uygulama.vetly.com.tr
```
Kurulum (her klinik bilgisayarında, şimdilik paketlenmemiş): Chrome > `chrome://extensions` > Geliştirici modu > "Paketlenmemiş öğe yükle" > `extension\dist`. Güncellemede aynı klasörün içeriği değiştirilip eklenti kartında "Yeniden yükle".

- Eklenti kimliği manifest'teki `key` alanından türetilir ve sabittir (`npm run extension-id`); frontend'de `VITE_TARBIL_EXTENSION_ID` aynı değerdir. `extension/key.pem` repoda DEĞİLDİR, güvenli yedekte saklanır (Web Store'a geçişte gerekir).
- Hekim eklentiyi Vetly > Ayarlar > Entegrasyonlar > "Eklentiyi bağla" ile bağlar; bağlantılar aynı ekrandan iptal edilir.
````

- [ ] **Step 4: `docs/implementation-plan.md`**

Bölüm 7 (TARBİL Entegrasyonu) altındaki `MockTarbilAdapter` maddesinin sonuna ekle:
```
  **Güncelleme (2026-10):** `MockTarbilAdapter` ve retry altyapısı kaldırıldı; resmi API olmadığından aktarım hekimin tarayıcısındaki Vetly TARBİL eklentisiyle yapılıyor (Faz 1: eşleştirme, bekleyenler, elle "gönderildi"; Faz 2: TARBİL formunu otomatik doldurma). Bkz. `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`.
```

- [ ] **Step 5: Son kontrol**

Run: `cd backend && ./mvnw test` ; `cd ../frontend && npm run build` ; `cd ../extension && npm test && npm run build`
Expected: Hepsi yeşil.

- [ ] **Step 6: Commit**

```bash
git add docs
git commit -m "docs: TARBIL eklentisi Faz 1 -- API/rol matrisi, mimari, dagitim"
```
