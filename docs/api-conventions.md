# API Konvansiyonları & Güvenlik

## URL Yapısı

```
/api/v1/<kaynak-coğul>              GET (liste), POST (oluştur)
/api/v1/<kaynak-coğul>/{id}         GET (tekil), PUT (güncelle), DELETE
/api/v1/<kaynak-coğul>/{id}/<alt-kaynak>   İlişkili kaynaklar
```

- Kaynak adları İngilizce, çoğul, kebab-case değil düz İngilizce çoğul: `/patients`, `/appointments`, `/invoice-lines`.
- Fiil URL'de OLMAZ (`/patients/create` YANLIŞ). HTTP metodu fiili taşır.
- Aksiyon endpoint'leri (durum değişikliği gibi) istisna: `POST /encounters/{id}/finalize`, `POST /invoices/{id}/void`.

## HTTP Durum Kodları

| Kod | Kullanım |
|---|---|
| 200 | Başarılı GET/PUT |
| 201 | Başarılı POST (kaynak oluşturuldu), `Location` header ile birlikte |
| 204 | Başarılı DELETE |
| 400 | Validation hatası (Bean Validation) |
| 401 | Kimlik doğrulanmadı (token yok/geçersiz) |
| 403 | Kimlik doğrulandı ama yetkisi yok |
| 404 | Kaynak bulunamadı |
| 409 | Çakışma (örn. aynı saatte randevu çakışması) |
| 422 | İş kuralı ihlali (örn. stok yetersiz) |
| 500 | Beklenmeyen sunucu hatası |

## Standart Hata Formatı

Her hata cevabı (`GlobalExceptionHandler` üretir), formatı asla değişmez:

```json
{
  "errorCode": "PATIENT_NOT_FOUND",
  "message": "Hasta bulunamadı: 3f2e...",
  "timestamp": "2026-07-28T14:32:00Z",
  "path": "/api/v1/patients/3f2e..."
}
```

Validation hatalarında ek `fieldErrors` dizisi:

```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Girdi doğrulama hatası",
  "fieldErrors": [{ "field": "name", "message": "Boş olamaz" }],
  "timestamp": "...", "path": "..."
}
```

## Pagination

Liste endpoint'leri `page` (0-bazlı) ve `size` (varsayılan 20, max 100) query param alır:

```
GET /api/v1/patients?page=0&size=20&sort=name,asc
```

Cevap zarfı:
```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 248, "totalPages": 13 }
```

## Multi-Tenancy

Her istekte JWT'den çözülen `tenantId`, `TenantContext` (ThreadLocal) üzerinden tüm repository sorgularına **otomatik** filtre olarak eklenir (Hibernate `@Filter` veya sorgu interceptor ile). Controller/use-case katmanında `tenantId`'yi elle her yerde parametre olarak geçirmeye gerek YOK — `TenantContext.current()` üzerinden okunur. İstisna: Referans modüldeki (`RegisterPatientCommand`) gibi command nesnelerinde tenantId açıkça taşınır çünkü command, use-case sınırını geçen bir DTO'dur.

## JWT Claim Yapısı

```json
{
  "sub": "<staffUserId>",
  "tenantId": "<uuid>",
  "branchIds": ["<uuid>", "..."],
  "role": "VET | TECHNICIAN | RECEPTIONIST | ADMIN",
  "exp": 1234567890
}
```

## Rol & Yetki Matrisi (Faz 1 kapsamı)

