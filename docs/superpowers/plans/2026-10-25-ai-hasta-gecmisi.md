# AI Hasta Geçmişi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI tanı ve AI tedavi önerisine hastanın son 1 yıllık geçmişini (hasta kartı, muayeneler, reçeteler, aşılar, lab, görüntüleme) metin olarak eklemek.

**Architecture:** Her veri sahibi modül kendi `domain` paketinde bir okuma portu açar (Modulith kuralı). `ai/application` içindeki `PatientHistoryAssembler` portlardan 1 yıllık veriyi toplar, tavanları uygular, okunamayan bölümü işaretler; `PatientHistoryFormatter` bunu tek bir metne çevirir. Bu metin `DiagnosisSuggestionInput` / `TreatmentRecommendationInput` içinde `patientHistory` olarak Claude ve Ollama adaptörlerine gider.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Modulith, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-10-25-ai-hasta-gecmisi-design.md`

## Global Constraints

- Yalnızca metin; dosya/görsel gönderilmez.
- Pencere: o günkü muayene tarihinden geriye 365 gün. LocalDate alanlar için saat dilimi `Europe/Istanbul`.
- Tavanlar: muayene 20, reçete 30, aşı 20, tahlil 15, görüntüleme 10. Aşılırsa en yeniler alınır ve bölüme `(daha eski kayıtlar kısaltıldı)` eklenir.
- Boş bölüm: `kayıt yok`. Okunamayan bölüm (port istisna fırlattı): `okunamadı`; öneri yine üretilir.
- Hasta adı, sahip bilgisi, mikroçip, pasaport metne girmez.
- Filtreler: reçete CANCELLED hariç; aşı yalnız ADMINISTERED; lab ve görüntüleme yalnız COMPLETED; muayene yalnız FINALIZED/AMENDED ve o günkü muayene hariç.
- Kilo: muayene kilosu > kart kilosu > `belirtilmemiş`.
- API sözleşmesi, ekran, kota/`AiCallGate`/`RecordAiJobUseCase` akışı değişmez.
- Commit yok (kullanıcı IntelliJ'den commit eder). Main üzerinde çalışılır.

## Review Focus

1. Başka kiracının kaydı: yeni portlar mevcut repository'lerin `@TenantId` filtresine dayanır; kiracı filtresini atlatan native sorgu yazılmamalı (Task 1-3 portları yalnız mevcut `findByPatientId` repository metotlarını kullanır).
2. Doğum tarihi/tür/ırk boş hasta: formatter çökmemeli, `belirtilmemiş` yazmalı (Task 4 testi `formatsMissingProfileFieldsAsNotSpecified`).
3. Lab sonucunda `resultedAt` boş: istek tarihi kullanılmalı (Task 3 testi `usesRequestedAtWhenResultedAtMissing`).
4. Port hatası öneriyi düşürmemeli (Task 4 testi `failingSectionIsMarkedUnreadable_andOthersStillRender`).
5. Sahip verisi sızıntısı: hasta adı metinde geçmemeli (Task 4 testi `neverIncludesPatientName`).

---

### Task 1: Hasta klinik profili portu

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/patient/domain/PatientClinicalProfile.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/PatientLookupPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/PatientLookupAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/patient/infrastructure/persistence/PatientLookupAdapterTest.java`

**Interfaces:**
- Produces:
  - `record PatientClinicalProfile(String speciesName, String breedName, Sex sex, boolean neutered, LocalDate birthDate, BigDecimal weightKg, String bloodType, String criticalAlert)` — ad/sahip/mikroçip YOK.
  - `Optional<PatientClinicalProfile> PatientLookupPort.findClinicalProfile(UUID patientId)` — hasta yoksa boş.

