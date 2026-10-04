# TARBİL P2 — Aşı Ürünü (stoktan seçim + "Ürün Ekle" otomasyonu)

**Tarih:** 2026-10-04 · **Durum:** onaylandı (bölüm 1 ve 2, sohbette) · **Üst tasarım:** `2026-10-04-tarbil-otomasyon-cekirdegi-design.md` (§3 canlı bulgular "Aşı ürünü", §12 P2)

## 1. Amaç

Hekim aşıyı Vetly'de **stoktan** seçer (ad + seri + SKT). Vetly stoğu 1 düşer. Eklenti TARBİL aşı belgesinde hayvandan sonra **"Ürün Ekle" → stok penceresinde seri ile arama → "Seç" → Ürün Adet = 1** adımlarını yapar. Hekime yalnız satır **Kaydet** (TARBİL stoğundan düşer), Detay alanlarının kontrolü ve **Onayla** kalır.

**Başarı ölçütü:** stoktan seçilmiş bir aşıda hekim "TARBİL'de doldur" dedikten sonra ürün satırı doğru seri ve 1 adetle hazır gelir; eklenti satır Kaydet'e ve Onayla'ya hiç basmaz.

## 2. Kurallar (değişmez)

- Eklenti **satır Kaydet** (`PerformInsertButton`), **Onayla** (`btnInsert`, `btnInsert2`), Reddet ve çıkışa basmaz. `PerformInsertButton` yasak desenlere eklenir ve testle doğrulanır.
- Seri bilinmiyorsa (aşı serbest yazılmış) ürün adımı tamamen hekimdedir; bugünkü "Ürün Ekle'den seçin" kartı kalır.
- Eklenti yalnız birebir **tek** eşleşmeyi seçer; belirsizlikte (yok / çok / SKT geçmiş / ürün adı tutmuyor) hekime bırakır.
- Konsola veri yazılmaz; TARBİL ViewState okunmaz.

## 3. Vetly

### 3.1 Veri
- V67: `vaccination_records.inventory_item_id UUID NULL` (FK yok; modüller arası kimlik). Lot, seçilen stok kaleminin `lot_number`'ından kopyalanır (`vaccination_records.lot_number` mevcut).
- `StockReferenceType` + `VACCINATION`.

### 3.2 Yeni Aşı formu
- "Aşı" alanı stoktan arama-seçim olur: şubenin (`branchIds[0]`) **aşı** kalemleri (`category = 'Aşı'` ya da `tarbil_system = 'HBSAPP_VACCINE'`), `quantity_on_hand > 0`. Seçenek: `Ad · Seri X · SKT gg.aa.yyyy · N adet`; SKT geçmiş seri kırmızı ve seçilemez.
- Stokta olmayan aşı için serbest yazım sürer (`inventoryItemId` null).
- İstek: `RecordVaccinationRequest` + `inventoryItemId` (opsiyonel). Seçiliyse form `vaccineName` ve `lotNumber`'ı stok kaleminden doldurup gönderir (`encounter` stok kalemini okuyamaz, bkz. 3.3); hekim lot alanını değiştiremez.

