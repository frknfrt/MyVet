# Telefondan Fotoğraf (Profil Fotoğrafı + Sonucu Çekip Okutma) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Klinik personeli telefondan hastaya profil fotoğrafı ekleyebilsin; Lab/Görüntüleme "Yeni Sonuç" penceresinden kamerayla kâğıt sonucu / röntgeni çekince kayıt oluşsun ve mevcut AI okuma kendiliğinden çalışsın.

**Architecture:** Frontend'de tek bir `prepareImage` yardımcısı her görseli JPEG'e çevirip küçültür. Profil fotoğrafı `patient` modülünde yeni bir kolon (`photo_storage_ref`) + `FileStoragePort` ile saklanır, üç yeni uç noktayla yönetilir. Sonuç akışında backend değişmez; frontend yüklenen dosya id'lerini `Location` başlığından alıp detay penceresine "bunları otomatik oku" diye geçirir, pencere mevcut `handleOcr()`'u bir kez tetikler.

**Tech Stack:** Spring Boot 3 / Java 21, Flyway, JUnit 5 + Mockito + MockMvc; React + TypeScript + Vite, Vitest (jsdom).

**Spec:** `docs/superpowers/specs/2026-11-06-mobil-foto-cekme-design.md`

## Global Constraints

- Hexagonal + modül sınırı: profil fotoğrafı kodu yalnızca `modules/patient` içinde; `ai` modülüne bağımlılık yok. `ApplicationModulesTest` geçmeli.
- Dosyalar yalnızca `platform/storage/FileStoragePort` üzerinden saklanır.
- Yeni migration: `V94__patient_photo.sql`. Mevcut `patients.photo_url` kolonuna ve `PatientProfile.photoUrl` alanına **dokunulmaz** (kullanılmayan eski alan).
- Profil fotoğrafı yetkisi = hasta yazma: `hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')`. Okuma: tüm roller.
- Lab/görüntüleme yetkileri ve "Tamamla" akışı değişmez (VET, TECHNICIAN, ADMIN).
- OCR sonucu bugünkü gibi form alanlarının üzerine yazar; yeni önizleme/onay kutusu **yok**.
- Küçültme: profil uzun kenar 800 px / kalite 0.85; sonuç uzun kenar 2000 px / kalite 0.9. Çıktı `image/jpeg`, uzantı `.jpg`.
- Sunucu profil fotoğrafı sınırı: 2 MB, yalnızca JPEG/PNG/WEBP (baytlardan doğrulanır).
- Renkler yalnızca `var(--color-*)`; AI butonları `variant="ai"`.
- Kullanıcıya görünen metinler Türkçe.
- **Repo kuralı: commit atılmaz** (main'de çalışılır). Her görevin sonundaki adım "değişiklikleri gözden geçir", commit değil.

## Review Focus

1. **Detay penceresi yeniden yüklendiğinde otomatik okuma ikinci kez çalışmamalı** (`load()` yükleme/tamamlama sonrası tekrar çağrılıyor; her okuma AI kotası harcar) → Task 6 testi.
2. **AI kotası dolu / model bağlı değil** iken otomatik okuma: kayıt ve fotoğraflar durur, hata mesajı görünür, alanlar elle doldurulabilir → Task 6 testi.
3. **Fotoğraf değiştirildiğinde eski görsel önbellekte kalmamalı** (aynı hasta için yeni fotoğraf hemen görünmeli) → Task 4 testi (URL sürümle değişir).
4. **Görsel olmayan dosyanın** (adı `.jpg` olan PDF) profil fotoğrafı olarak yüklenmesi 422 ile reddedilmeli → Task 2 testi.
5. **Tarayıcının çözemediği HEIC** fotoğrafı: sessizce bozuk dosya yüklenmemeli, Türkçe hata gösterilmeli → Task 1 testi.

---

### Task 1: `prepareImage` yardımcısı

**Files:**
- Create: `frontend/src/lib/prepareImage.ts`
- Test: `frontend/src/lib/prepareImage.test.ts`

**Interfaces:**
- Produces:
  - `export type ImagePreset = 'profile' | 'document'` (profile → 800 px / 0.85, document → 2000 px / 0.9)
  - `export function fitWithin(width: number, height: number, maxEdge: number): { width: number; height: number }` — oranı korur, asla büyütmez, tam sayıya yuvarlar.
  - `export class ImagePrepareError extends Error` — mesaj: `'Bu fotoğraf biçimi desteklenmiyor. Telefonun kamera ayarlarından "En Uyumlu" biçimi seçin veya ekran görüntüsü alıp yükleyin.'`
  - `export async function prepareImage(file: File, preset: ImagePreset): Promise<File>`

- [ ] **Step 1: Write the failing tests** (`// @vitest-environment jsdom`)
  - `fitWithin(4000, 3000, 2000)` → `{ width: 2000, height: 1500 }`; `fitWithin(3000, 4000, 800)` → `{ width: 600, height: 800 }`; `fitWithin(500, 300, 800)` → `{ width: 500, height: 300 }`.
  - `prepareImage(new File(['x'], 'rapor.pdf', { type: 'application/pdf' }), 'document')` aynı `File` nesnesini döndürür (`toBe`).
  - `globalThis.createImageBitmap` `vi.fn().mockRejectedValue(new Error('decode'))` iken `image/heic` dosyası → `ImagePrepareError` fırlatır.
  - `createImageBitmap` `{ width: 4000, height: 3000, close: vi.fn() }` döndürüp `HTMLCanvasElement.prototype.getContext` / `toBlob` mock'lanınca: dönen dosyanın `type === 'image/jpeg'`, `name === 'IMG_1.jpg'` (girdi `IMG_1.HEIC`), canvas boyutu 2000×1500, `toBlob` kalite argümanı `0.9`.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `cd frontend && npx vitest run src/lib/prepareImage.test.ts`
  Expected: FAIL (module not found)

- [ ] **Step 3: Implement `prepareImage.ts`**
  `file.type` `image/` ile başlamıyorsa (veya `image/gif`) dosyayı aynen döndür. Aksi hâlde `createImageBitmap(file, { imageOrientation: 'from-image' })` ile çöz (hata → `ImagePrepareError`), `fitWithin` boyutunda bir `<canvas>`'a çiz, `canvas.toBlob(cb, 'image/jpeg', quality)` ile kodla, `bitmap.close()`; ad uzantısını `.jpg` yap.

- [ ] **Step 4: Run tests to verify they pass**
  Run: `cd frontend && npx vitest run src/lib/prepareImage.test.ts`
  Expected: PASS

- [ ] **Step 5: Değişiklikleri gözden geçir** (`git diff`; commit yok)

---

### Task 2: Backend — profil fotoğrafı use case'leri ve depolama silme

**Files:**
- Create: `backend/src/main/resources/db/migration/V94__patient_photo.sql` — `ALTER TABLE patients ADD COLUMN photo_storage_ref TEXT;`
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/Patient.java` — `@Column(name = "photo_storage_ref") private String photoStorageRef;` + `replacePhoto(String storageRef)` / `clearPhoto()`; getter (Lombok deseni neyse).
- Modify: `backend/src/main/java/com/vetos/platform/storage/FileStoragePort.java` — `void delete(String storageRef);` (yoksa sessizce geç)
- Modify: `backend/src/main/java/com/vetos/platform/storage/PostgresFileStorageAdapter.java` — `repository.deleteById(UUID.fromString(storageRef))`, bulunamazsa hata yok.
- Create: `backend/src/main/java/com/vetos/modules/patient/domain/PatientPhotoFormat.java` — `static Optional<String> detectContentType(byte[] content)` → `"image/jpeg"` (FF D8 FF), `"image/png"` (89 50 4E 47), `"image/webp"` ("RIFF"…"WEBP"), diğerleri boş.
- Create: `backend/src/main/java/com/vetos/modules/patient/domain/exception/UnsupportedPatientPhotoException.java` — `extends DomainException`, kod `"UNSUPPORTED_PATIENT_PHOTO"`, mesaj `"Profil fotoğrafı JPEG, PNG veya WEBP olmalı ve 2 MB'ı geçmemeli"`.
- Create: `backend/src/main/java/com/vetos/modules/patient/application/UploadPatientPhotoUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/patient/application/RemovePatientPhotoUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/patient/application/GetPatientPhotoUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/patient/application/dto/PatientPhoto.java` — `record PatientPhoto(byte[] content, String contentType)`
- Test: `backend/src/test/java/com/vetos/modules/patient/application/PatientPhotoUseCasesTest.java`

**Interfaces:**
- Consumes: `PatientRepository.findById(UUID)` (kiracı filtreli), `FileStoragePort.store/retrieve/delete`, `PatientNotFoundException`.
- Produces:
  - `UploadPatientPhotoUseCase.execute(UUID patientId, byte[] content) : String` — yeni storageRef'i döner (frontend sürümü olarak kullanılır).
  - `RemovePatientPhotoUseCase.execute(UUID patientId) : void` — fotoğraf yoksa no-op.
  - `GetPatientPhotoUseCase.execute(UUID patientId) : PatientPhoto` — fotoğraf yoksa `PatientPhotoNotFoundException` (`"PATIENT_PHOTO_NOT_FOUND"`, 404 isim kuralıyla).
  - `Patient.getPhotoStorageRef() : String` (nullable)

- [ ] **Step 1: Write the failing tests** (Mockito, `UploadImagingRecordFileUseCaseTest` deseni)
  - `should_storeJpeg_and_setRefOnPatient`: JPEG baytları (`FF D8 FF E0 …`) → `fileStoragePort.store(content, "image/jpeg")` çağrılır, `patient.getPhotoStorageRef()` dönen ref olur, `patientRepository.save` çağrılır, dönüş değeri ref.
  - `should_deleteOldPhoto_whenReplacing`: hastanın ref'i `"old"` iken yükleme → `fileStoragePort.delete("old")` çağrılır.
  - `should_rejectNonImage`: `"%PDF-1.7"` baytları → `UnsupportedPatientPhotoException`, `store` hiç çağrılmaz.
  - `should_rejectOver2MB`: 2 * 1024 * 1024 + 1 baytlık JPEG başlıklı dizi → `UnsupportedPatientPhotoException`.
  - `should_throwNotFound_whenPatientMissing`: `findById` boş → `PatientNotFoundException`.
  - `remove_should_deleteAndClear` ve `remove_should_beNoOp_whenNoPhoto` (`delete` çağrılmaz).
  - `get_should_returnBytesAndDetectedType` ve `get_should_throwPhotoNotFound_whenNoPhoto`.

- [ ] **Step 2: Run tests to verify they fail**
  Run: `cd backend && ./mvnw test -Dtest=PatientPhotoUseCasesTest`
  Expected: FAIL (compilation errors)

- [ ] **Step 3: Implement** migration, entity alanları, port `delete`, adaptör, `PatientPhotoFormat`, istisnalar, üç use case (`@Transactional`; Get `readOnly`). `GetPatientPhotoUseCase` içerik türünü `PatientPhotoFormat.detectContentType` ile belirler (ayrı kolon tutulmaz).

- [ ] **Step 4: Run tests to verify they pass**
  Run: `cd backend && ./mvnw test -Dtest=PatientPhotoUseCasesTest+ApplicationModulesTest`
  Expected: PASS

- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 3: Backend — uç noktalar ve DTO alanları

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/patient/api/PatientsController.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/application/dto/PatientProfile.java`, `.../application/GetPatientProfileUseCase.java`, `.../api/dto/PatientProfileResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/application/dto/PatientSearchResult.java`, `.../application/SearchPatientsUseCase.java`, `.../api/dto/PatientSearchResultResponse.java`
- Modify: `docs/api-conventions.md` — uç nokta listesine üç satır.
- Test: `backend/src/test/java/com/vetos/PatientPhotoEndpointsSecurityTest.java` (`UsageEndpointsSecurityTest` deseni, canlı docker Postgres; `@MockitoBean` ile üç use case)

**Interfaces:**
- Consumes: Task 2 use case'leri.
- Produces (HTTP):
  - `PUT /api/v1/patients/{id}/photo` multipart `file` → `200 {"photoVersion": "<ref>"}`; `@PreAuthorize("hasAnyRole('VET', 'RECEPTIONIST', 'ADMIN')")`
  - `DELETE /api/v1/patients/{id}/photo` → `204`; aynı yetki
  - `GET /api/v1/patients/{id}/photo` → görsel baytları, `Content-Type` tespit edilen tür, `Cache-Control: private, max-age=31536000, immutable` (URL `?v=` ile sürümlenir)
  - `PatientProfileResponse` ve `PatientSearchResultResponse`'a `String photoVersion` (= `photoStorageRef`, null olabilir) eklenir.

- [ ] **Step 1: Write the failing tests**
  - `should_forbidPhotoUpload_forTechnician` → 403.
  - `should_uploadPhoto_forVet` → 200, `$.photoVersion` mock'un döndürdüğü değer.
  - `should_deletePhoto_forReceptionist` → 204.
  - `should_servePhoto_forTechnician` → 200, `Content-Type: image/jpeg`, `Cache-Control` içinde `immutable`.
  - `should_return404_whenPhotoMissing` → use case `PatientPhotoNotFoundException` fırlatınca 404, `$.errorCode == "PATIENT_PHOTO_NOT_FOUND"`.
  - `should_return422_forUnsupportedPhoto` → `UnsupportedPatientPhotoException` → 422, `$.errorCode == "UNSUPPORTED_PATIENT_PHOTO"` (`GlobalExceptionHandler.resolveStatus` varsayılanı).

- [ ] **Step 2: Run tests to verify they fail**
  Run: `cd backend && ./mvnw test -Dtest=PatientPhotoEndpointsSecurityTest`
  Expected: FAIL (404/405 — uç nokta yok)

- [ ] **Step 3: Implement** controller metotları ve DTO alanları. (Kiracı izolasyonu `findById` filtresinden gelir; başka kiracının hastası → `PatientNotFoundException` → 404.)

- [ ] **Step 4: Run tests + uçtan uca curl**
  Run: `cd backend && ./mvnw test -Dtest=PatientPhotoEndpointsSecurityTest+ApplicationModulesTest`
  Expected: PASS. Ardından çalışan backend'e karşı (CLAUDE.md yöntemi): giriş → `PUT` gerçek bir JPEG → `GET /api/v1/patients/{id}` `photoVersion` dolu → `GET .../photo` 200 → `DELETE` 204 → `GET .../photo` 404.
  Not: paylaşılan geliştirme DB'sinde V94 çalışınca :8080 backend'in yeniden başlatılması gerekebilir.

- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 4: Frontend — `patientApi` fotoğraf fonksiyonları ve `PatientAvatar`

**Files:**
- Modify: `frontend/src/api/patientApi.ts`
- Create: `frontend/src/components/patient/PatientAvatar.tsx`, `PatientAvatar.module.css`
- Test: `frontend/src/components/patient/PatientAvatar.test.tsx`

**Interfaces:**
- Consumes: Task 3 HTTP; `apiClient.put`/`delete`/`fetchFile`; `colorFor`, `initialsOf` (`pages/dashboard/avatarColor.ts`).
- Produces:
  - `PatientSearchResult.photoVersion: string | null`, `PatientProfile.photoVersion: string | null`
  - `patientApi.uploadPhoto(id: string, file: File): Promise<{ photoVersion: string }>` (multipart `PUT` — `client.ts`'e `putMultipart` eklenir; `requestMultipart`'a `method` parametresi verilerek)
  - `patientApi.removePhoto(id: string): Promise<void>`
  - `patientApi.photoUrl(id: string, version: string): string` → `${API_BASE_URL}/api/v1/patients/${id}/photo?v=${encodeURIComponent(version)}`
  - `<PatientAvatar patientId: string; name: string; photoVersion: string | null; size: number; />` — fotoğraf varsa `fetchFile(photoUrl)` → blob URL `<img alt={name}>` (modül düzeyinde `Map<url, objectUrl>` önbellek), yoksa / yükleme hatasında renkli baş harf dairesi.

- [ ] **Step 1: Write the failing tests** (jsdom, `LabResultDetailModal.test.tsx` render deseni; `patientApi` mock)
  - `photoVersion: null` → `<img>` yok, metin `"PA"` (`name: 'Pamuk Ali'`), `fetchFile` çağrılmaz.
  - `photoVersion: 'v1'` → `fetchFile` `photoUrl('p1','v1')` ile çağrılır, `<img alt="Pamuk Ali">` görünür.
  - Aynı bileşen `photoVersion: 'v2'` ile yeniden render → `fetchFile` `'v2'` URL'siyle yeniden çağrılır (Review Focus 3).
  - `fetchFile` reddedince baş harf dairesine düşer.

- [ ] **Step 2: Run** `cd frontend && npx vitest run src/components/patient` → FAIL
- [ ] **Step 3: Implement** API fonksiyonları ve bileşen.
- [ ] **Step 4: Run** `cd frontend && npx vitest run src/components/patient` → PASS
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 5: Frontend — hasta kartı menüsü ve liste avatarı

**Files:**
- Modify: `frontend/src/pages/patients/PatientDetailPage.tsx` (satır ~170 `.avatar` bloğu), `PatientDetailPage.module.css`
- Create: `frontend/src/pages/patients/PatientPhotoMenu.tsx`, `PatientPhotoMenu.module.css`
- Modify: `frontend/src/pages/patients/PatientsPage.tsx` (satır ~143 satır render), `PatientsPage.module.css`
- Test: `frontend/src/pages/patients/PatientPhotoMenu.test.tsx`

**Interfaces:**
- Consumes: Task 1 `prepareImage(file, 'profile')`, `ImagePrepareError`; Task 4 `PatientAvatar`, `patientApi.uploadPhoto/removePhoto`.
- Produces: `<PatientPhotoMenu patientId: string; hasPhoto: boolean; onChanged: (photoVersion: string | null) => void; />` — üç seçenek: "📷 Fotoğraf Çek" (`<input type="file" accept="image/*" capture="environment">`), "🖼 Galeriden Seç" (`capture` yok), "Fotoğrafı Kaldır" (`hasPhoto` iken; `danger` renk).

- [ ] **Step 1: Write the failing tests**
  - Kamera input'unun `capture` niteliği `"environment"`, galeri input'unda `capture` yok.
  - Dosya seçilince `prepareImage` `'profile'` ile, ardından `uploadPhoto` hazırlanmış dosyayla çağrılır; `onChanged('v9')`.
  - `prepareImage` `ImagePrepareError` fırlatınca hata mesajı ekranda, `uploadPhoto` çağrılmaz.
  - `hasPhoto: false` iken "Fotoğrafı Kaldır" görünmez; `true` iken tıklanınca `removePhoto` ve `onChanged(null)`.

- [ ] **Step 2: Run** `cd frontend && npx vitest run src/pages/patients/PatientPhotoMenu.test.tsx` → FAIL
- [ ] **Step 3: Implement**
  - Kartta `.avatar` div'i yerine 64 px `PatientAvatar` + sağ altta 📷 rozeti; rozet ve menü yalnızca `canWritePatient` iken (sayfadaki mevcut değişken). Telefonda (≤880 px) menü alttan açılan liste, masaüstünde avatarın altında küçük açılır menü. `onChanged` profil state'indeki `photoVersion`'ı günceller.
  - Listede her satırın başına 36 px `PatientAvatar` (`r.patientId`, `r.patientName`, `r.photoVersion`); `tableHead` başlık hizası buna göre.
- [ ] **Step 4: Run** testler PASS + `cd frontend && npm run build` hatasız.
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 6: Frontend — "📷 Fotoğraf Çek" ve otomatik okuma

**Files:**
- Modify: `frontend/src/api/client.ts` — `postMultipartForId(path, form): Promise<string>` (`requestForLocationId` gibi `Location`'dan son parçayı okur; `Location` CORS'ta zaten açık)
- Modify: `frontend/src/api/labApi.ts:87`, `frontend/src/api/imagingApi.ts:59` — `uploadFile(id, file): Promise<string>` (dosya id'si döner)
- Modify: `frontend/src/pages/laboratory/NewLabResultModal.tsx`, `frontend/src/pages/imaging/NewImagingRecordModal.tsx` (+ `.module.css`)
- Modify: `frontend/src/pages/laboratory/LaboratoryPage.tsx:218-224`, `frontend/src/pages/imaging/ImagingPage.tsx:234-…`
- Modify: `frontend/src/pages/laboratory/LabResultDetailModal.tsx`, `frontend/src/pages/imaging/ImagingRecordDetailModal.tsx`
- Test: `frontend/src/pages/laboratory/LabResultDetailModal.test.tsx`, `frontend/src/pages/imaging/ImagingRecordDetailModal.test.tsx` (mevcut dosyalara ekle), `frontend/src/pages/laboratory/NewLabResultModal.test.tsx` (yeni)

**Interfaces:**
- Consumes: Task 1 `prepareImage(file, 'document')`.
- Produces:
  - `NewLabResultModal` / `NewImagingRecordModal`: `onCreated: (id: string, opts: { autoOcrFileIds: string[] }) => void`
  - `LabResultDetailModal` / `ImagingRecordDetailModal`: yeni opsiyonel prop `autoOcrFileIds?: string[]`

- [ ] **Step 1: Write the failing tests**
  - `NewLabResultModal`: "📷 Fotoğraf Çek" input'u `accept="image/*"` + `capture="environment"`; kamera ile eklenen dosya `prepareImage(…, 'document')`'tan geçer; "Dosya Seç" ile eklenen PDF geçmez (aynen yüklenir). Kaydet sonrası `onCreated('r1', { autoOcrFileIds: ['f-cam'] })` — yalnızca kamera dosyalarının id'leri (uploadFile mock'u sırayla `'f-cam'`, `'f-pdf'` döndürür).
  - `LabResultDetailModal` `autoOcrFileIds={['f1']}` ile: `get` çözülünce `ocrFromImages('r1', ['f1'])` **bir kez** çağrılır ve dönen `items` form alanlarına yazılır; ardından `load()` tetiklenmesi (ör. dosya yükleme) sonrası **tekrar çağrılmaz** (Review Focus 1).
  - `ocrFromImages` `ApiError` (kota) ile reddedince hata banner'ı görünür, form düzenlenebilir kalır (Review Focus 2).
  - `autoOcrFileIds` verilmezse `ocrFromImages` çağrılmaz (bugünkü davranış korunur).
  - `ImagingRecordDetailModal` için aynı üç test (`findings` alanı).

- [ ] **Step 2: Run** `cd frontend && npx vitest run src/pages/laboratory src/pages/imaging` → FAIL
- [ ] **Step 3: Implement**
  - Yeni kayıt pencerelerinde "📷 Fotoğraf Çek" butonu (bu pencerede tek `primary` "Kaydet" ise `secondary`); dosya listesinde kamera dosyaları `fromCamera: true` işaretiyle tutulur, küçük önizleme gösterilir; tekrar basmak "+ Sayfa Ekle" işlevi görür (buton metni ilk çekimden sonra "📷 Sayfa Ekle"). Bütün görsel dosyalar (kamera veya seçici) `prepareImage(…, 'document')`'tan geçer; otomatik okuma listesine yalnızca kamera dosyaları girer.
  - Sayfalar `onCreated(id, { autoOcrFileIds })` alıp detay penceresine `autoOcrFileIds` geçirir; pencere kapanınca state temizlenir.
  - Detay pencerelerinde: `autoOcrFileIds` doluysa ilk başarılı `get` sonrası `selectedFileIds` bunlara ayarlanır ve mevcut `handleOcr()` çağrılır; bir `useRef` bayrağı aynı `resultId/recordId` için tekrar çalışmayı engeller. Detaydaki "+ Dosya/Görsel Ekle" görselleri de `prepareImage(…, 'document')`'tan geçer (otomatik okuma yok).
- [ ] **Step 4: Run** testler PASS + `cd frontend && npm run build` hatasız.
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)

