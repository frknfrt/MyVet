# TARBİL Otomasyon Çekirdeği — P0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aşıya özel TARBİL aktarım altyapısını, davranış değiştirmeden, belge türlerine (aşı, reçete, stok kabul) açık bir çekirdeğe dönüştürmek.

**Architecture:** Backend'de `tarbil_sync_log` tablosu ve `TarbilSyncLog` varlığı `tarbil_submission` / `TarbilSubmission` olur; satır artık `document_type` + `source_id` taşır. Eşleştirme türleri genişler, TARBİL hastalık ağacı referans tablo ve uç olarak eklenir. Eklentide dosyalar `core/ steps/ pages/ selectors/` yapısına taşınır, MAIN dünyadaki buton tıklamaları tek bir izin listesinden geçer, akış durumu belge türü taşır.

**Tech Stack:** Java 21, Spring Boot 3, Spring Modulith, JPA/Hibernate, Flyway, PostgreSQL, JUnit 5 + Mockito + MockMvc; Chrome MV3, TypeScript 5.5, Vite 5, Vitest 2 + jsdom.

**Spec:** `docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md` (§5.1, §6, §7.1, §12 madde 1). Önceki tasarım: `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`.

## Global Constraints

- **Davranış değişmez:** Vetly web arayüzü, eklenti API'sinin mevcut JSON alanları (`vaccinationRecordId` dahil) ve eklentinin TARBİL'deki davranışı aynı kalır; yalnız alan **eklenir** (`documentType`). (Spec §12 madde 1)
- `TarbilDocumentType` değerleri: `VACCINATION`, `PRESCRIPTION`, `STOCK_RECEIPT`. (Spec §5.1)
- `TarbilMappingKind` değerleri: `VACCINE`, `SPECIES`, `DISEASE`, `DRUG_ROUTE`, `STOCK_PRODUCT`. (Spec §5.1)
- Benzersizlik: (`tenant_id`, `document_type`, `source_id`). (Spec §5.1)
- Eklentide resmi kaydı tamamlayan butonlar (`btnInsert`, `btnInsert2`, `btnApprove`, `btnReject`, çıkış) **hiçbir izin listesinde olamaz**; bunu bir test sabitler. (Spec §2 kural 1, §7.1)
- `integration/tarbil` başka modülün verisine yalnız portlarla erişir (`ApplicationModulesTest`). (Spec §5.5)
- `tarbil_disease` küresel referans verisidir (kiracı alanı yok); `GET /api/v1/tarbil/diseases` ADMIN ve VET'e açık (mevcut `TarbilController` `@PreAuthorize`). (Spec §5.1, §6)
- Backend testleri canlı docker-compose Postgres ister: `cd backend && docker-compose up -d` (localhost:5433). Hekimin 8080'deki backend'ine dokunulmaz.
- Kullanıcıya görünen metinler Türkçe; konsola veri yazılmaz; testlerde gerçek kişi/hayvan verisi yok.

## Review Focus

1. **Canlı veritabanındaki mevcut aşı satırları** V62 migration'ından sonra aynı kimlik ve durumla kalmalı, Vetly Entegrasyonlar ekranında aynen görünmeli → Task 1 entegrasyon testi `migratedVaccinationSubmissionIsListedAsBefore`.
2. **Aşı dışı bir satır** (ileride `PRESCRIPTION`) aşı ekranlarını ve eklentinin bekleyen listesini bozmamalı; aşı hazırlayıcısı onu yok saymalı → Task 1 testi `should_notAssemble_when_documentTypeIsNotVaccination`.
3. **Güncelleme öncesi tarayıcıda kalmış akış durumu** (`documentType` alanı olmayan `tarbilFlow`) aşı akışı olarak sürmeli → Task 5 testi `treats a stored flow without documentType as a vaccination`.
4. **Onayla'ya bastırma girişimi** (köprüden `clickAllowed` ile `btnInsert` istenmesi) reddedilmeli, hiçbir tıklama olmamalı → Task 4 testleri `refuses to click Onayla even when asked through the bridge` ve `contains no button that finalizes an official record`.
5. **`GET /pending` parametresiz çağrı** eskisiyle aynı listeyi dönmeli (eklentinin bugünkü sürümü parametre göndermiyor) → Task 1 testi `should_returnAllTypes_when_noTypeGiven`.

---

## Dosya Yapısı

**Backend (`backend/src/main/java/com/vetos/modules/integration/tarbil/`)**
- `domain/TarbilSyncLog.java` → `domain/TarbilSubmission.java` (yeniden adlandır + genelleştir)
- `domain/TarbilSyncLogRepository.java` → `domain/TarbilSubmissionRepository.java`
- `domain/TarbilSyncType.java` → `domain/TarbilDocumentType.java`
- `domain/TarbilMappingKind.java` (değerler eklenir)
- `domain/TarbilDisease.java`, `domain/TarbilDiseaseRepository.java` (yeni)
- `infrastructure/persistence/TarbilSyncLogJpaRepository.java` → `TarbilSubmissionJpaRepository.java`
- `infrastructure/persistence/TarbilSyncLogRepositoryAdapter.java` → `TarbilSubmissionRepositoryAdapter.java`
- `infrastructure/persistence/TarbilDiseaseJpaRepository.java`, `TarbilDiseaseRepositoryAdapter.java` (yeni)
- `application/QueueTarbilSyncUseCase.java`, `GetSubmissionUseCase.java`, `ListPendingSubmissionsUseCase.java`, `TarbilSubmissionAssembler.java` (değişir)
- `application/ListTarbilDiseasesUseCase.java`, `application/dto/TarbilDiseaseSummary.java` (yeni)
- `application/dto/TarbilSubmissionView.java`, `api/dto/TarbilSubmissionResponse.java` (`documentType` eklenir)
- `api/dto/TarbilDiseaseResponse.java` (yeni); `api/TarbilController.java`, `api/TarbilExtensionController.java` (değişir)
- `backend/src/main/resources/db/migration/V62__tarbil_submission_generalize.sql`, `V63__tarbil_disease.sql` (yeni)

**Eklenti (`extension/src/`)**
```
tarbil/
├── content.ts                  (importlar güncellenir)
├── core/      bridge.ts, card.ts, views.ts, home.ts, success.ts (+ testler)
├── steps/     animalRows.ts, species.ts, findAnimal.ts (eski searchFlow) (+ testler)
├── pages/     vaccineReceipt.ts (eski receiptFlow) (+ test)
├── selectors/ index.ts, shared.ts, vaccineReceipt.ts, allowlist.ts (+ testler)
└── page/      main.ts, ops.ts, telerik.ts (MAIN dünya; ops değişir)
shared/        flowStore.ts (documentType, stepData), types.ts (DocumentType)
background/    tarbilTab.ts (import yolu)
```

---

### Task 1: Backend — aktarım kaydının genelleştirilmesi (`tarbil_submission`)

**Files:**
- Create: `backend/src/main/resources/db/migration/V62__tarbil_submission_generalize.sql`
- Rename+Modify: `domain/TarbilSyncLog.java` → `domain/TarbilSubmission.java`; `domain/TarbilSyncLogRepository.java` → `domain/TarbilSubmissionRepository.java`; `domain/TarbilSyncType.java` → `domain/TarbilDocumentType.java`; `infrastructure/persistence/TarbilSyncLogJpaRepository.java` → `TarbilSubmissionJpaRepository.java`; `infrastructure/persistence/TarbilSyncLogRepositoryAdapter.java` → `TarbilSubmissionRepositoryAdapter.java`
- Modify: `application/QueueTarbilSyncUseCase.java`, `application/GetSubmissionUseCase.java`, `application/ListPendingSubmissionsUseCase.java`, `application/TarbilSubmissionAssembler.java`, `application/dto/TarbilSubmissionView.java`, `api/dto/TarbilSubmissionResponse.java`, `api/TarbilExtensionController.java`
- Test (rename+modify): `test/.../tarbil/domain/TarbilSyncLogTest.java` → `TarbilSubmissionTest.java`; `QueueTarbilSyncUseCaseTest.java`, `GetSubmissionUseCaseTest.java`, `ListPendingSubmissionsUseCaseTest.java`, `TarbilExtensionSecurityIntegrationTest.java` ve `TarbilSyncLog` geçen diğer testler

**Interfaces:**
- Produces:
  - `enum TarbilDocumentType { VACCINATION, PRESCRIPTION, STOCK_RECEIPT }`
  - `TarbilSubmission.queue(UUID tenantId, TarbilDocumentType type, UUID patientId /* nullable */, UUID sourceId)`; `TarbilSubmission.queueVaccination(UUID tenantId, UUID patientId, UUID vaccinationRecordId)` (korunur); getter'lar `getDocumentType()`, `getSourceId()` (eski `getVaccinationRecordId()` yerine)
  - `TarbilSubmissionRepository`: `save`, `findById`, `findByTenantId`, `findByTenantIdAndStatus`, `findByDocumentTypeAndSourceId(TarbilDocumentType, UUID)`
  - `ListPendingSubmissionsUseCase.execute(UUID tenantId)` ve `execute(UUID tenantId, TarbilDocumentType typeOrNull)`
  - `TarbilSubmissionView` ve `TarbilSubmissionResponse` son bileşen olarak `TarbilDocumentType documentType`
  - `GET /api/v1/tarbil-extension/pending?type=VACCINATION` (parametre isteğe bağlı)

Bu task'taki tüm yollar `backend/src` altına göredir; `M=main/java/com/vetos/modules/integration/tarbil`, `T=test/java/com/vetos/modules/integration/tarbil`.

- [ ] **Step 1: Dosyaları yeniden adlandır ve tür adlarını mekanik olarak değiştir**

```bash
cd backend/src
M=main/java/com/vetos/modules/integration/tarbil
T=test/java/com/vetos/modules/integration/tarbil
git mv $M/domain/TarbilSyncLog.java $M/domain/TarbilSubmission.java
git mv $M/domain/TarbilSyncLogRepository.java $M/domain/TarbilSubmissionRepository.java
git mv $M/domain/TarbilSyncType.java $M/domain/TarbilDocumentType.java
git mv $M/infrastructure/persistence/TarbilSyncLogJpaRepository.java $M/infrastructure/persistence/TarbilSubmissionJpaRepository.java
git mv $M/infrastructure/persistence/TarbilSyncLogRepositoryAdapter.java $M/infrastructure/persistence/TarbilSubmissionRepositoryAdapter.java
git mv $T/domain/TarbilSyncLogTest.java $T/domain/TarbilSubmissionTest.java
FILES=$(grep -rlE '\bTarbilSyncLog(Repository|JpaRepository|RepositoryAdapter|Test)?\b|\bTarbilSyncType\b' main test)
perl -pi -e 's/\bTarbilSyncLogRepositoryAdapter\b/TarbilSubmissionRepositoryAdapter/g; s/\bTarbilSyncLogJpaRepository\b/TarbilSubmissionJpaRepository/g; s/\bTarbilSyncLogRepository\b/TarbilSubmissionRepository/g; s/\bTarbilSyncLogTest\b/TarbilSubmissionTest/g; s/\bTarbilSyncLog\b/TarbilSubmission/g; s/\bTarbilSyncType\b/TarbilDocumentType/g' $FILES
grep -rnE '\bTarbilSyncLog\b|\bTarbilSyncType\b' main test || echo "TEMIZ"
```
Expected: son satır `TEMIZ`. (`TarbilSyncLogSummary`, `TarbilSyncLogResponse`, `ListTarbilSyncLogsUseCase` web listesi adları bilerek **değişmez**; `\b` sınırı bunları korur.)

