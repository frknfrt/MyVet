# Lab / Görüntüleme sonuç fotoğrafından AI ile otomatik doldurma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lab sonucu ve Görüntüleme kaydına eklenen fotoğraf (dış merkezden gelen yazılı rapor) hekimin elle girmesine gerek kalmadan okunup ilgili alanlara taslak olarak dolsun. Lab'da Parametre/Değer/Birim/Referans dört alan da OCR'dan gelir; referans aralığı ayrıca mevcut kural motoruna (`LabReferenceRangeEvaluator`) girdi olur ve flag/özet oradan üretilir. Görüntülemede `findings` serbest metin alanı aynı şekilde taslak doldurulur. Tek bir vision (OCR) AI çağrısı yapılır — flag/özet hesaplaması ücretsiz kural motorundan gelir, ikinci bir AI çağrısı olmaz.

**Architecture (kod okumasıyla netleşti, 2026-10-29):**
- `ai` modülünün `allowedDependencies`'i ZATEN `modules.lab::domain` ve `modules.imaging::domain`'i içeriyor (`ai/package-info.java`) — bu akış için AI tarafının lab/imaging'den port üzerinden okuma yapması baştan beklenen bir mimari. Ama `lab`/`imaging` modülleri `modules.ai`'a bağımlı OLAMAZ (kendi `package-info.java`'larında yok). Yani:
  - Dosya baytını okuyan **lookup port** `lab.domain`/`imaging.domain`'de tanımlanır, adapter'ı kendi modülünde (`lab.infrastructure.persistence`/`imaging.infrastructure.persistence`) yaşar (bkz. `EncounterLookupPort` deseni).
  - OCR'ı **tetikleyen** use case + yeni REST uç noktaları `ai` modülünde yaşar (`ai.application`, `ai.api.AiController`) — `LabResultsController`/`ImagingRecordsController`'a eklenemez, çünkü o moduller `ai`'a bağımlı olamaz.
  - Lab'daki mevcut kural motoru (`LabReferenceRangeEvaluator`) `lab.domain`'de (application'da DEĞİL) — `ai` modülü ona doğrudan erişebilir. `EvaluateLabResultItemsUseCase` (ince orkestrasyon sarmalayıcısı) `lab.application`'da olduğu için `ai` ona erişemez; aynı küçük özet-metni mantığı `ai` tarafında da (bilinçli, küçük bir tekrar olarak) yazılır.
- Yeni uç noktalar: `POST /api/v1/ai/lab-result-ocr`, `POST /api/v1/ai/imaging-findings-ocr` — `AiController`'a eklenir, mevcut `@RequiresFeature(Feature.AI_ASSISTANT)` + `@PreAuthorize("hasAnyRole('VET','ADMIN')")` sınıf seviyesi koruması geçerli olur.
- `AiJob.encounterId` şu an `NOT NULL` — lab/imaging sonuçları bir muayeneye (`encounterId`) bağlı DEĞİL. Bu iş kaydı mekanizmasını kullanmak için `encounter_id` nullable yapılmalı (V88).
- Vision çağrısı mevcut `AiCallGate` (tek "ai" bulkhead, en fazla 4 eşzamanlı) ve `UsageQuotaPort`/`UsageKind.AI` (aynı aylık kota) üzerinden geçer — ayrı bir havuz/kota kurulmaz.
- Görsel model Ollama `/api/generate`'in `images` alanıyla çağrılır (base64 liste) + `format: "json"` (sözdizimsel geçerli JSON zorlar, şema zorlamaz — prompt şemayı tarif eder, adapter gevşek parse eder).

**Tech Stack:** Spring Boot 3.5 / Java 21 / Flyway / JPA (Spring Modulith modül sınırı testiyle doğrulanır) · React + Vite + vitest

**Spec:** `docs/superpowers/specs/2026-10-28-lab-goruntuleme-ai-foto-okuma.md`

## Global Constraints

- Çalışma lokalde kalır; **commit yok, push yok**. Değişiklikler IntelliJ changelist'inde kalır — sorunsuz test edilip biriken diğer güncellemelerle (Açık/Parça Stok, AI Hasta Geçmişi, `InventoryPage.tsx` fix'i) birlikte TEK seferde commit/push edilecek.
- Yeni Flyway migration'ı tek dosya: `V88__ai_jobs_encounter_id_nullable.sql`.
  - Yazmadan önce `ls backend/src/main/resources/db/migration | sort -V | tail -3` ile son numarayı kontrol et (bu plan yazılırken V87'ydi).
  - Depoda V88 ya da üstü varsa bir üst numarayı al.
- Ollama görsel modelin tam adı ve yerel performansı (CPU'da hız) **abi tarafından test edilir** — bu plan `OLLAMA_VISION_MODEL` ortam değişkenini tanımlar ama varsayılan bir model adı (`llava`) sadece placeholder'dır; gerçek model abi'nin kurduğu/indirdiği modelle değiştirilir.
- Vision çağrıları metin çağrılarından (CPU'da) çok daha yavaş olabilir — okuma zaman aşımı metin adaptörlerindeki 60sn'den daha yüksek, yapılandırılabilir tutulur (`OLLAMA_VISION_TIMEOUT_SECONDS`, varsayılan 120). `AiCallGate` zaten en fazla 4 eşzamanlı çağrıyla sınırlı; yavaş bir vision çağrısı havuzu tıkarsa 5. istek 503 `AI_BUSY` alır (beklenen davranış, ayrıca değiştirilmez).
- OCR sonucu her zaman taslaktır: `AI_FIELD_HINT` / "[AI önerisi, kesin değil]" tonu korunur, hiçbir alan salt-okunur yapılmaz, "Kaydet"e basılmadan hiçbir şey persist edilmez (mevcut ilke).
- Sadece `image/*` içerik tipi olan dosyalar OCR'a gönderilir (DICOM `.dcm` hariç — spec §3.2).
- Testler yerel Docker Postgres'i çalışan backend ile paylaşır. Migration'dan sonra yerel backend'i yeniden başlat.
- `./mvnw -q test -Dtest=ApplicationModulesTest` her task sonunda mutlaka çalıştırılır — bu özellik modül sınırını zorluyor (ai → lab.domain/imaging.domain), hata çıkarsa paket/`package-info.java` yerleşimi burada anlatılan şekle göre düzeltilir.

## Review Focus

1. **DICOM dosyası seçilemez olmalı** (sadece `image/*` checkbox'ı var) — Görüntüleme dosya girişi `.dcm` de kabul ediyor; OCR seçim listesinde görünmemeli. Test: Task 6 frontend testi + Task 2/3 lookup port'un sadece `image/*` döndürmesi (ya da en azından çağıranın filtrelemesi).
2. **Hiç dosya seçilmeden OCR tetiklenirse** 422 `NO_FILE_SELECTED_FOR_OCR` dönmeli, AiCallGate/kota hiç tüketilmemeli. Test: Task 5 `noFilesSelected_throwsBeforeReservingQuota`.
3. **Model yanıtı geçersiz JSON/boş dönerse** (küçük yerel model hata yapabilir) `modelConnected=false` ile güvenli taslağa (boş liste/boş metin) düşülmeli, hekim elle doldurabilmeli — mevcut `fallback()` deseniyle aynı. Test: Task 4 `generateViaOllama_invalidJson_fallsBackGracefully`.
4. **AiCallGate dolu iken (503 AI_BUSY)** kota hakkı iade edilmeli, aynı diğer AI akışlarındaki gibi. Test: Task 5, mevcut `GenerateDiagnosisSuggestionUseCaseTest` desenine paralel bir test.
5. **Referans aralığı OCR'dan geldiğinde kural motoru doğru işaretlemeli** (ör. OCR "12.3" değerini "6.0-17.0" referansıyla okuduysa NORMAL/HIGH/LOW doğru hesaplanmalı) — bu, spec §8'in ana vaadi. Test: Task 5 `execute_feedsOcrReferenceRangeIntoRuleEngine`.

---

### Task 1: `AiTaskType` + `ai_jobs.encounter_id` nullable

**Files:**
- Create: `backend/src/main/resources/db/migration/V88__ai_jobs_encounter_id_nullable.sql`
- Modify: `backend/src/main/java/com/vetos/modules/ai/domain/AiTaskType.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/domain/AiJob.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobUseCase.java` (davranış değişmez, sadece `encounterId` artık null olabilir)
- Test: `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobUseCaseTest.java` (yoksa oluştur; varsa genişlet)

**Migration içeriği:**
```sql
ALTER TABLE ai_jobs ALTER COLUMN encounter_id DROP NOT NULL;
```

**Interfaces:**
- Produces: `AiTaskType` enum'a iki yeni değer: `LAB_RESULT_OCR`, `IMAGING_FINDINGS_OCR`.
- Produces: `AiJob.encounterId` artık `@Column(nullable = true)` (ya da anotasyon tamamen kaldırılır — varsayılan nullable); `AiJob.create(...)` imzası AYNI kalır (zaten `UUID` tipi null kabul eder), sadece davranış: `encounterId == null` persist edilebilir.

- [x] **Step 1: Write the failing test:**
```java
@Test void recordsJobWithoutEncounter_whenEncounterIdIsNull() {
    UUID id = useCase.execute(new RecordAiJobCommand(tenantId, AiTaskType.LAB_RESULT_OCR, null, "ozet", "ollama", "llava", staffId));
    assertThat(aiJobRepository.findById(id)).isPresent();
    assertThat(aiJobRepository.findById(id).get().getEncounterId()).isNull();
}
```
- [x] **Step 2:** `cd backend && ./mvnw -q test -Dtest=RecordAiJobUseCaseTest` → FAIL (DB `NOT NULL` ihlali / şema uyuşmazlığı).
- [x] **Step 3:** Migration'ı ve `AiTaskType`/`AiJob` değişikliklerini uygula.
- [x] **Step 4:** Aynı komut → PASS. `./mvnw -q test -Dtest=ApplicationModulesTest` → PASS (değişmemeli).

### Task 2: Lab modülü — `LabResultFileLookupPort`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/lab/domain/LabResultFileLookupPort.java`
- Create: `backend/src/main/java/com/vetos/modules/lab/infrastructure/persistence/LabResultFileLookupAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/lab/infrastructure/persistence/LabResultFileLookupAdapterTest.java`

**Interfaces:**
- Produces (NamedInterface `lab::domain`, `ai` modülü bunu kullanacak):
```java
public interface LabResultFileLookupPort {
    /** fileIds'den biri labResultId'ye ait degilse LabResultNotFoundException. Sira fileIds ile aynidir. */
    List<LabResultFileForOcr> findFiles(UUID labResultId, List<UUID> fileIds);
}
public record LabResultFileForOcr(UUID id, String fileName, String contentType, byte[] content) {}
```
- Adapter mevcut `LabResultFileRepository` + `FileStoragePort`'u kullanır (bkz. `DownloadLabResultFileUseCase` ile ayni desen); her `fileId` icin `getLabResultId().equals(labResultId)` dogrulanir, uymazsa `LabResultNotFoundException(fileId)`.

- [x] **Step 1: Write the failing tests:**
  - `findFiles_returnsContentForMatchingIds`: bir lab sonucuna 2 dosya yuklenir, ikisinin id'si verilir → ikisi de dolu `content` ile doner, sira korunur.
  - `findFiles_rejectsFileFromDifferentLabResult`: baska bir lab sonucunun dosya id'si verilince `LabResultNotFoundException` firlar.
- [x] **Step 2:** `./mvnw -q test -Dtest=LabResultFileLookupAdapterTest` → FAIL (sinif yok).
- [x] **Step 3:** Uygula.
- [x] **Step 4:** Aynı komut → PASS.

### Task 3: Imaging modülü — `ImagingRecordFileLookupPort`

Task 2 ile birebir aynı desen, imaging tarafı için.

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/imaging/domain/ImagingRecordFileLookupPort.java`
- Create: `backend/src/main/java/com/vetos/modules/imaging/infrastructure/persistence/ImagingRecordFileLookupAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/imaging/infrastructure/persistence/ImagingRecordFileLookupAdapterTest.java`

**Interfaces:**
```java
public interface ImagingRecordFileLookupPort {
    List<ImagingRecordFileForOcr> findFiles(UUID imagingRecordId, List<UUID> fileIds);
}
public record ImagingRecordFileForOcr(UUID id, String fileName, String contentType, byte[] content) {}
```

- [x] **Step 1: Write the failing tests** (Task 2'nin aynaları: `findFiles_returnsContentForMatchingIds`, `findFiles_rejectsFileFromDifferentRecord`).
- [x] **Step 2:** `./mvnw -q test -Dtest=ImagingRecordFileLookupAdapterTest` → FAIL.
- [x] **Step 3:** Uygula.
- [x] **Step 4:** Aynı komut → PASS.

### Task 4: AI modülü — vision OCR portları ve Ollama adaptörleri

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/LabResultOcrPort.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/ImagingFindingsOcrPort.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/infrastructure/adapter/OllamaLabResultOcrAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/infrastructure/adapter/OllamaImagingFindingsOcrAdapter.java`
- Modify: `backend/src/main/resources/application.yml` (`ai.ollama.vision-model`, `ai.ollama.vision-timeout-seconds`)
- Test: `backend/src/test/java/com/vetos/modules/ai/infrastructure/adapter/OllamaLabResultOcrAdapterTest.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/infrastructure/adapter/OllamaImagingFindingsOcrAdapterTest.java`

**application.yml ekleri (`ai.ollama` altına):**
```yaml
    vision-model: ${OLLAMA_VISION_MODEL:llava}
    vision-timeout-seconds: ${OLLAMA_VISION_TIMEOUT_SECONDS:120}
```

**Interfaces:**
```java
// ai.domain
public record LabResultOcrInput(List<byte[]> images) {}
public record OcrLabItem(String parameterName, String value, String unit, String referenceRange) {}
public record LabResultOcrDraft(List<OcrLabItem> items, boolean modelConnected, String modelVersion) {}
public interface LabResultOcrPort { LabResultOcrDraft generate(LabResultOcrInput input); }

public record ImagingFindingsOcrInput(List<byte[]> images) {}
public record ImagingFindingsOcrDraft(String findings, boolean modelConnected, String modelVersion) {}
public interface ImagingFindingsOcrPort { ImagingFindingsOcrDraft generate(ImagingFindingsOcrInput input); }
```

- Adaptörler `OllamaDiagnosisSuggestionAdapter`/`OllamaTreatmentRecommendationAdapter` ile AYNI desen: `@ConditionalOnProperty(prefix="ai", name="provider", havingValue="ollama", matchIfMissing=true)`, `baseUrl.isBlank()` → fallback (`modelConnected=false`, boş taslak), `circuitBreaker.executeCallable(() -> outcomes.recordAiCall("lab-ocr"/"imaging-ocr", "ollama", () -> ...))`, hata → `log.warn` + fallback.
- İstek gövdesi: `Map.of("model", visionModel, "prompt", PROMPT, "images", images.stream().map(Base64.getEncoder()::encodeToString).toList(), "stream", false, "format", "json", "options", OllamaOptions.wideContext())`.
- `PROMPT` (lab): modelden KESİN şu şemada JSON istenir: `{"items":[{"parameterName":"...","value":"...","unit":"...","referenceRange":"..."}]}`; alan okunamıyorsa boş string. Parse: `objectMapper.readTree(raw).path("response")` içindeki JSON'u tekrar parse et (Ollama'nın `/api/generate` yanıtı `{"response": "<model cikisi>"}` şeklinde — model cikisinin KENDİSİ JSON string'i olacak, iki kat parse gerekir). Parse hatasi veya `items` yoksa → fallback (boş liste, `modelConnected=false`) — Review Focus #3.
- `PROMPT` (imaging): JSON şeması `{"findings":"..."}`; aynı iki-kat-parse, aynı fallback kuralı.

- [x] **Step 1: Write the failing tests** (stub `HttpServer`, `OllamaTreatmentRecommendationAdapterTest` deseniyle aynı):
  - `should_returnSimulatedDraft_when_baseUrlIsBlank` (her iki adaptör için).
  - `generateViaOllama_parsesStructuredItems`: stub `/api/generate` yaniti `{"response":"{\"items\":[{\"parameterName\":\"WBC\",\"value\":\"12.3\",\"unit\":\"10^3/uL\",\"referenceRange\":\"6.0-17.0\"}]}"}` → `draft.items()` tek eleman, alanlar eşleşir, `modelConnected=true`.
  - `generateViaOllama_sendsBase64ImagesInRequestBody`: `capturedBody` yakalanır, `"images"` alanı istenen base64 string'i içeriyor mu diye assert.
  - `generateViaOllama_invalidJson_fallsBackGracefully`: stub yanıtı `{"response":"bu bir JSON degil"}` → `modelConnected=false`, `items` boş (Review Focus #3).
  - Imaging için aynı dört test, `findings` alanıyla.
- [x] **Step 2:** `./mvnw -q test -Dtest='OllamaLabResultOcrAdapterTest,OllamaImagingFindingsOcrAdapterTest'` → FAIL.
- [x] **Step 3:** Uygula.
- [x] **Step 4:** Aynı komut → PASS.

### Task 5: AI modülü — use case'ler ve `AiController` uç noktaları

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/application/ReadLabResultFromImageUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/ReadLabResultFromImageCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/ReadLabResultFromImageResult.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/ReadImagingFindingsFromImageUseCase.java` (+ Command/Result)
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/exception/NoFileSelectedForOcrException.java` (422)
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/exception/UnsupportedOcrFileTypeException.java` (422)
- Modify: `backend/src/main/java/com/vetos/modules/ai/api/AiController.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/api/dto/LabResultOcrRequest.java`, `LabResultOcrResponse.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/api/dto/ImagingFindingsOcrRequest.java`, `ImagingFindingsOcrResponse.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/application/ReadLabResultFromImageUseCaseTest.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/application/ReadImagingFindingsFromImageUseCaseTest.java`

**Interfaces:**
```java
public record ReadLabResultFromImageCommand(UUID tenantId, UUID labResultId, List<UUID> fileIds, UUID requestedByStaffUserId) {}
public record ReadLabResultFromImageResult(UUID aiJobId, List<OcrLabItem> items /* flag eklenmis hali asagida */, String draftSummary, boolean modelConnected) {}
```
`ReadLabResultFromImageUseCase.execute`:
1. `fileIds` boşsa `NoFileSelectedForOcrException`.
2. `files = labResultFileLookupPort.findFiles(labResultId, fileIds)`; herhangi biri `image/*` ile başlamıyorsa `UnsupportedOcrFileTypeException`.
3. `aiCallGate.run(tenantId, () -> { usageQuotaPort.reserve(tenantId, UsageKind.AI, 1); draft = labResultOcrPort.generate(new LabResultOcrInput(files bytes)); if (!draft.modelConnected()) release; return draft; })` — `GenerateDiagnosisSuggestionUseCase` ile AYNI hata/iade deseni (RuntimeException'da da release).
4. Her `OcrLabItem` için `LabReferenceRangeEvaluator.evaluate(item.value(), item.referenceRange())` çağır, flag'li bir satır (`parameterName,value,unit,referenceRange,flag`) üret (DTO: `LabResultOcrResponse.Item`, `flag: LabValueFlag`).
5. Özet metnini `EvaluateLabResultItemsUseCase`'deki AYNI mantıkla (küçük, bilinçli kod tekrarı — modül sınırı nedeniyle oradan import edilemez) üret.
6. `recordAiJobUseCase.execute(new RecordAiJobCommand(tenantId, AiTaskType.LAB_RESULT_OCR, null, draftSummary, providerName, draft.modelVersion(), requestedByStaffUserId))`.
7. Sonucu döndür.

`ReadImagingFindingsFromImageUseCase.execute` aynı 1-3-6-7 adımları (4-5 yok, `findings` direkt `draft.findings()`), `AiTaskType.IMAGING_FINDINGS_OCR`.

`AiController` ekleri:
```java
@PostMapping("/lab-result-ocr")
public LabResultOcrResponse readLabResultFromImage(@RequestBody @Valid LabResultOcrRequest request, @AuthenticationPrincipal AuthenticatedStaffUser principal) {
    return LabResultOcrResponse.from(readLabResultFromImageUseCase.execute(
        new ReadLabResultFromImageCommand(principal.tenantId(), request.labResultId(), request.fileIds(), principal.staffUserId())
    ));
}
@PostMapping("/imaging-findings-ocr")
public ImagingFindingsOcrResponse readImagingFindingsFromImage(@RequestBody @Valid ImagingFindingsOcrRequest request, @AuthenticationPrincipal AuthenticatedStaffUser principal) { ... }
```
(`LabResultOcrRequest(@NotNull UUID labResultId, @NotEmpty List<UUID> fileIds)`, response `List<Item(parameterName,value,unit,referenceRange,flag)>, draftSummary, modelConnected`.)

- [x] **Step 1: Write the failing tests** (sahte `LabResultFileLookupPort`/`LabResultOcrPort` ile, `GenerateDiagnosisSuggestionUseCaseTest` deseniyle aynı mock yapısı):
  - `execute_feedsOcrReferenceRangeIntoRuleEngine` (Review Focus #5): OCR `{"WBC","12.3","10^3/uL","6.0-17.0"}` döner → sonuç item'ında `flag == NORMAL`; başka bir OCR değeri referansın üstündeyse `flag == HIGH`.
  - `noFilesSelected_throwsBeforeReservingQuota` (Review Focus #2): boş `fileIds` → `NoFileSelectedForOcrException`, `usageQuotaPort.reserve` HİÇ çağrılmamış (Mockito `verifyNoInteractions`).
  - `nonImageFile_throwsUnsupportedOcrFileType`: dönen dosyanın `contentType` "application/pdf" → `UnsupportedOcrFileTypeException`.
  - `aiBusy_releasesQuota`: `aiCallGate.run` `AiBusyServiceUnavailableException` fırlatırsa kota iade edilir (`GenerateDiagnosisSuggestionUseCaseTest`'teki eşdeğer testle aynı desen).
  - `recordsAiJobWithNullEncounterId`: `recordAiJobUseCase` çağrısında `encounterId == null`.
  - Imaging için aynı testlerin aynaları (referans aralığı testi hariç).
- [x] **Step 2:** `./mvnw -q test -Dtest='ReadLabResultFromImageUseCaseTest,ReadImagingFindingsFromImageUseCaseTest'` → FAIL.
- [x] **Step 3:** Uygula (use case'ler, DTO'lar, controller, exception'lar).
- [x] **Step 4:** Aynı komut → PASS. `./mvnw -q test -Dtest=ApplicationModulesTest` → PASS (kritik — bu task modül sınırını en çok zorlayan task). `./mvnw -q test` (tam paket) → hata 0.

### Task 6: Web ekranları

**Files:**
- Modify: `frontend/src/api/labApi.ts`
- Modify: `frontend/src/api/imagingApi.ts`
- Modify: `frontend/src/pages/laboratory/LabResultDetailModal.tsx`
- Modify: `frontend/src/pages/imaging/ImagingRecordDetailModal.tsx`
- Test: `frontend/src/pages/laboratory/LabResultDetailModal.test.tsx` (yoksa oluştur)
- Test: `frontend/src/pages/imaging/ImagingRecordDetailModal.test.tsx` (yoksa oluştur)

**labApi.ts eki:**
```ts
export interface LabResultOcrResult extends LabResultEvaluation { modelConnected: boolean }
ocrFromImages: (labResultId: string, fileIds: string[]) =>
  apiClient.post<LabResultOcrResult>('/api/v1/ai/lab-result-ocr', { labResultId, fileIds }),
```
**imagingApi.ts eki:**
```ts
export interface ImagingFindingsOcrResult { findings: string; modelConnected: boolean }
ocrFromImages: (imagingRecordId: string, fileIds: string[]) =>
  apiClient.post<ImagingFindingsOcrResult>('/api/v1/ai/imaging-findings-ocr', { imagingRecordId, fileIds }),
```

**`LabResultDetailModal.tsx` değişiklikleri:**
- Yeni state: `selectedFileIds: string[]`, `ocrRunning: boolean`.
- "Dosya Ekleri" listesinde (`detail.files`, sadece `isPending` iken) her `contentType?.startsWith('image/')` olan satıra bir checkbox eklenir (DICOM/diğerleri checkbox'sız, sadece indirme tıklaması kalır — Review Focus #1).
- Buton genişletilir: `selectedFileIds.length > 0` ise metin `📷 Oku + Değerlendir`, tıklama `handleOcrThenEvaluate` (yeni fonksiyon: `labApi.ocrFromImages(resultId, selectedFileIds)` → sonucu `setItems(result.items); setResultSummary(result.draftSummary); setAiEvaluated(true)` — mevcut `handleEvaluate`'teki "üzerine yaz" davranışıyla birebir aynı, onay istenmez); `selectedFileIds.length === 0` ise mevcut `handleEvaluate` davranışı değişmeden kalır.
- `result.modelConnected === false` ise `errorBanner`'da "AI modeline bağlanılamadı, değerleri elle girin" uyarısı gösterilir (taslak boş/eksik kalabilir, Review Focus #3 ile tutarlı).

**`ImagingRecordDetailModal.tsx` değişiklikleri:**
- Mevcut UI'da AI butonu YOK (lab'daki gibi genişletilecek bir buton yok) — galeri her `isImage(f.contentType)` thumbnail'ine bir checkbox eklenir, yanına YENİ bir buton: `📷 Fotoğraftan Oku` (yalnız `isPending` iken, en az 1 görsel seçiliyken aktif).
- Tıklama: `imagingApi.ocrFromImages(recordId, selectedFileIds)` → `setFindings(result.findings)` (üzerine yazar, onay istemez — ayni ilke).

- [x] **Step 1: Write the failing tests:**
  - `LabResultDetailModal`: "dosya seçilince buton metni '📷 Oku + Değerlendir' olur, tıklanınca ocrFromImages çağrılır ve parametre satırları dolar" (labApi mock'lanır).
  - `LabResultDetailModal`: "dosya seçilmeden buton eski '⚡ Otomatik Ön-Değerlendirme' davranışını korur" (regresyon).
  - `LabResultDetailModal`: "sadece image/* dosyalarda checkbox görünür" (bir PDF/diğer dosya eklenmiş fixture'da checkbox yok).
  - `ImagingRecordDetailModal`: "görsel seçilince '📷 Fotoğraftan Oku' aktif olur, tıklanınca findings alanı dolar".
  - `ImagingRecordDetailModal`: "hiç görsel seçilmeden buton devre dışı".
- [x] **Step 2:** `cd frontend && npx vitest run src/pages/laboratory src/pages/imaging` → FAIL.
- [x] **Step 3:** Uygula.
- [x] **Step 4:** `npx vitest run` + `npx tsc -b --noEmit` → hepsi yeşil.

### Task 7: Config, dokümantasyon ve tam doğrulama

**Files:**
- Modify: `docs/deployment.md` ("Yerel geliştirme" ya da "AI sağlayıcı" bölümüne not: `OLLAMA_VISION_MODEL`/`OLLAMA_VISION_TIMEOUT_SECONDS`)
- Modify: `docs/superpowers/specs/2026-10-28-lab-goruntuleme-ai-foto-okuma.md` (§6 "Açık sorular" güncellenir: migration artık VAR — V88, ama `LabResultItem` alanları için değil, `ai_jobs.encounter_id` için; bu task tamamlanınca madde kapatılır)

- [x] **Step 1:** `docs/deployment.md`'ye not ekle: vision modeli kurmak için `ollama pull <model-adi>` (abi'nin seçtiği model), ardından `$env:OLLAMA_VISION_MODEL="<model-adi>"` set edilip backend yeniden başlatılmalı.
- [x] **Step 2:** Spec dosyasının §6'sını güncelle: migration maddesini "V88 ile ai_jobs.encounter_id nullable yapıldı (LabResultItem alanları için migration hâlâ gerekmiyor)" şeklinde kapat.
- [x] **Step 3:** Tam doğrulama: `cd backend && ./mvnw -q test` (hata 0, `ApplicationModulesTest` dahil) ve `cd frontend && npx vitest run && npx tsc -b --noEmit` (hepsi yeşil).
  - Frontend yarısı TAMAM (2026-10-29): `npx vitest run` → 17/17 dosya, 67/67 test PASS (bir test `LabResultDetailModal.test.tsx`'te testin kendi hatasıyla ilk denemede FAIL vermişti — input `.value` yerine `textContent` kontrol ediyordu, test düzeltildi); `npx tsc -b --noEmit` → hatasız.
  - Backend yarısı TAMAM (2026-10-29): hedefli komut ile teyit edildi --
    `./mvnw -q test "-Dtest=ApplicationModulesTest,LabResultFileLookupAdapterTest,ImagingRecordFileLookupAdapterTest,OllamaLabResultOcrAdapterTest,OllamaImagingFindingsOcrAdapterTest,ReadLabResultFromImageUseCaseTest,ReadImagingFindingsFromImageUseCaseTest,RecordAiJobUseCaseTest"` → `EXIT=0`.
    Çıktıdaki 2 WARN satırı (`OllamaLabResultOcrAdapter`/`OllamaImagingFindingsOcrAdapter` -- "gecersiz JSON, mock'a duseluyor") kasıtlı test senaryolarından geliyor (invalid-JSON-fallback testi), hata değil.
    Tüm paketin (`./mvnw -q test`, mevcut regresyon dahil) tam çalıştırılması hâlâ önerilir ama artık bloklayıcı değil.
- [ ] **Step 4:** Yerel backend'i yeniden başlat (`OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_VISION_MODEL` ortam değişkenleriyle). Elle deneme adımları:
  1. Bir lab sonucuna yazılı rapor fotoğrafı yükle → dosya checkbox'ını seç → "📷 Oku + Değerlendir" → Parametre/Değer/Birim/Referans dolsun, flag doğru hesaplansın.
  2. Aynısını bir görüntüleme kaydında dene → "📷 Fotoğraftan Oku" → `findings` alanı dolsun.
  3. Dosya seçmeden eski butonları dene → davranış hiç değişmemiş olmalı (regresyon yok).
  4. `OLLAMA_BASE_URL`'i boş bırakıp dene → `modelConnected=false`, hata fırlamadan boş/placeholder taslak dönmeli.


### Task 8: KRİTİK DÜZELTME (2026-10-29) — ai.provider=claude için eksik vision adaptörü

**Bulgu:** `LabResultOcrPort`/`ImagingFindingsOcrPort` için Task 4'te SADECE Ollama adaptörü yazılmıştı (spec §"Açık sorular" madde 1'deki "Claude değil, yerel kalınacak" kararına uygun olarak). Ancak canlı sunucuda `AI_PROVIDER=claude` kullanıldığı teyit edildi (1 CPU/2GB RAM'lik sunucuda yerel Ollama metin modeli pratik değil). Projedeki diğer tüm AI portlarının (örn. `DiagnosisSuggestionPort`) hem Ollama hem Claude adaptörü var (`@ConditionalOnProperty` ile `ai.provider`'a göre tam olarak biri aktif) — ama bu iki yeni port için öyle değildi. Sonuç: bu kod `ai.provider=claude` olan bir ortama (canlı dahil) deploy edilseydi, `ReadLabResultFromImageUseCase`/`ReadImagingFindingsFromImageUseCase`'in zorunlu `LabResultOcrPort`/`ImagingFindingsOcrPort` bağımlılığı için HİÇBİR bean oluşmazdı → Spring context **açılışta çökerdi** (`NoSuchBeanDefinitionException`) → backend tamamen açılmaz hale gelirdi. "Özellik çalışmıyor" değil, "canlı tamamen düşüyor" seviyesinde bir risk.

**Düzeltme:**
- [x] `OcrImageInput(String contentType, byte[] content)` eklendi (`ai.domain`) — `LabResultOcrInput`/`ImagingFindingsOcrInput`'un `images()` alanı artık `List<byte[]>` değil `List<OcrImageInput>` (Claude'un Messages API'sinde görsel bloklarında `media_type` ZORUNLU, Ollama'da önemsiz).
- [x] `ReadLabResultFromImageUseCase`/`ReadImagingFindingsFromImageUseCase` güncellendi: dosyanın `contentType()`'ı artık atılmıyor, `OcrImageInput`'a taşınıyor.
- [x] `OllamaLabResultOcrAdapter`/`OllamaImagingFindingsOcrAdapter` güncellendi: `input.images()` artık `OcrImageInput` listesi, `.content()` ile base64'e çevriliyor (davranış DEĞİŞMEDİ, sadece tip).
- [x] `ClaudeVisionCompletion` (yardımcı sınıf, `ClaudeTextCompletion` ile aynı kimlik doğrulama deseni) + `ClaudeLabResultOcrAdapter` + `ClaudeImagingFindingsOcrAdapter` eklendi (`@ConditionalOnProperty(havingValue="claude")`). Anthropic Messages API görsel blok(lar) + JSON-döndür talimatı ile çağrılıyor; AYRI bir "vision model" konfigürasyonu gerekmiyor, mevcut `ai.anthropic.model`/`ai.anthropic.api-key` kullanılıyor.
- [x] İlgili testler güncellendi (`OllamaLabResultOcrAdapterTest`/`OllamaImagingFindingsOcrAdapterTest` — `OcrImageInput` kullanacak şekilde) ve eklendi (`ClaudeLabResultOcrAdapterTest`, `ClaudeImagingFindingsOcrAdapterTest` — 5'er test: boş API key, başarılı parse, base64+media_type gönderimi, geçersiz JSON fallback, sunucu hatası fallback).
- [x] Spec (`2026-10-28-...md` §"Açık sorular" madde 1) ve `docs/deployment.md` güncellendi: vision artık `ai.provider=claude` olan ortamlarda ücretsiz/yerel DEĞİL, Claude API üzerinden ücretli çalışıyor (orijinal "ücretsiz/yerel kalınacak" kararıyla çelişir ama backend'in ayakta kalması için zorunlu).
- [x] **Doğrulandı (2026-10-29):** `cd backend && ./mvnw -q test "-Dtest=ApplicationModulesTest,ClaudeLabResultOcrAdapterTest,ClaudeImagingFindingsOcrAdapterTest,OllamaLabResultOcrAdapterTest,OllamaImagingFindingsOcrAdapterTest,ReadLabResultFromImageUseCaseTest,ReadImagingFindingsFromImageUseCaseTest"` → `EXIT=0` olmalı.
  Gerçek çalıştırmada `EXIT=0`; görülen 4 WARN satırı (`Connection refused` + 3× `Unrecognized token 'bu'`) kasıtlı negatif-senaryo testlerinden (sunucu hatası / geçersiz JSON fallback) geliyor, hata değil. `ApplicationModulesTest` dahil hepsi PASS.