- [ ] **Step 1: Test yaz** — `findClinicalProfile_mapsCardFieldsWithSpeciesAndBreedNames` (tür "Köpek", ırk "Golden", kilo 31.5, kan grubu, kritik uyarı "Penisilin alerjisi", neutered true eşlenir) ve `findClinicalProfile_returnsEmpty_whenPatientMissing`.
- [ ] **Step 2: Çalıştır, kırmızı gör.** `cd backend && ./mvnw -q test -Dtest=PatientLookupAdapterTest` → derleme hatası (metot yok).
- [ ] **Step 3: Uygula** — `findTarbilProfile` ile aynı desen (tür/ırk adı repository'den).
- [ ] **Step 4: Yeşil gör.** Aynı komut → PASS.

### Task 2: Muayene modülü — bağlam, reçete ve aşı geçmişi

**Files:**
- Modify: `encounter/domain/EncounterClinicalContext.java` (alanlar ekle)
- Modify: `encounter/domain/EncounterLookupPort.java`, `encounter/infrastructure/persistence/EncounterLookupAdapter.java`
- Create: `encounter/domain/PrescriptionHistoryEntry.java`
- Modify: `encounter/domain/VaccinationLookupPort.java`, `encounter/infrastructure/persistence/VaccinationLookupAdapter.java`
- Create: `encounter/domain/VaccinationHistoryEntry.java`
- Test: `EncounterLookupAdapterTest.java`, `VaccinationLookupAdapterTest.java` (mevcut), ve mevcut `EncounterClinicalContext` kurucusunu çağıran testler (`GenerateDiagnosisSuggestionUseCaseTest`, `GenerateTreatmentRecommendationUseCaseTest`) yeni alanlarla güncellenir.

(Yollar `backend/src/main/java/com/vetos/modules/` altında.)

**Interfaces:**
- Produces:
  - `EncounterClinicalContext` sonuna `Instant encounterDate, BigDecimal weightKg` eklenir (muayenenin tarihi ve kilosu).
  - `record PrescriptionHistoryEntry(UUID encounterId, LocalDate issuedDate, List<Item> items)` içinde `record Item(String drugName, String dosage, String frequency, int durationDays, DrugRoute route)`.
  - `List<PrescriptionHistoryEntry> EncounterLookupPort.findPrescriptionHistory(UUID patientId, LocalDate since)` — CANCELLED hariç, `issuedDate >= since`, tarih azalan sırada, tümü (tavan yok).
  - `record VaccinationHistoryEntry(String vaccineName, LocalDate administeredDate, LocalDate nextDueDate)`.
  - `List<VaccinationHistoryEntry> VaccinationLookupPort.findAdministeredSince(UUID patientId, LocalDate since)` — yalnız ADMINISTERED, `administeredDate >= since`, azalan.
- `findClinicalContext(encounterId, historyLimit)` imzası aynı kalır.

- [ ] **Step 1: Testleri yaz**
  - `findClinicalContext_includesEncounterDateAndWeight`
  - `findPrescriptionHistory_excludesCancelledAndOlderThanSince_newestFirst` (3 reçete: biri iptal, biri `since` öncesi → 1 sonuç, kalemleriyle)
  - `findAdministeredSince_onlyAdministeredWithinWindow` (SCHEDULED ve CANCELLED dışarıda)
- [ ] **Step 2: Kırmızı gör.** `./mvnw -q test -Dtest='EncounterLookupAdapterTest,VaccinationLookupAdapterTest'`
- [ ] **Step 3: Uygula.** `EncounterLookupAdapter`'a `PrescriptionRepository` ve `PrescriptionItemRepository` (domain arayüzleri) enjekte edilir; test kurucusu güncellenir. Aşı için `VaccinationRecordRepository.findByPatientId` kullanılır.
- [ ] **Step 4: Yeşil gör** (aynı komut + iki AI use case testi).

### Task 3: Lab ve görüntüleme okuma portları

**Files:**
- Create: `lab/domain/LabResultLookupPort.java`, `lab/domain/LabResultHistoryEntry.java`, `lab/infrastructure/persistence/LabResultLookupAdapter.java`
- Create: `imaging/domain/ImagingRecordLookupPort.java`, `imaging/domain/ImagingHistoryEntry.java`, `imaging/infrastructure/persistence/ImagingRecordLookupAdapter.java`
- Test: `LabResultLookupAdapterTest.java`, `ImagingRecordLookupAdapterTest.java` (aynı paket yollarında, test ağacı)

**Interfaces:**
- Produces:
  - `record LabResultHistoryEntry(String testName, Instant date, String resultSummary, List<Value> values)` içinde `record Value(String parameterName, String value, String unit, String referenceRange, LabValueFlag flag)`.
  - `List<LabResultHistoryEntry> LabResultLookupPort.findCompletedSince(UUID patientId, Instant since)` — yalnız COMPLETED; `date = resultedAt ?? requestedAt`; `date >= since`; azalan.
  - `record ImagingHistoryEntry(ImagingModality modality, String bodyRegion, Instant date, String findings)`.
  - `List<ImagingHistoryEntry> ImagingRecordLookupPort.findCompletedSince(UUID patientId, Instant since)` — aynı kurallar.
- Adaptörler domain repository arayüzlerini (`LabResultRepository`, `LabResultItemRepository`, `ImagingRecordRepository`) kullanır.

- [ ] **Step 1: Testleri yaz**
  - `findCompletedSince_onlyCompletedWithinWindow_withValues_newestFirst` (lab; PENDING/CANCELLED/eski dışarıda; değerler flag'iyle)
  - `usesRequestedAtWhenResultedAtMissing` (lab)
  - `findCompletedSince_onlyCompletedWithinWindow_newestFirst` (görüntüleme)
- [ ] **Step 2: Kırmızı gör.** `./mvnw -q test -Dtest='LabResultLookupAdapterTest,ImagingRecordLookupAdapterTest'`
- [ ] **Step 3: Uygula.**
- [ ] **Step 4: Yeşil gör.**

### Task 4: Geçmişi toplama ve metne çevirme (ai/application)

**Files:**
- Create: `ai/application/PatientHistory.java`, `ai/application/PatientHistoryAssembler.java`, `ai/application/PatientHistoryFormatter.java`
- Modify: `ai/package-info.java` — `allowedDependencies`'e `"modules.patient::domain", "modules.lab::domain", "modules.imaging::domain"` ekle.
- Test: `ai/application/PatientHistoryAssemblerTest.java`, `ai/application/PatientHistoryFormatterTest.java`, mevcut `ApplicationModulesTest`.

**Interfaces:**
- Consumes: Task 1-3 portları; `EncounterClinicalContext` (Task 2).
- Produces:
  - `record PatientHistory(LocalDate encounterDate, PatientClinicalProfile profile /*null olabilir*/, BigDecimal encounterWeightKg, Section<PastEncounterSummary> encounters, Section<PrescriptionHistoryEntry> prescriptions, Section<VaccinationHistoryEntry> vaccinations, Section<LabResultHistoryEntry> labs, Section<ImagingHistoryEntry> imaging, UUID currentEncounterId)`; iç `record Section<T>(List<T> items, boolean truncated, boolean failed)` (+ `static failed()`).
  - `PatientHistory PatientHistoryAssembler.assemble(EncounterClinicalContext context)` — `@Service`.
  - `static String PatientHistoryFormatter.format(PatientHistory h)`.
- Sabitler (assembler): `WINDOW_DAYS = 365`, `MAX_ENCOUNTERS = 20`, `MAX_PRESCRIPTIONS = 30`, `MAX_VACCINATIONS = 20`, `MAX_LABS = 15`, `MAX_IMAGING = 10`.
- Muayeneler: assembler, use case'in verdiği `context.recentHistory()` listesini kullanır; use case artık `findClinicalContext(encounterId, MAX_ENCOUNTERS + 1)` çağırır. Pencere dışı olanlar elenir; pencere içinde 20'den fazla ise kısaltılır.
- Her port çağrısı ayrı try/catch; istisnada `Section.failed()` ve `log.warn`. Profil hatasında `profile = null` + uyarı; formatter profil satırlarını `okunamadı` yazar.

**Metin biçimi (formatter, birebir başlıklar):**

```
HASTA BİLGİSİ
Tür: Köpek | Irk: Golden | Yaş: 8 yıl 3 ay | Cinsiyet: Dişi, kısırlaştırılmış | Kilo: 31.5 kg | Kan grubu: DEA 1.1+
Kritik uyarı: Penisilin alerjisi

GEÇMİŞ MUAYENELER (son 1 yıl, en yeniden eskiye)
- 2026-03-01 | Değerlendirme: ... | Plan: ...

İLAÇLAR / REÇETELER (son 1 yıl)
- 2026-03-01 (bu muayene): Metacam 1.5 mg, günde 1, 5 gün, Oral; Clavon 1 ml, ...

AŞILAR (son 1 yıl)
- 2026-02-10 Nobivac DHPPi (sonraki: 2027-02-10)

KAN / LAB SONUÇLARI (son 1 yıl)
- 2026-03-01 Biyokimya — özet: ...
  * BUN: 45 mg/dL (ref 7-27) YÜKSEK

GÖRÜNTÜLEME (son 1 yıl)
- 2026-03-01 Röntgen, Toraks: <bulgular | bulgu yazılmamış>
```

- Eşlemeler: Sex MALE/FEMALE/UNKNOWN → Erkek/Dişi/Bilinmiyor; neutered → `kısırlaştırılmış`/`kısırlaştırılmamış`; modality XRAY/ULTRASOUND/DICOM/OTHER → Röntgen/Ultrason/DICOM/Diğer; flag LOW/HIGH/ABNORMAL → DÜŞÜK/YÜKSEK/ANORMAL, NORMAL → ek yok; route ORAL/TOPICAL/INJECTABLE → Oral/Topikal/Enjeksiyon.
- Boş alan → `belirtilmemiş`. Kritik uyarı boşsa satır yazılmaz. Yaş, `encounterDate`'e göre; 1 yaşından küçükse `N ay`.
- Bölüm boş → başlık altında `kayıt yok`; failed → `okunamadı`; truncated → başlığın altına son satır `(daha eski kayıtlar kısaltıldı)`.
- Instant tarihler `Europe/Istanbul`'da `yyyy-MM-dd`.

- [ ] **Step 1: Formatter testlerini yaz**
  - `formatsFullHistoryWithAllSections` (yukarıdaki örnekteki satırlar `contains` ile)
  - `formatsMissingProfileFieldsAsNotSpecified`
  - `emptySectionsSayNoRecords`
  - `truncatedSectionShowsNote`
  - `weightPrefersEncounterOverCard` ve `weightFallsBackToCard`
  - `ageUnderOneYearShowsMonths`
  - `marksCurrentEncounterPrescription` (`(bu muayene)`)
  - `neverIncludesPatientName` (profilde ad alanı yok; test, kaynakta ad olan bir hasta için metinde adın geçmediğini doğrular — assembler testinde `PatientSummary` adı "Karabaş" iken metin "Karabaş" içermez)
- [ ] **Step 2: Assembler testlerini yaz**
  - `assemblesWithinOneYearWindow_passingSinceToPorts` (since = encounterDate − 365 gün; portlara bu değerin gittiği `verify` ile)
  - `capsEachSectionAndMarksTruncated` (31 reçete → 30, truncated=true)
  - `dropsEncountersOutsideWindow`
  - `failingSectionIsMarkedUnreadable_andOthersStillRender` (lab portu RuntimeException → labs.failed, diğerleri dolu)
- [ ] **Step 3: Kırmızı gör.** `./mvnw -q test -Dtest='PatientHistory*Test'`
- [ ] **Step 4: Uygula + package-info güncelle.**
- [ ] **Step 5: Yeşil gör.** Aynı komut + `./mvnw -q test -Dtest=ApplicationModulesTest`.

### Task 5: Use case'ler, girdiler, adaptörler ve talimatlar

**Files:**
- Modify: `ai/domain/DiagnosisSuggestionInput.java` → `(String subjective, String objective, String physicalExamSummary, String vitalsSummary, String patientHistory)`
- Modify: `ai/domain/TreatmentRecommendationInput.java` → `(String currentAssessment, String patientHistory)`
- Delete: `ai/domain/HistoryEntry.java` (artık kullanılmaz)
- Modify: `GenerateDiagnosisSuggestionUseCase`, `GenerateTreatmentRecommendationUseCase` — kurucuya `PatientHistoryAssembler` eklenir; `findClinicalContext(id, 21)`; `PatientHistoryFormatter.format(assembler.assemble(context))`.
- Modify: `ClaudeDiagnosisSuggestionAdapter`, `ClaudeTreatmentRecommendationAdapter`, `OllamaDiagnosisSuggestionAdapter`, `OllamaTreatmentRecommendationAdapter` — `buildPrompt` geçmiş listesi yerine `patientHistory` metnini ekler.
- Test: dört adaptör testi + iki use case testi güncellenir.

**System prompt eklemesi** (Claude ve Ollama, tanı ve tedavi; mevcut "kesin tanı koyma" cümleleri kalır), mevcut metnin sonuna:

```
Sana ayrica hastanin son 1 yillik gecmisi (hasta bilgisi, gecmis muayeneler, ilaclar, asilar, lab sonuclari, goruntuleme bulgulari) verilecek. Bunlari dikkate al: ozellikle kritik uyari/alerji, normal disi lab degerleri ve hala kullaniliyor olabilecek ilaclarla etkilesim. Eski tanilari oldugu gibi tekrarlama; bugunku bulgularla birlikte degerlendir.
```

Tedavi prompt'una ek olarak: `Doz onerirken hastanin kilosunu kullan.`

- [ ] **Step 1: Testleri güncelle/yaz**
  - Claude adaptör testleri: `promptIncludesPatientHistoryBlock` (istek gövdesinde verilen `patientHistory` metni ve system prompt'ta "son 1 yillik gecmisi" geçer).
  - Use case testleri: `passesFormattedHistoryToPort` (assembler mock'u bir `PatientHistory` döner; port'a giden input'un `patientHistory` alanı formatter çıktısına eşit); `findClinicalContext(encounterId, 21)` stub'ı.
- [ ] **Step 2: Kırmızı gör.** `./mvnw -q test -Dtest='Claude*AdapterTest,Ollama*AdapterTest,Generate*UseCaseTest'`
- [ ] **Step 3: Uygula.**
- [ ] **Step 4: Yeşil gör.**

### Task 6: Tüm doğrulama

- [ ] **Step 1:** `cd backend && ./mvnw -q test > <scratchpad>/ai-history-suite.log 2>&1; tail` → tüm testler geçer (önceki 949 + yeniler).
- [ ] **Step 2:** Yerel backend'i yeniden başlat (eski java sürecini durdur, `./run-local.sh`), "Started VetosApplication" gör.
- [ ] **Step 3:** `docs/implementation-plan.md` veya ilgili modül notuna kısa bir satır (AI bağlamı artık 1 yıllık geçmiş içeriyor).
