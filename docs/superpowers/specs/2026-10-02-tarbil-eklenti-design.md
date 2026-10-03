# TARBİL Chrome Eklentisi (Aşı Bildirimi) — Tasarım Dokümanı

**Tarih:** 2026-10-02
**Durum:** Faz 1 uygulandı (`feature/tarbil-eklenti`). Faz 2 akışı 2026-10-03'te güncellendi (Bölüm 12), inceleme bekliyor
**İlgili modül:** `integration/tarbil` (backend), `frontend` (Ayarlar > Entegrasyonlar, aşı kaydı), yeni `extension/` klasörü

## 1. Bağlam ve Amaç

TARBİL'in (T.C. Tarım ve Orman Bakanlığı, `hbsapp.tarbil.gov.tr`) resmi bir API'si yok; hekimler aşı bildirimlerini web arayüzünden elle giriyor. Mevcut `integration/tarbil` modülü sunucudan TARBİL'e gönderim varsayımıyla kurulmuş (outbox + retry), ama arkasında gerçek bağlantı yok: `MockTarbilAdapter` kayıtların ~%90'ını rastgele `SYNCED` işaretliyor — yani Ayarlar > Entegrasyonlar ekranı **hiç gönderilmemiş kayıtları gönderilmiş gibi gösteriyor**. Canlı ortamda (2026-09-29'dan beri) bu yanıltıcı.

**Amaç:** Hekimin TARBİL'e kaydettiği kedi/köpek aşılarını Vetly'den tekrar elle yazmasına gerek kalmaması; Vetly'nin hangi aşının TARBİL'e *gerçekten* bildirildiğini doğru göstermesi.

**Yaklaşım:** Hekimin Chrome'unda çalışan bir eklenti, Vetly'deki aşı verisini TARBİL formuna doldurur; **Kaydet'e her zaman hekim basar**; eklenti TARBİL'in başarı mesajını yakalayıp Vetly'yi günceller. Vetly backend'i kuyruk, eşleştirme ve durumun tek gerçek kaynağıdır.

**Başarı ölçütü:** Bir aşı bildirimi birkaç tıklamaya iner; Vetly'deki TARBİL durumu (bekliyor / gönderildi / bildirilmeyecek) gerçeği yansıtır.

## 2. Kapsam

**Bu turda yapılacak:**
- Kedi/köpek aşı bildirimi (TARBİL "RECEIPT" modülü aşı sayfası + `VaccineKKBSAnimalSearchModalPage` hayvan arama penceresi).
- İki giriş noktası: TARBİL'de eklenti yan paneli (bekleyenler listesi) ve Vetly'de aşı kaydı yanında "TARBİL'e aktar" butonu.
- Eşleştirme kodu ile hekime özel, iptal edilebilir eklenti anahtarı.
- Başarının otomatik yakalanması + elle işaretleme yedeği.
- Aşı/hastalık/tür değerlerinin ilk kullanımda öğrenilmesi (klinik bazlı).
- Sahte senkronun kaldırılması (`MockTarbilAdapter`, retry altyapısı).

**Kapsam dışı (bilinçli olarak):**
- Kimliklendirme (çip kaydı), reçete, sahip değişikliği, ölüm bildirimi — sonraki sürümler.
- Büyükbaş/küçükbaş/ithal/küpesiz hayvan panelleri.
- Chrome Web Store yayını — şimdilik sadece kendi kliniklerde "paketlenmemiş uzantı" olarak kurulum.
- TARBİL'e eklentiden doğrudan HTTP isteği atmak (form postback'ini taklit etmek) — kırılgan ve kullanım koşulları açısından gri alan; eklenti sadece arayüzü doldurur ve okur. (Tek istisna: Bölüm 12.3'teki isteğe bağlı oturum canlı tutma — veri göndermeyen, açık TARBİL sekmesinden yapılan sade sayfa isteği.)
- e-Devlet girişini otomatikleştirmek ya da e-Devlet/TARBİL kimlik bilgisini eklentide saklamak — kişisel resmi kimlik; e-Devlet otomasyonu engelliyor. Giriş her zaman hekimin kendisi tarafından yapılır (Bölüm 12.2).
- Kaydet'e eklentinin basması (toplu onay) — 2026-10-03 kararı: şimdilik yok; eşleştirmeler oturduktan sonra yeniden değerlendirilecek (Bölüm 12.5).
- Sunucuda otomatik tarayıcı ile gönderim — hekimin e-Devlet/TARBİL kimlik bilgilerinin sunucuda saklanmasını gerektirir (KVKK/hukuk riski) ve "Kaydet'e hekim basar" ilkesine aykırı.

## 3. TARBİL Ekranı — Bilinenler

Sayfalar ASP.NET WebForms + Telerik RadControls 2012.3 + jQuery 1.7.2. Form alanları düz `<input>` değil Telerik bileşenleri: değer `$find(clientId).set_value(...)` / combobox API'siyle verilmeli (sadece sayfanın kendi JS dünyasında erişilebilir). İşlemler UpdatePanel async postback'leriyle (`X-MicrosoftAjax: Delta=true`) sayfanın kendi adresine gider; bitişi `Sys.WebForms.PageRequestManager` `endRequest` olayıyla izlenir.

**Ana aşı sayfası** (id öneki `ctl00_ctl00_ctl00_bodyCPH_ContentPlaceHolder1_cntVACCINEBodyContent_`):
- `dpApplicationDate` (uygulama tarihi), `cbxAnimalType` (Kedi = `245f5f71-2cfd-4a04-9072-640069e3268e`), `cbxVaccineValue`, `cbxDiseaseValue`, `cbxVacPurpose`, `cbxApplicationType`, `cbxDosageUnitValue`, `cbxOtherAnimalSex`, `cbxProvince`, `RadGridProduct` (+ `hiddenSelectedProductId`, `hiddenSelectedRow`, `hiddenTypeId`), `btnInsert`/`btnInsert2` (Kaydet).
- "Diğer hayvan" tablosu (`ReceiptAddOtherAnimal_RadOtherAnimal`) butonları: `bntPetVet`, `btnHaybis`, `btnAnimalMultiple` ("Hayvan Ara ve Ekle").
- Sahip alanları (`txtPersonIdNo`, `txtAnimalOwnerName`, `txtAnimalOwnerAddress`, `txtHoldingNo`) eklenti tarafından **doldurulmaz** — hayvan çip ile bulununca TARBİL'de kayıtlı sahip bilgisi kullanılır.

**Hayvan arama penceresi** (`/Modules/RECEIPT/Pages/ModalPages/VaccineKKBSAnimalSearchModalPage.aspx?AnimalType=C|D|G`, `window.open` ile açılan popup; id öneki `ctl00_ctl00_ContentPlaceHolder1_ContentPlaceHolderBody_UCVaccineKKBSAnimalSearch_`):
- Arama: `txtChipNo` (max 15), `txtPassportNo`, `txtAnimalOwnerName/Surname`, `cbxSpecies` (kilitli; C=Kedi, D=Köpek, G=Gelincik), `cbxBreedValue` (GUID), `cbxSex` (M/F/U), `UCProvinceDistrictNeigbourhood_cbxProvince/cbxDistrict/cbxNeigbourhood` (klinik adresiyle önceden dolu).
- Enter tuşu iptal edilmiş; arama `btnSearch` tıklamasıyla, sonuç `radGridAnimal` tablosunda (sütunlar: NAME, CHIPNO, PASSPORTNO, SPECIES, BREEDNAME, SEX, COLOR, BIRTHDATE, STATUS, ANIMALOWNER, MOTHERCHIPNO; satır başı onay kutusu).
- Seçim: onay kutusu + `btnAddBulkAnimal` ("Transfer Et") → sunucu `runReceiptUpdateParentAnimal(param)` → `window.opener.runToParentUpdateAnimal(param)` → pencere kapanır. Hayvanın TARBİL iç ID'sini sunucu üretir; eklentinin bilmesine gerek yoktur.

**Ana aşı sayfası kaynağından öğrenilenler (2026-10-03, kişisel veriler kayda geçirilmedi):**
- Adres: `/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx?type=1` (menü: Aşı Uygulama Belgesi > Ekle; `type=2` rapel belgesi). Modül ana sayfası `/Modules/RECEIPT/Pages/VaccineDefault.aspx`, sistem ana sayfası `/Default.aspx`, uygulama seçimi `/SelectApplication.aspx`.
- **Form aşamalı:** ilk yüklemede yalnız `dpApplicationDate` (bugünle dolu, `autoPostBack: true`) ve `cbxAnimalType` (seçimde postback) var. `cbxVaccineValue`, `cbxDiseaseValue`, `cbxVacPurpose`, `cbxApplicationType`, `cbxDosageUnitValue`, `cbxOtherAnimalSex`, `cbxProvince`, `RadGridProduct` ancak tür seçildikten sonraki postback ile oluşuyor. Doldurma sırası buna göre: tarih → tür → (postback bitişi) → diğer alanlar.
- `cbxAnimalType` değerleri: Kedi = `245f5f71-2cfd-4a04-9072-640069e3268e`, Köpek = `3eea84e6-b2d8-494f-a01c-89e739bb004d` (bunlar `SPECIES` eşleştirmesinin varsayılanı olabilir; öğrenme yine geçerli).
- **Kaydet butonunun adı "Onayla"** (`btnInsert` üstte, `btnInsert2` altta). Uyarı varsa sayfa `confirmButton` ile `window.confirm("Uyarılara Rağmen Kaydetmek İstediğinizden Emin Misiniz?")` sorabiliyor; bu onay da hekimde kalır.
- **Aşı ürünü stoktan seçiliyor:** `openProductWindow(satır)` → `/Modules/RECEIPT/Pages/ModalPages/UcVaccineStockSearchModalPage.aspx?animalid=<türGUID>&date=dd/MM/yyyy` popup'ı → seçim `window.opener.runToParentPageMethod("<ürünId>,...")` → `hiddenSelectedProductId` doldurulur → gizli `updaRadgridField` butonu tıklanır. Yani `VACCINE` eşleştirmesi Vetly aşı adını kliniğin **TARBİL stoğundaki** bir kaleme bağlar; aşı TARBİL stoğunda yoksa aktarım yapılamaz (kart bunu söylemeli).
- Hayvan ekleme: arama popup'ı `runToParentUpdateAnimal("OtherAnimal")` ile döner → gizli `updateOtherAnimal` butonu → `ReceiptAddOtherAnimal` paneli yenilenir.
- **Başarı/hata bildirimi yerleri:** `bodyCPH_ContentPlaceHolder1_UCVACCINENotification_pnlNotifiSuccess` / `_pnlNotifiError` / `_pnlNotifiWarning` / `_pnlNotifiInfo` (içlerinde `.Onecolumn`) ve sayfa altında aynı yapıda `UCNotification1`; ayrıca Telerik `RadNotificationOkDefault` / `ErrorDefault` açılır pencereleri ve `TBSErrorHandler` (`İşlem Hatası` / `Sistem Hatası`). Başarı yakalama bu panellerin içeriğinin değişmesini izleyecek; kesin metin hâlâ gerekli (Bölüm 11 madde 2).
- **Oturum:** sayfadaki `SessionTimeoutControl` uyarıyı 215.940.000 ms sonra gösteriyor (yani istemci tarafı zaman aşımı ≈ 60 saat; 60 sn kala "Oturuma devam etmek için tıklayınız" çıkar ve `notification.update()` ile oturumu kendisi uzatır). Async postback zaman aşımı 360 sn. Gerçek sunucu süresi gözlemle doğrulanmalı (Bölüm 11 madde 8); 60 saat doğruysa Bölüm 12.3'teki canlı tutma gereksiz kalabilir.
- **Tür = Kedi seçildikten sonraki async postback yanıtı (2026-10-03):** `PageContentPanel` yeniden çiziliyor ve şunlar beliriyor:
  - `pnlCatAndDog` → `ReceiptAddOtherAnimal_RadOtherAnimal` tablosu (başta "Kayıt Bulunamadı."). Sütunlar: Sistem Küpe/Çip No, Hayvan Doğum Tarihi, Cinsiyeti, Irkı, Eşgali, Sil. Üç ekleme butonu: `bntPetVet` ("PetVet'ten Hayvan Ara ve Ekle", komut `AddPetVetMultiple`), `btnHaybis` ("Haybis'den ...", `AddHaybisMultiple`), `btnAnimalMultiple` ("Hayvan Ara ve Ekle", `AddMultiple`). Hangisinin kullanılacağı hekime soruldu (Bölüm 11 madde 10).
  - `pnlOwnerInfo`: `txtHoldingNo` (İşletme No, `autoPostBack`), `txtAnimalOwnerName`, `txtPersonIdNo` (11 haneli maskeli), `txtAnimalOwnerAddress`. Hayvan eklendikten sonra bunların TARBİL tarafından doldurulup doldurulmadığı doğrulanacak; eklenti yine bunları **doldurmaz**.
  - `RadGridProduct` (ürün tablosu, `EditMode: InPlace`, detay tablolu): komut satırında "Ürün Ekle" (`...RadGridProduct$ctl00$ctl02$ctl00$InitInsertButton` postback'i). Sütunlar: Ürün, Takdim Şekli, **Seri Numarası**, Son Kullanım Tarihi, Stok Miktarı, Stok Tipi, Ürün Adet. → Seri/lot numarası stok kaleminden geliyor; Vetly'deki lot numarası yalnız doğru stok kalemini seçmeye yardım eder.
  - Yanıtın ViewState'inde, ürün eklenince açılan detay satırına ait olduğu anlaşılan alanlar var: Aşı Grubu, Aşı, Uygulama Türü (Aerosol, burun, enjeksiyon, oral, …), Doz ve Doz birimi (ADET, CC, DAMLA, MG, ML, ppb, ppm), Aşılama Amacı (Mihrak `M` / Özel Talep `O` / Program `P` / Rapel `R`), Makbuz Cilt (Seri) Numarası, "Belge No: Kayıt Sonrası Oluşturulacak.", ve **Vetbis** uyarısı ("Vetbis'e Aktarmadan Devam Edilsin mi?"). Kesin yapı, ürün eklenmiş hâlin yanıtıyla doğrulanacak.
- **Hayvan arama penceresi doğrulandı (2026-10-03):** hekimin kullandığı "PetVet'ten Hayvan Ara ve Ekle" butonu da aynı `VaccineKKBSAnimalSearchModalPage.aspx?AnimalType=C` popup'ını açıyor (yukarıdaki tarif geçerli). Ek ayrıntılar: `txtChipNo` maxlength 15; il/ilçe/mahalle klinik adresiyle önceden seçili; `cbxSex` değerleri M/F/U; `cbxBreedValue` türe göre ırk listesi (GUID). Sonuç tablosu `radGridAnimal` (`EditMode: EditForms`, sayfa 20 satır, `BIRTHDATE` biçimi `dd/MM/yy`); başlıktaki "tümünü seç" kutusu postback yapıyor — satır kutusunun da postback yapıp yapmadığı arama yanıtıyla doğrulanacak. Popup'ın kendi bildirim paneli `ContentPlaceHolder1_UCRECEIPTNotification_pnlNotifi*` (sonuç yok / hata mesajları burada çıkabilir).
- Dikkat: sayfa fonksiyonlarında `debugger;` satırları var (geliştirici araçları açıkken akış durur); sağ tık kapalı; `window.alert` → `radalert`. Çıkış `__doPostBack('Exit_Click')` — eklenti asla tetiklememeli.

**Açık sorular** (uygulama planından önce kullanıcıdan alınacak, bkz. Bölüm 11): başarı mesajının biçimi, aşı ürünü tablosunun (`RadGridProduct`) davranışı, `runToParentUpdateAnimal` sonrası tablo, konum filtresinin çip aramasını kısıtlayıp kısıtlamadığı, köpek ırk listesi.

## 4. Bileşenler ve Sınırlar

```
Hekimin Chrome'u
  Vetly sekmesi ──"TARBİL'e aktar"──┐        TARBİL sekmesi (aşı sayfası + arama popup'ı)
                                    ▼                     ▲ doldurur / okur
                 Vetly TARBİL Eklentisi: ① yan panel  ② arka plan (Vetly API, anahtar)  ③ TARBİL betikleri
                                    │ HTTPS + eklenti anahtarı
                                    ▼
                 Vetly backend — integration/tarbil (kuyruk, eşleştirme, anahtarlar)
```

- **Backend (`integration/tarbil`):** tek gerçek kaynak. Hasta/aşı verisine `patient` ve `encounter` modüllerinin `*LookupPort`'ları üzerinden erişir (architecture.md kuralı).
- **Web uygulaması:** Ayarlar > Entegrasyonlar'a eklenti bağlama/iptal + öğrenilen eşleştirmeler; aşı kaydı yanında "TARBİL'e aktar".
- **Eklenti:** Manifest V3, TypeScript, Vite; yan panel React + `frontend/src/styles/tokens.css`. Monorepo'da `extension/`.

**Bilinçli sınırlar:**
- Eklenti TARBİL'e kendisi istek atmaz, Kaydet'e basmaz.
- Eklentiye sadece aktarım için gereken veri gelir: hasta adı, tür, ırk, cinsiyet, çip no, aşı adı, lot, tarih. Sahip TC/adres/telefon gelmez.
- Eklenti anahtarı sadece `/api/v1/tarbil-extension/**` uçlarına erişir.

## 5. Backend Değişiklikleri

### 5.1 Sahte senkronun kaldırılması
- `MockTarbilAdapter`, `TarbilSyncPort` (+ `TarbilSyncRequest`, `TarbilSyncOutcome`), `TarbilSyncExecutor`, `TarbilRetryScheduler`, `RetryDueTarbilSyncsUseCase`, `RetryTarbilSyncUseCase` ve `POST /api/v1/tarbil/sync-logs/{id}/retry` kaldırılır. `QueueTarbilSyncUseCase` yalnızca `PENDING` satır yazar; sunucu hiçbir kaydı kendi başına gönderilmiş saymaz.
- Gerekçe: Sunucu tarafında gönderim olmadığı için port'u "hiçbir şey yapmayan" bir adaptörle yaşatmak, çağrılan ama iş yapmayan bir executor zinciri bırakırdı. İleride resmi bir TARBİL API'si çıkarsa port/adaptör o gün, gerçek ihtiyaçla yeniden eklenir (eklenti akışı bundan bağımsız çalışmaya devam eder).
- `IDENTIFICATION`/`TREATMENT` kuyruklama durdurulur (`PatientIdentificationUpdatedEventListener` kaldırılır); enum değerleri mevcut satırlar için korunur.
- Migration: mock'un `SYNCED` işaretlediği satırlar `PENDING`'e çekilir (hiçbiri gerçekten gönderilmedi).

### 5.2 Veri modeli

**`tarbil_sync_log`** (genişletilir; aşı başına bir satır):

| Alan | Açıklama |
|---|---|
| `vaccination_record_id` | UUID, `VACCINATION` satırları için benzersiz (tekrar önleme) |
| `status` | `PENDING` → `SUBMITTED` veya `DISMISSED` (`FAILED` kaldırılır, mevcut değerler `PENDING`'e) |
| `submitted_at`, `submitted_by_staff_id` | Gönderim zamanı ve hekim |
| `confirmation_method` | `AUTO` (başarı mesajı yakalandı) / `MANUAL` (elle işaretlendi) |
| `tarbil_reference` | TARBİL kayıt numarası gösterirse (nullable) |
| `dismissed_reason`, `dismissed_at`, `dismissed_by_staff_id` | "Bildirilmeyecek" bilgisi |

`payload`, `attempt_count`, `next_retry_at` kaldırılır. Aşı ve hasta verisi satırda saklanmaz; istek anında lookup portlarından okunur (çip sonradan eklense bile güncel).

**`tarbil_value_mapping`** (öğrenilen eşleştirmeler): `id`, `tenant_id`, `kind` (`VACCINE` | `SPECIES`), `vetly_key`, `tarbil_fields` (jsonb, ör. `{"vaccine":{"value":"…","text":"…"},"disease":{…}}`), `learned_by_staff_id`, `updated_at`. Benzersiz: `(tenant_id, kind, vetly_key)`. `VACCINE` anahtarı: aşı adının normalize hali (trim, tek boşluk, Türkçe-duyarlı küçük harf). `SPECIES` anahtarı: Vetly `speciesId`. `tarbil_fields` JSON tutulur çünkü formun tüm alanları henüz bilinmiyor; yeni alan migration gerektirmez.

**`tarbil_extension_token`**: `id`, `tenant_id`, `staff_user_id`, `label`, `token_hash`, `pairing_code_hash`, `pairing_expires_at`, `paired_at`, `created_at`, `last_used_at`, `revoked_at`. Kod ve anahtar yalnızca SHA-256 özeti olarak saklanır; anahtarın kendisi sadece eşleştirme yanıtında bir kez döner.

### 5.3 API uçları

**Web (mevcut JWT, `/api/v1/tarbil`):**

| Uç | Yetki |
|---|---|
| `POST /extension/pairing-codes` → 8 karakter, 10 dk geçerli kod | VET, ADMIN (kendi adına) |
| `GET /extension/tokens`, `DELETE /extension/tokens/{id}` | Kendi anahtarları; ADMIN kliniğin tümü |
| `GET /sync-logs` (mevcut), `POST /sync-logs/{id}/dismiss`, `POST /sync-logs/{id}/restore` | VET, ADMIN |
| `GET /mappings`, `DELETE /mappings/{id}` | VET, ADMIN |

**Eklenti (eklenti anahtarı, `/api/v1/tarbil-extension`):**

| Uç | Açıklama |
|---|---|
| `POST /pair` (anahtarsız) | `{code, label}` → `{token}`; kod tek kullanımlık; `RateLimitFilter` kapsamında |
| `GET /me` | Klinik adı, hekim adı |
| `GET /pending` | Bekleyen aşılar + her biri için bilinen eşleştirmeler |
| `GET /submissions/{id}` | Tek kayıt (durumu dahil; `SUBMITTED` ise eklenti uyarır) |
| `POST /submissions/{id}/submitted` | `{method, tarbilReference?}` — idempotent (zaten `SUBMITTED` ise mevcut kaydı döner) |
| `POST /submissions/{id}/dismiss` | `{reason}` |
| `PUT /mappings/{kind}/{key}` | Öğrenilen eşleştirmeyi yazar (son yazan kazanır) |

### 5.4 Güvenlik
- Eklenti uçları için ayrı `SecurityFilterChain` (`PlatformAdminSecurityConfig` ile aynı paralel desen): `Authorization: Bearer vtx_…` doğrulanır, `TenantContext` ve hekim ayarlanır, `last_used_at` güncellenir. Mevcut JWT zinciri değişmez.
- Eklenti anahtarı JWT yerine geçmez; JWT eklenti uçlarına erişemez. İptal edilen anahtar sonraki istekte reddedilir.
- CORS değişikliği gerekmez (istekler `host_permissions`'lı service worker'dan gider).
- `api-conventions.md` rol matrisine yeni uçlar ve VET erişimi eklenir.

## 6. Eklenti İç Yapısı

```
extension/
├── manifest.json            MV3; izinler: storage, sidePanel, scripting
│                            host_permissions: uygulama.vetly.com.tr, hbsapp.tarbil.gov.tr (+ dev: localhost)
│                            externally_connectable: uygulama.vetly.com.tr (+ dev: localhost)
│                            key: sabit eklenti id'si için
└── src/
    ├── shared/      messages.ts, types.ts
    ├── background/  index.ts (mesaj yönlendirici), vetlyApi.ts (Vetly API'ye giden TEK yer),
    │                tokenStore.ts (chrome.storage.local), confirmationOutbox.ts
    ├── sidepanel/   App.tsx, PairingView.tsx, PendingList.tsx, SubmissionCard.tsx
    └── tarbil/
        ├── content.ts          izole dünya: arka plan ↔ sayfa köprüsü, sayfa içi "Vetly kartı"
        └── page/               MAIN dünya
            ├── selectors.ts    TARBİL'e özgü TÜM id'ler/metinler (tek dosya)
            ├── telerik.ts      setText, selectCombo, click, waitForAjax
            ├── vaccinePage.ts  formu doldur, seçili değerleri oku, başarıyı yakala
            └── animalSearch.ts çip yaz, Ara, eşleşen satırı işaretle
```

- **Arka plan:** Vetly API'yi yalnız bu katman çağırır; anahtar başka parçaya verilmez. Vetly API taban adresi derleme moduna göre (prod: `https://uygulama.vetly.com.tr`, dev: `http://localhost:8080`).
- **Aktif aşı:** `chrome.storage.session`'da tek kayıt (tarayıcı kapanınca silinir); ana sayfa ve arama popup'ı buradan okur.
- **Kısıt:** `chrome.sidePanel.open` yalnız eklenti içi kullanıcı hareketiyle çalışır; bu yüzden Vetly'deki buton paneli açmaz, TARBİL sayfasındaki "Vetly kartı" o aşıyla açılır.
- **Gizlilik:** Üretim derlemesinde konsola hasta/çip bilgisi yazılmaz.

## 7. Veri Akışları

1. **Bağlama (bir kez):** Vetly > Ayarlar > Entegrasyonlar > "Eklentiyi bağla" → kod (`K7QM-2XPA`) → yan panelde kod + cihaz adı → `POST /pair` → anahtar `chrome.storage.local`'e → panelde "Klinik – Hekim".
2. **Kuyruklama (otomatik):** `VaccinationRecordedEvent` → `vaccination_record_id` ile `PENDING` satır (idempotent). Dışarıya hiçbir şey gitmez.
3. **Panelden doldurma:** Hekim TARBİL'e kendisi girer, aşı sayfasını açar → panel `GET /pending` → "Doldur" → aktif aşı kaydedilir → sayfa betiği sırayla (her combobox'tan sonra AJAX bekleyerek): `dpApplicationDate`, `cbxAnimalType` (`SPECIES` eşleştirmesi), varsa `VACCINE` eşleştirmesindeki alanlar → "Hayvan Ara ve Ekle" → popup'ta çip yazılır, Ara'ya basılır, **çipi birebir eşleşen tek satır** işaretlenir → **hekim "Transfer Et"e basar** → kart: "Kontrol edip Kaydet'e basın" (eşleştirmesi bilinmeyen alanlar listelenir) → **hekim Kaydet'e basar**.
4. **Başarı ve öğrenme:** Başarı bildirimi yakalanır (+ varsa kayıt no) → ilk kez aktarılan aşıysa seçili aşı/hastalık değerleri okunur → `POST /submitted {AUTO}` + `PUT /mappings/VACCINE/{anahtar}` → kart "✓ TARBİL'e kaydedildi". 15 sn içinde başarı yakalanmazsa "Kaydedildiyse işaretle" → `MANUAL` (bu durumda eşleştirme **öğrenilmez**).
5. **Vetly'den başlatma:** "TARBİL'e aktar" → `chrome.runtime.sendMessage(eklentiId, {type:"SELECT_SUBMISSION", id})` (yanıt yoksa kurulum yönergesi) → TARBİL aşı sayfası yeni sekmede → sayfa içi Vetly kartı o aşıyla açılır → "Doldur" → akış 3'ün doldurma adımından devam.
6. **Bildirilmeyecek:** Panelde "Bildirilmeyecek" (+ sebep) → `DISMISSED`; Entegrasyonlar ekranından geri alınabilir.

**Özel durumlar:** Çip numarası yoksa otomatik arama yapılmaz, kart kimliklendirme gerekebileceğini söyler. Arama sıfır ya da birden fazla sonuç döndürürse hiçbir satır otomatik işaretlenmez; sıfır sonuçta konum filtresi ipucu gösterilir.

## 8. Hata Durumları

| Durum | Davranış |
|---|---|
| Anahtar geçersiz/iptal (401) | Anahtar silinir, panel eşleştirme ekranına döner |
| Vetly'ye ulaşılamıyor | Panel "Çevrimdışı"; `submitted` bildirimleri `confirmationOutbox`'a (`chrome.storage.local`) yazılır, bağlantı gelince otomatik gönderilir — TARBİL'e kaydedilmiş hiçbir onay kaybolmaz |
| TARBİL oturumu kapalı | Beklenen alanlar yok / giriş sayfası → kart "e-Devlet ile giriş yapın" + giriş sayfasını açan buton; aktif toplu aktarım korunur, girişten sonra kaldığı aşıdan devam eder (Bölüm 12.2) |
| TARBİL ekranı değişmiş (id bulunamadı) | Doldurma hemen durur; kart hangi adımda/hangi alanda durduğunu söyler; düzeltme `selectors.ts`'te |
| AJAX 10 sn içinde bitmedi | Doldurma o adımda durur, tamamlanan/kalan adımlar gösterilir |
| Zaten `SUBMITTED` aşı seçildi | Kart tarihle uyarır, form doldurulmaz (mükerrer resmi kayıt önlenir) |
| Aşı kaydı Vetly'de silinmiş | `pending`'de görünmez; `GET /submissions/{id}` 404 → "Bu aşı kaydı artık yok" |
| Yanlış eşleştirme öğrenilmiş | Ayarlar > Entegrasyonlar'dan silinir, sonraki aktarımda yeniden öğrenilir |
| Kod deneme saldırısı | 10 dk, tek kullanım, ~2⁴⁰ kod uzayı, `RateLimitFilter` |

## 9. Test

**Backend:**
- Use-case birim testleri: `submitted` idempotent; dismiss/restore; süresi dolmuş ve kullanılmış kod reddi; iptal edilmiş anahtar reddi; aynı aşı için çift satır oluşmaması; mock'un kaldırılmasından sonra hiçbir kaydın kendiliğinden `SUBMITTED` olmaması.
- Güvenlik entegrasyon testi (Testcontainers): eklenti anahtarı `/api/v1/patients`'a erişemez; JWT eklenti uçlarına erişemez; A kliniğinin anahtarı B kliniğinin kayıtlarını göremez.
- `ApplicationModulesTest` geçer (tarbil → patient/encounter yalnız port üzerinden).
- Çalışan instance'a karşı curl ile uçtan uca: eşleştirme → `pending` → `submitted`.

**Eklenti:**
- Vitest: aşı adı normalizasyonu, çip ile satır eşleştirme, mesaj yönlendirme, çevrimdışı onay kuyruğu.
- Sayfa betikleri: sahte `$find`/`PageRequestManager` ile, kullanıcının sağladığı kişisel verisi temizlenmiş TARBİL HTML'leri fixture olarak.
- Gerçek TARBİL'de elle kabul testi, kontrol listesiyle. **Kural: test için sahte resmi kayıt oluşturulmaz**; ilk denemeler gerçekten yapılmış aşıların bildiriminde, Kaydet öncesi alanlar gözle kontrol edilerek yapılır.

## 10. Dokümantasyon Güncellemeleri
- `docs/api-conventions.md`: yeni uçlar ve rol matrisi.
- `docs/architecture.md`: eklenti anahtarı için üçüncü `SecurityFilterChain` notu.
- `docs/deployment.md`: eklentinin derlenmesi ve "paketlenmemiş uzantı" olarak kurulumu.
- `docs/implementation-plan.md`: TARBİL bölümündeki "MockTarbilAdapter stub" maddesinin güncellenmesi.

## 11. Uygulama Öncesi Kullanıcıdan Alınacak Bilgiler
Kişisel veriler (TC, ad, adres, telefon, çip) `***` ile maskelenerek; çerez/oturum başlıkları hiçbir zaman paylaşılmadan:
1. ~~Ana aşı sayfasının kaynağı~~ — alındı (2026-10-03, ilk yükleme hâli; Bölüm 3). **Eksik:** tür olarak Kedi seçildikten sonraki hâlinin kaynağı (aşı/hastalık/amaç/doz alanları ve `RadGridProduct` o zaman oluşuyor).
2. **Başarılı bir kayıttan sonra** ekranda görünen mesajın ekran görüntüsü + o isteğin Network yanıtı.
3. **"Transfer Et" isteğinin Network yanıtı** (`runReceiptUpdateParentAnimal(` geçen satır).
4. **Aşı ürünü seçimi akışı:** `UcVaccineStockSearchModalPage.aspx` stok popup'ının kaynağı ve seri/lot numarasının nereden geldiği (stok kaleminde mi, ayrıca mı giriliyor).
5. **Konum filtresi testi:** farklı mahallede kayıtlı bir hayvan çip numarasıyla bulunabiliyor mu?
6. **Köpek arama penceresinin kaynağı** (`AnimalType=D`, `Ctrl+U`).
7. **e-Devlet girişinden sonra dönülen TARBİL sayfasının adresi** (yalnız adres; oturum/token parametreleri silinerek) ve aşı sayfasına menüden nasıl gidildiği.
8. **Oturum süresi:** TARBİL 20–30 dk boş bırakılınca oturum kapanıyor mu, kapanınca ne görünüyor (giriş sayfası adresi / mesaj).
10. ~~Hayvan ekleme butonu~~ — "PetVet'ten Hayvan Ara ve Ekle" (aynı KKBS arama popup'ı). **Eksik:** popup'ta çiple "Ara" yanıtı (sonuç satırının yapısı) ve "Transfer Et" sonrası ana sayfanın async yanıtı (tablo satırı + sahip alanları).
11. **Ürün Ekle** tıklandıktan sonraki async yanıt ve stok popup'ı (`UcVaccineStockSearchModalPage.aspx`) kaynağı.
9. **Asistan yetkilendirme:** TARBİL'de hekim adına teknisyen/asistan yetkilendirme seçeneği var mı.

## 12. Faz 2 Akışı: "Günde 1 giriş, aşı başına 1 tık" (2026-10-03 kararı)

**Gerekçe:** TARBİL'e yalnız e-Devlet ile giriliyor. Giriş otomatikleştirilemez (Bölüm 2), ama hekimin eforu girişin sıklığı ve aşı başına tıklama sayısıyla belirlenir. Hedef: girişi günde bir kereye, her aşıyı tek tıka (Kaydet) indirmek. Bölüm 7'deki 3 ve 5 numaralı akışların yerini alır; diğer akışlar geçerli.

### 12.1 Toplu aktarım ("Hepsini aktar")
- Vetly'de bekleyen aşı varken görünür bant: "N aşı TARBİL'e aktarılmayı bekliyor → Hepsini aktar". Yan panelde de aynı buton.
- Toplu aktarım, bekleyen aşıların sıralı listesini aktif kuyruk olarak `chrome.storage.session`'a yazar (Faz 1'deki tek "aktif aşı"nın genellemesi) ve TARBİL'i açar.
- Kuyruk tek tek işlenir: doldur → hekim Kaydet → başarı yakalanır → `POST /submitted {AUTO}` → sıradaki aşı kendiliğinden yüklenir. Hekim Vetly'ye dönüp işaretleme yapmaz.
- Kart her an "Bu aşıyı atla" (kuyrukta sona alır), "Bildirilmeyecek" ve "Aktarımı durdur" sunar. Kuyruk bitince özet: "5 aşı kaydedildi, 1 atlandı".

### 12.2 Giriş ve dönüş
- Eklenti TARBİL'i açtığında oturum açıksa doğrudan aşı sayfasına gider.
- Oturum kapalıysa (giriş sayfası ya da beklenen alanlar yok) kart: "e-Devlet ile giriş yapın" + giriş sayfasını açan buton. Kimlik bilgisi eklentide tutulmaz; hekim isterse Chrome'un şifre yöneticisini kullanır.
- e-Devlet hekimi TARBİL'e döndürdüğünde içerik betiği aktif kuyruk olduğunu görür ve aşı sayfasına kendisi geçer; kuyruk kaldığı yerden sürer. (Dönüş sayfasının adresi: Bölüm 11 madde 7.)

### 12.3 Oturumu canlı tutma (isteğe bağlı, varsayılan açık, panelden kapatılabilir)
- Yalnız açık bir TARBİL sekmesi varken, içerik betiği o sekmeden periyodik olarak aynı kökene veri göndermeyen sade bir sayfa isteği (GET) atar; böylece sunucu tarafı oturum zaman aşımına uğramaz.
- Aralık, Bölüm 11 madde 8'de öğrenilecek zaman aşımının yarısından kısa seçilir. TARBİL sekmesi yoksa hiçbir istek atılmaz; tarayıcı kapanınca biter.
- Bu bir form postback'i değildir ve hiçbir veri değiştirmez (Bölüm 2'deki istisna).

### 12.4 Hekimin tıklaması yalnız Kaydet
- Bölüm 7.3'teki "hekim Transfer Et'e basar" adımı değişti: arama sonucu **çipi birebir eşleşen tek satır** ise eklenti satırı işaretleyip "Transfer Et"e kendisi basar (bu adım resmi kayıt oluşturmaz, yalnız hayvanı forma ekler). Sıfır ya da birden fazla sonuçta hiçbir şey otomatik yapılmaz, karar hekime kalır.
- Kaydet her zaman hekimde (karar A). Eşleştirmesi bilinmeyen alanlar kartta listelenir; hekim onları TARBİL'de seçip Kaydet'e basar, eklenti öğrenir (Bölüm 7.4).

### 12.5 Ertelenen: toplu onayla eklentinin Kaydet'e basması (karar B)
Hekimin günde bir kez "N aşıyı kaydet" onayı vermesi ve eklentinin Kaydet'e basması şimdilik yapılmıyor (yanlış eşleştirmenin birden çok resmi kayda yayılma riski; hukuki sorumluluğun netliği). Eşleştirmeler birkaç hafta sorunsuz çalıştıktan sonra, yalnız eşleştirmesi öğrenilmiş aşılar için açılabilen bir ayar olarak yeniden değerlendirilir; hiç görülmemiş aşı her zaman 12.4 ile ilerler.
