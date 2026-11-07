# Dijital Onay Formları Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Klinik yöneticisi kâğıt onay formunu fotoğraftan (AI taslağı + düzenleme) e-form şablonuna çevirebilsin; personel şablonu hasta kartından/muayeneden tablette açıp bilinen bilgileri dolu getirsin, müşteri elle imzalasın; imzalı form değiştirilemez şekilde saklansın ve PDF'i alınabilsin.

**Architecture:** Yeni `consentform` modülü (şablon, kaynak görsel, imzalı form; OpenPDF ile istek anında PDF). Görselden taslak çıkarma `ai` modülünde lab OCR deseniyle (`AiCallGate` + kota + Claude/Ollama adaptörü); `ai`, görsele `consentform.domain`'deki lookup portuyla erişir. Kayda geri yazma, döngüsel modül bağımlılığını önlemek için `patient` modülünde ayrı dar bir uç noktadır; frontend imzalı form kaydedildikten sonra onu çağırır. Önceden doldurma ve fark (diff) hesabı frontend'de mevcut hasta/sahip profil uç noktalarından yapılır.

**Tech Stack:** Spring Boot 3 / Java 21, Flyway, Spring Modulith, OpenPDF 3.0.5, JUnit 5 + Mockito + MockMvc; React + TypeScript + Vite, Vitest (jsdom).

**Spec:** `docs/superpowers/specs/2026-11-07-dijital-onay-formlari-design.md`

## Global Constraints

- Modül sınırları: `consentform` → `patient.domain` (PatientLookupPort/OwnerLookupPort), `tenant.domain` (TenantLookupPort, personel adı için encounter raporunun kullandığı lookup), `platform.storage`. `ai` → `consentform.domain`. `patient` hiçbir şekilde `consentform`'a bağlanmaz. `ApplicationModulesTest` geçmeli.
- Tüm tablolarda `tenant_id` + `@TenantId`. Yeni migration: `V95__consent_forms.sql` (prod Flyway sürümleri deploy öncesi kontrol edilir).
- Şablon içeriği, imza anı kopyası ve değerler veritabanında **TEXT** kolonda saklanır (jsonb DEĞİL — jsonb anahtar sırasını değiştirir, SHA-256 özeti bozulur).
- Dosyalar yalnız `FileStoragePort` üzerinden (kaynak görsel, imza PNG).
- Şablonda **tam olarak bir** `SIGNATURE` öğesi.
- Geri yazılabilir bağlantılar yalnız: `owner.phone`, `owner.secondaryPhone`, `owner.nationalId`, `owner.birthDate`, `owner.address`, `owner.email`, `patient.color`, `patient.weightKg`, `patient.microchipNumber`.
- İmzalayan "Başka biri" ise hiçbir `owner.*` geri yazma yapılmaz; imzalayan ad/TC/telefon yalnız imzalı formda kalır.
- Roller: şablon yazma/arşiv/kaynak görsel/AI taslak → ADMIN; şablon okuma (liste/detay/boş PDF) → VET, TECHNICIAN, RECEPTIONIST, ADMIN; imzalı form oluştur/listele/gör/PDF → VET, TECHNICIAN, RECEPTIONIST, ADMIN; iptal → VET, ADMIN; form geri yazma uç noktası → VET, TECHNICIAN, RECEPTIONIST, ADMIN.
- AI çıktısı her zaman taslak; mavi (`--color-ai-600` ailesi) "AI taslağı — onayınızı bekliyor". Kural tabanlı uyarılar terrakota (`warning`).
- Renkler yalnız `var(--color-*)`; Ayarlar sekmesi yalnız `SETTINGS_TABS`'e satır eklenerek; kullanıcıya görünen metinler Türkçe.
- Fotoğraf: `prepareImage(file, 'document')` + `capture="environment"`.
- Repo kuralı: commit atılmaz; her görevin son adımı "değişiklikleri gözden geçir".

## Review Focus

1. **Şablon sonradan düzenlendiğinde eski imzalı form değişmemeli** (görünüm + PDF imza anı kopyasından) → Task 4 testi `should_renderFromSnapshot_after_templateEdited`.
2. **"Başka biri imzalıyor" seçiliyken sahip kaydına hiçbir şey yazılmamalı** (frontend diff boş + sunucu `owner.*` geri yazmayı reddeder) → Task 6 ve Task 8 testleri.
3. **Geçersiz TC no geri yazma satırında seçilemez olmalı, formda kalmalı** → Task 6 testi; sunucu da reddeder → Task 4b testi.
4. **AI aynı formda birden fazla imza yeri / şemaya uymayan çıktı döndürürse** yönetici yine düzenlenebilir bir taslak almalı (fazlası silinir + uyarı; bozuk çıktıda bir kez yeniden deneme, sonra boş taslak) → Task 3 testleri.
5. **İmzalı formun içeriği veritabanında elle değiştirilirse** görüntülemede "bütünlük doğrulanamadı" görünmeli → Task 4 testi `should_flagIntegrityFailure_when_storedValuesChanged`.