| Endpoint grubu | VET | TECHNICIAN | RECEPTIONIST | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `/patients/**` (okuma) | ✅ | ✅ | ✅ | ✅ |
| `/patients/**` (yazma) | ✅ | ❌ | ✅ | ✅ |
| `/encounters/**` (SOAP yazma/onaylama) | ✅ | ❌ | ❌ | ✅ |
| `/appointments/**` | ✅ | ✅ | ✅ | ✅ |
| `/invoices/**`, `/payments/**` | ❌ | ❌ | ✅ | ✅ |
| `/inventory/**` (yazma) | ❌ | ✅ | ❌ | ✅ |
| `/settings/**`, `/users/**` | ❌ | ❌ | ❌ | ✅ |
| `/tarbil/**` (durum, bildirilmeyecek/geri al, eklenti bağlama, eşleştirmeler, `GET /tarbil/diseases` hastalık ağacı) | ✅ | ❌ | ❌ | ✅ |
| `/tarbil-extension/**` (yalnız eklenti anahtarı; `/pair` herkese açık + hız sınırlı; sürüm kontrolü: bkz. Versiyonlama) | eklenti | eklenti | eklenti | eklenti |
| `/encounters/**` (okuma: SOAP, malzeme listesi) | ✅ | ✅ | ❌ | ✅ |
| `/message-templates/**`, `/notifications/**` (SMS/WhatsApp) | ❌ | ❌ | ✅ | ✅ |
| `**/campaign-candidates` (SMS/WhatsApp kampanya adayı listeleri) | ✅ | ❌ | ✅ | ✅ |
| `GET /invoices/today-summary` (istisna, aşağıya bak) | ✅ | ✅ | ✅ | ✅ |
| `/form-templates/**` (okuma: liste, detay) | ✅ | ✅ | ✅ | ✅ |
| `/form-templates/**` (yazma, arşiv, kaynak görsel) | ❌ | ❌ | ❌ | ✅ |
| `/signed-forms/**` (oluştur, liste, detay, imza) | ✅ | ✅ | ✅ | ✅ |
| `POST /form-write-backs` (imzalı formdan sahip/hasta kaydına geri yazma) | ✅ | ✅ | ✅ | ✅ |
| `POST /signed-forms/{id}/void` | ✅ | ❌ | ❌ | ✅ |

