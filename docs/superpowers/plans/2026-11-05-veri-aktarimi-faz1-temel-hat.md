# Veri Aktarımı Faz 1 — Temel Hat (Sahip + Hasta) Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Platform admin, bir kliniğe Vetly standart Excel şablonuyla sahip ve hasta kayıtlarını önizleyip aktarabilsin, aktarımı geri alabilsin; karşılığı olmayan eski bilgiler kaybolmasın.

**Architecture:** Yeni Modulith modülü `com.vetos.modules.dataimport`. Excel → `ParsedWorkbook` → `ImportValidator` → `ImportReport` (önizleme, `import_batches` tablosunda JSONB) → arka plan işçisi (`ImportCommitWorker`) parça parça yazar → her yazılan/eşleşen kayıt `import_record_links` tablosuna (kaynak kimliği + parti + eski bilgiler) işlenir → geri alma bu tablodan çalışır. Diğer modüllere yalnızca açık (`NamedInterface("domain")`) arayüzlerden erişilir.

**Tech Stack:** Java 21 / Spring Boot 3.5.16, Spring Modulith, JPA (Hibernate 6.6, `@TenantId`), Flyway, PostgreSQL, Apache POI (`poi-ooxml` 5.4.1, yeni bağımlılık); React 18 + Vite + Vitest.

**Spec:** `docs/superpowers/specs/2026-11-05-eski-sistemden-veri-aktarimi-design.md`

**Kapsam:** Bu plan spec §7'deki Faz 1'dir: şablon + doğrulama/önizleme + parti + geri alma, yalnızca **sahip** ve **hasta**. Klinik kayıtları, para, stok, eşleştirme ekranı ve `.bak` aracı sonraki fazların ayrı planlarıdır. Bu fazın tabloları ve sözleşmeleri (`import_record_links`, `ImportEntityType`, `ImportReport`) onlar için genişletilebilir kurulur.

## Global Constraints

- **Commit yok, push yok.** Bütün değişiklikler IntelliJ changelist'inde kalır. Görevlerdeki "Commit" adımı yerine `git status` ile değişen dosyaları listele.
- Migration'lar geriye uyumlu olmalı: yalnızca yeni tablo ekle, mevcut sütunlara dokunma. Dosya adı: `V93__data_import.sql` (yazım anında bir sonraki boş numara). **Canlıya çıkmadan önce** canlıdaki `flyway_schema_history` kontrol edilir; başka geliştiriciler canlıya migration çıkarmış olabilir.
- Entegrasyon testleri, kullanıcının çalışan `:8080` backend'iyle **aynı geliştirme veritabanını** kullanır. Bu plandaki migration yalnızca tablo eklediği için çalışan backend'i bozmaz.
- Modül sınırı: `dataimport`, `patient` modülüne yalnızca `com.vetos.modules.patient.domain` (NamedInterface) üzerinden erişir. `ApplicationModulesTest` yeşil kalmalı.
- Kiracı kuralı: Bir kliniğe yazan her işlem, transaction **başlamadan önce** `TenantContext.set(tenantId)` ile çalışır ve `finally` içinde önceki bağlamı geri yükler (bkz. `TenantScopedTestSupport` yorumu).
- Admin uç noktaları `/api/v1/platform-admin/**` altında ve `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`.
- Yükleme sınırı: mevcut `max-file-size: 15MB` (değiştirilmez).
- Arayüz metinleri Türkçe.
- KVKK: Yüklenen dosya (`import_batches.source_file`) aktarım bitince, iptalde ve 7 gün dokunulmayan önizlemelerde `NULL` yapılır.

## Review Focus

1. **Telefon biçimleri:** `0532 123 45 67`, `+90 532 123 4567`, `5321234567` aynı mevcut sahiple eşleşmeli (Task 3, `should_matchExistingOwner_acrossPhoneFormats`).
2. **Excel tarih hücreleri:** Tarih hücresi sayısal Excel tarihi, `05.03.2019` metni, `2019-03-05` metni olarak gelebilir ya da boş olabilir. İlk üçü aynı tarihe çözülmeli; boş hücre `null` olmalı; okunamayan metin satır hatası vermeli (Task 2 ve Task 3 testleri).
3. **Türkçe büyük/küçük harf:** `köpek`, `KÖPEK`, `Kopek` → `Köpek` türüyle eşleşmeli (Task 3, `should_matchSpecies_turkishInsensitive`).
4. **Aynı dosya iki kez:** İkinci aktarım hiç yeni kayıt oluşturmamalı, satırları "daha önce aktarılmış" saymalı (Task 5, `should_createNothing_whenSameFileCommittedTwice`).
5. **Yarıda çöken aktarım:** İşçi 700. satırda hata alırsa o ana kadar yazılanlar geri alınmalı ve parti `FAILED` olmalı (Task 5, `should_rollbackPartialWrites_whenWorkerFails`).

---

## Dosya Yapısı

