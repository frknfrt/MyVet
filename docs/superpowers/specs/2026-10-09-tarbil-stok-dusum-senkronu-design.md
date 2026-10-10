# TARBİL Stok Düşüm Senkronu — Tasarım

Tarih: 2026-10-09 · Durum: taslak (kullanıcı onayı bekliyor)
İlgili: `2026-10-04-tarbil-otomasyon-cekirdegi-design.md` (P2/P3), `2026-10-04-tarbil-p2-asi-urunu-design.md`,
P1a stok eşitleme (`2026-10-04-tarbil-p1a-stok-esitleme.md`).

## 1. Amaç

Vetly ile TARBİL stoğu senkron kalsın. TARBİL'e bağlı bir ürünün (aşı ya da ilaç) Vetly stoğu, **TARBİL'de düşüm
gerçekleştiğinde** düşer — öncesinde değil. Böylece P1a stok karşılaştırması "güncel" kalır; arada fark yalnız hekimin
henüz TARBİL'e geçmediği bekleyen kalemlerdir ve bunlar Vetly'de açıkça görünür.

Kullanıcı kararları (2026-10-09):
- Muayene tamamlanınca hekime "TARBİL'den stok düş" seçeneği sunulur.
- Hekim TARBİL'de düşümü ne zaman yaparsa eklenti o zaman Vetly'ye bildirir; Vetly stoğu o anda düşer.
- Aşı da aynı modele geçer (tutarlılık).
- Reçete muayene olmadan da yazılabilir (hasta sayfasından). **Vetly'de yazılan her reçete TARBİL'e gider**: stoktan
  verilen kalemler "Stoktan Düşerek İlaç Reçetesi"ne, eczaneden alınacaklar normal "İlaç Reçetesi"ne. Sıra: önce stoktan
  düşen reçete (bu tasarım, Faz 1), ardından eczane reçetesi (Faz 2).
- Hekim her zaman resmi düğmeye kendisi basar (aşı: ürün satırı Kaydet + Onayla; ilaç: Reçeteyi Onayla).

## 2. Canlı bulgular (2026-10-09, yalnız yapı okundu)

- vetilac › **Doğrudan Stok Düşme** (`StockOperationPage.aspx`): neden yalnız Kırılma / Kayıp → kullanım için değil.
- vetilac › **Stok Düşme Talebi** (`StockUpdateRequestPage.aspx`): il/ilçe onaylı talep (bozulma, SKT, açık ürün kullanımı,
  kayıp, sel, yangın…) → olağan dışı durumlar, kapsam dışı.
- vetilac › **Reçete Satış/Teslim** (`ReceiptSearch.aspx`): başka hekimin reçetesine seri no + şifre ile satış → kapsam dışı.
- hbsapp › **Stoktan Düşerek İlaç Reçetesi Ekle** (`/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx?type=1`):
  reçete sınıfı (`rbtnReceiptClass`: Muayene/Tedavi | Tavsiye), `dpTreatmentStartDate`, teşhis listesi (`cbxDisease` +
  hastalık onay kutuları, `txtDiseaseOther`), hayvan bilgileri (`cbxAnimalType` + hayvan ekleme), `btnInsert` /
  `btnInsert2` "Reçeteyi Onayla". **İlaç klinikte kullanılınca TARBİL stoğu bu reçeteyle düşer.**
- Ürün/stok satırları hayvan seçildikten sonra açılıyor; yapısı henüz görülmedi → §8 canlı inceleme.

## 3. Kapsam

**Faz 1 (bu tasarım) — TARBİL stoğunu düşüren her şey:**
1. **Muayenede kullanılan ilaç:** TARBİL'e bağlı stok kalemleri (`EncounterFinalizedEvent.usedItems`).
2. **Aşı:** stoktan seçilerek yapılan aşı (`VaccinationRecord.inventoryItemId`), P2 akışı.
3. **Reçete — kliniğin stoğundan verilen kalemler:** muayeneli ya da muayenesiz Vetly reçetesinde "Kliniğin stoğundan ver"
   işaretli kalemler (§5.6).

**Faz 2 (ayrı tasarım) — eczane reçetesi:** reçetenin stoktan verilmeyen kalemleri TARBİL "İlaç Reçetesi Ekle"
(`ReceiptPage.aspx?type=1`) ile gider; stok etkisi yok. Faz 1'de bu kalemler Vetly'de kalır (bugünkü gibi), TARBİL'e gitmez.