**TARBİL uçları (P0 çekirdek, 2026-10-04):**
- `GET /api/v1/tarbil-extension/pending?type=VACCINATION|PRESCRIPTION|STOCK_RECEIPT` — `type` isteğe bağlı; yoksa tüm türler. Yanıtın her öğesinde `documentType` var (eski alanlar, `vaccinationRecordId` dahil, değişmedi).
- `GET /api/v1/tarbil/diseases` (ADMIN, VET) — TARBİL reçete hastalık ağacı: `[{id, parentId, name, path, selectable}]`, `sort_order` sırasıyla (73 düğüm).
- `POST /api/v1/vaccination-records` — isteğe `inventoryItemId` (opsiyonel) eklendi: stoktan seçilen aşı; `ADMINISTERED` olunca Vetly stoğundan 1 düşer, iptalde geri eklenir (olayla, `inventory` modülü). Eklentiye giden aşı verisinde `tarbilProductName` alanı var.
- `POST /api/v1/tarbil-extension/stock-snapshots` (eklenti) — `{system: HBSAPP_VACCINE|VETILAC_MEDICINE, lines[{productName, presentation, lotNumber, expiryDate, quantity, openedQuantity}]}` (en çok 500) → 201 `{snapshotId}`.
- `POST /api/v1/tarbil-extension/stock-snapshots/compare` (eklenti) — aynı gövde → 200 `{newCount, quantityDiffersCount, matchedCount}`; eşleştirilen personelin şubesinin stoğuyla ürün+lot grupları karşılaştırılır, hiçbir şey kaydedilmez. Şube yoksa 422.
- `POST /api/v1/tarbil-extension/submissions/{id}/submitted` — gövde `{method, tarbilReference?, lines?[{productName, serialNumber, quantity}]}` (en çok 50). İlk onayda bekleyen stok düşer: MANUAL ya da `lines` yoksa planlanan adetler, aksi halde seri no eşleşen satırlar bildirilen adetle; eşleşmeyen satırlar aktarıma not edilir (spec 2026-10-09 §5.3). Tekrarlanan onay stoğu yeniden düşmez.
- Eklenti aktarım yanıtında `documentType = PRESCRIPTION` için `prescription {sourceKind: ENCOUNTER|PRESCRIPTION, tarbilDiseaseId, tarbilDiseaseName, treatmentStartDate, lines[{itemName, tarbilProductName, lotNumber, quantity}]}`; aşı alanları boş (`vaccineName = "Reçete"`).
- `GET /api/v1/tarbil/encounters/{id}/deduction` (VET/TECHNICIAN/ADMIN) — `{lines[{itemName, lotNumber, quantity, status, settledQuantity}], submissionId, submissionStatus}`; başka kiracının muayenesi 404.
- `POST /api/v1/tarbil/encounters/{id}/deduction` (VET/ADMIN) — `{tarbilDiseaseId}` → 201 `{submissionId}` (idempotent); bekleyen yoksa ya da muayene taslaksa 409.
- `GET /api/v1/inventory/pending-deductions` (VET/TECHNICIAN/ADMIN) — TARBİL düşümü bekleyen kalemler `[{id, inventoryItemId, itemName, lotNumber, quantity, referenceType, referenceId, createdAt}]`.
- `POST /api/v1/inventory/pending-deductions/{ENCOUNTER|VACCINATION|PRESCRIPTION}/{referenceId}/settle-vetly-only` (VET/ADMIN) → 204; bekleyenler planlanan adetle düşer, bekleyen TARBİL aktarımı "Yalnız Vetly'den düşüldü" ile reddedilir.
- `POST /api/v1/prescriptions` — `encounterId` isteğe bağlı (muayenesiz reçete), `tarbilDiseaseId`; kalemde `inventoryItemId` + `stockQuantity` birlikte ("kliniğin stoğundan ver"). Stoktan verilen kalem varsa teşhis zorunlu (422); muayene başka hastanınsa 422. Stoktan verilen kalem varsa `PRESCRIPTION` aktarımı otomatik kuyruğa girer.
- `POST /api/v1/prescriptions/{id}/cancel` (VET/ADMIN) → 204; aktif değilse 409. Bekleyen düşüm ve bekleyen aktarım kapanır; TARBİL'de düşülmüş stok iade edilmez.
- Stok kalemi yanıtı (`GET /api/v1/inventory-items`) `tarbilProductName` içerir; doluysa kalem TARBİL düşümünü bekler.
- `GET /api/v1/tarbil/stock-sync?system=` (ADMIN, VET) — son TARBİL stok görüntüsü, Vetly stoğuyla karşılaştırmalı (`NEW`, `QUANTITY_DIFFERS`, `MATCHED`, `APPLIED`); görüntü yoksa `snapshotId: null`.
- `POST /api/v1/tarbil/stock-sync/{snapshotId}/apply` (ADMIN, VET) — `{lineIds}` → `{applied}`; kullanıcının ilk şubesine işlenir.
- `PUT /api/v1/patients/{id}/photo` (VET, RECEPTIONIST, ADMIN) — multipart `file`; JPEG/PNG/WEBP, ≤2 MB → `{photoVersion}`; 422 UNSUPPORTED_PATIENT_PHOTO.
- `DELETE /api/v1/patients/{id}/photo` (VET, RECEPTIONIST, ADMIN) → 204.
- `GET /api/v1/patients/{id}/photo` (tüm roller) — görsel baytları, `Cache-Control: private, max-age=31536000, immutable` (URL `?v={photoVersion}` ile sürümlenir); fotoğraf yoksa 404 PATIENT_PHOTO_NOT_FOUND. Hasta profili/arama yanıtlarında `photoVersion` (null olabilir).
- `POST /api/v1/form-templates/source-images` (ADMIN) — multipart `file`; JPEG/PNG/WEBP (magic byte), ≤10 MB → 201 `{id}`; 422 UNSUPPORTED_FORM_SOURCE_IMAGE.
- `GET /api/v1/form-templates/source-images/{id}` (ADMIN) — görsel baytları; yoksa/başka kiracınınsa 404 FORM_SOURCE_IMAGE_NOT_FOUND.
- `POST /api/v1/form-templates` (ADMIN) — `{content, sourceImageId?}` → 201 `{id}`; başlık `content.title`dan türetilir (ayrı `title` alanı yok sayılır); içerik doğrulaması (tam bir SIGNATURE, benzersiz kodlar, bilinen `binding` anahtarları vb.) ihlalinde 422 INVALID_FORM_TEMPLATE.
- `PUT /api/v1/form-templates/{id}` (ADMIN) — `{content}` → 204; ARCHIVED şablonda 422 FORM_TEMPLATE_ARCHIVED. İmzalı formlar kendi kopyasını taşır, etkilenmez.
- `POST /api/v1/form-templates/{id}/archive` (ADMIN) → 204; yalnız durum değişir.
- `GET /api/v1/form-templates?status=ACTIVE|ALL` (VET/TECHNICIAN/RECEPTIONIST/ADMIN; varsayılan ACTIVE) → `[{id, title, status, updatedAt, signedCount, hasSourceImage}]`.
- `GET /api/v1/form-templates/{id}` (VET/TECHNICIAN/RECEPTIONIST/ADMIN) → `{id, title, status, content, sourceImageId, clinicName}`; başka kiracının şablonu 404 FORM_TEMPLATE_NOT_FOUND.
- `POST /api/v1/form-write-backs` (VET/TECHNICIAN/RECEPTIONIST/ADMIN) — `{ownerId, patientId, signedFormId, items:[{binding, value}]}` → 200 `{applied:[binding…], skipped:[{binding, reason}]}`. Yalnız 9 binding yazılır (`owner.phone/secondaryPhone/nationalId/birthDate/address/email`, `patient.color/weightKg/microchipNumber`); geçersiz/boş/yazılamaz değerler `skipped`. Hasta sahibe ait değilse 422 PATIENT_OWNER_MISMATCH; sahip/hasta yoksa 404. `signedFormId` yalnız log içindir.
- `POST /api/v1/signed-forms` (VET/TECHNICIAN/RECEPTIONIST/ADMIN) — `{templateId, patientId, encounterId|null, values:{kod: string|string[]}, signer:{type:'OWNER'|'OTHER', name|null, nationalId|null, phone|null}, signaturePngBase64}` → 201 `{id}`. `ownerId` hastadan alınır (istemciden alınmaz); OWNER imzalayanın adı sahip kaydından gelir, OTHER'da `name` zorunlu ve `nationalId` verilirse 11 hane rakam. Şablon ACTIVE olmalı (aksi 422 FORM_TEMPLATE_ARCHIVED); değer/imzalayan hataları (zorunlu alan boş, bilinmeyen kod, geçersiz seçenek/tarih/sayı) 422 INVALID_SIGNED_FORM_VALUES; imza base64 çözülemez/PNG değil/200 KB üstü 422 INVALID_SIGNATURE; `encounterId` verilirse muayene bu hastaya ait olmalı (yok/başka kiracı/başka hasta hep 422 INVALID_ENCOUNTER). Özet; şablon kopyası, değerler, imzalayan, imza PNG ve kayıt üstverisini (şablon/hasta/sahip/muayene kimlikleri, signedAt ms, signedBy, IP, UA) kapsar. Şablon metni (`template_snapshot`), değerler (şablon sırasıyla), imza PNG ve SHA-256 özeti, IP ve User-Agent (≤500) ile saklanır; kayıt değiştirilemez.
- `GET /api/v1/signed-forms?patientId=…|ownerId=…` (VET/TECHNICIAN/RECEPTIONIST/ADMIN) → `[{id, templateTitle, signedAt, signerName, signerType, status}]` (signed_at azalan); ikisi de yoksa 422.
- `GET /api/v1/signed-forms/{id}` (aynı roller) → `{id, templateTitle, content (imza anındaki kopyadan), values, signer:{type,name,nationalId,phone}, signedAt, signedByName, patientId, ownerId, encounterId, status, voidReason, voidedAt, integrityOk}`; `content`/`values` kayıt bozulmuşsa null olabilir (integrityOk=false, 500 değil); `integrityOk` özetin kayıtlı sütunlar + imza PNG'sinden yeniden hesaplanmasıyla bulunur; başka kiracının formu 404 SIGNED_FORM_NOT_FOUND.
- `GET /api/v1/signed-forms/{id}/signature` (aynı roller) — imza PNG baytları.
- `POST /api/v1/signed-forms/{id}/void` (VET/ADMIN) — `{reason}` (boş olamaz, 400) → 204; zaten VOIDED ise 422 SIGNED_FORM_ALREADY_VOIDED. PUT/PATCH/DELETE yoktur (405).