```
backend/src/main/java/com/vetos/modules/dataimport/
  package-info.java                         @ApplicationModule(displayName = "Data Import (Veri Aktarımı)")
  domain/
    ImportBatch.java, ImportBatchStatus.java, ImportBatchRepository.java
    ImportRecordLink.java, ImportEntityType.java, ImportRecordLinkRepository.java
    ImportReport.java (record + iç record'lar)
    ExistingDataLookup.java (port), ImportTargetWriter.java (port), ImportDependencyInspector.java (port)
  application/
    excel/ImportWorkbookParser.java, ImportTemplateWriter.java, ParsedWorkbook.java, PhoneNormalizer.java
    ImportValidator.java
    CreateImportPreviewUseCase.java, ListImportBatchesUseCase.java, DiscardImportUseCase.java
    StartImportCommitUseCase.java, ImportCommitWorker.java
    RollbackImportUseCase.java
    GetLegacyDataUseCase.java
    DiscardStaleImportPreviewsJob.java
  infrastructure/
    persistence/ (JPA repo + adapter'lar)
    PatientModuleImportAdapter.java (ExistingDataLookup + ImportTargetWriter)
    PgImportDependencyInspector.java
  api/
    PlatformAdminImportsController.java, LegacyDataController.java, dto/
backend/src/main/resources/db/migration/V93__data_import.sql
frontend/src/api/dataImportApi.ts
frontend/src/pages/platform-admin/TenantImportSection.tsx (+ .module.css, .test.tsx)
frontend/src/components/legacy/LegacyDataCard.tsx (+ .test.tsx)
```

---

### Task 1: Tablolar, varlıklar ve depolar

**Files:**
- Create: `backend/src/main/resources/db/migration/V93__data_import.sql`
- Create: `dataimport/package-info.java`, `domain/package-info.java`, `domain/ImportBatch.java`, `ImportBatchStatus.java`, `ImportBatchRepository.java`, `ImportRecordLink.java`, `ImportEntityType.java`, `ImportRecordLinkRepository.java`
- Create: `infrastructure/persistence/*JpaRepository.java`, `*RepositoryAdapter.java`
- Modify: `modules/platformadmin/domain/AuditAction.java`: `DATA_IMPORT_COMMITTED`, `DATA_IMPORT_ROLLED_BACK`, `DATA_IMPORT_FAILED` sabitleri
- Test: `backend/src/test/java/com/vetos/modules/dataimport/ImportPersistenceIntegrationTest.java`