---

## Paylaşılan içerik modeli (Task 1 tanımlar, herkes kullanır)

JSON (backend record'ları ve frontend tipleri birebir):

```
FormTemplateContent { title: string, sections: FormSection[] }
FormSection        { title: string | null, items: FormItem[] }
FormItem {
  type: 'TEXT_BLOCK'|'SHORT_TEXT'|'LONG_TEXT'|'DATE'|'PHONE'|'NUMBER'|'CHOICE'|'SIGNATURE',
  code: string | null,      // TEXT_BLOCK dışında zorunlu; şablonda benzersiz; [a-z0-9_]{1,40}
  label: string | null,     // TEXT_BLOCK dışında zorunlu
  text: string | null,      // yalnız TEXT_BLOCK; {{code}} yer tutucuları AYNI bölümdeki bir alanın kodu olmalı
  required: boolean,
  binding: string | null,   // aşağıdaki BINDINGS listesinden biri veya null; SIGNATURE ve TEXT_BLOCK'ta null
  unit: string | null,      // yalnız NUMBER
  options: string[] | null, // yalnız CHOICE, en az 2
  multiple: boolean         // yalnız CHOICE
}
Signed values: { [code: string]: string | string[] }   // SIGNATURE değerleri içermez
```

BINDINGS: `owner.fullName, owner.phone, owner.secondaryPhone, owner.nationalId, owner.birthDate, owner.address, owner.email, patient.name, patient.species, patient.breed, patient.sex, patient.age, patient.color, patient.weightKg, patient.microchipNumber, clinic.name, today`.

---

### Task 1: `consentform` modülü — şema, içerik modeli ve doğrulama

**Files:**
- Create: `backend/src/main/resources/db/migration/V95__consent_forms.sql` — tablolar:
  - `form_source_images(id uuid pk, tenant_id uuid not null, storage_ref text not null, content_type text not null, uploaded_by uuid not null, uploaded_at timestamptz not null)`
  - `form_templates(id uuid pk, tenant_id, title text not null, content text not null, source_image_id uuid null references form_source_images, status text not null check (status in ('ACTIVE','ARCHIVED')), created_by uuid not null, created_at, updated_by uuid not null, updated_at)`
  - `signed_forms(id uuid pk, tenant_id, template_id uuid not null references form_templates, template_title text not null, template_snapshot text not null, values_json text not null, signature_storage_ref text not null, patient_id uuid not null, owner_id uuid not null, encounter_id uuid null, signer_type text not null check (signer_type in ('OWNER','OTHER')), signer_name text not null, signer_national_id text null, signer_phone text null, signed_by uuid not null, signed_at timestamptz not null, client_ip text null, user_agent text null, content_digest text not null, status text not null check (status in ('VALID','VOIDED')), void_reason text null, voided_by uuid null, voided_at timestamptz null)`; indeksler `(tenant_id, patient_id)`, `(tenant_id, owner_id)`.
- Create (`backend/src/main/java/com/vetos/modules/consentform/`): `domain/package-info.java` (`@NamedInterface("domain")`), `domain/FormTemplateContent.java` (+ `FormSection`, `FormItem`, `FormItemType` records/enum), `domain/FormBinding.java` (enum with `key()` "owner.phone" vb. ve `boolean writable()`), `domain/FormTemplateContentValidator.java`, `domain/FormTemplateContentCodec.java` (Jackson ile `String toJson(FormTemplateContent)` / `FormTemplateContent fromJson(String)` — alan sırası record bildirim sırası, sabit), `domain/exception/InvalidFormTemplateException.java` (code `INVALID_FORM_TEMPLATE`, 422), `domain/FormTemplate.java`, `domain/FormSourceImage.java`, `domain/SignedForm.java` (entity'ler; `SignedForm` için setter yok, yalnız `voidForm(reason, staffId)`), repository arayüzleri + `infrastructure/persistence` adaptörleri (`docs/reference-module.md` sırası).
- Test: `backend/src/test/java/com/vetos/modules/consentform/domain/FormTemplateContentValidatorTest.java`

**Interfaces:**
- Produces: `FormTemplateContentValidator.validate(FormTemplateContent c) : void` (ihlalde `InvalidFormTemplateException`, mesaj Türkçe ve ilk ihlali söyler); `FormBinding.fromKey(String) : Optional<FormBinding>`; `FormTemplateContentCodec.toJson/fromJson`; entity'ler ve repository'ler (Task 2/4 kullanır).

- [ ] **Step 1: Write the failing tests** — `should_accept_minimalValidTemplate` (bir bölüm, bir SHORT_TEXT, bir SIGNATURE); `should_reject_whenNoSignature`; `should_reject_whenTwoSignatures`; `should_reject_duplicateCodes`; `should_reject_invalidCodeFormat` ("Ad Soyad"); `should_reject_unknownBinding` ("owner.iban"); `should_reject_bindingOnSignature`; `should_reject_choiceWithLessThanTwoOptions`; `should_reject_placeholderToFieldInOtherSection` (`{{klinik}}` başka bölümdeki kod); `should_reject_emptyTitle`; codec için `roundTrip_should_produceIdenticalJson` (toJson(fromJson(x)) == x).
- [ ] **Step 2: Run** `cd backend && ./mvnw test -Dtest=FormTemplateContentValidatorTest` → FAIL (derleme)
- [ ] **Step 3: Implement** migration, model, validator, codec, entity'ler, repository'ler.
- [ ] **Step 4: Run** `cd backend && ./mvnw test -Dtest=FormTemplateContentValidatorTest,ApplicationModulesTest` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 2: Şablon use case'leri ve API

**Files:**
- Create (`consentform/application`): `UploadFormSourceImageUseCase`, `CreateFormTemplateUseCase`, `UpdateFormTemplateUseCase`, `ArchiveFormTemplateUseCase`, `ListFormTemplatesUseCase`, `GetFormTemplateUseCase`, `GetFormSourceImageUseCase`; `domain/FormSourceImageLookupPort.java` + `infrastructure/persistence/FormSourceImageLookupAdapter.java`; `domain/exception/FormTemplateNotFoundException`, `FormSourceImageNotFoundException`.
- Create (`consentform/api`): `FormTemplatesController.java` + DTO'lar.
- Modify: `docs/api-conventions.md` (uç nokta listesi + rol matrisi satırı).
- Test: `backend/src/test/java/com/vetos/modules/consentform/application/FormTemplateUseCasesTest.java`, `backend/src/test/java/com/vetos/FormTemplatesEndpointsSecurityTest.java`

**Interfaces:**
- Consumes: Task 1.
- Produces (HTTP, hepsi `/api/v1`):
  - `POST /form-templates/source-images` multipart `file` (JPEG/PNG/WEBP baytlardan, ≤ 10 MB) → `201 {id}` — ADMIN
  - `GET /form-templates/source-images/{id}` → görsel baytları — ADMIN
  - `POST /form-templates` `{title, content, sourceImageId|null}` → `201 {id}` — ADMIN (validator çalışır; `title` = `content.title`)
  - `PUT /form-templates/{id}` `{content}` → 204 — ADMIN (ARCHIVED ise 422 `FORM_TEMPLATE_ARCHIVED`)
  - `POST /form-templates/{id}/archive` → 204 — ADMIN
  - `GET /form-templates?status=ACTIVE|ALL` (varsayılan ACTIVE) → `[{id, title, status, updatedAt, signedCount, hasSourceImage}]` — okuma rolleri
  - `GET /form-templates/{id}` → `{id, title, status, content, sourceImageId, clinicName}` — okuma rolleri (`clinicName` = `TenantLookupPort.findTenantName`)
- Produces (Java): `FormSourceImageLookupPort.find(UUID imageId) : FormSourceImageForAi` (`record FormSourceImageForAi(UUID id, String contentType, byte[] content)`; yoksa `FormSourceImageNotFoundException`) — Task 3 kullanır.

- [ ] **Step 1: Write the failing tests** — use case: `create_should_validateAndStoreContentAsCodecJson`, `create_should_rejectInvalidContent`, `update_should_rejectArchived`, `archive_should_keepSignedFormsUntouched` (yalnız status değişir), `list_should_countSignedForms`. Security: TECHNICIAN `POST /form-templates` → 403; RECEPTIONIST `GET /form-templates` → 200; başka kiracının şablonu `GET` → 404; VET `GET /form-templates/source-images/{id}` → 403; PDF/WEBP olmayan dosya `POST source-images` → 422.
- [ ] **Step 2: Run** `cd backend && ./mvnw test -Dtest=FormTemplateUseCasesTest,FormTemplatesEndpointsSecurityTest` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı komut + `ApplicationModulesTest` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 3: AI ile görselden şablon taslağı

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/ai/domain/AiTaskType.java` — `FORM_TEMPLATE_EXTRACTION` ekle.
- Create (`ai/domain`): `FormTemplateExtractionPort.java` (`FormTemplateExtractionDraft extract(OcrImageInput image)`), `FormTemplateExtractionDraft.java` (`record(String rawJson, boolean modelConnected, String modelVersion)`).
- Create (`ai/infrastructure/adapter`): `ClaudeFormTemplateExtractionAdapter.java`, `OllamaFormTemplateExtractionAdapter.java` (lab OCR adaptörlerinin birebir deseni, `@ConditionalOnProperty`; prompt içeriği §6 şemasını, BINDINGS listesini ve "satır içi boşlukları `{{code}}` yer tutucusu + aynı bölümde alan olarak çıkar; kutucuk gruplarını CHOICE yap; tam bir SIGNATURE" kurallarını anlatır, yalnız JSON döndürmesini ister).
- Create (`ai/application`): `ExtractFormTemplateFromImageUseCase.java`, `ExtractFormTemplateFromImageCommand.java` (`tenantId, sourceImageId, requestedByStaffUserId`), `ExtractFormTemplateFromImageResult.java` (`record(UUID aiJobId, boolean modelConnected, FormTemplateContent content, List<String> warnings)`).
- Modify: `ai/api/AiController.java` — `POST /api/v1/ai/form-template-drafts` `{sourceImageId}` → result; metot düzeyinde `@PreAuthorize("hasRole('ADMIN')")`.
- Test: `backend/src/test/java/com/vetos/modules/ai/application/ExtractFormTemplateFromImageUseCaseTest.java`

**Interfaces:**
- Consumes: `FormSourceImageLookupPort.find`, `FormTemplateContentCodec.fromJson`, `FormTemplateContentValidator.validate` (Task 1/2); `OcrImageFormatDetector`, `OcrImageNormalizer`, `AiCallGate`, `UsageQuotaPort`, `RecordAiJobUseCase` (mevcut).
- Produces: HTTP `POST /api/v1/ai/form-template-drafts` → `{aiJobId, modelConnected, content, warnings[]}` (Task 7 kullanır).

**Algoritma (use case):** görsel formatı lab OCR gibi kontrol edilir (HEIC → mevcut `UnsupportedOcrImageFormatException`); kota/gate lab ile aynı. `extract` → `rawJson` parse + `SIGNATURE` fazlası temizlenir (ilk tutulur, uyarı `"Birden fazla imza alanı bulundu; yalnızca ilki tutuldu."`), hiç yoksa sona `{type:SIGNATURE, code:"imza", label:"İmza", required:true}` eklenir + uyarı `"İmza alanı bulunamadı; sona eklendi."`; bilinmeyen binding → null + uyarı; ardından validate. Parse/validate başarısızsa **bir kez** daha `extract` çağrılır (ikinci çağrı ek kota harcamaz — aynı rezervasyon içinde); yine başarısızsa ya da `modelConnected=false` ise içerik = `FALLBACK` (`title:""`, tek bölüm, tek SIGNATURE öğesi) + uyarı `"Form otomatik okunamadı; alanları elle ekleyin."`, `modelConnected` gerçek değeri.

- [ ] **Step 1: Write the failing tests** (sahte port): `should_returnParsedContent_when_modelReturnsValidJson`; `should_keepFirstSignatureOnly_andWarn`; `should_appendSignature_when_missing`; `should_nullUnknownBinding_andWarn`; `should_retryOnce_then_fallback_when_jsonInvalidTwice` (port 2 kez çağrılır, içerik FALLBACK); `should_fallback_without_consumingQuota_when_modelNotConnected`; `should_rejectHeic`.
- [ ] **Step 2: Run** `cd backend && ./mvnw test -Dtest=ExtractFormTemplateFromImageUseCaseTest` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı + `ApplicationModulesTest` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 4: İmzalı form — oluşturma, okuma, iptal, bütünlük

**Files:**
- Create (`consentform/domain`): `SignedFormDigest.java` (`static String compute(String templateSnapshot, String valuesJson, String signerJson, byte[] signaturePng)` — SHA-256 hex; girdi baytları: dört parçanın UTF-8/ham baytları, aralarında tek `\n` (0x0A), imza için ham PNG baytları), `SignerType` enum.
- Create (`consentform/application`): `CreateSignedFormUseCase`, `GetSignedFormUseCase`, `ListSignedFormsUseCase`, `VoidSignedFormUseCase`, `GetSignatureImageUseCase` + komut/sonuç DTO'ları.
- Create (`consentform/api`): `SignedFormsController.java` + DTO'lar.
- Modify: `docs/api-conventions.md`.
- Test: `backend/src/test/java/com/vetos/modules/consentform/application/SignedFormUseCasesTest.java`, `backend/src/test/java/com/vetos/SignedFormsEndpointsSecurityTest.java`

**Interfaces:**
- Consumes: Task 1/2; `PatientLookupPort.findSummaryById` (hasta var mı + `ownerId`); `FileStoragePort`.
- Produces (HTTP):
  - `POST /signed-forms` body `{templateId, patientId, encounterId|null, values: {code: string|string[]}, signer: {type: 'OWNER'|'OTHER', name|null, nationalId|null, phone|null}, signaturePngBase64}` → `201 {id}`. Kurallar: şablon ACTIVE; `ownerId` hastadan alınır (istemciden alınmaz); zorunlu alanlar dolu (CHOICE: en az bir seçenek; seçenekler şablondakilerden); bilinmeyen kod 422; imza base64 çözülür, PNG magic bayt, ≤ 200 KB, aksi 422 `INVALID_SIGNATURE`; `signer.type=OWNER` ise `signer_name` = sahip adı (OwnerLookupPort); `OTHER` ise `name` zorunlu, `nationalId` verilirse 11 hane rakam (biçim kontrolü); `template_snapshot` = şablonun o anki `content` metni (aynen); `values_json` = gelen değerlerin kod sırasına göre (şablon sırası) serileştirilmiş hâli; IP = `request.getRemoteAddr()`, UA = `User-Agent` başlığı; özet hesaplanır.
  - `GET /signed-forms?patientId=…` veya `?ownerId=…` → `[{id, templateTitle, signedAt, signerName, signerType, status}]` (signed_at azalan)
  - `GET /signed-forms/{id}` → `{id, templateTitle, content (snapshot'tan), values, signer, signedAt, signedByName, patientId, ownerId, encounterId, status, voidReason, voidedAt, integrityOk}`; `integrityOk` = özet yeniden hesaplanıp karşılaştırılır.
  - `GET /signed-forms/{id}/signature` → PNG baytları
  - `POST /signed-forms/{id}/void` `{reason}` (boş olamaz) → 204; zaten VOIDED ise 422.
  - Hiçbir PUT/PATCH/DELETE yok.

- [ ] **Step 1: Write the failing tests** — `should_storeSnapshotValuesSignatureAndDigest`; `should_takeOwnerFromPatient_notFromClient`; `should_renderFromSnapshot_after_templateEdited` (şablon güncellenince `GET` eski metni döner); `should_flagIntegrityFailure_when_storedValuesChanged` (repository'deki values_json değiştirilince `integrityOk=false`); `should_reject_missingRequiredField`; `should_reject_nonPngSignature`; `should_reject_archivedTemplate`; `should_require_name_for_otherSigner`; `void_should_requireReason_and_rejectTwice`. Security: TECHNICIAN void → 403; VET void → 204; RECEPTIONIST create → 201; başka kiracının formu `GET` → 404; `PUT /signed-forms/{id}` → 405.
- [ ] **Step 2: Run** `cd backend && ./mvnw test -Dtest=SignedFormUseCasesTest,SignedFormsEndpointsSecurityTest` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı + `ApplicationModulesTest` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 4b: Hasta modülü — formdan geri yazma uç noktası

**Files:**
- Create (`patient/application`): `ApplyFormWriteBacksUseCase.java`, `dto/FormWriteBackCommand.java` (`record(UUID ownerId, UUID patientId, UUID signedFormId, List<Item> items)`; `Item(String binding, String value)`), `dto/FormWriteBackResult.java` (`applied: List<String>`, `skipped: List<Skipped(String binding, String reason)>`).
- Create (`patient/api`): `FormWriteBacksController.java` — `POST /api/v1/form-write-backs`, `@PreAuthorize("hasAnyRole('VET','TECHNICIAN','RECEPTIONIST','ADMIN')")`.
- Modify: `patient/domain/Owner.java` / `Patient.java` yalnız gerekli dar güncelleme metotları eksikse (mevcut `updateContactInfo`, `updateNationalId`, `recordWeight` vb. öncelikli).
- Test: `backend/src/test/java/com/vetos/modules/patient/application/ApplyFormWriteBacksUseCaseTest.java`

**Interfaces:**
- Produces: HTTP `POST /api/v1/form-write-backs` body = `FormWriteBackCommand` alanları → `200 {applied[], skipped[{binding, reason}]}`.
- Kurallar: hasta bu sahibe ait değilse 422; yalnız Global Constraints'teki 9 yazılabilir binding kabul edilir, diğerleri `skipped` ("yazılamaz alan"); `owner.nationalId` `TcKimlikValidator.isValid` değilse `skipped` ("geçersiz TC kimlik no"); `owner.birthDate` ISO tarih değilse skipped; `patient.weightKg` > 0 sayı değilse skipped; boş değer skipped. `signedFormId` yalnız denetim kaydı içindir (bu modül onu doğrulamaz — modül bağımlılığı yok).

- [ ] **Step 1: Write the failing tests** — `should_updateOwnerPhoneAndPatientColor`; `should_skipInvalidNationalId`; `should_skipNonWritableBinding` (`owner.fullName`); `should_reject_whenPatientNotOwnedByOwner`; `should_skipBlankValues`.
- [ ] **Step 2: Run** `cd backend && ./mvnw test -Dtest=ApplyFormWriteBacksUseCaseTest` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı + `ApplicationModulesTest` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 5: PDF

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/consentform/infrastructure/pdf/ConsentFormPdfRenderer.java` (`EncounterReportPdfRenderer` font yaklaşımı: Helvetica + `Cp1254`).
- Create (`consentform/application`): `RenderSignedFormPdfUseCase`, `RenderBlankTemplatePdfUseCase`; `domain/ConsentFormPdfPort.java` (`byte[] render(ConsentFormPdfData data)`), `ConsentFormPdfData` record (`clinicName, title, content, values, signerLine, signedAt, signaturePng|null, digestShort|null, voided, voidReason|null`).
- Modify: `FormTemplatesController` (`GET /form-templates/{id}/pdf`), `SignedFormsController` (`GET /signed-forms/{id}/pdf`) — `Content-Disposition: attachment; filename="<başlık>-<tarih>.pdf"`.
- Test: `backend/src/test/java/com/vetos/modules/consentform/infrastructure/pdf/ConsentFormPdfRendererTest.java`

**Kurallar:** TEXT_BLOCK içindeki `{{code}}` değerle (boşsa `……………`) değiştirilir; CHOICE seçilenler "☒/☐" yerine "[X] Kedi  [ ] Köpek" metniyle; imzalayan satırı `"İmzalayan: <ad> (hasta sahibi)"` veya `"İmzalayan: <ad> (hasta yakını/diğer), TC: <tc>"`; altbilgi `"Belge özeti: <ilk 16 hex>"`; iptalse başlık altında kırmızı "İPTAL EDİLDİ — <gerekçe>".

- [ ] **Step 1: Write the failing tests** — PDF baytlarından metin çıkarıp (OpenPDF `PdfTextExtractor`) doğrula: `should_containTurkishCharacters` ("İmza", "Şablon", "ğüşıöç"); `should_fillInlinePlaceholder`; `should_printOtherSignerLine`; `should_stampVoided`; `should_printDigestShort`.
- [ ] **Step 2: Run** `cd backend && ./mvnw test -Dtest=ConsentFormPdfRendererTest` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı + `ApplicationModulesTest` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 6: Frontend — model, API istemcisi, bağlama yardımcıları

**Files:**
- Create: `frontend/src/forms/formModel.ts` (içerik modeli tipleri, `BINDINGS`, `WRITABLE_BINDINGS`, `BINDING_LABELS` Türkçe — ör. `'owner.phone': 'Sahip telefonu'`), `frontend/src/forms/bindings.ts`, `frontend/src/api/formsApi.ts`.
- Test: `frontend/src/forms/bindings.test.ts`

**Interfaces:**
- Produces:
  - `formsApi`: `uploadSourceImage(file): Promise<string>` (postMultipartForId), `extractDraft(sourceImageId): Promise<{aiJobId, modelConnected, content, warnings}>`, `createTemplate({content, sourceImageId}): Promise<string>`, `updateTemplate(id, content)`, `archiveTemplate(id)`, `listTemplates(status?)`, `getTemplate(id)`, `sourceImageUrl(id)`, `templatePdfUrl(id)`, `createSignedForm(payload): Promise<string>`, `listSignedForms({patientId}|{ownerId})`, `getSignedForm(id)`, `signatureUrl(id)`, `signedFormPdfUrl(id)`, `voidSignedForm(id, reason)`, `applyWriteBacks(cmd): Promise<{applied, skipped}>`.
  - `resolveBindingValues(ctx: {patient: PatientProfile, owner: OwnerProfile, clinicName: string, today: Date}): Record<FormBinding, string>` (`patient.age` = `ageLabelFrom(birthDate)`; `patient.sex` = "Dişi"/"Erkek"/""; tarihler `GG.AA.YYYY` değil ISO `YYYY-MM-DD` — DATE input değeri).
  - `initialValues(content, resolved): Record<string, string | string[]>` — bağlı alanlar dolu; CHOICE için `matchChoiceOption(options, value)` (Türkçe küçük harf + trim ile tam eşleşme) seçili, eşleşme yoksa `[]`.
  - `computeWriteBackDiff(content, values, resolved, signerType): {binding, label, oldValue, newValue, valid: boolean, reason?: string}[]` — yalnız WRITABLE bağlı alanlar, değer boş değil ve kayıttakinden farklı (telefonlarda yalnız rakamlar karşılaştırılır); `signerType==='OTHER'` ise `owner.*` hiç dönmez; `owner.nationalId` `tcKimlik.ts` ile geçersizse `valid:false, reason:'Geçersiz TC kimlik no — kayda yazılamaz'`.
  - `fillPlaceholders(text, values): string`.

- [ ] **Step 1: Write the failing tests** — `resolveBindingValues` (sex, age, clinic); `matchChoiceOption(['Kedi','Köpek','Kuş','Diğer'], 'KÖPEK') === 'Köpek'`, `'Tavşan'` → null; `computeWriteBackDiff`: telefon "0532 111 22 33" vs "05321112233" → fark yok; değişen telefon → 1 satır; `signerType 'OTHER'` → owner satırı yok ama `patient.color` var; geçersiz TC → `valid:false`; `fillPlaceholders('…{{klinik}} muayenehanesi', {klinik:'Yalıkavak'})`.
- [ ] **Step 2: Run** `cd frontend && npx vitest run src/forms` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 7: Ayarlar > Formlar (liste, görselden oluşturma, düzenleyici)

**Files:**
- Modify: `frontend/src/pages/settings/SettingsPage.tsx` — `SETTINGS_TABS`'e `{ path: 'formlar', label: 'Formlar', roles: ['ADMIN'] }` ve bileşeni.
- Create: `frontend/src/pages/settings/forms/FormsSettings.tsx` (iç rotalar: liste `''`, `yeni`, `:templateId`), `FormTemplateList.tsx`, `NewFormFromImage.tsx`, `FormTemplateEditor.tsx`, `FormItemEditor.tsx`, + `.module.css`'ler; `frontend/src/forms/FormRenderer.tsx` (içeriği tek sütun gösterir; `mode: 'preview' | 'fill' | 'readonly'`; Task 8/9 de kullanır).
- Test: `frontend/src/pages/settings/forms/FormTemplateEditor.test.tsx`, `frontend/src/forms/FormRenderer.test.tsx`

**Interfaces:**
- Consumes: Task 6 `formsApi`, model; `prepareImage`.
- Produces: `<FormRenderer content values onChange? mode signatureSlot?: ReactNode />` — `TEXT_BLOCK` metnini paragraf olarak, satır içi `{{code}}`'yu `fill` modunda o alanın değeriyle (boşsa altı çizili boşluk) gösterir; alanları türüne göre input olarak çizer; `SIGNATURE` yerine `signatureSlot`'u (yoksa "İmza alanı" kutusu) koyar.
- Düzenleyici davranışı: taslak geldiğinde mavi çerçeve + uyarılar listesi; solda referans görsel (varsa), sağda düzenleme + canlı önizleme (≤880px'te alt alta); öğe başına: etiket/metin düzenleme, tür seçimi, bağlantı seçimi (`BINDING_LABELS`), zorunlu kutusu, CHOICE seçenekleri (virgülle değil satır satır), NUMBER birimi, ↑/↓, Sil; bölüm sonunda "+ Alan ekle" (tür menüsü); kod otomatik üretilir (etiketten Türkçe karakterler sadeleştirilip `[a-z0-9_]`, çakışmada `_2`); "Formu Oluştur"/"Kaydet" öncesi istemcide de Task 1 kuralları (tek imza, benzersiz kod) kontrol edilir; kaydedilmemiş değişiklikte çıkış uyarısı; "Boş PDF önizleme" (kayıtlı şablonda `templatePdfUrl`). Liste: başlık, durum, son düzenleme, imzalı sayısı; Düzenle / Arşivle / Referans görsel.

- [ ] **Step 1: Write the failing tests** — `FormRenderer`: placeholder doldurma, CHOICE tek/çok seçim değişikliği `onChange`, readonly'de input yok. Editör: "+ Alan ekle" → SHORT_TEXT eklenir ve kodu `alan`/`alan_2`; ↑ sıra değiştirir; Sil; ikinci SIGNATURE eklenmeye çalışılınca engel + mesaj; AI uyarıları görünür; "Formu Oluştur" `createTemplate`'i içerik + `sourceImageId` ile çağırır.
- [ ] **Step 2: Run** `cd frontend && npx vitest run src/forms src/pages/settings/forms` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı + `npm run build` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 8: Tam ekran doldurma ve imza

**Files:**
- Create: `frontend/src/forms/SignaturePad.tsx` (+ css) — `<canvas>` + pointer events (parmak/kalem/fare), `devicePixelRatio` ölçekleme, "Temizle"; `onChange(pngBlob | null)`; boşken `null`.
- Create: `frontend/src/pages/forms/FillFormPage.tsx` (+ css) — rota `/formlar/doldur?templateId=&patientId=&encounterId=` (`App.tsx`'e `RequireAuth` ile, AppShell **olmadan**, roller VET/TECHNICIAN/RECEPTIONIST/ADMIN).
- Test: `frontend/src/pages/forms/FillFormPage.test.tsx`, `frontend/src/forms/SignaturePad.test.tsx`

**Interfaces:**
- Consumes: Task 6 (`resolveBindingValues`, `initialValues`, `computeWriteBackDiff`, `formsApi`), Task 7 (`FormRenderer`), `patientApi.getProfile`, `patientApi.getOwnerProfile`.
- Akış: yükleme → `FormRenderer mode='fill'` + imza bölümü: "İmzalayan" radyo (varsayılan "Hasta sahibi — <ad>", "Başka biri imzalıyor" → ad (zorunlu), TC (isteğe bağlı, `tcKimlik.ts` ile doğrulanır), telefon) + `SignaturePad`. "Onayla ve Gönder": zorunlu alanlar/imza/imzalayan adı kontrolü → diff boş değilse "Kaydınız da güncellensin mi?" adımı (satır başına Evet/Hayır, `valid:false` satırlar seçilemez ve gerekçe gösterilir) → `createSignedForm` → seçilen satırlar varsa `applyWriteBacks` (başarısız olursa form kaydı geçerli kalır; "Form kaydedildi ama kayıt güncellenemedi" uyarısı) → "Teşekkürler" ekranı: "PDF indir" (`signedFormPdfUrl`, `fetchFile` ile) ve "Kapat" (geldiği sayfaya `navigate(-1)`; yoksa hasta kartı).
- İmza PNG → base64 (`FileReader`), şeffaf arka plan.

- [ ] **Step 1: Write the failing tests** — sahte API'lerle: bağlı alanlar dolu açılır; imzasız gönderim engellenir ve mesaj görünür; diff adımı yalnız farklar için çıkar; "Başka biri imzalıyor" + ad girilince diff adımında owner satırı yok ve `createSignedForm` `signer.type==='OTHER'` ile çağrılır, `applyWriteBacks` owner binding'i içermez; geçersiz TC satırı seçilemez; `applyWriteBacks` hata verince form başarı ekranı + uyarı. SignaturePad: çizim yokken `onChange(null)`, "Temizle" sonrası `null`.
- [ ] **Step 2: Run** `cd frontend && npx vitest run src/pages/forms src/forms/SignaturePad.test.tsx` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** aynı + `npm run build` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 9: Giriş noktaları, imzalı form listeleri, görüntüleme ve iptal

**Files:**
- Create: `frontend/src/pages/forms/FillFormButton.tsx` ("📝 Form Doldur" → aktif şablon seçimi küçük menü → `/formlar/doldur?...`), `SignedFormsList.tsx` (hasta veya sahip için), `SignedFormViewModal.tsx` (readonly `FormRenderer` + imza görseli + imzalayan + tarih + `integrityOk=false` ise terrakota "Bütünlük doğrulanamadı" uyarısı + "PDF olarak indir" + yetkiliyse "İptal et" gerekçeli).
- Modify: `frontend/src/pages/patients/PatientDetailPage.tsx` (başlık eylemlerine `FillFormButton`, sayfaya "Formlar" kartı `SignedFormsList patientId`), `frontend/src/pages/encounter/EncounterPage.tsx` veya `PatientHeaderBar.tsx` (`FillFormButton` + `encounterId`), `frontend/src/pages/owners/OwnerDetailPage.tsx` (yeni sekme `'formlar'` → `SignedFormsList ownerId`).
- Test: `frontend/src/pages/forms/SignedFormViewModal.test.tsx`, `frontend/src/pages/forms/FillFormButton.test.tsx`

**Interfaces:** Consumes Task 6/7/8.

- [ ] **Step 1: Write the failing tests** — FillFormButton: aktif şablon yoksa "Henüz form yok — Ayarlar > Formlar'dan ekleyin" (ADMIN'e link); seçimde doğru URL (encounterId dahil). View modal: `integrityOk=false` uyarısı; TECHNICIAN'da "İptal et" yok, VET'te var; gerekçesiz iptal engellenir; VOIDED formda "İPTAL EDİLDİ" + gerekçe.
- [ ] **Step 2: Run** `cd frontend && npx vitest run src/pages/forms` → FAIL
- [ ] **Step 3: Implement**
- [ ] **Step 4: Run** `cd frontend && npx vitest run && npm run build` → PASS
- [ ] **Step 5: Uçtan uca doğrulama (sahibine rapor):** çalışan backend'e (yeniden başlatılmış, V95 uygulanmış) karşı: iki örnek görselle (`C:\Users\Furkan\Desktop\TedaviForum.jpeg`, `C:\Users\Furkan\Desktop\anestezi formu .jpeg`) şablon oluştur → bir hastada doldur/imzala → PDF indir → şablonu düzenle → eski formun değişmediğini gör. Backend yeniden başlatılamıyorsa bunu açıkça raporla.
- [ ] **Step 6: Değişiklikleri gözden geçir** (commit yok)