- [ ] **Step 2: Varlığı, enum'u ve depoyu genelleştir**

`$M/domain/TarbilDocumentType.java` (tümüyle değiştir):

```java
package com.vetos.modules.integration.tarbil.domain;

/** TARBIL'e aktarilan belge turu (spec 2026-10-04 S5.1). source_id'nin anlami ture gore degisir. */
public enum TarbilDocumentType { VACCINATION, PRESCRIPTION, STOCK_RECEIPT }
```

`$M/domain/TarbilSubmission.java` (tümüyle değiştir):

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
 * Bir Vetly kaydinin (asi, recete, stok kabul) TARBIL'e aktarim durumu. Sunucu TARBIL'e hicbir sey
 * gondermez; resmi kaydi hekim TARBIL arayuzunde onaylar, eklenti (ya da hekim elle) bunu buraya bildirir.
 * source_id: VACCINATION -> vaccination_records.id, PRESCRIPTION -> prescriptions.id,
 * STOCK_RECEIPT -> stock_receipts.id. Bkz. docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md.
 */
@Entity
@Table(name = "tarbil_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Stok kabulde hasta yoktur. */
    @Column(name = "patient_id")
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private TarbilDocumentType documentType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

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

    public static TarbilSubmission queue(UUID tenantId, TarbilDocumentType type, UUID patientId, UUID sourceId) {
        TarbilSubmission submission = new TarbilSubmission();
        submission.tenantId = tenantId;
        submission.documentType = type;
        submission.patientId = patientId;
        submission.sourceId = sourceId;
        submission.status = TarbilSyncStatus.PENDING;
        submission.queuedAt = Instant.now();
        return submission;
    }

    public static TarbilSubmission queueVaccination(UUID tenantId, UUID patientId, UUID vaccinationRecordId) {
        return queue(tenantId, TarbilDocumentType.VACCINATION, patientId, vaccinationRecordId);
    }

    /** @return true ise durum degisti; false ise zaten SUBMITTED'di (idempotent, ilk kayit korunur). */
    public boolean markSubmitted(UUID staffId, TarbilConfirmationMethod method, String reference, Instant now) {
        if (status == TarbilSyncStatus.SUBMITTED) {
            return false;
        }
        // DISMISSED'tan da gecilir: TARBIL'e gercekten kaydedildiyse "bildirilmeyecek" niyeti olgunun onune gecemez.
        this.dismissedReason = null;
        this.dismissedByStaffId = null;
        this.dismissedAt = null;
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

`$M/domain/TarbilSubmissionRepository.java` (tümüyle değiştir):

```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilSubmissionRepository {
    TarbilSubmission save(TarbilSubmission submission);
    Optional<TarbilSubmission> findById(UUID id);
    List<TarbilSubmission> findByTenantId(UUID tenantId);
    List<TarbilSubmission> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSubmission> findByDocumentTypeAndSourceId(TarbilDocumentType documentType, UUID sourceId);
}
```

`$M/infrastructure/persistence/TarbilSubmissionJpaRepository.java` (tümüyle değiştir):

```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TarbilSubmissionJpaRepository extends JpaRepository<TarbilSubmission, UUID> {
    List<TarbilSubmission> findByTenantId(UUID tenantId);
    List<TarbilSubmission> findByTenantIdAndStatusOrderByQueuedAtAsc(UUID tenantId, TarbilSyncStatus status);
    Optional<TarbilSubmission> findByDocumentTypeAndSourceId(TarbilDocumentType documentType, UUID sourceId);
}
```

`$M/infrastructure/persistence/TarbilSubmissionRepositoryAdapter.java` (tümüyle değiştir):

```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilSubmissionRepositoryAdapter implements TarbilSubmissionRepository {

    private final TarbilSubmissionJpaRepository jpaRepository;

    @Override public TarbilSubmission save(TarbilSubmission submission) { return jpaRepository.save(submission); }

    @Override public Optional<TarbilSubmission> findById(UUID id) { return jpaRepository.findById(id); }

    @Override public List<TarbilSubmission> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }

    @Override
    public List<TarbilSubmission> findByTenantIdAndStatus(UUID tenantId, TarbilSyncStatus status) {
        return jpaRepository.findByTenantIdAndStatusOrderByQueuedAtAsc(tenantId, status);
    }

    @Override
    public Optional<TarbilSubmission> findByDocumentTypeAndSourceId(TarbilDocumentType documentType, UUID sourceId) {
        return jpaRepository.findByDocumentTypeAndSourceId(documentType, sourceId);
    }
}
```

- [ ] **Step 3: Migration'ı yaz**

`backend/src/main/resources/db/migration/V62__tarbil_submission_generalize.sql`:

```sql
-- TARBIL otomasyon cekirdegi P0 (docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md S5.1).
-- Asiya ozel aktarim kaydi belge turlerine acilir; mevcut satirlar VACCINATION olarak kalir, kimlikleri degismez.
ALTER TABLE tarbil_sync_log RENAME TO tarbil_submission;
ALTER TABLE tarbil_submission RENAME COLUMN sync_type TO document_type;
ALTER TABLE tarbil_submission RENAME COLUMN vaccination_record_id TO source_id;
-- Stok kabulde hasta yok.
ALTER TABLE tarbil_submission ALTER COLUMN patient_id DROP NOT NULL;

DROP INDEX IF EXISTS uq_tarbil_sync_log_vaccination_record_id;
CREATE UNIQUE INDEX uq_tarbil_submission_source ON tarbil_submission (tenant_id, document_type, source_id);

ALTER INDEX IF EXISTS idx_tarbil_sync_log_tenant_status RENAME TO idx_tarbil_submission_tenant_status;
ALTER INDEX IF EXISTS idx_tarbil_sync_log_tenant_id RENAME TO idx_tarbil_submission_tenant_id;
ALTER INDEX IF EXISTS idx_tarbil_sync_log_patient_id RENAME TO idx_tarbil_submission_patient_id;
```

- [ ] **Step 4: Kullanım yerlerini yeni depo metoduna ve alan adına geçir**

```bash
cd backend/src
M=main/java/com/vetos/modules/integration/tarbil
T=test/java/com/vetos/modules/integration/tarbil
perl -pi -e 's/findByVaccinationRecordId\((\w+)\)/findByDocumentTypeAndSourceId(TarbilDocumentType.VACCINATION, $1)/g; s/getVaccinationRecordId\(\)/getSourceId()/g' \
  $M/application/QueueTarbilSyncUseCase.java $M/application/GetSubmissionUseCase.java $M/application/TarbilSubmissionAssembler.java \
  $T/application/QueueTarbilSyncUseCaseTest.java $T/application/GetSubmissionUseCaseTest.java $T/domain/TarbilSubmissionTest.java
for f in $M/application/QueueTarbilSyncUseCase.java $M/application/GetSubmissionUseCase.java $T/application/QueueTarbilSyncUseCaseTest.java $T/application/GetSubmissionUseCaseTest.java; do
  grep -q 'import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;' $f || \
  perl -0pi -e 's/(import com\.vetos\.modules\.integration\.tarbil\.domain\.TarbilSubmissionRepository;)/import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;\n$1/' $f
done
sed -i 's/DELETE FROM tarbil_sync_log WHERE tenant_id/DELETE FROM tarbil_submission WHERE tenant_id/' test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java
grep -rn "findByVaccinationRecordId\|getVaccinationRecordId\|tarbil_sync_log" main/java test/java || echo "TEMIZ"
```
Expected: `TEMIZ`. (`TarbilSubmissionView.vaccinationRecordId` bileşeni ve encounter modülündeki `VaccinationRecordedEvent.vaccinationRecordId()` bilerek değişmez — eklenti API sözleşmesi.)

- [ ] **Step 5: Derle ve mevcut testleri çalıştır (yeniden adlandırma davranışı korudu mu)**

Run: `cd backend && ./mvnw -q test -Dtest='Tarbil*Test,QueueTarbilSyncUseCaseTest,GetSubmissionUseCaseTest,ListPendingSubmissionsUseCaseTest,MarkSubmittedUseCaseTest,ListTarbilSyncLogsUseCaseTest,GetTarbilStatusSummaryUseCaseTest' > /tmp/t1a.log 2>&1; echo exit=$?; grep -E "Tests run:|ERROR\]" /tmp/t1a.log | tail -5`
Expected: `exit=0` (derleme hatası ya da başarısız test yok).

- [ ] **Step 6: Yeni davranışlar için başarısız testleri yaz**

`$T/domain/TarbilSubmissionTest.java` sınıfının içine ekle:

```java
    @Test
    void should_carryDocumentTypeAndSource_when_queuedGenerically() {
        UUID tenantId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();

        TarbilSubmission submission = TarbilSubmission.queue(tenantId, TarbilDocumentType.PRESCRIPTION, null, prescriptionId);

        assertThat(submission.getDocumentType()).isEqualTo(TarbilDocumentType.PRESCRIPTION);
        assertThat(submission.getSourceId()).isEqualTo(prescriptionId);
        assertThat(submission.getPatientId()).isNull();
        assertThat(submission.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
    }

    @Test
    void should_beVaccination_when_queuedForVaccination() {
        TarbilSubmission submission = TarbilSubmission.queueVaccination(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThat(submission.getDocumentType()).isEqualTo(TarbilDocumentType.VACCINATION);
    }
```

`$T/application/ListPendingSubmissionsUseCaseTest.java` sınıfının içine ekle:

```java
    @Test
    void should_notAssemble_when_documentTypeIsNotVaccination() {
        TarbilSubmission prescription = TarbilSubmission.queue(tenantId, TarbilDocumentType.PRESCRIPTION, UUID.randomUUID(), UUID.randomUUID());
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(prescription));

        assertThat(useCase().execute(tenantId)).isEmpty();
        verifyNoInteractions(vaccinationLookupPort);
    }

    @Test
    void should_returnAllTypes_when_noTypeGiven() {
        UUID patientId = UUID.randomUUID();
        UUID vaccinationId = UUID.randomUUID();
        TarbilSubmission vaccination = TarbilSubmission.queueVaccination(tenantId, patientId, vaccinationId);
        when(syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING)).thenReturn(List.of(vaccination));
        when(vaccinationLookupPort.findForTarbil(vaccinationId)).thenReturn(Optional.of(new VaccinationTarbilView(
            vaccinationId, tenantId, patientId, "Karma", null, LocalDate.of(2026, 10, 1), VaccinationStatus.ADMINISTERED)));
        when(patientLookupPort.findTarbilProfile(patientId)).thenReturn(Optional.empty());

        assertThat(useCase().execute(tenantId)).hasSize(1);
        assertThat(useCase().execute(tenantId, null)).hasSize(1);
        assertThat(useCase().execute(tenantId, TarbilDocumentType.VACCINATION).get(0).documentType()).isEqualTo(TarbilDocumentType.VACCINATION);
        assertThat(useCase().execute(tenantId, TarbilDocumentType.PRESCRIPTION)).isEmpty();
    }
```

Bu dosyanın import bloğuna ekle (yoksa): `import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;` ve `import static org.mockito.Mockito.verifyNoInteractions;`.

- [ ] **Step 7: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd backend && ./mvnw -q test -Dtest='TarbilSubmissionTest,ListPendingSubmissionsUseCaseTest' > /tmp/t1b.log 2>&1; echo exit=$?; grep -E "ERROR\].*(cannot find symbol|symbol:)|Tests run:.*Fail" /tmp/t1b.log | head -8`
Expected: `exit=1`; derleme hatası `execute(UUID, TarbilDocumentType)` ve `documentType()` bulunamıyor.

- [ ] **Step 8: Hazırlayıcı korumasını, tür filtresini ve `documentType` alanını ekle**

`$M/application/TarbilSubmissionAssembler.java` içinde `liveVaccination` metodunu şununla değiştir:

```java
    /** Eklenti ve web ekrani ayni kurali kullansin: ayni kiracida, iptal edilmemis asi. Asi disi satirlar burada yok sayilir. */
    Optional<VaccinationTarbilView> liveVaccination(TarbilSubmission log) {
        if (log.getDocumentType() != TarbilDocumentType.VACCINATION) {
            return Optional.empty();
        }
        return vaccinationLookupPort.findForTarbil(log.getSourceId())
            .filter(v -> v.tenantId().equals(log.getTenantId()))
            .filter(v -> v.status() != VaccinationStatus.CANCELLED);
    }
```

Aynı dosyada `new TarbilSubmissionView(` çağrısının son argümanını `vaccineKey, vaccineMapping, speciesMapping` → `vaccineKey, vaccineMapping, speciesMapping, log.getDocumentType()` yap ve import ekle: `import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;`.

`$M/application/dto/TarbilSubmissionView.java` kayıt bileşenlerinin sonunu değiştir:

```java
    String vaccineKey, String vaccineMappingJson, String speciesMappingJson,
    TarbilDocumentType documentType
) {}
```
ve import ekle: `import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;`.

`$M/api/dto/TarbilSubmissionResponse.java` kaydını şununla değiştir (import'a `TarbilDocumentType` ekle):

```java
public record TarbilSubmissionResponse(
    UUID id, UUID vaccinationRecordId, TarbilSyncStatus status,
    String patientName, String microchipNumber, UUID speciesId, String speciesName, String breedName,
    String sex, LocalDate birthDate, String vaccineName, String lotNumber, LocalDate administeredDate,
    Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference,
    String vaccineKey, @JsonRawValue String vaccineMapping, @JsonRawValue String speciesMapping,
    TarbilDocumentType documentType
) {
    public static TarbilSubmissionResponse from(TarbilSubmissionView v) {
        return new TarbilSubmissionResponse(v.id(), v.vaccinationRecordId(), v.status(), v.patientName(), v.microchipNumber(),
            v.speciesId(), v.speciesName(), v.breedName(), v.sex(), v.birthDate(), v.vaccineName(), v.lotNumber(),
            v.administeredDate(), v.submittedAt(), v.confirmationMethod(), v.tarbilReference(),
            v.vaccineKey(), v.vaccineMappingJson(), v.speciesMappingJson(), v.documentType());
    }
}
```

`$M/application/ListPendingSubmissionsUseCase.java` içindeki `execute` metodunu şununla değiştir (import'a `TarbilDocumentType` ekle):

```java
    @Transactional(readOnly = true)
    public List<TarbilSubmissionView> execute(UUID tenantId) {
        return execute(tenantId, null);
    }

    /** typeOrNull null ise tum turler (eklentinin parametresiz cagrisi eskisiyle ayni sonucu verir). */
    @Transactional(readOnly = true)
    public List<TarbilSubmissionView> execute(UUID tenantId, TarbilDocumentType typeOrNull) {
        return syncLogRepository.findByTenantIdAndStatus(tenantId, TarbilSyncStatus.PENDING).stream()
            .filter(s -> typeOrNull == null || s.getDocumentType() == typeOrNull)
            .map(assembler::assemble)
            .flatMap(Optional::stream)
            .toList();
    }
```

`$M/api/TarbilExtensionController.java` içindeki `pending` metodunu şununla değiştir (import'a `com.vetos.modules.integration.tarbil.domain.TarbilDocumentType` ekle; `org.springframework.web.bind.annotation.*` zaten varsa `RequestParam` için ek import gerekmez):

```java
    @GetMapping("/pending")
    public List<TarbilSubmissionResponse> pending(@RequestParam(name = "type", required = false) TarbilDocumentType type) {
        return listPendingSubmissionsUseCase.execute(TenantContext.current(), type).stream().map(TarbilSubmissionResponse::from).toList();
    }
```

- [ ] **Step 9: Entegrasyon testi ekle (taşınmış aşı satırı eskisi gibi görünür)**

`test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java` sınıfına ekle (static import'lara `org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath` ekle):

```java
    @Test
    void migratedVaccinationSubmissionIsListedAsBefore() throws Exception {
        UUID ownerA = asTenant(tenantA, () -> ownerRepository.save(Owner.register(tenantA, "Test Sahibi", "5550000000", null, null)).getId());
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID patientA = asTenant(tenantA, () -> patientRepository.save(
            Patient.register(tenantA, ownerA, "Deneme", speciesId, null, null, null, null)).getId());
        UUID vaccinationA = asTenant(tenantA, () -> vaccinationRecordRepository.save(
            VaccinationRecord.administer(tenantA, patientA, "Karma", "L-1", java.time.LocalDate.now(), staffA)).getId());
        UUID submissionId = asTenant(tenantA, () -> tarbilSyncLogRepository.save(
            TarbilSubmission.queueVaccination(tenantA, patientA, vaccinationA)).getId());

        mockMvc.perform(get("/api/v1/tarbil-extension/pending").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(submissionId.toString()))
            .andExpect(jsonPath("$[0].vaccinationRecordId").value(vaccinationA.toString()))
            .andExpect(jsonPath("$[0].documentType").value("VACCINATION"));
        mockMvc.perform(get("/api/v1/tarbil-extension/pending?type=PRESCRIPTION").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }
```

**Not (uygulayıcı için):** `Owner.register`, `Patient.register` ve `VaccinationRecord.administer` imzalarını bu sınıfın **mevcut** "tenant-B aşısı" testindeki (dosyada `vaccinationB` oluşturan yer) çağrılardan birebir kopyala; yukarıdaki argüman listeleri o çağrılarla aynı olmalı. Farklıysa mevcut testteki biçimi kullan ve ledger'a `Ruling` yaz.

- [ ] **Step 10: Testleri çalıştır, geçtiğini gör**

Run: `cd backend && ./mvnw -q test -Dtest='TarbilSubmissionTest,ListPendingSubmissionsUseCaseTest,TarbilExtensionSecurityIntegrationTest' > /tmp/t1c.log 2>&1; echo exit=$?; grep -E "Tests run:|FAIL" /tmp/t1c.log | tail -4`
Expected: `exit=0`.

- [ ] **Step 11: Tüm backend testleri (migration canlı DB'de çalışır)**

Run: `cd backend && ./mvnw -q test > /tmp/t1d.log 2>&1; echo exit=$?; grep -lE "<(failure|error)" target/surefire-reports/TEST-*.xml | head`
Expected: `exit=0`; ikinci komut hiçbir dosya listelemez (başarısız ya da hatalı test yok).

- [ ] **Step 12: Commit**

```bash
git add -A backend/src
git commit -m "refactor(tarbil): tarbil_sync_log -> tarbil_submission (belge turu + kaynak), pending ?type, documentType"
```

---

### Task 2: Backend — eşleştirme türleri ve TARBİL hastalık ağacı

**Files:**
- Modify: `$M/domain/TarbilMappingKind.java`
- Create: `$M/domain/TarbilDisease.java`, `$M/domain/TarbilDiseaseRepository.java`, `$M/infrastructure/persistence/TarbilDiseaseJpaRepository.java`, `$M/infrastructure/persistence/TarbilDiseaseRepositoryAdapter.java`, `$M/application/ListTarbilDiseasesUseCase.java`, `$M/application/dto/TarbilDiseaseSummary.java`, `$M/api/dto/TarbilDiseaseResponse.java`, `backend/src/main/resources/db/migration/V63__tarbil_disease.sql`
- Modify: `$M/api/TarbilController.java`
- Test: `$T/application/LearnTarbilMappingUseCaseTest.java` (ekleme), `$T/application/ListTarbilDiseasesUseCaseTest.java` (yeni), `test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java` (ekleme)

**Interfaces:**
- Consumes: Task 1'deki her şey derlenmiş hâlde.
- Produces:
  - `enum TarbilMappingKind { VACCINE, SPECIES, DISEASE, DRUG_ROUTE, STOCK_PRODUCT }`
  - `TarbilDisease.of(UUID id, UUID parentId, String name, boolean selectable, int sortOrder)`; getter'lar `getId/getParentId/getName/isSelectable/getSortOrder`
  - `TarbilDiseaseRepository.findAllOrdered(): List<TarbilDisease>`
  - `record TarbilDiseaseSummary(UUID id, UUID parentId, String name, String path, boolean selectable)`
  - `ListTarbilDiseasesUseCase.execute(): List<TarbilDiseaseSummary>` — `path` yaprakta `"KATEGORİ > AD"`, kökte `"AD"`
  - `GET /api/v1/tarbil/diseases` → `[{id, parentId, name, path, selectable}]` (73 öğe, `sort_order` sırasıyla)

- [ ] **Step 1: Başarısız testleri yaz**

`$T/application/LearnTarbilMappingUseCaseTest.java` sınıfına ekle:

```java
    @Test
    void should_storeTrimmedKey_when_diseaseMappingLearned() {
        when(repository.findByTenantIdAndKindAndVetlyKey(tenantId, TarbilMappingKind.DISEASE, "iç parazit"))
            .thenReturn(Optional.empty());

        useCase().execute(tenantId, staffId, TarbilMappingKind.DISEASE, "  iç parazit ", "{\"diseaseId\":\"95860589-0057-42cc-8209-663e1e459594\"}");

        ArgumentCaptor<TarbilValueMapping> captor = ArgumentCaptor.forClass(TarbilValueMapping.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKind()).isEqualTo(TarbilMappingKind.DISEASE);
        assertThat(captor.getValue().getVetlyKey()).isEqualTo("iç parazit");
    }
```

`$T/application/ListTarbilDiseasesUseCaseTest.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTarbilDiseasesUseCaseTest {

    @Mock private TarbilDiseaseRepository repository;

    @Test
    void should_buildCategoryPaths_when_listing() {
        UUID category = UUID.fromString("53fe7807-e179-4a57-b4c2-fb5d59f1882d");
        UUID leaf = UUID.fromString("95860589-0057-42cc-8209-663e1e459594");
        UUID rootLeaf = UUID.fromString("afe6153c-f045-4c51-a13d-e4b2e6ffbb0e");
        when(repository.findAllOrdered()).thenReturn(List.of(
            TarbilDisease.of(category, null, "SİNDİRİM SİSTEMİ HASTALIKLARI", false, 1),
            TarbilDisease.of(leaf, category, "PARAZİTER HASTALIKLAR", true, 2),
            TarbilDisease.of(rootLeaf, null, "METABOLİZMA HASTALIKLARI", true, 3)));

        List<TarbilDiseaseSummary> result = new ListTarbilDiseasesUseCase(repository).execute();

        assertThat(result).extracting(TarbilDiseaseSummary::path).containsExactly(
            "SİNDİRİM SİSTEMİ HASTALIKLARI", "SİNDİRİM SİSTEMİ HASTALIKLARI > PARAZİTER HASTALIKLAR", "METABOLİZMA HASTALIKLARI");
        assertThat(result).extracting(TarbilDiseaseSummary::selectable).containsExactly(false, true, true);
        assertThat(result.get(1).parentId()).isEqualTo(category);
    }
}
```

`test/java/com/vetos/TarbilExtensionSecurityIntegrationTest.java` sınıfına ekle (static import'ta `jsonPath` Task 1'de eklendi; `org.hamcrest.Matchers.hasItem` ekle):

```java
    @Test
    void jwtListsTarbilDiseaseCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil/diseases").header("Authorization", "Bearer " + jwtA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(73))
            .andExpect(jsonPath("$[?(@.id == '95860589-0057-42cc-8209-663e1e459594')].path")
                .value(hasItem("SİNDİRİM SİSTEMİ HASTALIKLARI > PARAZİTER HASTALIKLAR")))
            .andExpect(jsonPath("$[0].selectable").value(false));
    }

    @Test
    void extensionTokenCannotListDiseaseCatalogThroughWebApi() throws Exception {
        mockMvc.perform(get("/api/v1/tarbil/diseases").header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isUnauthorized());
    }
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd backend && ./mvnw -q test -Dtest='LearnTarbilMappingUseCaseTest,ListTarbilDiseasesUseCaseTest' > /tmp/t2a.log 2>&1; echo exit=$?; grep -E "cannot find symbol|symbol:" /tmp/t2a.log | head -5`
Expected: `exit=1`; `DISEASE`, `TarbilDisease`, `ListTarbilDiseasesUseCase` bulunamıyor.

- [ ] **Step 3: Eşleştirme türlerini genişlet**

`$M/domain/TarbilMappingKind.java` (tümüyle değiştir):

```java
package com.vetos.modules.integration.tarbil.domain;

/**
 * VACCINE: normalize asi adi. SPECIES: Vetly speciesId (UUID metni). DISEASE: Vetly teshis anahtari -> TARBIL hastalik
 * dugumu. DRUG_ROUTE: Vetly DrugRoute adi -> TARBIL kullanim yolu. STOCK_PRODUCT: Vetly stok kalemi kimligi -> TARBIL
 * urun adi + takdim sekli. (spec 2026-10-04 S5.1)
 */
public enum TarbilMappingKind { VACCINE, SPECIES, DISEASE, DRUG_ROUTE, STOCK_PRODUCT }
```

- [ ] **Step 4: Hastalık referansını yaz**

`$M/domain/TarbilDisease.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** TARBIL recete hastalik agaci (kuresel referans, kiraci yok). id = TARBIL'deki dugum degeri. */
@Entity
@Table(name = "tarbil_disease")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilDisease {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean selectable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static TarbilDisease of(UUID id, UUID parentId, String name, boolean selectable, int sortOrder) {
        TarbilDisease disease = new TarbilDisease();
        disease.id = id;
        disease.parentId = parentId;
        disease.name = name;
        disease.selectable = selectable;
        disease.sortOrder = sortOrder;
        return disease;
    }
}
```

`$M/domain/TarbilDiseaseRepository.java`:

```java
package com.vetos.modules.integration.tarbil.domain;

import java.util.List;

public interface TarbilDiseaseRepository {
    /** sort_order sirasiyla (kategori, ardindan yapraklari). */
    List<TarbilDisease> findAllOrdered();
}
```

`$M/infrastructure/persistence/TarbilDiseaseJpaRepository.java`:

```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TarbilDiseaseJpaRepository extends JpaRepository<TarbilDisease, UUID> {
    List<TarbilDisease> findAllByOrderBySortOrderAsc();
}
```

`$M/infrastructure/persistence/TarbilDiseaseRepositoryAdapter.java`:

```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class TarbilDiseaseRepositoryAdapter implements TarbilDiseaseRepository {

    private final TarbilDiseaseJpaRepository jpaRepository;

    @Override
    public List<TarbilDisease> findAllOrdered() {
        return jpaRepository.findAllByOrderBySortOrderAsc();
    }
}
```

`$M/application/dto/TarbilDiseaseSummary.java`:

```java
package com.vetos.modules.integration.tarbil.application.dto;

import java.util.UUID;

public record TarbilDiseaseSummary(UUID id, UUID parentId, String name, String path, boolean selectable) {}
```

`$M/application/ListTarbilDiseasesUseCase.java`:

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** TARBIL hastalik agaci; recete formu ve eklenti eslestirmesi icin "KATEGORI > AD" yolu ile. */
@Service
@RequiredArgsConstructor
public class ListTarbilDiseasesUseCase {

    private final TarbilDiseaseRepository repository;

    @Transactional(readOnly = true)
    public List<TarbilDiseaseSummary> execute() {
        List<TarbilDisease> all = repository.findAllOrdered();
        Map<UUID, TarbilDisease> byId = all.stream().collect(Collectors.toMap(TarbilDisease::getId, Function.identity()));
        return all.stream().map(d -> {
            TarbilDisease parent = d.getParentId() == null ? null : byId.get(d.getParentId());
            String path = parent == null ? d.getName() : parent.getName() + " > " + d.getName();
            return new TarbilDiseaseSummary(d.getId(), d.getParentId(), d.getName(), path, d.isSelectable());
        }).toList();
    }
}
```

`$M/api/dto/TarbilDiseaseResponse.java`:

```java
package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;

import java.util.UUID;

public record TarbilDiseaseResponse(UUID id, UUID parentId, String name, String path, boolean selectable) {
    public static TarbilDiseaseResponse from(TarbilDiseaseSummary s) {
        return new TarbilDiseaseResponse(s.id(), s.parentId(), s.name(), s.path(), s.selectable());
    }
}
```

`$M/api/TarbilController.java`: import'lara `com.vetos.modules.integration.tarbil.api.dto.TarbilDiseaseResponse` ve `com.vetos.modules.integration.tarbil.application.ListTarbilDiseasesUseCase` ekle; alan listesine `private final ListTarbilDiseasesUseCase listTarbilDiseasesUseCase;` ekle; `mappings()` metodundan önce ekle:

```java
    @GetMapping("/diseases")
    public List<TarbilDiseaseResponse> diseases() {
        return listTarbilDiseasesUseCase.execute().stream().map(TarbilDiseaseResponse::from).toList();
    }
```

- [ ] **Step 5: Migration'ı yaz (73 düğüm, spec Ek A)**

`backend/src/main/resources/db/migration/V63__tarbil_disease.sql`:

```sql
-- TARBIL recete hastalik agaci (2026-10-04 canli okuma; spec 2026-10-04 Ek A). Kuresel referans, kiraci yok.
-- id = TARBIL'deki dugum degeri; kok kategoriler secilemez (METABOLIZMA HASTALIKLARI haric, o kendisi yaprak).
CREATE TABLE tarbil_disease (
    id          UUID PRIMARY KEY,
    parent_id   UUID REFERENCES tarbil_disease (id),
    name        TEXT NOT NULL,
    selectable  BOOLEAN NOT NULL,
    sort_order  INT NOT NULL
);

INSERT INTO tarbil_disease (id, parent_id, name, selectable, sort_order) VALUES
    ('2045c974-fb2a-40cb-9785-c2fb2c4da175', NULL, 'İHBARI ZORUNLU HASTALIKLAR', FALSE, 1),
    ('8672192e-a427-45a4-9ec0-2fbdcf86db39', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'AT VEBASI', TRUE, 2),
    ('74660e2e-ebd2-46b2-b921-9b65d9367655', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'ATLARIN ENFEKSİYÖZ ANEMİSİ', TRUE, 3),
    ('aa339b22-75b9-4adb-998d-1402c097e5a1', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'BRUCELLOZİS', TRUE, 4),
    ('0ee1cf24-1f47-4dea-b27a-9666cffd8c4d', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'TÜBERKÜLOZ', TRUE, 5),
    ('2b6f17cc-7211-4150-9a82-108fbaa0acd0', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'ŞARBON (Antrax)', TRUE, 6),
    ('070e3ece-71c1-4631-b097-44cb37a91fcd', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'ŞAP', TRUE, 7),
    ('7fab27c1-2d14-4e7a-8a35-c815f6512313', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'SIĞIR VEBASI', TRUE, 8),
    ('7c6c9108-7ba5-46b0-b9f3-69c00e1b3150', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'MAVİDİL', TRUE, 9),
    ('fc803b18-cd6e-4209-85bf-a19493481e46', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'KUDUZ', TRUE, 10),
    ('b1bbad5e-9a18-4d52-8440-154b1d1ca2b5', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'NAKLEDİLEBİLİR SÜNGERİMSİ BEYİN HASTALIKLARI (BSE, FSE, Scrapie)', TRUE, 11),
    ('0854d753-42ae-4aa5-b46c-cf50f38dbce5', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'SIĞIRLARIN NODÜLER EKZANTEMİ (Lumpy skin)', TRUE, 12),
    ('854b2e2c-b88d-4a08-9d81-ccc25119c291', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'EPİZOOTİC HAEMORRHAGİC DİSEASE (EHD)', TRUE, 13),
    ('6702cb58-64d3-4ecd-b567-4aff0671d137', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'KOYUN KEÇİ ÇİÇEĞİ', TRUE, 14),
    ('bda44beb-01a3-4e37-91eb-589ec4f37dfb', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'KOYUN KEÇİ VEBASI', TRUE, 15),
    ('47f2379f-e3eb-4bf8-850b-18422f187633', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'NEWCASTLE (Yalancı Tavuk Vebası)', TRUE, 16),
    ('4b9e1d40-c15e-4552-9eaa-e1756027d7e8', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'TAVUK VEBASI (Avıan İnfluenza-Kuş Gribi)', TRUE, 17),
    ('bee7b6d3-09bb-4d9b-96f6-5fd901f003dd', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'ARILARIN AMERİKAN YAVRU ÇÜRÜKLÜĞÜ', TRUE, 18),
    ('f389d0ca-f98c-4a99-8f11-07d51510345d', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'KÜÇÜK KOVAN KURDU (Aethina Tumida)', TRUE, 19),
    ('1252661e-2a44-43f1-b9f0-cc4e7e95736b', '2045c974-fb2a-40cb-9785-c2fb2c4da175', 'TROPILAELAPS AKARI (Tropılaelaps Mıte)', TRUE, 20),
    ('64a8bb52-5e35-4364-8f5f-3f70bd20cba9', NULL, 'SOLUNUM SİSTEMİ HASTALIKLARI', FALSE, 21),
    ('305a8aa5-ee45-4212-914a-8532856a160c', '64a8bb52-5e35-4364-8f5f-3f70bd20cba9', 'BAKTERİYEL HASTALIKLAR', TRUE, 22),
    ('6995708e-c3b4-42f3-9ac0-2be848ac79ad', '64a8bb52-5e35-4364-8f5f-3f70bd20cba9', 'VİRAL HASTALIKLAR', TRUE, 23),
    ('2fb4adaf-b728-4033-863c-42c2ee0aef30', '64a8bb52-5e35-4364-8f5f-3f70bd20cba9', 'PARAZİTER HASTALIKLAR', TRUE, 24),
    ('cd64dbcd-7960-486e-8e00-ac5e2bd6e8de', '64a8bb52-5e35-4364-8f5f-3f70bd20cba9', 'ENFEKSİYÖZ OLMAYAN HASTALIKLAR', TRUE, 25),
    ('dc8ed43d-5a0e-4d34-8a85-e3d8914311f0', '64a8bb52-5e35-4364-8f5f-3f70bd20cba9', 'OPERATİF MÜDAHALELER', TRUE, 26),
    ('c8e7c024-0ac1-4055-a899-5a7b9ccfa29a', '64a8bb52-5e35-4364-8f5f-3f70bd20cba9', 'DİĞER', TRUE, 27),
    ('53fe7807-e179-4a57-b4c2-fb5d59f1882d', NULL, 'SİNDİRİM SİSTEMİ HASTALIKLARI', FALSE, 28),
    ('ccbbf4f5-4c76-49ba-ab78-0543eba881b5', '53fe7807-e179-4a57-b4c2-fb5d59f1882d', 'BAKTERİYEL HASTALIKLAR', TRUE, 29),
    ('68d52735-6c87-4275-b277-7e19dc549260', '53fe7807-e179-4a57-b4c2-fb5d59f1882d', 'VİRAL HASTALIKLAR', TRUE, 30),
    ('95860589-0057-42cc-8209-663e1e459594', '53fe7807-e179-4a57-b4c2-fb5d59f1882d', 'PARAZİTER HASTALIKLAR', TRUE, 31),
    ('8f3c7ae9-cbf3-4d03-b017-5ba2082f4cfc', '53fe7807-e179-4a57-b4c2-fb5d59f1882d', 'PROTOZOER HASTALIKLAR', TRUE, 32),
    ('b785f4f1-bc5b-4ba5-b4d9-13bc35d973ee', '53fe7807-e179-4a57-b4c2-fb5d59f1882d', 'ENFEKSİYÖZ OLMAYAN HASTALIKLAR', TRUE, 33),
    ('e7c69496-1b07-4d4f-9663-968e0b1f730b', '53fe7807-e179-4a57-b4c2-fb5d59f1882d', 'OPERATİF MÜDAHALELER', TRUE, 34),
    ('426e99fc-99c9-4544-8bbd-3d212136c0c5', '53fe7807-e179-4a57-b4c2-fb5d59f1882d', 'DİĞER', TRUE, 35),
    ('bf8a4ebf-6827-46df-940e-4947875b1cb7', NULL, 'ÜRO-GENİTAL SİSTEM HASTALIKLARI', FALSE, 36),
    ('540c15fd-27e9-4b83-82da-c7ffbd6f6098', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'BAKTERİYEL HASTALIKLAR', TRUE, 37),
    ('33994353-e989-49f5-a08a-5d50ed8b22b5', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'VİRAL HASTALIKLAR', TRUE, 38),
    ('04277d12-f81c-4fa2-a04f-a31d5899d1ed', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'ENFEKSİYÖZ OLMAYAN HASTALIKLAR', TRUE, 39),
    ('263b994a-c91a-484b-b1c0-9042076941b2', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'DOĞUM', TRUE, 40),
    ('cd11bccb-2e4f-4b1d-aea4-2c8b0ba27c03', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'OPERATİF MÜDAHALELER', TRUE, 41),
    ('3f98a99c-36c7-4545-93d0-3dd1cdfeada8', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'MASTİTİS', TRUE, 42),
    ('1d985c05-a3ce-423d-97f3-8e0634ef5504', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'İNFERTİLİTE (KISIRLIK)', TRUE, 43),
    ('dc0944b2-78fa-4f84-88a9-099d49d747f9', 'bf8a4ebf-6827-46df-940e-4947875b1cb7', 'DİĞER', TRUE, 44),
    ('dda7c9cc-840f-41c8-8e2c-acab91c95f46', NULL, 'SİNİR SİSTEMİ HASTALIKLARI', FALSE, 45),
    ('740f11aa-e184-4b3a-850a-0f9216f6978c', 'dda7c9cc-840f-41c8-8e2c-acab91c95f46', 'MERKEZİ SİNİR SİSTEMİ HASTALIKLARI', TRUE, 46),
    ('935fed74-f92c-489e-a5b0-c5b12e30905c', 'dda7c9cc-840f-41c8-8e2c-acab91c95f46', 'PERİFER SİNİR SİSTEMİ HASTALIKLARI', TRUE, 47),
    ('a2e584a4-db63-450d-b9c1-7c694c256f3a', 'dda7c9cc-840f-41c8-8e2c-acab91c95f46', 'DİĞER', TRUE, 48),
    ('6fb53b79-3e4a-403a-90f3-56c3b3ad4e00', NULL, 'KAS ve İSKELET SİSTEMİ HASTALIKLARI', FALSE, 49),
    ('8ccba8ac-f6ce-4c77-b28a-4aea79bc0164', '6fb53b79-3e4a-403a-90f3-56c3b3ad4e00', 'AYAK HASTALIKLARI', TRUE, 50),
    ('1407dc5c-67c9-43f3-9ea3-50213f793598', '6fb53b79-3e4a-403a-90f3-56c3b3ad4e00', 'CERRAHİ MÜDAHALELER', TRUE, 51),
    ('0a3ef696-7117-4038-a602-a9eed9d0c393', NULL, 'KARDİYO-VASKÜLER SİSTEM HASTALIKLARI', FALSE, 52),
    ('e74c195a-096b-4a66-87f8-4e8e53cb86e4', '0a3ef696-7117-4038-a602-a9eed9d0c393', 'KALP', TRUE, 53),
    ('830a44c4-50b4-4953-a49f-a578c4f66ce2', '0a3ef696-7117-4038-a602-a9eed9d0c393', 'DOLAŞIM SİSTEMİ HASTALIKLARI', TRUE, 54),
    ('76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', NULL, 'DERİ HASTALIKLARI', FALSE, 55),
    ('37d8c374-d26d-416d-810b-888d049a7f66', '76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', 'BAKTERİYEL HASTALIKLAR', TRUE, 56),
    ('3b385985-5dbc-4df0-882b-a400992b04c4', '76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', 'VİRAL HASTALIKLAR', TRUE, 57),
    ('e8d86353-2e29-4104-b7a6-0be51f84fedc', '76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', 'PARAZİTER HASTALIKLAR', TRUE, 58),
    ('002f8edc-833a-4f6a-88f2-51e4f99de340', '76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', 'MANTAR HASTALIKLARI', TRUE, 59),
    ('02aa1e8a-da67-42ff-8c75-4dc5dd512fde', '76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', 'ENFEKSİYÖZ OLMAYAN HASTALIKLAR', TRUE, 60),
    ('bccd89cb-a9f2-45b5-8a6b-0c995276ce52', '76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', 'OPERATİF MÜDAHALELER', TRUE, 61),
    ('00524170-ea54-41d7-bff8-3b9f112cfe57', '76f634a7-9dbc-4f8b-b8a0-411b71f40fb9', 'DİĞER', TRUE, 62),
    ('0e859585-e0b9-4d0b-8192-d0436f9ad81b', NULL, 'DUYU SİSTEMİ HASTALIKLARI', FALSE, 63),
    ('ebb7684d-3d48-4c8d-9c7d-9467df465894', '0e859585-e0b9-4d0b-8192-d0436f9ad81b', 'GÖZ HASTALIKLARI', TRUE, 64),
    ('754a5a5d-7e33-404f-b357-203aebf4d3ca', '0e859585-e0b9-4d0b-8192-d0436f9ad81b', 'KULAK HASTALIKLARI', TRUE, 65),
    ('afe6153c-f045-4c51-a13d-e4b2e6ffbb0e', NULL, 'METABOLİZMA HASTALIKLARI', TRUE, 66),
    ('29377d15-17b4-4a87-9f5d-606e98770e64', NULL, 'ARI HASTALIKLARI', FALSE, 67),
    ('c542ad38-a403-4cc8-b022-141435234cc9', '29377d15-17b4-4a87-9f5d-606e98770e64', 'PARAZİTER HASTALIKLAR', TRUE, 68),
    ('be78a762-099e-4fc4-b47d-2a470ee94ec3', '29377d15-17b4-4a87-9f5d-606e98770e64', 'BAKTERİYEL HASTALIKLAR', TRUE, 69),
    ('2ad4fe05-2af3-44a3-ad6a-3f862367b915', NULL, 'BALIK HASTALIKLARI', FALSE, 70),
    ('e41d3138-b934-481d-8929-5d20f901fd4d', '2ad4fe05-2af3-44a3-ad6a-3f862367b915', 'BAKTERİYEL HASTALIKLAR', TRUE, 71),
    ('1ccf1aa3-3ad1-4160-81e4-b2dcd18d5331', '2ad4fe05-2af3-44a3-ad6a-3f862367b915', 'PARAZİTER HASTALIKLAR', TRUE, 72),
    ('770d390d-4caa-4675-87a1-cd7e91aeeb65', '2ad4fe05-2af3-44a3-ad6a-3f862367b915', 'DİĞER', TRUE, 73);
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd backend && ./mvnw -q test -Dtest='LearnTarbilMappingUseCaseTest,ListTarbilDiseasesUseCaseTest,TarbilExtensionSecurityIntegrationTest' > /tmp/t2b.log 2>&1; echo exit=$?; grep -E "Tests run:|FAIL" /tmp/t2b.log | tail -4`
Expected: `exit=0`.

- [ ] **Step 7: Tüm backend testleri ve modül sınırları**

Run: `cd backend && ./mvnw -q test > /tmp/t2c.log 2>&1; echo exit=$?`
Expected: `exit=0` (`ApplicationModulesTest` dahil; surefire toplamında `failures 0 errors 0`).

- [ ] **Step 8: Commit**

```bash
git add -A backend/src
git commit -m "feat(tarbil): esletirme turleri (DISEASE, DRUG_ROUTE, STOCK_PRODUCT) ve TARBIL hastalik agaci + GET /tarbil/diseases"
```

---

### Task 3: Eklenti — dosyaların `core/ steps/ pages/ selectors/` yapısına taşınması

**Files:**
- Create: `extension/scripts/move-tarbil-modules.mjs` (tek seferlik; task sonunda silinir)
- Move: `src/tarbil/{bridge,card,views,home}` → `core/`; `{animalRows,species}` → `steps/`; `searchFlow` → `steps/findAnimal`; `receiptFlow` → `pages/vaccineReceipt`; `selectors` → `selectors/shared` (testleriyle birlikte)
- Create: `src/tarbil/selectors/vaccineReceipt.ts`, `src/tarbil/selectors/index.ts`
- Modify: taşınan dosyaların ve `content.ts`, `page/ops.ts`, `page/main.ts`, `background/tarbilTab.ts(+test)`'in import yolları

**Interfaces:**
- Produces: dışa aktarılan adlar **değişmez** (`runSearchFlow`, `createReceiptFlow`, `RECEIPT`, `SEARCH`, `pageKind`, …); yalnız dosya yolları değişir. `selectors/index.ts` hepsini yeniden dışa aktarır, böylece `'./selectors'` / `'../selectors'` importları çalışmaya devam eder.

- [ ] **Step 1: Taşıma betiğini yaz**

`extension/scripts/move-tarbil-modules.mjs`:

```js
// Tek seferlik: src/tarbil dosyalarini yeni klasorlere tasir ve goreli importlari yeniden yazar.
import { execSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

const SRC = resolve('src');
const T = join(SRC, 'tarbil');
const MOVES = {
  'tarbil/bridge': 'tarbil/core/bridge',
  'tarbil/bridge.test': 'tarbil/core/bridge.test',
  'tarbil/card': 'tarbil/core/card',
  'tarbil/card.test': 'tarbil/core/card.test',
  'tarbil/views': 'tarbil/core/views',
  'tarbil/home': 'tarbil/core/home',
  'tarbil/home.test': 'tarbil/core/home.test',
  'tarbil/animalRows': 'tarbil/steps/animalRows',
  'tarbil/animalRows.test': 'tarbil/steps/animalRows.test',
  'tarbil/species': 'tarbil/steps/species',
  'tarbil/species.test': 'tarbil/steps/species.test',
  'tarbil/searchFlow': 'tarbil/steps/findAnimal',
  'tarbil/searchFlow.test': 'tarbil/steps/findAnimal.test',
  'tarbil/receiptFlow': 'tarbil/pages/vaccineReceipt',
  'tarbil/receiptFlow.test': 'tarbil/pages/vaccineReceipt.test',
  'tarbil/selectors': 'tarbil/selectors/shared',
  'tarbil/selectors.test': 'tarbil/selectors/shared.test',
};

const walk = (dir) => readdirSync(dir).flatMap((n) => {
  const p = join(dir, n);
  return statSync(p).isDirectory() ? walk(p) : /\.(ts|tsx)$/.test(n) ? [p] : [];
});
const key = (abs) => relative(SRC, abs).replace(/\\/g, '/').replace(/\.(ts|tsx)$/, '');

// 1) Once tum dosyalarin yeni yerini hesapla.
const files = walk(SRC);
const newPathOf = (abs) => {
  const k = key(abs);
  return MOVES[k] ? join(SRC, MOVES[k] + abs.slice(abs.lastIndexOf('.'))) : abs;
};
// 2) Importlari, hedefin YENI yerine gore yeniden yaz (dosyanin kendi yeni yerinden).
for (const abs of files) {
  const from = newPathOf(abs);
  const src = readFileSync(abs, 'utf8').replace(/from '(\.{1,2}\/[^']+)'/g, (m, spec) => {
    const targetKey = key(resolve(dirname(abs), spec) + '.ts');
    // selectors.ts klasore donusuyor: importlar barrel'e (selectors/index.ts) gitsin, shared.ts'e degil
    // (RECEIPT bir sonraki adimda selectors/vaccineReceipt.ts'e ayrilacak).
    const movedKey = targetKey === 'tarbil/selectors' ? targetKey : (MOVES[targetKey] ?? targetKey);
    let rel = relative(dirname(from), join(SRC, movedKey)).replace(/\\/g, '/');
    if (!rel.startsWith('.')) rel = './' + rel;
    return `from '${rel}'`;
  });
  writeFileSync(abs, src);
}
// 3) Dosyalari git ile tasi.
for (const [oldK, newK] of Object.entries(MOVES)) {
  const oldAbs = join(SRC, oldK + '.ts');
  if (!existsSync(oldAbs)) throw new Error('Yok: ' + oldAbs);
  execSync(`git mv "${oldAbs}" "${join(SRC, newK + '.ts')}"`, { stdio: 'inherit', shell: true });
}
console.log('tasindi:', Object.keys(MOVES).length);
```

- [ ] **Step 2: Klasörleri oluştur ve betiği çalıştır**

Run: `cd extension && mkdir -p src/tarbil/core src/tarbil/steps src/tarbil/pages src/tarbil/selectors && node scripts/move-tarbil-modules.mjs && git status --short src | head -30`
Expected: `tasindi: 17`; `git status` 17 yeniden adlandırma (`R`) ve import değişiklikleri gösterir.

**Not:** `selectors` importları bilerek klasöre (`'./selectors'`, `'../selectors'`) yönlendirilir; bu yol bir sonraki adımda eklenen `selectors/index.ts` barrel'ine çözülür. Step 3 bitene kadar `tsc` hata verebilir — beklenen.

- [ ] **Step 3: `RECEIPT`'i ayrı dosyaya böl ve barrel ekle**

`src/tarbil/selectors/shared.ts` içinden `RECEIPT` sabitinin tamamını (yorumuyla) **kes** ve şu dosyaya koy:

`src/tarbil/selectors/vaccineReceipt.ts`:

```ts
// "Asi Uygulama Belgesi Ekle" sayfasina ozgu id sonekleri (spec 2026-10-02 S3, 2026-10-04 S3).

/** Asi Uygulama Belgesi Ekle sayfasi. */
export const RECEIPT = {
  date: '_cntVACCINEBodyContent_dpApplicationDate',
  animalType: '_cntVACCINEBodyContent_cbxAnimalType',
  petVet: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet',
  animalGrid: '_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00',
  // Onayla butonlari: eklenti bunlara ASLA basmaz, yalniz hekimin tiklamasini fark eder.
  insertButtons: ['_cntVACCINEBodyContent_btnInsert', '_cntVACCINEBodyContent_btnInsert2'],
  // Bildirim panelleri her yuklemede bos olarak DOM'da; yalniz metinli basari paneli sayilir (2026-10-04 canli dogrulama).
  successPanel: '_UCVACCINENotification_pnlNotifiSuccess',
} as const;
```

`src/tarbil/selectors/index.ts`:

```ts
export * from './shared';
export * from './vaccineReceipt';
```

`selectors/shared.ts`'in kalan içeriğini kontrol et: `TARBIL_ORIGIN`, `VACCINE_PAGE_URL`, `ANIMAL_TYPE`, `SEARCH`, `bySuffix`, `PageKind`, `pageKind` kalmalı.

- [ ] **Step 4: Tip kontrolü, testler ve derleme**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "Test Files|Tests " && npm run build:dev > /tmp/x3.log 2>&1; echo build=$?`
Expected: tip hatası yok; `Tests 90 passed (90)`; `build=0`.

- [ ] **Step 5: Betiği sil ve commit**

```bash
rm -f extension/scripts/move-tarbil-modules.mjs
git add -A extension/src
git commit -m "refactor(tarbil-ext): core/steps/pages/selectors klasor yapisi (davranis ayni)"
```

---

### Task 4: Eklenti — buton izin listesi (`clickAllowed`)

**Files:**
- Create: `extension/src/tarbil/selectors/allowlist.ts`, `extension/src/tarbil/selectors/allowlist.test.ts`
- Modify: `extension/src/tarbil/selectors/index.ts`, `extension/src/tarbil/page/telerik.ts` (`PageErrorCode`), `extension/src/tarbil/page/ops.ts`, `extension/src/tarbil/page/ops.test.ts`, `extension/src/tarbil/pages/vaccineReceipt.ts`, `extension/src/tarbil/pages/vaccineReceipt.test.ts`, `extension/src/tarbil/steps/findAnimal.ts`, `extension/src/tarbil/steps/findAnimal.test.ts`

**Interfaces:**
- Consumes: `RECEIPT` (`selectors/vaccineReceipt.ts`), `SEARCH` (`selectors/shared.ts`) — Task 3.
- Produces:
  - `ALLOWED_BUTTONS: { vaccineReceipt: { petVet }, animalSearch: { search, transfer } }`
  - `FORBIDDEN_BUTTON_PATTERNS: RegExp[]`
  - `allowedButtonSuffix(page: string, button: string): string | null`
  - MAIN komutu `clickAllowed({ page, button })` (eski `clickPetVet` ve `transfer` kalkar); `PageErrorCode` + `'NOT_ALLOWED'`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/tarbil/selectors/allowlist.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { ALLOWED_BUTTONS, FORBIDDEN_BUTTON_PATTERNS, allowedButtonSuffix } from './allowlist';
import { RECEIPT } from './vaccineReceipt';

describe('button allowlist', () => {
  it('contains no button that finalizes an official record', () => {
    const all = Object.values(ALLOWED_BUTTONS).flatMap((page) => Object.values(page));
    for (const suffix of all) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(false);
    }
  });

  it('treats the vaccine page Onayla buttons as forbidden', () => {
    for (const suffix of RECEIPT.insertButtons) {
      expect(FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)), suffix).toBe(true);
    }
  });

  it('resolves only listed page/button pairs', () => {
    expect(allowedButtonSuffix('vaccineReceipt', 'petVet')).toBe(RECEIPT.petVet);
    expect(allowedButtonSuffix('vaccineReceipt', 'insert')).toBeNull();
    expect(allowedButtonSuffix('nope', 'petVet')).toBeNull();
    expect(allowedButtonSuffix('vaccineReceipt', 'toString')).toBeNull();
  });
});
```

`extension/src/tarbil/page/ops.test.ts` dosyasının `describe('page ops', () => {` bloğunun sonuna ekle:

```ts
  it('clicks an allowlisted button by page and key', async () => {
    const ID = 'ctl00_X_ReceiptAddOtherAnimal_RadOtherAnimal_ctl00_ctl02_ctl00_bntPetVet';
    document.body.innerHTML = `<a id="${ID}"></a>`;
    const prm = instantPrm();
    let clicked = 0;
    const env: TelerikEnv = { doc: document, find: (id) => (id === ID ? { click: () => { clicked++; prm.fire(); } } : null), prm: () => prm, isReady: () => true };

    await createPageOps(env).clickAllowed({ page: 'vaccineReceipt', button: 'petVet' });

    expect(clicked).toBe(1);
  });

  it('refuses to click Onayla even when asked through the bridge', async () => {
    const ID = 'ctl00_X_cntVACCINEBodyContent_btnInsert';
    document.body.innerHTML = `<a id="${ID}"></a>`;
    let clicked = 0;
    const env: TelerikEnv = { doc: document, find: () => ({ click: () => { clicked++; } }), prm: () => instantPrm(), isReady: () => true };
    const ops = createPageOps(env);

    await expect(ops.clickAllowed({ page: 'vaccineReceipt', button: 'insert' })).rejects.toMatchObject({ code: 'NOT_ALLOWED' });
    expect(ops).not.toHaveProperty('clickPetVet');
    expect(ops).not.toHaveProperty('transfer');
    expect(clicked).toBe(0);
  });
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/tarbil/selectors/allowlist.test.ts src/tarbil/page/ops.test.ts 2>&1 | grep -E "resolve|×|Tests "`
Expected: `./allowlist` çözümlenemiyor; ops testlerinde `clickAllowed is not a function`.

- [ ] **Step 3: İzin listesini yaz**

`extension/src/tarbil/selectors/allowlist.ts`:

```ts
import { SEARCH } from './shared';
import { RECEIPT } from './vaccineReceipt';

/**
 * Eklentinin basabilecegi butonlarin TAMAMI (spec 2026-10-04 S2, S7.1). Burada olmayan butona MAIN dunya basmaz.
 * Resmi kaydi tamamlayan butonlar (Onayla, Receteyi Onayla, Urun Kabul Onayla/Reddet, cikis) bu listeye GIREMEZ.
 */
export const ALLOWED_BUTTONS = {
  vaccineReceipt: { petVet: RECEIPT.petVet },
  animalSearch: { search: SEARCH.search, transfer: SEARCH.transfer },
} as const;

export const FORBIDDEN_BUTTON_PATTERNS: readonly RegExp[] = [/btnInsert2?$/i, /btnApprove$/i, /btnReject$/i, /exit/i];

export function allowedButtonSuffix(page: string, button: string): string | null {
  const buttons = (ALLOWED_BUTTONS as Record<string, Record<string, string>>)[page];
  if (!buttons || !Object.prototype.hasOwnProperty.call(ALLOWED_BUTTONS, page)) return null;
  if (!Object.prototype.hasOwnProperty.call(buttons, button)) return null;
  const suffix = buttons[button];
  return FORBIDDEN_BUTTON_PATTERNS.some((re) => re.test(suffix)) ? null : suffix;
}
```

`extension/src/tarbil/selectors/index.ts` sonuna ekle:

```ts
export * from './allowlist';
```

- [ ] **Step 4: MAIN komutlarını izin listesine bağla**

`extension/src/tarbil/page/telerik.ts` içinde `PageErrorCode` satırını şununla değiştir:

```ts
export type PageErrorCode = 'NOT_FOUND' | 'OPTION_NOT_FOUND' | 'AJAX_TIMEOUT' | 'AJAX_ERROR' | 'NOT_READY' | 'BAD_INPUT' | 'NOT_ALLOWED';
```

`extension/src/tarbil/page/ops.ts` (tümüyle değiştir; `checkRow` ve tür seçimi Faz 2a düzeltmeleriyle aynı kalır):

```ts
import type { PageHandler } from '../core/bridge';
import { RECEIPT, SEARCH, allowedButtonSuffix, bySuffix } from '../selectors';
import { PageError, clickButton, clickElement, selectComboValue, setDate, setText, waitUntil, type TelerikEnv } from './telerik';

/**
 * Koprudan cagrilan komutlar. Butonlara yalniz clickAllowed ile, selectors/allowlist.ts'teki listeden basilir;
 * Onayla/Kaydet/cikis gibi resmi kaydi tamamlayan butonlar listede olamaz (spec 2026-10-04 S2, S7.1).
 */
export function createPageOps(env: TelerikEnv): Record<string, PageHandler> {
  const allowed = (page: string, button: string): string => {
    const suffix = allowedButtonSuffix(page, button);
    if (!suffix) throw new PageError('NOT_ALLOWED', `İzin listesinde olmayan buton: ${page}.${button}`);
    return suffix;
  };
  return {
    ready: () => waitUntil(env.isReady, 10_000),
    setDate: ({ iso }: { iso: string }) => setDate(env, RECEIPT.date, iso),
    // Tur postback'i tamamlanmissa PetVet butonu vardir; yoksa secim istemcide kalmis demektir, zorla yeniden sec.
    selectAnimalType: ({ value }: { value: string }) =>
      selectComboValue(env, RECEIPT.animalType, value, !env.doc.querySelector(bySuffix(RECEIPT.petVet))),
    clickAllowed: async ({ page, button }: { page: string; button: string }) => clickButton(env, allowed(page, button)),
    searchChip: async ({ chip }: { chip: string }) => {
      setText(env, SEARCH.chip, chip);
      await clickButton(env, allowed('animalSearch', 'search'));
    },
    // Yalniz arama tablosundaki satir kutulari: id ile herhangi bir oge (ornegin Onayla) tiklanamaz.
    checkRow: ({ checkboxId }: { checkboxId: string }) => {
      const box = env.doc.getElementById(checkboxId);
      if (!box || !box.matches(`table${bySuffix(SEARCH.grid)} input[type="checkbox"]`)) {
        return Promise.reject(new PageError('NOT_FOUND', 'Arama sonucunda böyle bir satır kutusu yok'));
      }
      return clickElement(env, checkboxId);
    },
  };
}
```

**Not:** Task 3'ten sonra `ops.ts`'in import yolu `'../core/bridge'` olmalı; betik bunu zaten yazdıysa aynen kalır.

- [ ] **Step 5: Çağıranları yeni komuta geçir**

`extension/src/tarbil/pages/vaccineReceipt.ts` içinde:

```ts
    await d.bridge.call('clickPetVet');
```
satırını şununla değiştir:
```ts
    await d.bridge.call('clickAllowed', { page: 'vaccineReceipt', button: 'petVet' });
```

`extension/src/tarbil/steps/findAnimal.ts` içinde:

```ts
      await d.bridge.call('transfer', undefined, 5000).catch(() => undefined);
```
satırını şununla değiştir:
```ts
      await d.bridge.call('clickAllowed', { page: 'animalSearch', button: 'transfer' }, 5000).catch(() => undefined);
```

`extension/src/tarbil/pages/vaccineReceipt.test.ts` içinde `'fills date and species, then opens the PetVet search'` testinin beklentisini değiştir:

```ts
    expect(calls.map((c) => c.op)).toEqual(['ready', 'setDate', 'selectAnimalType', 'clickAllowed']);
    expect(calls[3].args).toEqual({ page: 'vaccineReceipt', button: 'petVet' });
```
(`calls[1]` ve `calls[2]` beklentileri aynı kalır.) Aynı dosyada `toContain('clickPetVet')` geçen beklentiyi `toContain('clickAllowed')` yap.

`extension/src/tarbil/steps/findAnimal.test.ts` içinde `'searches by chip, checks the single alive match and transfers it'` testinin ilk beklentisini değiştir:

```ts
    expect(calls.map((c) => c.op)).toEqual(['ready', 'searchChip', 'checkRow', 'clickAllowed']);
    expect(calls[3].args).toEqual({ page: 'animalSearch', button: 'transfer' });
```

- [ ] **Step 6: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "×|Test Files|Tests "`
Expected: tip hatası yok; `Tests 95 passed (95)` (90 + allowlist 3 + ops 2).

- [ ] **Step 7: Commit**

```bash
git add -A extension/src
git commit -m "feat(tarbil-ext): buton izin listesi - MAIN dunya yalniz listedeki butonlara basar, onay butonlari yasak"
```

---

### Task 5: Eklenti — akış durumunda belge türü ve ortak başarı yakalama (`core/success.ts`)

**Files:**
- Modify: `extension/src/shared/types.ts`, `extension/src/shared/flowStore.ts`, `extension/src/shared/flowStore.test.ts`, `extension/src/tarbil/pages/vaccineReceipt.ts`
- Create: `extension/src/tarbil/core/success.ts`, `extension/src/tarbil/core/success.test.ts`

**Interfaces:**
- Produces:
  - `type DocumentType = 'VACCINATION' | 'PRESCRIPTION' | 'STOCK_RECEIPT'` (`shared/types.ts`); `Submission.documentType?: DocumentType`
  - `FlowState.documentType: DocumentType`, `FlowState.stepData?: Record<string, unknown>`; `FlowStore.arm(submissionId, documentType?: DocumentType)` (varsayılan `'VACCINATION'`); `get()` eski kayıtlarda `documentType`'ı `'VACCINATION'` doldurur
  - `isConfirmClick(target: EventTarget | null, confirmSuffixes: readonly string[]): boolean`
  - `markStaleSuccess(doc: Document, panelSuffix: string, stale: WeakSet<Element>): void`
  - `hasFreshSuccess(doc: Document, panelSuffix: string, stale: WeakSet<Element>): boolean`

- [ ] **Step 1: Başarısız testleri yaz**

`extension/src/shared/flowStore.test.ts` içindeki `'arms a submission'` testini şununla değiştir ve dosyanın `describe` bloğunun sonuna iki test ekle:

```ts
  it('arms a submission', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('s1');
    expect(await flow.get()).toEqual({ submissionId: 's1', documentType: 'VACCINATION', step: 'armed', updatedAt: 1000 });
  });
```

```ts
  it('records the document type when arming', async () => {
    const flow = createFlowStore(memoryStore(), () => 1000);
    await flow.arm('r1', 'PRESCRIPTION');
    expect(await flow.get()).toMatchObject({ submissionId: 'r1', documentType: 'PRESCRIPTION', step: 'armed' });
  });

  it('treats a stored flow without documentType as a vaccination', async () => {
    const store = memoryStore();
    await store.set(FLOW_KEY, { submissionId: 'old', step: 'searching', updatedAt: 5 });
    const flow = createFlowStore(store, () => 10);

    expect(await flow.get()).toMatchObject({ submissionId: 'old', documentType: 'VACCINATION', step: 'searching' });
    expect(await flow.update('old', { stepData: { itemIndex: 1 } })).toMatchObject({ documentType: 'VACCINATION', stepData: { itemIndex: 1 } });
  });
```
ve import satırını `import { FLOW_KEY, createFlowStore } from './flowStore';` yap.

`extension/src/tarbil/core/success.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';
import { hasFreshSuccess, isConfirmClick, markStaleSuccess } from './success';

const CONFIRM = ['_cntVACCINEBodyContent_btnInsert', '_cntVACCINEBodyContent_btnInsert2'];
const PANEL = '_UCVACCINENotification_pnlNotifiSuccess';

describe('success capture', () => {
  it('recognizes a click inside an official confirm button', () => {
    document.body.innerHTML = '<a id="x_cntVACCINEBodyContent_btnInsert"><input id="in" type="button"></a><button id="other"></button>';
    expect(isConfirmClick(document.getElementById('in'), CONFIRM)).toBe(true);
    expect(isConfirmClick(document.getElementById('other'), CONFIRM)).toBe(false);
    expect(isConfirmClick(null, CONFIRM)).toBe(false);
  });

  it('ignores empty panels and panels that were there before the click', () => {
    document.body.innerHTML = `<div id="a${PANEL}"></div><div id="b${PANEL}">Eski mesaj</div>`;
    const stale = new WeakSet<Element>();
    expect(hasFreshSuccess(document, PANEL, stale)).toBe(true); // metinli b var, henuz stale degil
    markStaleSuccess(document, PANEL, stale);
    expect(hasFreshSuccess(document, PANEL, stale)).toBe(false);

    document.body.insertAdjacentHTML('beforeend', `<div id="c${PANEL}">Kaydedildi</div>`);
    expect(hasFreshSuccess(document, PANEL, stale)).toBe(true);
  });
});
```

- [ ] **Step 2: Testleri çalıştır, başarısız olduğunu gör**

Run: `cd extension && npx vitest run src/shared/flowStore.test.ts src/tarbil/core/success.test.ts 2>&1 | grep -E "resolve|×|Tests "`
Expected: `./success` çözümlenemiyor; flowStore testlerinde `documentType` beklentisi tutmuyor.

- [ ] **Step 3: Türü ve akış durumunu genişlet**

`extension/src/shared/types.ts` sonuna ekle ve `Submission` arayüzüne alan ekle:

```ts
export type DocumentType = 'VACCINATION' | 'PRESCRIPTION' | 'STOCK_RECEIPT';
```
`Submission` içinde `speciesMapping: Record<string, unknown> | null;` satırının altına:
```ts
  /** Backend P0'dan itibaren gelir; eski yanitlarda yoksa VACCINATION sayilir. */
  documentType?: DocumentType;
```

`extension/src/shared/flowStore.ts` (tümüyle değiştir):

```ts
import type { KeyValueStore } from '../background/chromeStorage';
import type { DocumentType } from './types';

/**
 * TARBIL doldurma akisinin durumu (chrome.storage.session, tarayici kapaninca silinir).
 * Ana sayfa ile ayri pencerede acilan TARBIL pencereleri (hayvan arama, stok) bu kayit uzerinden haberlesir.
 * stepData: belge turune ozel ara durum (ornegin recetede { itemIndex, awaiting }).
 */
export const FLOW_KEY = 'tarbilFlow';

export type FlowStep = 'armed' | 'filling' | 'searching' | 'transferred' | 'needsVet' | 'awaitingConfirm' | 'done' | 'error';

export interface FlowState {
  submissionId: string;
  documentType: DocumentType;
  step: FlowStep;
  updatedAt: number;
  message?: string;
  insertClickedAt?: number;
  redirectedAt?: number;
  stepData?: Record<string, unknown>;
}

export interface FlowStore {
  get(): Promise<FlowState | null>;
  arm(submissionId: string, documentType?: DocumentType): Promise<void>;
  update(submissionId: string, patch: Partial<Omit<FlowState, 'submissionId' | 'updatedAt'>>): Promise<FlowState | null>;
}

export function createFlowStore(store: KeyValueStore, now: () => number = Date.now): FlowStore {
  async function get(): Promise<FlowState | null> {
    const raw = await store.get<Partial<FlowState>>(FLOW_KEY);
    // Eklenti guncellemesinden once yazilmis kayitlarda belge turu yok: o zaman yalniz asi vardi.
    return raw ? ({ documentType: 'VACCINATION', ...raw } as FlowState) : null;
  }
  return {
    get,
    arm: (submissionId, documentType = 'VACCINATION') =>
      store.set(FLOW_KEY, { submissionId, documentType, step: 'armed', updatedAt: now() } satisfies FlowState),
    async update(submissionId, patch) {
      const current = await get();
      if (!current || current.submissionId !== submissionId) return null;
      // updatedAt adim zamanidir: yalniz adim degisince yenilenir (yonlendirme isareti bayat akisi tazelemesin).
      const next: FlowState = { ...current, ...patch, updatedAt: patch.step ? now() : current.updatedAt };
      await store.set(FLOW_KEY, next);
      return next;
    },
  };
}
```

- [ ] **Step 4: Başarı yakalamayı çekirdeğe çıkar**

`extension/src/tarbil/core/success.ts`:

```ts
import { bySuffix } from '../selectors/shared';

// Basari yakalamanin belge turunden bagimsiz kurallari (spec 2026-10-04 S7.1): basari = hekimin resmi onay
// butonuna tiklamasindan SONRA beliren METINLI basari paneli. TARBIL panelleri her yuklemede bos cizer.

export function isConfirmClick(target: EventTarget | null, confirmSuffixes: readonly string[]): boolean {
  const el = target as Element | null;
  if (!el || typeof el.closest !== 'function') return false;
  return confirmSuffixes.some((suffix) => el.closest(bySuffix(suffix)) !== null);
}

/** Onay tiklamasi aninda sayfada zaten olan paneller (onceki islemlerden kalma) sayilmaz. */
export function markStaleSuccess(doc: Document, panelSuffix: string, stale: WeakSet<Element>): void {
  doc.querySelectorAll(bySuffix(panelSuffix)).forEach((el) => stale.add(el));
}

export function hasFreshSuccess(doc: Document, panelSuffix: string, stale: WeakSet<Element>): boolean {
  return Array.from(doc.querySelectorAll(bySuffix(panelSuffix))).some(
    (el) => !stale.has(el) && (el.textContent ?? '').trim().length > 0,
  );
}
```

`extension/src/tarbil/pages/vaccineReceipt.ts` içinde:
1. import'lara ekle: `import { hasFreshSuccess, isConfirmClick, markStaleSuccess } from '../core/success';`
2. `evaluate` içindeki şu bloğu:
```ts
      // TARBIL bildirim panellerini her yuklemede BOS olarak cizer (2026-10-04 canli dogrulama); yalniz metinli panel sayilir.
      const freshSuccess = Array.from(d.doc.querySelectorAll(bySuffix(RECEIPT.successPanel))).some(
        (el) => !staleSuccess.has(el) && (el.textContent ?? '').trim().length > 0,
      );
```
şununla değiştir:
```ts
      const freshSuccess = hasFreshSuccess(d.doc, RECEIPT.successPanel, staleSuccess);
```
3. tıklama dinleyicisindeki şu iki satırı:
```ts
      if (!target?.closest || !RECEIPT.insertButtons.some((suffix) => target.closest(bySuffix(suffix)))) return;
      d.doc.querySelectorAll(bySuffix(RECEIPT.successPanel)).forEach((el) => staleSuccess.add(el));
```
şununla değiştir:
```ts
      if (!isConfirmClick(target, RECEIPT.insertButtons)) return;
      markStaleSuccess(d.doc, RECEIPT.successPanel, staleSuccess);
```
4. Artık kullanılmayan `const target = e.target as Element | null;` satırını `const target = e.target;` yap (tip `EventTarget | null`).

- [ ] **Step 5: Testleri çalıştır, geçtiğini gör**

Run: `cd extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "×|Test Files|Tests "`
Expected: tip hatası yok; `Tests 99 passed (99)` (95 + flowStore 2 + success 2).

- [ ] **Step 6: Commit**

```bash
git add -A extension/src
git commit -m "feat(tarbil-ext): akis durumunda belge turu/stepData, basari yakalama core/success'e"
```

---

### Task 6: Dokümantasyon, frontend tipi ve son doğrulama

**Files:**
- Modify: `docs/api-conventions.md`, `docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md`, `frontend/src/api/tarbilApi.ts`

**Interfaces:**
- Consumes: Task 1–5.

- [ ] **Step 1: API belgesini güncelle**

`docs/api-conventions.md` içinde TARBİL eklenti uçlarının listelendiği yeri bul (`grep -n "tarbil-extension" docs/api-conventions.md`) ve `GET /pending` satırını şu anlamla güncelle; web uçlarının listesine `GET /api/v1/tarbil/diseases` (ADMIN, VET) ekle:

```markdown
- `GET /api/v1/tarbil-extension/pending?type=VACCINATION|PRESCRIPTION|STOCK_RECEIPT` — `type` isteğe bağlı; yoksa tüm türler. Yanıtın her öğesinde `documentType` var.
- `GET /api/v1/tarbil/diseases` (ADMIN, VET) — TARBİL reçete hastalık ağacı: `[{id, parentId, name, path, selectable}]`, `sort_order` sırasıyla.
```

- [ ] **Step 2: Frontend tipine alanı ekle**

`grep -n "vaccinationRecordId" frontend/src/api/tarbilApi.ts` ile eklentiye/web'e dönen submission tipini bul; varsa o arayüze şu satırı ekle (yoksa adımı atla ve ledger'a not düş):

```ts
  documentType?: 'VACCINATION' | 'PRESCRIPTION' | 'STOCK_RECEIPT';
```

- [ ] **Step 3: Spec'e P0 tamamlandı notu**

`docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md` Bölüm 12 madde 1'in sonuna ekle:

```markdown
   **Uygulandı:** `docs/superpowers/plans/2026-10-04-tarbil-otomasyon-p0.md` (V62 `tarbil_submission`, V63 `tarbil_disease`, eklentide `core/steps/pages/selectors` + izin listesi).
```

- [ ] **Step 4: Son doğrulama (üç taraf)**

Run:
```bash
cd backend && ./mvnw -q test > /tmp/t6b.log 2>&1; echo backend=$?
cd ../extension && npx tsc --noEmit && npx vitest run 2>&1 | grep -E "Tests " && npm run build:dev > /tmp/t6e.log 2>&1; echo ext_build=$?; grep -c "console\." dist/content.js dist/page.js
cd ../frontend && npx tsc -b --noEmit; echo frontend=$?
```
Expected: `backend=0`; `Tests 99 passed (99)`; `ext_build=0`; `console.` sayıları `0`; `frontend=0`.

- [ ] **Step 5: Commit**

```bash
git add docs/api-conventions.md docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md frontend/src/api/tarbilApi.ts
git commit -m "docs(tarbil): P0 cekirdek - API belgesi, frontend tipi, spec notu"
```