**İstisna (Hızlı Satış turu):** `GET /invoices/today-summary`, `/invoices/**` satırını metot seviyesinde bir `@PreAuthorize` ile kasıtlı olarak geçersiz kılar ve dört role de açıktır — anasayfadaki "Bugünkü satış" KPI kartı için salt-okunur tek bir toplam döner, fatura/müşteri detayı sızdırmaz. `/invoices/**` altındaki diğer tüm uçlar RECEPTIONIST + ADMIN olarak kalır.

**Kiracı izolasyonu (2026-10-05 ek tur):** `tenant_id` taşıyan tüm iş varlıkları Hibernate `@TenantId` ile otomatik filtrelenir (kimlikle okuma dahil); başka kliniğin kaydına istek 404 döner. Bilinçli istisnalar ve gerekçe: `docs/superpowers/specs/2026-09-17-kiraci-izolasyonu-sertlestirme-design.md` §6, §11 (elle filtrelenenler: `NotificationLog`, `NotificationSettings`, TARBİL tabloları).

Yetki kontrolü Spring Security `@PreAuthorize("hasRole('VET')")` ile controller metodu seviyesinde yapılır — use-case katmanında rol kontrolü YAPILMAZ (rol, bir HTTP/API kavramıdır, domain'in bilmesi gerekmez).

**Kenar durum kararı (Faz 2, Yönetim ekranları turu):** Aynı kiracı içi bir dizin listesi (örn. `GET /staff-users`) birden fazla amaçla kullanılıyorsa (hem hafif bir "seçici" hem de bir yönetim ekranının tam listesi), tek endpoint zenginleştirilir ve mevcut erişim seviyesinde bırakılır — PII olmayan alanlar (ad, e-posta, rol, uzmanlık) için ayrı bir ADMIN-only endpoint AÇILMAZ. Yazma/aksiyon endpoint'leri (`POST`/`PUT`/`DELETE`) yine de ilgili role kısıtlanır.

**Karar (Faz 2, role-bazlı yetkilendirme denetimi turu, kullanıcı onayıyla):** Proje geneli bir rol/yetki denetiminde üç boşluk bulundu ve düzeltildi:
1. `EncountersController`'ın **okuma** uçları (`GET /{id}`, `GET` liste, `GET /by-appointment/{id}`, `GET /{id}/materials`) artık TECHNICIAN'a da açık — tedavi görevi/aşı uygulaması için tedavi planını görmesi gerekiyor. Yazma uçları (SOAP/vital/fizik muayene güncelleme, finalize, malzeme kaydı) hâlâ sadece VET/ADMIN. Frontend: `EncounterPage.tsx`'teki `isReadOnly`, `session.role === 'TECHNICIAN'` durumunda da true oluyor (sayfa görünür ama tüm yazma aksiyonları gizli).
2. `MessageTemplatesController` + `NotificationsController` (SMS/WhatsApp şablon/log/kampanya) ADMIN-only'den RECEPTIONIST+ADMIN'e açıldı — requirements.md'deki resepsiyonist tanımı "hatırlatma gönderimi" içeriyor, önceki kısıtlama kasıtsız bir eksiklikti.
3. `AppointmentsController.GET /campaign-candidates` ve `VaccinationRecordsController.GET /campaign-candidates` hiç rol kısıtı taşımıyordu (TECHNICIAN dahil herkese açıktı) — `OwnersController`'daki eşdeğer uç (VET/RECEPTIONIST/ADMIN) ile tutarlı hale getirildi.

Ayrıca frontend'de daha önce **hiç** rol bazlı görünürlük yoktu (`navConfig.tsx` her role aynı sidebar'ı gösteriyordu, `RequireAuth` sadece "giriş yapılmış mı" kontrolü yapıyordu). `NavItemConfig`/`RequireAuth` artık opsiyonel bir `roles` listesi taşıyor; Laboratuvar/Görüntüleme (VET/TECHNICIAN/ADMIN) ve Finans/Raporlar (RECEPTIONIST/ADMIN) hem sidebar'da hem route seviyesinde bu role göre filtreleniyor. `SettingsPage.tsx`'teki `SETTINGS_TABS` da aynı desenle `adminOnly: boolean`'dan `roles?: StaffRole[]`'e genelleştirildi (SMS/WhatsApp sekmesi artık ADMIN+RECEPTIONIST).