"TARBİL'e bağlı kalem" = `inventory_items.tarbil_product_name` dolu stok kalemi (P1a eşitlemesiyle oluşan / bağlanan).
Bağlı olmayan kalemler (sarf, TARBİL dışı ürün) bugünkü gibi anında düşer.

## 4. Veri modeli — bekleyen düşüm

Yeni tablo `pending_stock_deduction` (inventory modülü, `@TenantId`):

| Alan | Açıklama |
|---|---|
| id, tenant_id | |
| inventory_item_id | düşülecek stok kalemi |
| quantity | Vetly'de planlanan adet |
| reference_type | `ENCOUNTER` \| `VACCINATION` \| `PRESCRIPTION` |
| reference_id | encounter / vaccination record / prescription id |
| status | `PENDING` → `DEDUCTED` \| `CANCELLED` |
| settled_via | `TARBIL` \| `VETLY_ONLY` (DEDUCTED iken) |
| settled_quantity, settled_at, settled_by | gerçekleşen |
| created_at | |

Tekil indeks: `(reference_type, reference_id, inventory_item_id)` — aynı olay iki kez gelse de tek satır.
Düşüm gerçekleşince bugünkü `stock_movement` (OUT, `reference_type` ENCOUNTER/VACCINATION) yazılır; V77'deki tekil indeks
mantığı korunur (bir referans için bir OUT).

## 5. Akışlar

### 5.1 Muayene (ilaç)

1. Muayene tamamlanır (`EncounterFinalizedEvent`). Inventory dinleyicisi:
   - TARBİL'e bağlı **olmayan** kalemler → bugünkü gibi anında OUT.
   - TARBİL'e bağlı kalemler → `pending_stock_deduction` (PENDING), stok düşmez.
2. Muayene sayfasında kart: "Bu muayenede TARBİL'e bağlı N ilaç kullanıldı — TARBİL düşümü bekliyor." Düğmeler:
   - **TARBİL'den stok düş** → hekim **TARBİL teşhisi** seçer (V72 `tarbil_disease`, zorunlu) → TARBİL kuyruğuna
     `PRESCRIPTION` aktarımı girer (source = encounter id; reçete sınıfı Muayene/Tedavi; tedavi başlangıcı = muayene tarihi).
   - **Yalnız Vetly'den düş** → PENDING satırlar `VETLY_ONLY` ile düşülür (TARBİL'e gidilmez; yanlış eşleme vb. için).
3. Eklenti yan panelde aktarımı gösterir; "TARBİL'e aktar" → "Stoktan Düşerek İlaç Reçetesi Ekle" sayfası açılır ve doldurulur:
   sınıf, tarih, teşhis, hayvan (çip/pasaport araması, mevcut), ürün satırları (stoktan seri no + adet, P2 stok penceresi kalıbı).
4. Hekim **Reçeteyi Onayla**'ya basar. Eklenti başarıyı yakalar ve onaylanan satırları (ürün adı, seri no, adet) bildirir;
   yakalayamazsa yan paneldeki "TARBİL'de yaptım" (MANUAL) kullanılır — bu durumda planlanan adetler esas alınır.
5. Vetly: aktarım SUBMITTED olur, PENDING satırlar `TARBIL` ile DEDUCTED → stok düşer.

### 5.2 Aşı

1. Aşı stoktan seçilerek kaydedilir → aktarım bugünkü gibi otomatik kuyruğa girer; stok **düşmez**, `pending_stock_deduction`
   (VACCINATION, adet 1) oluşur. (Değişiklik: `ApplyVaccinationStockUseCase.administered` artık OUT yazmaz.)
2. Eklenti P2 akışını yürütür; hekim ürün satırı Kaydet + Onayla'ya basar; eklenti başarıyı yakalar.
3. Vetly: SUBMITTED + PENDING → DEDUCTED (`TARBIL`).

### 5.3 Bildirilen satırlarla eşleştirme