### 3.3 Stok düşme / iade (olayla)
`encounter` → `inventory` doğrudan bağımlılık **kurulamaz** (inventory zaten `encounter::domain.event` dinliyor; döngü). Bu yüzden:
- **Karar:** `encounter` stok kalemini hiç okumaz; `inventoryItemId`'yi saklar ve olayla bildirir. Ad/lot istemciden gelir (3.2). Stok işlemi tamamen `inventory` dinleyicisindedir.
- `VaccinationRecordedEvent` + `inventoryItemId` (null olabilir). `ADMINISTERED` durumda yayınlanır (bugün de öyle: kayıt ve "uygulandı olarak işaretle").
- Yeni `VaccinationCancelledEvent(vaccinationRecordId, inventoryItemId, wasAdministered)`; `CancelVaccinationUseCase` yayınlar.
- `inventory` dinleyicisi (`@EventListener`, aynı işlem):
  - Recorded + `inventoryItemId` → kalem bu kiracıda yoksa yok sayılır (log yok, PII yok); varsa 1 `OUT` (`VACCINATION`, referans = aşı kaydı). Stok 0 ise **reddedilmez**: miktar eksiye düşmez (0'da kalır), hareket yazılmaz, kayıt yine oluşur (yarış durumu; form zaten 0 stoğu göstermez).
  - Cancelled + `wasAdministered` + `inventoryItemId` → 1 `IN` (`VACCINATION`).
  - Aynı aşı kaydı için ikinci `OUT` yazılmaz (referansa göre kontrol) — "uygulandı olarak işaretle" ile kayıt sırasında çift yayın olsa bile.

### 3.4 Eklentiye giden veri
`TarbilSubmissionView` + `tarbilProductName` (aşı kaydının stok kaleminden; `VaccinationLookupPort` → `VaccinationTarbilView` + `inventoryItemId`, `integration/tarbil` → yeni `InventoryItemLookupPort` (inventory domain, mevcut `modules.inventory::domain` bağımlılığı) ile ad). `lotNumber` zaten gidiyor.

## 4. Eklenti

### 4.1 Akış (aşı belgesi)
Mevcut: tarih → tür → PetVet → hayvan doğrulandı (`awaitingConfirm`). Yeni adımlar (`FlowStep` + `choosingProduct`, `productReady`):

1. Hayvan doğrulandı ve `lotNumber` var → `choosingProduct`; MAIN `clickAllowed({page:'vaccineReceipt', button:'addProduct'})` (`RadGridProduct_ctl00_ctl02_ctl00_InitInsertButton`). Sunucu stok penceresini açar. Pencere `POPUP_WAIT_MS` içinde ürün getirmezse kart "Stok penceresini aç" düğmesi gösterir (aynı buton). Hayvan yoksa TARBİL hatası ("Hayvan Seçimi Yapılmadı") → hata kartı.
2. **Stok penceresi** (`pageKind` `vaccineStockPopup`: `/modules/receipt/pages/modalpages/ucvaccinestocksearchmodalpage.aspx`): akış `choosingProduct` ve ≤ 120 sn ise: `searchSerial({serial})` (MAIN: `txtSerialNo` = seri, `btnSearch` izin listesi `vaccineStockPopup.search`), tabloyu oku (`radGridStock_ctl00`: Aşı Adı, Takdim Şekli, Seri Numarası, SKT, Ürün Miktarı — başlığa göre), eşleşme: normalize seri eşit **ve** (Vetly `tarbilProductName` yoksa ya da normalize ad eşit). Tek ve SKT ≥ bugün → `selectStockRow({linkId})` (yalnız `radGridStock_ctl00` içindeki `SelectlinkButton`; `clickElement`). Aksi halde akış `needsVet` + pencerede açıklama kartı.
3. Ana sayfa: `thead tr.rgEditRow` içinde seri hücresi Vetly serisi ile eşitse `setText(txtQuantity, '1')` → `productReady` → kart "Ürün satırı hazır …; satırdaki Kaydet'e siz basın (TARBİL stoğundan düşer), Detay alanlarını kontrol edin, sonra Onayla". Seri farklı → "Yanlış ürün seçildi" uyarısı, adet yazılmaz.
4. Onayla + başarı yakalama değişmez (`awaitingConfirm` ile aynı mantık `productReady` için de geçerli).

### 4.2 Seçiciler / izin listesi
- `RECEIPT.addProduct = '_RadGridProduct_ctl00_ctl02_ctl00_InitInsertButton'`, `RECEIPT.productGrid = '_RadGridProduct_ctl00'`, `RECEIPT.productQuantity = '_RadGridProduct_ctl00_ctl02_ctl03_txtQuantity'`.
- `VACCINE_STOCK_POPUP = { serial: '_UcVaccineStockSearch_txtSerialNo', search: '_UcVaccineStockSearch_btnSearch', grid: '_UcVaccineStockSearch_radGridStock_ctl00' }`.
- `ALLOWED_BUTTONS.vaccineReceipt.addProduct`, `ALLOWED_BUTTONS.vaccineStockPopup.search`. `FORBIDDEN_BUTTON_PATTERNS` + `/PerformInsertButton$/i`.

### 4.3 Hatalar
| Durum | Davranış |
|---|---|
| Seri yok (serbest aşı) | Ürün adımı atlanır, bugünkü kart |
| Pencere açılmadı | "Stok penceresini aç" düğmesi + açılır pencere izni metni |
| Stokta seri yok / çok satır / ad tutmuyor | Pencerede ve ana kartta açıklama; seçim hekimde |
| SKT geçmiş | Seçilmez; "Son kullanma tarihi geçmiş" uyarısı |
| Formdaki ürün serisi farklı | "Yanlış ürün" uyarısı; adet yazılmaz |

## 5. Kapsam dışı
- Satır Kaydet sonrası **Detay** alanları (aşı/doz/birim/uygulama türü/amaç): canlı görülemedi (satır Kaydet stoktan düşüyor). İlk gerçek aşıda yapı okunur; sonraki turda öğrenme.
- Çok şube seçimi (stok `branchIds[0]`).
- Parça stok / bir flakondan birden çok hayvan: her aşı 1 adet.

## 6. Test
- Backend: dinleyici birim testleri (OUT, IN, 0 stok, çift yayın, başka kiracı kalemi), `RecordVaccinationUseCase` / `CancelVaccinationUseCase` olay içerikleri, assembler `tarbilProductName`, entegrasyon testi (aşı kaydı → stok 1 düşer → iptal → geri gelir).
- Eklenti: `pageKind`, izin/yasak listesi (`PerformInsertButton`), stok penceresi okuyucu + eşleşme, MAIN `searchSerial`/`selectStockRow`/`setQuantity`, akış testleri (seri yok, tek eşleşme, çok, SKT geçmiş, yanlış ürün, `productReady` sonrası başarı yakalama).
- Frontend: tip kontrolü + derleme; stok seçicinin sadeliği elle doğrulanır.
- Canlı kabul: ilk gerçek stoktan seçilmiş aşı.

**Migration notu:** V67 sütun eklemesi geriye uyumludur (çalışan eski backend'i bozmaz).

**Uygulandı:** `docs/superpowers/plans/2026-10-04-tarbil-p2-asi-urunu.md` (V67).