## Versiyonlama

`v1` şu an tek versiyon (spec: `docs/superpowers/specs/2026-10-18-api-sozlesmesi-faz7-design.md`).

**Kırıcı değişiklik** (mevcut bir istemciyi bozar, sürüm gerektirir):
- bir alanı silmek ya da adını değiştirmek,
- bir alanın tipini değiştirmek,
- zorunlu yeni bir girdi (alan, parametre, başlık) eklemek,
- bir adresi kaldırmak,
- durum kodlarının ya da `errorCode` değerlerinin anlamını değiştirmek.

**Kırıcı olmayan değişiklik** (sürüm gerekmez): yeni alan, isteğe bağlı yeni girdi, yeni adres. İstemciler (web uygulaması, eklenti) tanımadıkları alanları görmezden gelmek zorundadır.

**v2 kuralı:** Kırıcı değişiklik gerekirse yalnız etkilenen adresler için `/api/v2/...` açılır; `v1` karşılığı en az 6 ay daha çalışır.

**Kullanımdan kaldırma:** Kalkacak bir adres cevaplarında `Deprecation: true` ve `Sunset: <HTTP tarihi>` (kapanış tarihi) başlıklarını taşır. Bugün kalkacak adres yok; kural olarak duruyor, kodda karşılığı yok.