**Interfaces:**
- Produces:
  - Tablo `import_batches`: `id UUID PK`, `tenant_id UUID NOT NULL REFERENCES tenants(id)`, `source_system TEXT NOT NULL`, `file_name TEXT NOT NULL`, `status TEXT NOT NULL`, `source_file BYTEA`, `report JSONB NOT NULL`, `total_rows INT NOT NULL DEFAULT 0`, `processed_rows INT NOT NULL DEFAULT 0`, `rejected_match_source_ids JSONB NOT NULL DEFAULT '[]'`, `error_message TEXT`, `created_by_admin_id UUID NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`, `committed_at TIMESTAMPTZ`, `rolled_back_at TIMESTAMPTZ`; index `(tenant_id, created_at DESC)`.
  - Tablo `import_record_links`: `id UUID PK`, `batch_id UUID NOT NULL REFERENCES import_batches(id)`, `tenant_id UUID NOT NULL`, `source_system TEXT NOT NULL`, `entity_type TEXT NOT NULL`, `source_id TEXT NOT NULL`, `target_id UUID NOT NULL`, `created_by_import BOOLEAN NOT NULL`, `legacy_data JSONB NOT NULL DEFAULT '{}'`, `created_at TIMESTAMPTZ NOT NULL`; `UNIQUE (tenant_id, source_system, entity_type, source_id)`; index `(tenant_id, entity_type, target_id)`; index `(batch_id)`.
  - `enum ImportBatchStatus { PREVIEWED, COMMITTING, COMMITTED, FAILED, ROLLED_BACK, DISCARDED }`
  - `enum ImportEntityType { OWNER, PATIENT }` (sonraki fazlar genişletir)
  - `ImportBatch`: `@TenantId` **yok** (platform admin varlığı). Fabrika `ImportBatch.preview(UUID tenantId, String sourceSystem, String fileName, byte[] file, ImportReport report, int totalRows, UUID adminId)`. Durum metotları: `startCommit(List<String> rejectedMatchSourceIds)` (yalnızca PREVIEWED'dan), `progress(int processedRows)`, `markCommitted()` (`source_file=null`), `markFailed(String message)` (`source_file=null`), `markRolledBack()` (yalnızca COMMITTED'dan), `discard()` (yalnızca PREVIEWED'dan, `source_file=null`). Geçersiz geçişte `IllegalStateException`. `report` alanı JSONB↔`ImportReport` dönüşümü (`@JdbcTypeCode(SqlTypes.JSON)`).
  - `ImportRecordLink`: `@TenantId` **var** (klinik kullanıcısı eski bilgileri okurken filtrelensin). Fabrika `ImportRecordLink.of(UUID tenantId, UUID batchId, String sourceSystem, ImportEntityType type, String sourceId, UUID targetId, boolean createdByImport, Map<String,String> legacyData)`.
  - `ImportBatchRepository`: `save`, `findById(UUID)`, `findByTenantIdOrderByCreatedAtDesc(UUID)`, `findByStatusAndCreatedAtBefore(ImportBatchStatus, Instant)`.
  - `ImportRecordLinkRepository`: `saveAll(List<ImportRecordLink>)`, `findByBatchId(UUID)`, `existingSourceIds(UUID tenantId, String sourceSystem, ImportEntityType type, Collection<String> sourceIds): Set<String>`, `findTarget(UUID tenantId, String sourceSystem, ImportEntityType type, String sourceId): Optional<UUID>`, `findByTarget(ImportEntityType type, UUID targetId): Optional<ImportRecordLink>`, `deleteByBatchId(UUID)`.

- [ ] **Step 1: Write the failing test**: `ImportPersistenceIntegrationTest` (`@SpringBootTest`, `extends TenantScopedTestSupport`; kiracı oluşturma ve temizlik `TenantIsolationTest` yardımcılarıyla aynı yoldan):
  - `should_roundTripBatchWithReport`: kaydedilen partinin `report.totals().get(OWNER).toCreate()` değeri geri okununca aynı.
  - `should_rejectDuplicateSourceIdPerTenant`: aynı `(tenant, "KolayVet", OWNER, "17")` ile ikinci link → `DataIntegrityViolationException`.
  - `should_hideLinksOfOtherTenant`: A kiracısının linki, `asTenant(B, ...)` içinde `findByTarget` ile bulunamaz.
- [ ] **Step 2: Run it to verify it fails.** `cd backend && ./mvnw -q test -Dtest=ImportPersistenceIntegrationTest`. Beklenen: derleme hatası (sınıflar yok).
- [ ] **Step 3: Write the migration, entities and adapters** (yukarıdaki imzalarla; adapter kalıbı `patient/infrastructure/persistence/OwnerRepositoryAdapter` ile aynı). `ImportReport` bu adımda Task 3'teki tanımıyla oluşturulur.
- [ ] **Step 4: Run the test plus the module boundary test.** `./mvnw -q test -Dtest='ImportPersistenceIntegrationTest,ApplicationModulesTest'`. Beklenen: PASS.
- [ ] **Step 5: Changelist kontrolü.** `git status`. Commit yok.

---

### Task 2: Excel şablonu ve okuyucu

**Files:**
- Modify: `backend/pom.xml`: `org.apache.poi:poi-ooxml:5.4.1`
- Create: `application/excel/ParsedWorkbook.java`, `ImportWorkbookParser.java`, `ImportTemplateWriter.java`
- Test: `backend/src/test/java/com/vetos/modules/dataimport/application/excel/ImportWorkbookParserTest.java`

**Interfaces:**
- Produces:
  - Şablon sayfaları ve sütunları (başlık satırı 1. satır; `*` zorunlu; başlık eşleştirmesi büyük/küçük harf, Türkçe karakter ve boşluk duyarsız):
    - **`Sahipler`**: `kaynak_id*`, `ad_soyad*`, `telefon*`, `ikinci_telefon`, `e_posta`, `tc_kimlik`, `adres`, `il`, `ilce`, `notlar`
    - **`Hastalar`**: `kaynak_id*`, `sahip_kaynak_id*`, `ad*`, `tur*`, `irk`, `cinsiyet` (`Erkek`/`Dişi`/boş), `dogum_tarihi`, `kisirlastirilmis` (`Evet`/`Hayır`/boş), `mikrocip_no`, `pasaport_no`, `renk`, `notlar`
    - Şablonda **olmayan** her sütun satırın `extras` haritasına girer (başlık → hücre metni). Bunlar sonradan "eski sistem bilgileri" olur.
  - `record ParsedRow(int rowNumber, Map<String,String> values, Map<String,String> extras)`. `values` anahtarları yukarıdaki normalize sütun adlarıdır. Tüm hücreler metne çevrilir; tarih biçimli sayısal hücre `yyyy-MM-dd` metnine çevrilir.
  - `record ParsedWorkbook(List<ParsedRow> owners, List<ParsedRow> patients, List<String> structuralErrors)`. Eksik sayfa ya da eksik zorunlu başlık `structuralErrors` içine girer (ör. `"'Hastalar' sayfasında 'tur' sütunu yok"`). Tamamen boş satırlar atlanır.
  - `ImportWorkbookParser.parse(InputStream xlsx): ParsedWorkbook`. `.xlsx` olmayan girdi `InvalidImportFileException` (`dataimport.domain.exception`) fırlatır.
  - `ImportTemplateWriter.write(OutputStream out)`: iki sayfa, kalın başlık satırı, ikinci satırda örnek bir kayıt.

- [ ] **Step 1: Write the failing tests** (test dosyaları POI ile bellekte oluşturulur):
  - `should_readTemplateItWrites`: `ImportTemplateWriter` çıktısı parse edilince `structuralErrors` boş, örnek satır okunur.
  - `should_normalizeHeaders`: `"Kaynak ID"`, `"AD SOYAD"`, `"Telefon "` → `kaynak_id`, `ad_soyad`, `telefon`.
  - `should_putUnknownColumnsIntoExtras`: `"Bakiye Notu"` sütunu → `extras.get("Bakiye Notu")`.
  - `should_convertDateCellsToIsoText`: sayısal tarih hücresi (2019-03-05) → `"2019-03-05"`; metin `"05.03.2019"` aynen kalır (çözümleme Task 3'te).
  - `should_reportMissingSheetAndRequiredHeader`.
  - `should_skipBlankRows`.
  - `should_throwOnNonXlsx`: rastgele bayt → `InvalidImportFileException`.
- [ ] **Step 2: Run them to verify they fail.** `./mvnw -q test -Dtest=ImportWorkbookParserTest`. Beklenen: FAIL / derleme hatası.
- [ ] **Step 3: Implement** with `WorkbookFactory.create`, `DataFormatter`, `DateUtil.isCellDateFormatted`.
- [ ] **Step 4: Run them to verify they pass.** Beklenen: PASS.
- [ ] **Step 5: Changelist kontrolü.** `git status`.

---

### Task 3: Doğrulayıcı ve önizleme raporu

**Files:**
- Create: `domain/ImportReport.java`, `domain/ExistingDataLookup.java`, `application/excel/PhoneNormalizer.java`, `application/ImportValidator.java`
- Test: `backend/src/test/java/com/vetos/modules/dataimport/application/ImportValidatorTest.java` (saf birim testi, `ExistingDataLookup` için elle yazılmış sahte nesne)

**Interfaces:**
- Consumes: `ParsedWorkbook`, `ParsedRow` (Task 2).
- Produces:
  - `ExistingDataLookup` (port): `List<SpeciesRef> species()`, `List<BreedRef> breeds(UUID speciesId)`, `List<OwnerRef> ownersByNormalizedPhones(UUID tenantId, Collection<String> normalizedPhones)`, `Set<String> alreadyImported(UUID tenantId, String sourceSystem, ImportEntityType type, Collection<String> sourceIds)`. `record SpeciesRef(UUID id, String name)`, `record BreedRef(UUID id, UUID speciesId, String name)`, `record OwnerRef(UUID id, String fullName, String phone, String normalizedPhone)`.
  - `PhoneNormalizer.normalize(String raw): String`: rakam dışını atar; `90` ile başlayan 12 haneliyi ve `0` ile başlayan 11 haneliyi 10 haneye indirir (`5321234567`). Boş girdi `""` döner.
  - `record ImportReport(Map<ImportEntityType, EntityTotals> totals, List<RowIssue> errors, List<RowIssue> warnings, List<OwnerMatch> ownerMatches, List<String> newSpecies, List<String> structuralErrors)`. `newSpecies`: Vetly'de olmayan, aktarımda eklenecek tür adları (tekilleştirilmiş; dosyada ilk görülen yazımıyla, baş/son boşluk kırpılmış).
    - `record EntityTotals(int rowsInFile, int toCreate, int matched, int alreadyImported, int errorRows)`
    - `record RowIssue(String sheet, int rowNumber, String sourceId, String message)`
    - `record OwnerMatch(String sourceId, String sourceName, String sourcePhone, UUID existingOwnerId, String existingOwnerName)`
    - `boolean canCommit()`: `structuralErrors` boşsa ve en az bir kayıt oluşturulacak ya da eşleşecekse `true`.
  - `ImportValidator.validate(UUID tenantId, String sourceSystem, ParsedWorkbook wb, ExistingDataLookup lookup): ImportReport`
  - Kurallar (hata: satır aktarılmaz; uyarı: satır aktarılır):
    - Hata: zorunlu alan boş; aynı sayfada tekrar eden `kaynak_id`; `telefon` normalize sonrası 10 hane değil; `cinsiyet`/`kisirlastirilmis` tanınmayan değer; `dogum_tarihi` `yyyy-MM-dd` ya da `dd.MM.yyyy` olarak okunamıyor ya da gelecekte; `sahip_kaynak_id` dosyada da yok, daha önce aktarılmış sahiplerde de yok; sahibi hatalı satır olan hasta (`"Sahip satırı hatalı"`).
    - Uyarı: `irk` eşleşmiyor (ırk boş bırakılır, orijinal metin eski bilgilere yazılır); dosyada aynı telefonla birden fazla sahip.
    - Yeni tür: `tur` hiçbir Vetly türüyle eşleşmiyorsa hata **değildir**; ad `newSpecies` listesine girer (aynı türün farklı yazımları, örneğin `Tavşan`/`tavsan`, tek kayıt olur) ve satır geçerli sayılır.
    - Eşleşme (dolu klinik): normalize telefonu klinikteki bir sahiple aynı olan sahip satırı `ownerMatches`'e girer ve `matched` sayılır, `toCreate` sayılmaz.
    - Daha önce aktarılmış (`alreadyImported`): link tablosunda olan `kaynak_id`'ler sayılır ve atlanır; hata sayılmaz.
    - Tür ve ırk eşleştirmesi: `Locale("tr")` ile küçük harfe çevirip Türkçe karakterleri sadeleştirerek (`ö→o, ü→u, ş→s, ı→i, ğ→g, ç→c`) karşılaştır.

- [ ] **Step 1: Write the failing tests:**
  - `should_countCreatesForCleanFile`: 2 sahip, 3 hasta → `totals[OWNER].toCreate=2`, `totals[PATIENT].toCreate=3`, `errors` boş, `canCommit()=true`.
  - `should_matchExistingOwner_acrossPhoneFormats`: klinikte `5321234567`; dosyada `"0532 123 45 67"` ve başka bir satırda `"+90 532 123 4567"` → iki `OwnerMatch`, `matched=2`.
  - `should_matchSpecies_turkishInsensitive`: `"köpek"`, `"KÖPEK"`, `"Kopek"` → hata yok.
  - `should_listUnknownSpeciesAsNew`: `"Tavşan"` ve `"tavsan"` → hata yok, `newSpecies == ["Tavşan"]`, iki hasta da `toCreate` içinde.
  - `should_warnOnUnknownBreed`.
  - `should_errorOnBadDate_andAcceptBothFormats`: `"05.03.2019"` ve `"2019-03-05"` geçer; `"5 Mart"` ve gelecek tarih hata verir.
  - `should_errorOnDuplicateSourceId`.
  - `should_cascadeOwnerErrorToPatients`: telefonu boş sahip → o sahibin hastası `"Sahip satırı hatalı"` hatası alır.
  - `should_resolvePatientOwnerFromPreviousImport`: `sahip_kaynak_id` dosyada yok ama `alreadyImported` içinde var → hata yok.
  - `should_skipAlreadyImportedRows`: `alreadyImported=1`, `toCreate` düşer.
  - `should_notCommit_whenStructuralErrors`.
- [ ] **Step 2: Run them to verify they fail.** `./mvnw -q test -Dtest=ImportValidatorTest`.
- [ ] **Step 3: Implement** `PhoneNormalizer` and `ImportValidator`.
- [ ] **Step 4: Run them to verify they pass.**
- [ ] **Step 5: Changelist kontrolü.**

---

### Task 4: Önizleme, liste, iptal ve şablon uç noktaları

**Files:**
- Create: `application/CreateImportPreviewUseCase.java`, `ListImportBatchesUseCase.java`, `DiscardImportUseCase.java`, `DiscardStaleImportPreviewsJob.java`
- Create: `infrastructure/PatientModuleImportAdapter.java` (`ExistingDataLookup` kısmı)
- Create: `api/PlatformAdminImportsController.java`, `api/dto/ImportBatchResponse.java`
- Test: `.../application/CreateImportPreviewUseCaseTest.java` (Mockito), `.../api/PlatformAdminImportsControllerSecurityTest.java` (`@SpringBootTest @AutoConfigureMockMvc`, kalıp `UsageEndpointsSecurityTest`)

**Interfaces:**
- Consumes: `ImportWorkbookParser`, `ImportValidator`, `ExistingDataLookup`, `ImportBatchRepository`.
- Produces:
  - `CreateImportPreviewUseCase.execute(UUID tenantId, String sourceSystem, String fileName, byte[] file, UUID adminId): ImportBatch`. `sourceSystem` boşsa ya da 60 karakterden uzunsa `IllegalArgumentException` fırlatır. Kiracının var olduğunu doğrular. Rapor oluşturur, `PREVIEWED` parti kaydeder; `totalRows` = sahip + hasta satırı.
  - `ListImportBatchesUseCase.execute(UUID tenantId): List<ImportBatch>`
  - `DiscardImportUseCase.execute(UUID batchId)`
  - `DiscardStaleImportPreviewsJob`: `@Scheduled(cron = "0 30 3 * * *")`; 7 günden eski `PREVIEWED` partileri `discard()` eder.
  - `PatientModuleImportAdapter implements ExistingDataLookup`: `SpeciesRepository.findAll`, `BreedRepository.findBySpeciesId` ve `OwnerRepository` kullanır. Telefonla sahip arama için `OwnerRepository`'ye **eklenmez**; `dataimport` kendi native sorgusunu çalıştırır (`SELECT id, full_name, phone FROM owners WHERE tenant_id = ? AND is_anonymous_placeholder = false`) ve normalize ederek süzer.
  - REST (`/api/v1/platform-admin/tenants/{tenantId}/imports`):
    - `POST` multipart (`file`, `sourceSystem`) → `ImportBatchResponse`
    - `GET` → `List<ImportBatchResponse>`
    - `GET /{batchId}` → `ImportBatchResponse`
    - `POST /{batchId}/discard` → 204
  - `GET /api/v1/platform-admin/imports/template.xlsx`: `Content-Disposition: attachment; filename="vetly-aktarim-sablonu.xlsx"`
  - `record ImportBatchResponse(UUID id, UUID tenantId, String sourceSystem, String fileName, ImportBatchStatus status, ImportReport report, int totalRows, int processedRows, String errorMessage, Instant createdAt, Instant committedAt, Instant rolledBackAt)`
  - Hatalar: `InvalidImportFileException` → 400 (mevcut global hata işleyiciye kaydedilir), yanlış durum geçişi → 409.

- [ ] **Step 1: Write the failing tests:**
  - `should_savePreviewedBatchWithReport`.
  - `should_rejectBlankSourceSystem`.
  - `should_discardStalePreviewsOnly`: 8 günlük `PREVIEWED` partisi `DISCARDED` olur; 8 günlük `COMMITTED` partisine dokunulmaz.
  - Güvenlik: klinik personeli token'ıyla `POST .../imports` → 403; tokensız → 401; platform admin → 200 ve gövdede `status = "PREVIEWED"`.
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass.** Ayrıca `ApplicationModulesTest` çalıştır.
- [ ] **Step 5: Changelist kontrolü.**

---

### Task 5: Aktarımı başlatma ve arka plan işçisi

**Files:**
- Create: `domain/ImportTargetWriter.java`, `application/StartImportCommitUseCase.java`, `application/ImportCommitWorker.java`
- Modify: `infrastructure/PatientModuleImportAdapter.java` (`ImportTargetWriter` kısmı)
- Modify: `api/PlatformAdminImportsController.java`: `POST /{batchId}/commit`
- Test: `.../ImportCommitIntegrationTest.java` (`@SpringBootTest`, `extends TenantScopedTestSupport`)

**Interfaces:**
- Consumes: Task 1–4'teki her şey.
- Produces:
  - `ImportTargetWriter` (port), çağrıldığı transaction'ın kiracısında çalışır:
    - `UUID createSpecies(String name)`: `Species.create(name)` + `SpeciesRepository.save`. Tür tablosu kiracısızdır (tüm kliniklerin ortak listesi). Geri almada **silinmez**; başka klinikler kullanmaya başlamış olabilir.
    - `UUID createOwner(UUID tenantId, ParsedRow row)`: `Owner.register(tenantId, ad_soyad, telefon, e_posta, adres)`, ardından `updateNationalId` ve il/ilçe/notlar/ikinci telefon için `updateDetails`. Rıza alanları **false** kalır; aktarım SMS rızası uydurmaz.
    - `UUID createPatient(UUID tenantId, ParsedRow row, UUID ownerId, UUID speciesId, UUID breedIdOrNull)`: `Patient.register(...)`, ardından `updateProfile` (kısırlaştırma), `updateMicrochip`, `updatePassportNumber`, `updateDetails` (renk, notlar). Kayıtlar repository `save` ile yazılır; `Register*UseCase` **çağrılmaz**, böylece kayıt olayları ve yan etkiler tetiklenmez.
  - `StartImportCommitUseCase.execute(UUID batchId, UUID adminId, List<String> rejectedMatchSourceIds)`: `startCommit` çağırır, kaydeder ve `ImportCommitWorker.run(batchId, adminId)` işini `@Async` ile başlatır. Parti `PREVIEWED` değilse ya da `report.canCommit()` false ise 409 döner.
  - `ImportCommitWorker.run(UUID batchId, UUID adminId)`:
    1. Dosyayı yeniden parse edip yeniden doğrular (önizleme ile aktarım arasında klinik veri girmiş olabilir).
    2. Önce yeniden doğrulamadaki `newSpecies` için türler oluşturulur (bu arada başka biri eklemişse var olan kullanılır). Sonra sahipler, sonra hastalar, 500'lük parçalar halinde, her parça ayrı transaction'da ve `TenantContext` hedef kiracıya ayarlı yazılır.
    3. Hatalı satırlar ve daha önce aktarılmış satırlar atlanır.
    4. Eşleşen sahip `rejectedMatchSourceIds` içinde değilse yeni kayıt açılmaz; `created_by_import=false` link yazılır. Listedeyse yeni sahip oluşturulur.
    5. Her kayıt için link yazılır. `legacy_data` = `extras` + eşleşmeyen ırk için `{"Eski ırk": "<metin>"}`.
    6. Her parçadan sonra `progress(processed)` çağrılır.
    7. Başarıda `markCommitted()` ve audit `DATA_IMPORT_COMMITTED` (`RecordAuditLogUseCase`, varlık türü `"TENANT"`, kimlik `tenantId`, ayrıntıda parti kimliği ve sayılar).
    8. Herhangi bir hatada `RollbackImportUseCase`'in silme yolu (Task 6) bağımlılık kontrolü yapılmadan bu partiye uygulanır; ardından `markFailed(mesaj)` ve audit `DATA_IMPORT_FAILED`.
  - REST: `POST /{batchId}/commit` gövdesi `{ "rejectedMatchSourceIds": ["17", ...] }` → 202 + `ImportBatchResponse`.

- [ ] **Step 1: Write the failing tests** (işçiyi testte senkron çağır; `@Async` yolunu ayrıca test etme):
  - `should_createOwnersPatientsAndLinks`: 2 sahip + 3 hasta → kiracıda 2 sahip ve 3 hasta, 5 link, parti `COMMITTED`, `source_file` null, `processed_rows=5`.
  - `should_linkMatchedOwnerWithoutCreating`: klinikte telefonu eşleşen sahip var → sahip sayısı artmaz, hasta o sahibe bağlanır, link `created_by_import=false`.
  - `should_createNewOwner_whenMatchRejected`.
  - `should_storeExtrasAsLegacyData`.
  - `should_createUnknownSpeciesOnce_andKeepItAfterRollback`: dosyada `"Tavşan"` ile 2 hasta → `species` tablosunda tek bir "Tavşan" kaydı, iki hasta ona bağlı; parti geri alınınca tür durur. (Test sonunda oluşturulan türü elle sil.)
  - `should_createNothing_whenSameFileCommittedTwice`: ikinci partinin raporunda `alreadyImported=5`, kayıt sayıları değişmez.
  - `should_rollbackPartialWrites_whenWorkerFails`: `ImportTargetWriter` sahte nesnesi 700. çağrıda hata fırlatır (1.000 satırlık dosya) → kiracıda yeni kayıt kalmaz, link kalmaz, parti `FAILED`, `error_message` dolu.
  - `should_notPublishPatientRegisteredEvents`: `@RecordApplicationEvents` ile patient modülünün kayıt olaylarından hiçbiri yayınlanmaz.
  - `should_reject_whenBatchNotPreviewed` → `IllegalStateException`.
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass.**
- [ ] **Step 5: Changelist kontrolü.**

---

### Task 6: Geri alma

**Files:**
- Create: `domain/ImportDependencyInspector.java`, `infrastructure/PgImportDependencyInspector.java`, `application/RollbackImportUseCase.java`
- Modify: `api/PlatformAdminImportsController.java`: `POST /{batchId}/rollback`
- Test: `.../ImportRollbackIntegrationTest.java`

**Interfaces:**
- Produces:
  - `ImportDependencyInspector.findForeignDependents(String table, Collection<UUID> ids, Collection<UUID> excludedIds): List<Dependent>`; `record Dependent(String table, UUID rowId, String column)`. Uygulama: `pg_constraint` üzerinden `table(id)`'ye referans veren tüm yabancı anahtarları bulur. Her biri için `ids` kümesine bağlı olup `excludedIds` (aynı partinin oluşturduğu kayıtlar) içinde **olmayan** satırları döndürür. Tablo adları yalnızca `owners` ve `patients` olabilir (sabit izin listesi).
  - `ImportEntityType` → tablo: `OWNER→owners`, `PATIENT→patients`.
  - `RollbackImportUseCase.execute(UUID batchId, UUID adminId): RollbackResult`; `record RollbackResult(boolean rolledBack, List<Dependent> blockers)`.
    - Parti `COMMITTED` değilse 409 döner.
    - `created_by_import=true` linklerin hedeflerine kiracı dışı bağımlılık varsa `rolledBack=false` döner ve hiçbir şey silinmez.
    - Yoksa tek transaction'da (hedef kiracı bağlamında) önce bu partinin oluşturduğu hastalar, sonra oluşturduğu sahipler silinir, ardından partinin bütün linkleri silinir. `markRolledBack()` çağrılır ve audit `DATA_IMPORT_ROLLED_BACK` yazılır.
    - `created_by_import=false` (eşleşen) sahiplere dokunulmaz.
  - `void deleteCreatedRecords(ImportBatch batch)`: bağımlılık kontrolü yapmayan iç yol; Task 5'teki hata durumunda kullanılır.
  - REST: `POST /{batchId}/rollback` → 200 + `RollbackResult`.

- [ ] **Step 1: Write the failing tests:**
  - `should_deleteOnlyBatchCreatedRecords`: dolu klinik (önceden 1 sahip + 1 hasta) + aktarım (1 eşleşen sahip, 1 yeni sahip, 2 hasta) → geri alma sonrası yalnızca önceki 1 sahip ve 1 hasta kalır; eşleşen sahip duruyor; link yok; parti `ROLLED_BACK`.
  - `should_block_whenClinicAddedPatientToImportedOwner`: aktarımla gelen sahibe klinik yeni bir hasta ekledi → `rolledBack=false`, `blockers[0].table="patients"`, hiçbir kayıt silinmedi.
  - `should_notBlockOnRecordsOfSameBatch`: aktarımla gelen sahip–hasta bağı engel sayılmaz.
  - `should_reject_whenNotCommitted` → 409.
- [ ] **Step 2: Run them to verify they fail.**
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass.** Ardından Task 5 testlerini tekrar çalıştır (`deleteCreatedRecords` artık gerçek).
- [ ] **Step 5: Changelist kontrolü.**

---

### Task 7: Klinik tarafında "Eski sistem bilgileri"

**Files:**
- Create: `application/GetLegacyDataUseCase.java`, `api/LegacyDataController.java`
- Create: `frontend/src/api/dataImportApi.ts` (`getLegacyData` kısmı), `frontend/src/components/legacy/LegacyDataCard.tsx`, `LegacyDataCard.test.tsx`
- Modify: sahip detay sayfası (`frontend/src/pages/owners/OwnerDetailPage.tsx`) ve hasta detay sayfası (`frontend/src/pages/patients/PatientDetailPage.tsx`): kartı ekle
- Test: `.../LegacyDataControllerIntegrationTest.java`

**Interfaces:**
- Produces:
  - `GET /api/v1/legacy-data?entityType=OWNER|PATIENT&id={uuid}` (klinik kullanıcısı, normal kimlik doğrulama): kayıt varsa `{ "sourceSystem": "KolayVet", "sourceId": "17", "importedAt": "...", "data": { "Bakiye Notu": "..." } }`, yoksa 204.
  - `GetLegacyDataUseCase.execute(ImportEntityType type, UUID targetId): Optional<LegacyData>`. Kiracı filtresi `@TenantId` ile otomatik uygulanır.
  - `LegacyDataCard({ entityType, id })`: 204 gelirse hiçbir şey göstermez. Veri varsa başlığı "Eski sistem bilgileri (KolayVet)" olan, kapalı başlayan katlanır bir kart gösterir; içinde anahtar–değer listesi olur.

- [ ] **Step 1: Write the failing tests:**
  - Arka uç: `should_returnLegacyDataForOwnTenant`, `should_return204ForOtherTenantsRecord`.
  - Ön yüz: `renders nothing when no legacy data`, `renders key-value rows collapsed by default and expands on click`.
- [ ] **Step 2: Run them to verify they fail.** `./mvnw -q test -Dtest=LegacyDataControllerIntegrationTest` ve `cd frontend && npx vitest run src/components/legacy`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass.**
- [ ] **Step 5: Changelist kontrolü.**

---

### Task 8: Admin paneli — "Veri aktarımı" bölümü

**Files:**
- Modify: `frontend/src/platformAdmin/platformAdminClient.ts`: `postMultipart<T>(path, form)` ve `getBlob(path)` ekle (`api/client.ts` içindeki `requestMultipart` kalıbıyla)
- Modify: `frontend/src/api/platformAdminApi.ts` ya da `dataImportApi.ts`: admin çağrıları
- Create: `frontend/src/pages/platform-admin/TenantImportSection.tsx`, `.module.css`, `.test.tsx`
- Modify: `frontend/src/pages/platform-admin/TenantDetailPage.tsx`: `<TenantImportSection tenantId={...} />` bölümünü ekle

**Interfaces:**
- Consumes: Task 4–6 uç noktaları; `ImportBatchResponse` ve `RollbackResult` TS karşılıkları.
- Produces: `TenantImportSection({ tenantId }: { tenantId: string })` bileşeni. İçeriği:
  1. "Şablonu indir" düğmesi.
  2. Yükleme formu: "Kaynak program" metin alanı (zorunlu) ve `.xlsx` dosya seçici; "Önizle" düğmesi.
  3. Parti listesi: tarih, kaynak, dosya, durum rozeti (`PREVIEWED`=Önizleme, `COMMITTING`=Aktarılıyor, `COMMITTED`=Aktarıldı, `FAILED`=Başarısız, `ROLLED_BACK`=Geri alındı, `DISCARDED`=İptal).
  4. Seçili partinin raporu:
     - Kayıt türü başına sayılar.
     - Yapısal hatalar (varsa kırmızı uyarı ve "Aktar" kapalı).
     - Hatalar ve uyarılar tablosu (sayfa, satır, kaynak_id, mesaj).
     - "Eklenecek yeni türler" listesi (varsa; ör. "Tavşan, Kaplumbağa"). Yanında not: "Bu türler tüm kliniklerin tür listesine eklenecek."
     - Eşleşen sahipler tablosu: her satırda varsayılan **işaretli** "mevcut kayda bağla" kutusu; işareti kaldırılanlar `rejectedMatchSourceIds` olur.
  5. "Aktar" düğmesi: onay penceresinde `"<n> kayıt oluşturulacak, <m> kayıt mevcut kayıtlarla eşleşecek, <k> hatalı satır atlanacak."` yazar.
  6. `COMMITTING` iken 2 saniyede bir yenileme ve ilerleme çubuğu (`processedRows/totalRows`).
  7. `COMMITTED` partide "Geri al" düğmesi. Engel varsa engel listesi gösterilir: `"Bu aktarım geri alınamaz: klinik aktarılan kayıtlara yeni kayıt eklemiş"` ve tablo/kimlik listesi.
  8. `PREVIEWED` partide "İptal et" düğmesi.

- [ ] **Step 1: Write the failing tests** (`TenantImportSection.test.tsx`, API çağrıları taklit edilir):
  - `disables commit when structural errors exist`.
  - `sends unchecked matches as rejectedMatchSourceIds`.
  - `polls while committing and stops when committed`.
  - `shows blockers when rollback is refused`.
- [ ] **Step 2: Run them to verify they fail.** `cd frontend && npx vitest run src/pages/platform-admin/TenantImportSection`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them to verify they pass.** Ardından `npx tsc -b`.
- [ ] **Step 5: Uçtan uca elle doğrulama.** Çalışan dev ortamında admin panelinde bir test kliniği aç, şablonu indir, 3 sahip ve 4 hasta ile doldur (biri hatalı, biri mevcut sahiple eşleşen), önizle, aktar, sahip detay sayfasında "Eski sistem bilgileri" kartını gör, sonra geri al. Çalışan `:8080` backend'i **yeniden başlatma**; migration'ın yüklenmesi için gerekiyorsa kullanıcıdan yeniden başlatmasını iste.
- [ ] **Step 6: Changelist kontrolü.** `git status`.

---

## Bu planda verilen kararlar (spec'te açık bırakılanlar)

- Kaynak kimliği ve eski bilgiler her tabloya sütun eklenerek değil, tek bir `import_record_links` tablosunda tutulur. Böylece mevcut tablolara dokunulmaz ve sonraki fazlar aynı tabloyu kullanır.
- Ham dosya önizleme ile aktarım arasında `import_batches.source_file` içinde saklanır, aktarımda yeniden okunup yeniden doğrulanır, sonra silinir.
- Eşleşmeyen **tür** Vetly'ye eklenir (kullanıcı kararı). Tür listesi ortak olduğu için önizlemede ayrıca gösterilir ve geri almada silinmez. Eşleşmeyen **ırk** uyarıdır ve metni eski bilgilere yazılır.
- Aktarılan sahiplerin SMS, WhatsApp ve pazarlama rızaları `false` başlar.
- Yalnızca `.xlsx` kabul edilir.