Eklentinin bildirdiği satırlar (seri no + adet) aktarımın PENDING satırlarıyla **lot/seri no** üzerinden eşleşir:
- eşleşen satır → bildirilen adet kadar düşülür (hekim TARBİL'de adedi değiştirdiyse Vetly de o kadar düşer);
- bildirilmeyen PENDING satır → PENDING kalır (stok sayfasında görünür, hekim TARBİL'e tekrar gider ya da "Yalnız Vetly'den düş");
- PENDING'de olmayan bildirilen satır → düşülmez, aktarım kaydına not düşülür (P1a karşılaştırması farkı gösterir).
MANUAL onayda bildirilen satır yoktur → tüm PENDING satırlar planlanan adetle düşülür.

### 5.4 İptal / reddetme

- Aşı iptali, düşüm **öncesi** → PENDING → CANCELLED (stok hiç düşmemişti, iade yok).
- Aşı iptali, düşüm **sonrası** (TARBİL'de düşmüş) → Vetly stoğu otomatik iade edilmez; uyarı: "TARBİL'de düşüm yapıldı;
  düzeltmeyi TARBİL'de yapın, stok karşılaştırması farkı gösterecek." (TARBİL esas kaynaktır.)
- Aktarımın reddedilmesi (Dismiss) PENDING satırlara dokunmaz; bekleyen listede kalırlar ve hekim "Yalnız Vetly'den düş" seçebilir.
  Geri alma (Restore) normal akışa döner.

### 5.5 Görünürlük

- Stok sayfası: "TARBİL düşümü bekleyen N kalem" bölümü — kalem, adet, kaynak (muayene/aşı, hasta, tarih), "Yalnız Vetly'den düş".
- Muayene sayfası kartı, PENDING satır kalmayınca "TARBİL'den düşüldü ✓" olur.
- `quantity_on_hand` değişmez; bekleyen kalemler ayrı gösterilir (gerekirse "kullanılabilir = eldeki − bekleyen" ileride).

### 5.6 Reçete (muayeneli / muayenesiz)

Vetly değişiklikleri:
- `prescriptions.encounter_id` isteğe bağlı olur. Hasta sayfasına **"Yeni reçete"** (VET/ADMIN) eklenir; aynı reçete kartı
  açılır. Muayene sayfasındaki reçete kartı aynen kalır.
- Reçeteye **TARBİL teşhisi** (`tarbil_disease_id`) eklenir; stoktan verilen kalem varsa zorunlu.
- Reçete kalemine isteğe bağlı **"Kliniğin stoğundan ver"**: stok kalemi (TARBİL'e bağlı, lot) + adet. Katalog ilacı (doz,
  sıklık, gün, yol) bugünkü gibi kalır; stok kalemi seçimi katalog ilacıyla ilişkilendirilir (aynı kalemde ikisi birlikte).
- Reçete yazılınca (`PrescriptionIssuedEvent`):
  - stoktan verilen kalemler → `pending_stock_deduction` (`reference_type = PRESCRIPTION`, reference = reçete id);
  - en az bir stoktan verilen kalem varsa TARBİL kuyruğuna `PRESCRIPTION` aktarımı otomatik girer (source = reçete id;
    sınıf Muayene/Tedavi; tedavi başlangıcı = reçete tarihi). Muayene kartındaki gibi ayrı düğme gerekmez — hekim reçeteyi
    yazarken stoktan vermeyi zaten seçmiştir. "Yalnız Vetly'den düş" stok sayfasındaki bekleyen listesinden yapılır.
- Eklenti, muayene kaynağıyla aynı "Stoktan Düşerek İlaç Reçetesi Ekle" akışını kullanır (ürün satırları = stoktan verilen kalemler).
- Reçete iptali: düşüm öncesi → PENDING → CANCELLED, aktarım reddedilir; düşüm sonrası → Vetly'de iade yok, uyarı:
  "TARBİL'de reçeteyi iptal edin" (TARBİL › Reçete İptal Et; otomasyonu Faz 2+).
- Aynı muayenede hem kullanılan ilaç hem stoktan verilen reçete varsa iki ayrı TARBİL reçetesi oluşur (fiziksel olarak ayrı
  birimler: klinikte uygulanan / sahibine verilen). Birleştirme ilk sürümde yok.

Kaynak türleri: `TarbilSubmission(PRESCRIPTION)` artık iki kaynaktan gelir → `source_kind` (`ENCOUNTER` | `PRESCRIPTION`)
alanı eklenir; mevcut "PRESCRIPTION → prescriptions.id" yorumu güncellenir.

## 6. Modül sınırları

- `inventory` → `encounter` olaylarını dinler (bugünkü gibi). `integration` → `inventory`'ye zaten bağımlı (`TarbilStockSyncPort`).
- Düşüm gerçekleştirme: inventory `domain` NamedInterface'inde yeni port `TarbilStockDeductionPort`
  (`listPending(referenceType, referenceId)`, `settle(referenceType, referenceId, List<SettledLine>, via)`, `settleVetlyOnly(...)`).
  `MarkSubmittedUseCase` onay sonrası bu portu çağırır — inventory, integration'ı dinlemez (döngü yok).
- Aynı işlemde (transaction) SUBMITTED + düşüm; idempotent (DEDUCTED satır tekrar düşülmez).

## 7. Eklenti

- Yeni sayfa türü `prescriptionStockReceipt` (hbsapp `ReceiptPageNew.aspx?type=1`); izin listesi / FORBIDDEN mevcut kurala uyar:
  `btnInsert`/`btnInsert2` (Reçeteyi Onayla) asla eklentice basılmaz.
- Doldurma adımları: sınıf, tarih, teşhis (TARBİL hastalık değeri), hayvan (mevcut arama op'ları), ürün satırları (stok penceresi).
- Başarı yakalama: onay sonrası sayfa/mesaj → `CONFIRM` + onaylanan satırlar (`lines[{productName, serialNumber, quantity}]`).
- Aşı akışında da başarı yakalamaya kaydedilmiş ürün satırları eklenir (`readSavedProductSerials`, mevcut).
- Onay isteği genişler: mevcut `POST /api/v1/tarbil-extension/submissions/{id}/submitted` gövdesine isteğe bağlı `lines`.

## 8. Açık noktalar / canlı inceleme

1. Reçete formunun ürün bölümü (hayvan seçildikten sonra): stok penceresi mi, satır içi düzenleme mi, alan adları. Hekim forma
   bir hayvan seçer ve **onaylamadan** bırakır; yalnız yapı okunur.
2. "Reçeteyi Onayla" sonrası başarı ekranı / mesajı (gerçek bir reçetede hekim onayladıktan sonra okunur).
3. Aşı Onayla sonrası başarı ekranı (P2'den açık).
4. Teşhis: TARBİL formu çoklu hastalık seçimine izin veriyor; ilk sürüm tek teşhis.
5. Faz 2 için "İlaç Reçetesi Ekle" (`ReceiptPage.aspx?type=1`) formunun yapısı.

Durum (2026-10-10): **Plan A tamamlandı** (`docs/superpowers/plans/2026-10-09-tarbil-stok-dusum-senkronu.md`) — backend,
web ve eklentide reçete aktarımını gösterme + elle onay. **Plan B** = eklentinin "Stoktan Düşerek İlaç Reçetesi Ekle" formunu
doldurması ve başarı satırlarıyla AUTO onay (yukarıdaki 1–3 canlı inceleme sonrası).

## 9. Test

- Backend birim: finalize → bağlı/bağlı olmayan ayrımı; settle eşleştirme kuralları (§5.3); MANUAL; iptal öncesi/sonrası;
  idempotent onay; Vetly-only.
- Entegrasyon: muayene → kart → kuyruk → confirm (lines) → stok hareketi; aşı aynı; kiracı izolasyonu.
- Eklenti: sayfa tanıma, doldurma adımları (mock Telerik, canlıdan doğrulanmış davranışla), başarı yakalama, onay satırları.
- Frontend: muayene kartı durumları, stok sayfası bekleyen listesi, hasta sayfasından muayenesiz reçete,
  "Kliniğin stoğundan ver" kalem alanları ve TARBİL teşhisi zorunluluğu.

## 10. Geçiş

Sistem test aşamasında. Mevcut aşı kayıtlarının eski modelle düşülmüş stok hareketleri olduğu gibi kalır; yeni model yalnız
yeni kayıtlara uygulanır. Prod'a çıkmadan önce `pending_stock_deduction` boş başlar.

## 11. Canlı inceleme bulguları (2026-10-10, Plan B için)

`ReceiptPageNew.aspx?type=1` ("Stoktan Düşerek İlaç Reçetesi Ekle"), hayvan türü Kedi seçili, onaysız; yalnız yapı okundu.
Önek: `bodyCPH_ContentPlaceHolder1_cntRECEIPTBodyContent_`.

- Üst form: `rbtnReceiptClass_0` Muayene/Tedavi Reçetesi, `_1` Tavsiye Reçetesi; `dpTreatmentStartDate` (dolu gelir);
  teşhis `cbxDisease` (74 öğe) + `imgAddDisease` ekle, `txtDiseaseOther` + `imgAddOther`, `btnDeleteDisease`;
  `cbxAnimalType` (34 tür, "Kedi", "Köpek" vb.); hayvan ekleme: `bntPetVet` (PetVet'ten), `btnHaybis`, `btnAnimalMultiple`;
  hayvan tablosu `ReceiptAddOtherAnimal_RadOtherAnimal` (Küpe/Çip, Doğum, Cinsiyet, Irk, Eşgal, Sil); sahip alanları
  `txtHoldingNo`, `txtAnimalOwnerName`, `txtPersonIdNo`, `txtAnimalOwnerAddress`. Onay: `btnInsert` / `btnInsert2`
  ("Reçeteyi Onayla") — yalnız hekim basar.
- Ürün bölümü `RadGridStock`: "Stok Ekle" `RadGridStock_ctl00_ctl02_ctl00_InitInsertButton` (postback, ekleme satırı açar).
  Ekleme satırı (`…_ctl00_ctl02_ctl03_`): `txtStockProductName` (kilitli) + `imgSearch` → `openProductStockWindow("")`;
  gizli `hiddenStockType`; etiketler `lblComPre`, `lblSerialNumber`, `lblExpirydate`, `lblStockQuantityDesc`, `lblStockType`;
  girilecek alanlar `txtStockQuantity` (Kullanılacak Adet) ve `txtStockAmount` (Kullanılacak Miktarı); satır düğmeleri
  `PerformInsertButton` (Kaydet) ve `CancelButton` (İptal).
- `openProductStockWindow`: tedavi başlangıç tarihi boşsa `alert("Tedavi Başlangıç Tarihi Giriniz")`; değilse
  `window.open('/Modules/RECEIPT/Pages/ModalPages/UcPharmacyStockSearchForReceiptModalPage.aspx?animalid=<türGUID>&typeid=1&date=dd/MM/yyyy')`.
- Pencere (önek `…UcPharmacyStockSearchForReceipt_`): `rbtnStockType_0` Bütün Stok (seçili) / `_1` Açık Stok;
  `txtSerialNumber`, `txtProductName`, `txtOldProductName`, `dpExpiryDateBegin/End`, `txtCommercialPresentation`,
  `txtAtcCode`, `txtProductPermissionNo`, `txtPurificationBegin/End`; `btnSearch`. Tablo `radGridStock_ctl00`
  (aramadan önce boş; sayfa başına 20, sayfalı): Seç, Ürün Adı, Takdim Şekli, Seri Numarası, Son Kullanma Tarihi, Ürün Miktarı.
  Seç = `a[id$="SelectlinkButton"]` → `runLinkButton2("<id>,<id>,,,")` → `returnToParent2` (açan sayfada alanları doldurur,
  `opener.runToParentPageMethod2`) → pencere kapanır.
- Ana sayfa `runToParentPageMethod2(param)`: `param.split(",")[0]` → `hiddenSelectedStockId`, sonra gizli `updaRadgridField2`
  tıklanır (postback, ekleme satırı dolar). Aşı penceresiyle aynı desen (`SelectlinkButton`, seri ile arama); eklentinin
  `selectStockRow` / `productRows` kodu ilaç penceresi için öneki değiştirilerek kullanılabilir.
- Seç sonrası ekleme satırı (2026-10-10): `txtStockProductName` (kilitli) ürün adıyla, `lblComPre` takdim şekliyle,
  `lblSerialNumber` seriyle, `lblExpirydate` SKT ile, `lblStockQuantityDesc` "<n> Adet" biçiminde stokla, `lblStockType`
  "Bütün Stok" ile dolar; `hiddenStockType` = 0. `txtStockQuantity` (Adet) RadNumericTextBox, tam sayı, min 0;
  `txtStockAmount` (Miktar) RadNumericTextBox, 2 ondalık, min 0,01. İki alanda istemci tarafı hesap/olay yok.
- Hâlâ bilinmiyor: Adet ile Miktar'ın anlamı ve zorunluluğu (sunucu doğrulaması); Kaydet sonrası satır yapısı;
  "Reçeteyi Onayla" sonrası başarı ekranı.