---

### Task 7: Telefon/tablet görünümü ve cihazda doğrulama

**Files:**
- Modify (yalnızca CSS gerektiği kadar): `NewLabResultModal.module.css`, `NewImagingRecordModal.module.css`, lab/görüntüleme detay modal CSS'leri, `LaboratoryPage`/`ImagingPage` CSS, `PatientsPage.module.css`, `PatientDetailPage.module.css`, gerekirse `components/ui` Modal CSS'i.

**Interfaces:** Yok (yalnızca görünüm).

- [ ] **Step 1: Ölç** — `npm run dev` ile Chrome DevTools cihaz modunda 375×812 ve 768×1024: listelenen her ekranda yatay kaydırma var mı, pencereler ekrana sığıyor mu, butonlar ≥44 px mi? Sorunları listele.
- [ ] **Step 2: Düzelt** — ≤880 px'te pencereler tam ekran (`width` prop'u yok sayılır), iki sütunlu düzenler tek sütun, butonlar en az 44 px yükseklik. Yalnızca `var(--color-*)`.
- [ ] **Step 3: Doğrula** — Step 1 ölçümü tekrar; `npm run build` ve `npx vitest run` hatasız.
- [ ] **Step 4: Gerçek cihaz** — aynı ağdaki iPhone (Safari) ve Android (Chrome) ile (`npm run dev -- --host`, backend'in CORS/çerez ayarı LAN adresine izin vermiyorsa kullanıcıdan prod-benzeri ortam iste): profil fotoğrafı çek → kart ve listede görünür; kâğıt hemogram çek → kayıt açılır, değerler dolar; röntgen ekranı/filmi çek → bulgu uyarı metniyle dolar. Sonuçları kullanıcıya raporla; cihaz yoksa bunu açıkça belirt.
- [ ] **Step 5: Değişiklikleri gözden geçir** (commit yok)