**Web uygulaması ↔ backend:** Her zaman birlikte yayınlanır; aralarında sürüm kontrolü yoktur.

### TARBİL eklentisi: zorunlu güncelleme

Eklenti sunucuyla birlikte yayınlanmayan tek istemcidir. Her isteğe `X-Client-Version: x.y.z` ekler (kaynak: `extension/package.json`).
`/api/v1/tarbil-extension/**` (`/pair` dahil) altında backend bunu `VETLY_EXTENSION_MIN_VERSION`'la (`vetly.extension.min-version`, varsayılan `0.3.0`; açık/parça stok sürümüyle yükseltildi, bkz. `docs/deployment.md`) sayısal karşılaştırır:
- Başlık yoksa `0.1.0`, bozuksa `0.0.0` sayılır.
- Eskiyse **426** döner: `errorCode: CLIENT_UPGRADE_REQUIRED`, Türkçe güncelleme mesajı, `X-Min-Client-Version` başlığı. Eklenti yan panelde kalıcı uyarı gösterir, eşleştirmeyi silmez.

En düşük sürümü yükseltme sırası:
1. Yeni eklenti sürümünü `extension/` içinde `npm run package` ile paketleyip web uygulamasını yayınlayın.
2. Klinikler güncellesin diye bekleyin (Ayarlar > Entegrasyonlar'daki kart eski sürümde "Yeni sürüm var" der). Backend her eşleşmiş eklentinin son gönderdiği sürümü `tarbil_extension_token.last_client_version`'a yazar (boş = başlık göndermeyen 0.1.0); kartın cihaz listesinde ve `GET /api/v1/tarbil/extension/tokens` yanıtında `lastClientVersion` olarak görünür. Tüm kliniklerin dağılımı için sorgu: `docs/deployment.md` > Faz 7.
3. Ancak sonra sunucuda `VETLY_EXTENSION_MIN_VERSION`'ı yükseltip backend'i yeniden başlatın.

### API dokümanı (OpenAPI)

Geliştirmede `/swagger-ui.html` (JSON: `/v3/api-docs`) kimliksiz açıktır; içerik koddan otomatik çıkar, adreslere anotasyon eklenmez. `prod` profilinde kapalıdır.
