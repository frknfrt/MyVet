# TARBİL Otomasyon Çekirdeği ve Belge Türleri — Tasarım Dokümanı

**Tarih:** 2026-10-04
**Durum:** Tasarım onaylandı (sohbette bölüm bölüm), yazılı belge incelemede
**Önceki tasarım:** `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md` (aşı aktarımı, Faz 1 + Faz 2a — uygulandı). Bu belge onu **genelleştirir**; oradaki kurallar (Bölüm 2 kapsam dışı, Bölüm 4 sınırlar) aksi yazılmadıkça geçerlidir.
**İlgili modüller:** `integration/tarbil`, `inventory`, `encounter` (backend); `extension/`; `frontend` (stok, reçete, ayarlar).

## 1. Amaç

Hekim bir işlemi **yalnız Vetly'de** girer; eklenti TARBİL'deki ilgili resmi formu doldurur, hekim TARBİL'de yalnız kontrol edip **Onayla**'ya basar. Aşı aktarımı (Faz 2a) ile kurulan yapı, ortak bir çekirdeğe dönüştürülerek başka belge türlerine genişletilir.

**Kapsamdaki belge türleri:**

| Belge türü | Yön | TARBİL sayfası |
|---|---|---|
| Aşı uygulama belgesi (aşı ürünü dahil) | Vetly → TARBİL | `hbsapp` `/Modules/RECEIPT/Pages/ATS/VaccineReceipt/VaccineReceiptPage.aspx?type=1` |
| Stoktan düşerek ilaç reçetesi | Vetly → TARBİL | `hbsapp` `/Modules/RECEIPT/Pages/Receipt/ReceiptPageNew.aspx?type=1` |
| Stok kabul (ilaç; aşı sonraki adım) | TARBİL → Vetly → TARBİL | `vetilac` `/Pages/OrderApprove.aspx` (ilaç); aşı tarafı P1'de incelenecek |

**Başarı ölçütü:** Daha önce aktarılmış bir aşı/ilaç için hekim TARBİL'de hiçbir alanı elle doldurmaz; Vetly ile TARBİL stoğu aynı lotlarla tutulur.

**Bu turda kapsam dışı (sonra):** kimliklendirme (çip kaydı), ölüm bildirimi, sahip değişikliği — `hbsapp` ve `vetilac` menülerinde yoklar (büyük ihtimalle ayrı PetVet sitesi; adres bilinmiyor). Çekirdek bunları sonradan eklemeye açık tasarlanır. Bu türlerde sahip verisinin (TC, ad, adres, telefon) **yalnız o belge için, anlık, eklentide saklanmadan ve Vetly'de denetim kaydıyla** taşınması kararlaştırıldı (2026-10-04); ayrıntısı o belge türlerinin kendi tasarımında.

## 2. Değişmeyen kurallar

1. **Resmi kaydı tamamlayan butona hekim basar.** Eklentide Onayla / Reçeteyi Onayla / Ürün Kabul Onayla / Reddet / Çıkış komutu **yoktur**.
2. Eklenti TARBİL'e doğrudan HTTP isteği atmaz; yalnız sayfanın kendi bileşenlerini tetikler ve okur.
3. Aşı, reçete ve stok akışlarında sahip verisi eklentiye gelmez; sahip alanları okunmaz/yazılmaz; ViewState okunmaz.
4. Konsola hasta/çip/ürün verisi yazılmaz.
5. Belirsizlikte (0 ya da >1 eşleşme, lot tutmuyor, stok yetersiz) eklenti **seçim yapmaz**, hekime bırakır ve kaldığı yerden devam eder.
6. Test için TARBİL'de resmi kayıt oluşturulmaz.

**Değişen kural — buton izin listesi:** Eski "Kaydet'e basılmaz" kuralı, reçetede formun parçası olan satır içi Kaydet butonları (stok satırı, iç pencere) yüzünden **izin listesine** dönüşür: eklenti yalnız sayfa tanımında adı tek tek yazılmış butonlara basabilir. Kural 1'deki onay butonları hiçbir listeye girmez; bunu bir test sabitler. (Satır Kaydet'lerinin resmi kayıt oluşturmadığı varsayımı P3 canlı analizinde doğrulanacak — Bölüm 9.)

## 3. TARBİL ekranları — canlı analiz (2026-10-04)

Claude in Chrome ile hekimin oturumunda, **yalnız yapı** (id, bileşen türü, etiket, sütun başlığı) okunarak; alan değeri/sayfa metni okunmadan, kayıt oluşturmadan.

**İki sistem:** `hbsapp.tarbil.gov.tr` (Aşı Takip Sistemi, E-Reçete; aşı stoğu) ve `vetilac.tarbil.gov.tr` (İlaç Takip Sistemi / Satış Yeri; ilaç stoğu). Ana sayfa `/Default.aspx`; modüller `/Modules/RECEIPT/Pages/VaccineDefault.aspx`, `ReceiptDefault.aspx`, `MedicineDefault.aspx` (→ `vetilac` `/Pages/PharmacyDefault.aspx`).

**Ortak parçalar (aşı ve reçete aynı):** `cbxAnimalType` (`RadComboBox`, 34 tür; Kedi `245f5f71-2cfd-4a04-9072-640069e3268e`, Köpek `3eea84e6-b2d8-494f-a01c-89e739bb004d`) → tür postback'i sonrası `ReceiptAddOtherAnimal_RadOtherAnimal` (PetVet / Haybis / Hayvan Ara butonları, aynı hayvan tablosu), sahip alanları (TARBİL doldurur), bildirim panelleri `pnlNotifi{Warning,Info,Error,Success}` (her yüklemede boş ve görünür; başarı yalnız metinli panel). PetVet arama penceresi `VaccineKKBSAnimalSearchModalPage.aspx?AnimalType=C|D` (önceki tasarım §3).

**E-reçete — `Receipt/ReceiptPageNew.aspx?type=1` ("Stoktan Düşerek İlaç Reçetesi Ekle"), önek `cntRECEIPTBodyContent_`:**
- `dpTreatmentStartDate` (Tedavi Başlangıç Tarihi, `RadDatePicker`).
- `cbxDisease` (`RadComboBox`, içinde `cbxDisease_i0_treeDisease` `RadTreeView`, 73 düğüm; yalnız yapraklar seçilebilir) + `imgAddDisease` (görsel buton, postback) → `lbxDiagnosedDisease` (`RadListBox`, "Eklenen Hastalık Listesi") + `btnDeleteDisease`. `txtDiseaseOther` (Diğer Teşhis Edilen Hastalık). Ağaç: Ek A.
- Tür → hayvan bölümü (ortak).
- `RadGridStock` komut "Stok Ekle" (`InitInsertButton` postback); sütunlar: Ürün, Takdim Şekli, Seri Numarası, Son Kullanım Tarihi, Stok Miktarı, Stok Tipi, **Kullanılacak Adet**, **Kullanılacak Miktarı**, Sil.
- İlaç stok penceresi `ModalPages/UcPharmacyStockSearchForReceiptModalPage.aspx?animalid=<türGUID>`: `txtProductName`, **`txtSerialNumber` (Seri No)**, `txtAtcCode`, `cbxActiveList` (etken madde), farmakolojik grup, farmasötik şekil, uygulama şekli, SKT aralığı, arınma türü; "Bütün Stok / Açık Stok" seçimi; `btnSearch`; `radGridStock` sütunları: Ürün Adı, Takdim Şekli, Seri Numarası, Son Kullanma Tarihi, Ürün Miktarı.
- Onay: `btnInsert` / `btnInsert2` metni "Reçeteyi Onayla".
- Kullanıcının tarifiyle (henüz canlı görülmedi): stok seçilince satır tabloya gelir → adet/miktar + satır Kaydet → sayfa içinde ikinci bir bölüm açılır: hayvan seçimi, uygulama dozu, kullanım yolu (damla vb.) + Kaydet → Reçeteyi Onayla.

**Aşı ürünü — aşı stok penceresi `ModalPages/UcVaccineStockSearchModalPage.aspx?animalid=<türGUID>&date=dd/MM/yyyy`:** `cbxProduct` (811 aşılık ulusal katalog), `cbxComPre` (takdim şekli), **`txtSerialNo`**, SKT aralığı, ruhsat sahibi; seçimler Aşı/Dilüent, Bütün/Parça Stok, Kendi/Tüm Açık Stoklar; `radGridStock`: Aşı Adı, Takdim Şekli, Seri Numarası, Son Kullanma Tarihi, Ürün Miktarı, Ruhsat Sahibi Tip/İsim. Ürün satırı detay alanları (aşı grubu, aşı, uygulama türü, doz, doz birimi, aşılama amacı, makbuz cilt no) önceki tasarım §3'te ViewState'ten çıkarıldı; P2 canlı analizinde doğrulanacak.

**Stok — `hbsapp` aşı:** `ATS/VaccineStock/VaccineStockSearch.aspx` (kendi stok, bitmiş/açık/parça/kabul bekleyen stok tabloları; ana tablo: Ruhsat Tipi, Aşı Adı, Takdim Şekli, Seri No, SKT, Ürün Miktarı, üretilen doz küçük/büyük hayvan, Ruhsat Sahibi, Depo, Üretim Yeri, Satış Yeri, Stok Tipi), `ClinicVaccineStockTransferApproveSearch.aspx` (Transfer Kabul), `ATS/VaccineOrder/VaccineOrderApprovalForClinic.aspx` (Siparişlerimi Ara: Sipariş No, Gönderen, Verilen, Tarih, Durum).

**Stok — `vetilac` ilaç:** `/Pages/StockSearch.aspx` (Satış Yeri, Ürün, Takdim Şekli, Miktar, SKT, Açılmış Kalan Miktar (+SKT), Seri No, Kaydeden), `/Pages/StockMovementSearch.aspx`, **`/Pages/OrderApprove.aspx` (Ürün Kabul)**: `btnSearch` "Onay Bekleyen Siparişlerimden Ara", depo/sipariş no/ürün filtreleri; `radGridOrder` sütunları: Ürün, Takdim Şekli, Son Kullanma Tarihi, Seri Numarası, Kaydeden, Sipariş Numarası, Sipariş Miktarı, Gelen Miktar, Mal Fazlası, Onaylanan Miktar, İade Miktarı, İade Açıklaması, MF Onaylanan, MF İade (+açıklama), Zayi Miktarı (+açıklama); `btnApprove` "Onayla", `btnReject` "Reddet". Diğer menü: Stok Transfer, Stok Düşme Talebi, Doğrudan Stok Düşme, Sipariş Ver, Sipariş Ara/İade, Reçete Satış/Teslim.

**Gözlem:** Klinik TARBİL'de kendi başına stok yaratamıyor; stok, depo satışıyla "onay bekleyen" olarak gelir ve kabul edilir. Elle değişiklik sayfaları (Stok Güncelle, Talep Ekle, Stok Düşme Talebi) talep niteliğinde görünüyor (içleri incelenmedi).

**Teknik notlar:** PetVet penceresi sunucu yanıtındaki `window.open` ile açılır; kullanıcı hareketi olmadan engellenir (doğrulandı) → TARBİL için açılır pencere izni önerilir, yoksa karttaki buton. Telerik `__doPostBack` çağrı zincirinde strict-mode fonksiyon görürse hata verir; eklentinin `page.js`'i gerçek Chrome'da etkilenmedi (doğrulandı), ama yeni MAIN dünya komutları da canlıda denenmeli.

## 4. Mimari

```
Vetly backend
  integration/tarbil
    TarbilSubmission       tek tablo: belge türü + kaynak kayıt + durum
    DocumentAssembler<T>   belge türü başına: aktarım verisini istek anında lookup portlarından üretir
    TarbilValueMapping     öğrenilen eşleştirmeler (VACCINE, SPECIES, DISEASE, DRUG_ROUTE, STOCK_PRODUCT)
    TarbilDisease          73 düğümlük referans ağaç (Ek A)
    extension API          belge türünden bağımsız uçlar + stok kabul içe aktarımı
  inventory                stok kalemi TARBİL alanları, StockReceipt (mal kabul), StockReceiptPort
  encounter                reçeteye hastalık/adet/stok bağlantısı; aşıya stok bağlantısı
Eklenti
  core/      köprü, kart, akış durumu (documentType + stepData), başarı yakalama
  steps/     selectAnimalType, findAnimal, pickStock, selectDisease
  pages/     vaccineReceipt, prescription, vetilacOrderApprove
  selectors/ sayfa başına id sonekleri + buton izin listesi
  page/      MAIN dünya genel komutları (clickAllowed dahil)
```

## 5. Backend veri modeli

### 5.1 P0 — aktarım kaydı genelleşir
- `tarbil_sync_log` → `tarbil_submission`: `document_type` (`VACCINATION` | `PRESCRIPTION` | `STOCK_RECEIPT`), `source_id` (eski `vaccination_record_id`), durum alanları aynı (`PENDING` / `SUBMITTED` / `DISMISSED`, onay yöntemi, TARBİL referansı, bildirilmeyecek bilgisi). Benzersiz: (`tenant_id`, `document_type`, `source_id`). Migration: mevcut satırlar `VACCINATION`.
- `tarbil_value_mapping.kind` genişler: `DISEASE` (Vetly teşhis anahtarı → TARBİL düğüm GUID), `DRUG_ROUTE` (Vetly `DrugRoute` → TARBİL kullanım yolu), `STOCK_PRODUCT` (Vetly stok kalemi → TARBİL ürün adı + takdim şekli).
- `TarbilDisease` referans tablosu (Ek A), migration ile tohumlanır; `GET /api/v1/tarbil/diseases`.

### 5.2 P1 — stok (`inventory`)
- `inventory_item` + `tarbil_system` (`HBSAPP_VACCINE` | `VETILAC_MEDICINE` | `NONE`), `tarbil_product_name`, `tarbil_presentation`, `unit` (`ADET` | `ML` | `GR` | `DOZ`), `drug_catalog_id` (nullable; katalog ↔ stok kopukluğunu kapatır).
- Yeni `stock_receipt` (mal kabul): `id`, `tenant_id`, `tarbil_system`, `tarbil_order_no`, `store_name`, `status` (`PENDING_REVIEW` → `ACCEPTED` | `REJECTED`), `imported_at`, `accepted_at`, `accepted_by_staff_id`.
- `stock_receipt_line`: `product_name`, `presentation`, `lot_number`, `expiry_date`, `ordered_qty`, `incoming_qty`, `free_qty` (mal fazlası), `accepted_qty`, `returned_qty`, `returned_note`, `lost_qty`, `lost_note`, `inventory_item_id` (kabulde bağlanır/oluşur). Benzersiz: (`tenant`, `system`, `order_no`, `product_name`, `lot_number`).
- Kabul: aynı (`tarbil_product_name`, `lot_number`) stok kalemi varsa artırılır, yoksa oluşturulur; `StockMovement` `IN`, yeni `StockReferenceType.TARBIL_RECEIPT`. `StockReceiptAcceptedEvent` yayınlanır.

### 5.3 P2 — aşı (`encounter`)
- `vaccination_record.inventory_item_id` (nullable). Lot stok kaleminden gelir; eski düz metin lot alanı korunur.

### 5.4 P3 — reçete (`encounter`)
- `prescription.treatment_start_date`, `prescription.disease_other` (serbest metin).
- `prescription_disease` (`prescription_id`, `tarbil_disease_id`) — bir ya da çok.
- `prescription_item.inventory_item_id`, `dispense_quantity` (adet, nullable), `dispense_amount` + `dispense_unit` (miktar, nullable); en az biri zorunlu.

### 5.5 Modül sınırları
`integration/tarbil` başka modülün verisine yalnız portlarla erişir (`VaccinationLookupPort`, yeni `PrescriptionLookupPort`, `InventoryLookupPort`, `StockReceiptPort`). Mal kabul **`inventory`** modülünde yapılır; `tarbil` modülü `StockReceiptAcceptedEvent` ile `STOCK_RECEIPT` aktarımı açar. Reçete için `PrescriptionIssuedEvent` → `PRESCRIPTION` aktarımı (aşıdaki `VaccinationRecordedEvent` gibi).

## 6. API

**Eklenti (`/api/v1/tarbil-extension`, eklenti anahtarı):**
| Uç | Açıklama |
|---|---|
| `GET /pending?type=` | Bekleyen belgeler; `type` yoksa hepsi |
| `GET /submissions/{id}` | Belge + türe göre `payload` |
| `POST /submissions/{id}/submitted`, `/dismiss` | Değişmez (idempotent) |
| `PUT /mappings/{kind}` | `kind` yeni türleri kabul eder |
| `POST /stock-receipts/import` | Eklentinin TARBİL'den okuduğu bekleyen siparişler: `{system, orders:[{orderNo, store, lines:[{productName, presentation, lot, expiry, orderedQty, incomingQty, freeQty}]}]}`; idempotent; değişen miktar satırı günceller (yalnız `PENDING_REVIEW`) |

**Payload türleri:**
- `VACCINATION`: mevcut alanlar + `stock {tarbilProductName, presentation, lot}` + öğrenilmiş `vaccineFields` (doz, birim, uygulama türü, amaç).
- `PRESCRIPTION`: `treatmentStartDate`, `diseases[{tarbilId, path}]`, `diseaseOther`, `speciesId/Name`, `microchipNumber`, `items[{tarbilProductName, presentation, lot, dispenseQuantity|dispenseAmount+unit, dose, route}]`.
- `STOCK_RECEIPT`: `system`, `orderNo`, `lines[{productName, lot, acceptedQty, returnedQty, returnedNote, lostQty, lostNote, freeAcceptedQty}]`.

**Web (JWT):** `GET /api/v1/inventory/stock-receipts`, `GET /{id}`, `POST /{id}/accept` (satır miktarları), `POST /{id}/reject`; `GET /api/v1/tarbil/diseases`. Yetki: VET, ADMIN (stok kabul ayrıca ADMIN'e açık; mevcut rol matrisine işlenir).

## 7. Eklenti

### 7.1 Yapı
```
extension/src/tarbil/
├── core/       bridge.ts, card.ts, views/, flowStore.ts (genel), success.ts
├── steps/      selectAnimalType.ts, findAnimal.ts, pickStock.ts, selectDisease.ts
├── pages/      vaccineReceipt.ts, prescription.ts, vetilacOrderApprove.ts
├── selectors/  shared.ts, vaccineReceipt.ts, prescription.ts, vetilacOrder.ts
└── page/       ops.ts (MAIN dünya), telerik.ts, main.ts
```
- **Akış durumu:** `{ submissionId, documentType, step, stepData, updatedAt }`; `stepData` ör. `{ itemIndex, awaiting: 'animal'|'stock', lot }`. Açılır pencereler (hayvan arama, stok) bu kayıttan hangi kalem için açıldıklarını okur. Sayfa yenilense de `itemIndex`'ten devam edilir.
- **Ortak adımlar** tek iş yapar ve ayrı test edilir: `selectAnimalType` (yarım postback'te zorla yeniden seçim), `findAnimal` (mevcut PetVet akışı), `pickStock` (Seri No ile ara; tek satır → seç; 0/>1 → hekime), `selectDisease` (ağaçtan düğüm + `imgAddDisease`; eklenen listede doğrula).
- **MAIN dünya komutları** genelleşir: `setDate`, `selectCombo`, `setText`, `selectTreeNode`, `checkRow`, `clickAllowed(page, buttonKey)`. `clickAllowed` yalnız `selectors/<sayfa>.ts` içindeki izin listesindeki butona basar.
- **İzin listesi (ilk hâli):** aşı → PetVet, arama penceresi Ara/Transfer Et, Ürün Ekle, stok penceresi Ara; reçete → hastalık ekle, PetVet, Stok Ekle, stok satırı Kaydet, iç bölüm Kaydet, stok penceresi Ara; vetilac Ürün Kabul → "Onay Bekleyen Siparişlerimden Ara". **Hiçbirinde** `btnInsert`, `btnInsert2`, `btnApprove`, `btnReject`, Exit yok (test).
- **Başarı yakalama:** sayfa tanımı "resmi onay butonu"nu ve başarı panelini verir; başarı = o butona hekim tıklamasından sonra beliren **metinli** başarı paneli (Faz 2a kuralının genel hâli).
- **Manifest:** `vetilac.tarbil.gov.tr/*` host izni ve içerik betikleri (izole + MAIN).

### 7.2 Akışlar
1. **Aşı:** tarih → tür → hayvan → Ürün Ekle → stok penceresi Seri No = Vetly lotu → tek eşleşme seç → aşı/doz/birim/uygulama türü/amaç (öğrenilmiş) → hekim Onayla → başarı → `submitted AUTO`. İlk kez görülen aşıda alanları hekim seçer, eklenti öğrenir (`VACCINE` eşleştirmesi).
2. **Reçete:** tarih → hastalıklar → tür → hayvan → her kalem: Stok Ekle → ilaç stok penceresi Seri No → seç → adet/miktar → satır Kaydet → iç bölüm: hayvan, doz, kullanım yolu (`DRUG_ROUTE` eşleştirmesi) → Kaydet → sonraki kalem → hekim Reçeteyi Onayla → başarı.
3. **Stok kabul:** `vetilac` Ürün Kabul'de eklenti "Onay Bekleyen Siparişlerimden Ara"ya basar, tabloyu okur → `POST /stock-receipts/import` → Vetly'de Mal Kabul ekranı (hekim gelen/iade/zayi girer, kabul eder → Vetly stoğu) → `STOCK_RECEIPT` bekler → eklenti satırlara onaylanan/iade/zayi yazar → hekim Onayla.

## 8. Hata durumları
| Durum | Davranış |
|---|---|
| Stok penceresinde Seri No ile 0 ya da >1 sonuç | Seçim yok; kart "N sonuç — doğru satırı seçin"; hekim seçince kalemden devam |
| Lot tutmuyor / reçetede adet > stok | Durur, uyarır; başka lot seçmez |
| Vetly'deki hastalık kodu TARBİL ağacında yok | O hastalık hekime; Vetly'ye "hastalık listesi güncellenmeli" bildirimi |
| Aynı sipariş yeniden içe aktarıldı | Tekrar oluşmaz; miktar değiştiyse `PENDING_REVIEW` satır güncellenir, kabul ekranında fark gösterilir |
| Postback yanıt yok / beklenen öğe yok | Adım durur; kart hangi adım/kalemde durduğunu söyler; kalan adımlar elle tamamlanabilir |
| Açılır pencere engellendi | Kartta pencereyi açan buton (kullanıcı hareketi) + site izni yönergesi |
| Belge Vetly'de silindi/iptal edildi | `pending`'den düşer; açık akış kartta "kayıt artık yok" der |

## 9. Doğrulanacak varsayımlar (alt projelerin canlı analizinde)
- Reçetede satır Kaydet ve iç bölüm Kaydet resmi kayıt oluşturmaz (doğrulama: Kaydet sonrası sayfayı kapatıp "Kendi Reçetelerimi Ara"da kayıt olmadığını görmek — hekimin izniyle).
- Aşı ürün satırı detay alanlarının kesin yapısı (P2).
- Aşı siparişlerinin `hbsapp`'taki kabul ekranı (P1).
- Stok pencerelerinden ana sayfaya dönüş mekanizması (`window.opener.runToParentPageMethod` benzeri) ve seçilen satırın ana tabloya gelişi.
- Başarı panelinin metni/biçimi (ilk gerçek Onayla'da).

## 10. Güvenlik ve gizlilik
- İzin listesi dışı buton tıklaması MAIN dünyada reddedilir; onay butonlarının hiçbir listede olmadığı testle sabitlenir.
- Köprü yalnız aynı pencereden komut kabul eder (Faz 2a C1 düzeltmesi korunur).
- Stok kabulde depo/satış yeri adları ticari bilgi olarak Vetly'ye taşınır; kişisel veri yoktur.
- `vetilac` izni yalnız içerik betikleri içindir; çerez okunmaz, konsola veri yazılmaz.

## 11. Test
- **Backend:** her `DocumentAssembler` ve mal kabul use case'leri için birim testleri; `stock-receipts/import` idempotentliği; kiracı izolasyonu entegrasyon testleri yeni uçları kapsar; `ApplicationModulesTest` (port sınırları).
- **Eklenti:** ortak adımlar ve sayfa modülleri için jsdom testleri; fixture'lar sentetik ama **2026-10-04 canlı okunan gerçek yapı** (id sonekleri, sütun başlıkları, Ek A ağacı) ile; izin listesi testi.
- **Canlı kabul:** her alt projenin sonunda Chrome'dan yapı kontrolü; ilk gerçek belge hekimle, Onayla hekimde.

## 12. Alt projeler ve sıra
Her biri ayrı plan → ayrı dal → ayrı birleştirme; her adımdan sonra ürün çalışır.
1. **P0 Çekirdek:** `tarbil_submission` genelleştirmesi + migration; eşleştirme türleri; `TarbilDisease` referansı; eklentide aşı akışının `core/steps/pages/selectors` yapısına taşınması ve `clickAllowed` izin listesi. **Davranış değişmez**; mevcut testler yeşil kalır.
2. **P1 Stok:** stok kalemi TARBİL alanları, `stock_receipt`, Mal Kabul ekranı, `vetilac` Ürün Kabul okuma + yazma; aşı kabul ekranı analizi.
3. **P2 Aşı ürünü:** aşı ↔ stok bağlantısı, aşı stok penceresi, ürün detay alanları ve öğrenme.
4. **P3 E-reçete:** reçete modeli eklemeleri, Vetly reçete formu (hastalık seçimi, adet/miktar, stok kalemi), `ReceiptPageNew` sayfa modülü.

P0'ın uygulama planı bu belge onaylanınca yazılır; P1–P3 için kendi planlarından önce kısa canlı analiz + gerekirse bu belgeye ek yapılır.

## Ek A — TARBİL hastalık ağacı (reçete, 2026-10-04)
Kök kategoriler seçilemez; yapraklar seçilir. `METABOLİZMA HASTALIKLARI` kendisi yapraktır.

| Kategori > Alt kategori | GUID |
|---|---|
| İHBARI ZORUNLU HASTALIKLAR > AT VEBASI | 8672192e-a427-45a4-9ec0-2fbdcf86db39 |
| İHBARI ZORUNLU HASTALIKLAR > ATLARIN ENFEKSİYÖZ ANEMİSİ | 74660e2e-ebd2-46b2-b921-9b65d9367655 |
| İHBARI ZORUNLU HASTALIKLAR > BRUCELLOZİS | aa339b22-75b9-4adb-998d-1402c097e5a1 |
| İHBARI ZORUNLU HASTALIKLAR > TÜBERKÜLOZ | 0ee1cf24-1f47-4dea-b27a-9666cffd8c4d |
| İHBARI ZORUNLU HASTALIKLAR > ŞARBON (Antrax) | 2b6f17cc-7211-4150-9a82-108fbaa0acd0 |
| İHBARI ZORUNLU HASTALIKLAR > ŞAP | 070e3ece-71c1-4631-b097-44cb37a91fcd |
| İHBARI ZORUNLU HASTALIKLAR > SIĞIR VEBASI | 7fab27c1-2d14-4e7a-8a35-c815f6512313 |
| İHBARI ZORUNLU HASTALIKLAR > MAVİDİL | 7c6c9108-7ba5-46b0-b9f3-69c00e1b3150 |
| İHBARI ZORUNLU HASTALIKLAR > KUDUZ | fc803b18-cd6e-4209-85bf-a19493481e46 |
| İHBARI ZORUNLU HASTALIKLAR > NAKLEDİLEBİLİR SÜNGERİMSİ BEYİN HASTALIKLARI (BSE, FSE, Scrapie) | b1bbad5e-9a18-4d52-8440-154b1d1ca2b5 |
| İHBARI ZORUNLU HASTALIKLAR > SIĞIRLARIN NODÜLER EKZANTEMİ (Lumpy skin) | 0854d753-42ae-4aa5-b46c-cf50f38dbce5 |
| İHBARI ZORUNLU HASTALIKLAR > EPİZOOTİC HAEMORRHAGİC DİSEASE (EHD) | 854b2e2c-b88d-4a08-9d81-ccc25119c291 |
| İHBARI ZORUNLU HASTALIKLAR > KOYUN KEÇİ ÇİÇEĞİ | 6702cb58-64d3-4ecd-b567-4aff0671d137 |
| İHBARI ZORUNLU HASTALIKLAR > KOYUN KEÇİ VEBASI | bda44beb-01a3-4e37-91eb-589ec4f37dfb |
| İHBARI ZORUNLU HASTALIKLAR > NEWCASTLE (Yalancı Tavuk Vebası) | 47f2379f-e3eb-4bf8-850b-18422f187633 |
| İHBARI ZORUNLU HASTALIKLAR > TAVUK VEBASI (Avıan İnfluenza-Kuş Gribi) | 4b9e1d40-c15e-4552-9eaa-e1756027d7e8 |
| İHBARI ZORUNLU HASTALIKLAR > ARILARIN AMERİKAN YAVRU ÇÜRÜKLÜĞÜ | bee7b6d3-09bb-4d9b-96f6-5fd901f003dd |
| İHBARI ZORUNLU HASTALIKLAR > KÜÇÜK KOVAN KURDU (Aethina Tumida) | f389d0ca-f98c-4a99-8f11-07d51510345d |
| İHBARI ZORUNLU HASTALIKLAR > TROPILAELAPS AKARI (Tropılaelaps Mıte) | 1252661e-2a44-43f1-b9f0-cc4e7e95736b |
| SOLUNUM SİSTEMİ HASTALIKLARI > BAKTERİYEL HASTALIKLAR | 305a8aa5-ee45-4212-914a-8532856a160c |
| SOLUNUM SİSTEMİ HASTALIKLARI > VİRAL HASTALIKLAR | 6995708e-c3b4-42f3-9ac0-2be848ac79ad |
| SOLUNUM SİSTEMİ HASTALIKLARI > PARAZİTER HASTALIKLAR | 2fb4adaf-b728-4033-863c-42c2ee0aef30 |
| SOLUNUM SİSTEMİ HASTALIKLARI > ENFEKSİYÖZ OLMAYAN HASTALIKLAR | cd64dbcd-7960-486e-8e00-ac5e2bd6e8de |
| SOLUNUM SİSTEMİ HASTALIKLARI > OPERATİF MÜDAHALELER | dc8ed43d-5a0e-4d34-8a85-e3d8914311f0 |
| SOLUNUM SİSTEMİ HASTALIKLARI > DİĞER | c8e7c024-0ac1-4055-a899-5a7b9ccfa29a |
| SİNDİRİM SİSTEMİ HASTALIKLARI > BAKTERİYEL HASTALIKLAR | ccbbf4f5-4c76-49ba-ab78-0543eba881b5 |
| SİNDİRİM SİSTEMİ HASTALIKLARI > VİRAL HASTALIKLAR | 68d52735-6c87-4275-b277-7e19dc549260 |
| SİNDİRİM SİSTEMİ HASTALIKLARI > PARAZİTER HASTALIKLAR | 95860589-0057-42cc-8209-663e1e459594 |
| SİNDİRİM SİSTEMİ HASTALIKLARI > PROTOZOER HASTALIKLAR | 8f3c7ae9-cbf3-4d03-b017-5ba2082f4cfc |
| SİNDİRİM SİSTEMİ HASTALIKLARI > ENFEKSİYÖZ OLMAYAN HASTALIKLAR | b785f4f1-bc5b-4ba5-b4d9-13bc35d973ee |
| SİNDİRİM SİSTEMİ HASTALIKLARI > OPERATİF MÜDAHALELER | e7c69496-1b07-4d4f-9663-968e0b1f730b |
| SİNDİRİM SİSTEMİ HASTALIKLARI > DİĞER | 426e99fc-99c9-4544-8bbd-3d212136c0c5 |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > BAKTERİYEL HASTALIKLAR | 540c15fd-27e9-4b83-82da-c7ffbd6f6098 |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > VİRAL HASTALIKLAR | 33994353-e989-49f5-a08a-5d50ed8b22b5 |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > ENFEKSİYÖZ OLMAYAN HASTALIKLAR | 04277d12-f81c-4fa2-a04f-a31d5899d1ed |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > DOĞUM | 263b994a-c91a-484b-b1c0-9042076941b2 |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > OPERATİF MÜDAHALELER | cd11bccb-2e4f-4b1d-aea4-2c8b0ba27c03 |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > MASTİTİS | 3f98a99c-36c7-4545-93d0-3dd1cdfeada8 |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > İNFERTİLİTE (KISIRLIK) | 1d985c05-a3ce-423d-97f3-8e0634ef5504 |
| ÜRO-GENİTAL SİSTEM HASTALIKLARI > DİĞER | dc0944b2-78fa-4f84-88a9-099d49d747f9 |
| SİNİR SİSTEMİ HASTALIKLARI > MERKEZİ SİNİR SİSTEMİ HASTALIKLARI | 740f11aa-e184-4b3a-850a-0f9216f6978c |
| SİNİR SİSTEMİ HASTALIKLARI > PERİFER SİNİR SİSTEMİ HASTALIKLARI | 935fed74-f92c-489e-a5b0-c5b12e30905c |
| SİNİR SİSTEMİ HASTALIKLARI > DİĞER | a2e584a4-db63-450d-b9c1-7c694c256f3a |
| KAS ve İSKELET SİSTEMİ HASTALIKLARI > AYAK HASTALIKLARI | 8ccba8ac-f6ce-4c77-b28a-4aea79bc0164 |
| KAS ve İSKELET SİSTEMİ HASTALIKLARI > CERRAHİ MÜDAHALELER | 1407dc5c-67c9-43f3-9ea3-50213f793598 |
| KARDİYO-VASKÜLER SİSTEM HASTALIKLARI > KALP | e74c195a-096b-4a66-87f8-4e8e53cb86e4 |
| KARDİYO-VASKÜLER SİSTEM HASTALIKLARI > DOLAŞIM SİSTEMİ HASTALIKLARI | 830a44c4-50b4-4953-a49f-a578c4f66ce2 |
| DERİ HASTALIKLARI > BAKTERİYEL HASTALIKLAR | 37d8c374-d26d-416d-810b-888d049a7f66 |
| DERİ HASTALIKLARI > VİRAL HASTALIKLAR | 3b385985-5dbc-4df0-882b-a400992b04c4 |
| DERİ HASTALIKLARI > PARAZİTER HASTALIKLAR | e8d86353-2e29-4104-b7a6-0be51f84fedc |
| DERİ HASTALIKLARI > MANTAR HASTALIKLARI | 002f8edc-833a-4f6a-88f2-51e4f99de340 |
| DERİ HASTALIKLARI > ENFEKSİYÖZ OLMAYAN HASTALIKLAR | 02aa1e8a-da67-42ff-8c75-4dc5dd512fde |
| DERİ HASTALIKLARI > OPERATİF MÜDAHALELER | bccd89cb-a9f2-45b5-8a6b-0c995276ce52 |
| DERİ HASTALIKLARI > DİĞER | 00524170-ea54-41d7-bff8-3b9f112cfe57 |
| DUYU SİSTEMİ HASTALIKLARI > GÖZ HASTALIKLARI | ebb7684d-3d48-4c8d-9c7d-9467df465894 |
| DUYU SİSTEMİ HASTALIKLARI > KULAK HASTALIKLARI | 754a5a5d-7e33-404f-b357-203aebf4d3ca |
| METABOLİZMA HASTALIKLARI | afe6153c-f045-4c51-a13d-e4b2e6ffbb0e |
| ARI HASTALIKLARI > PARAZİTER HASTALIKLAR | c542ad38-a403-4cc8-b022-141435234cc9 |
| ARI HASTALIKLARI > BAKTERİYEL HASTALIKLAR | be78a762-099e-4fc4-b47d-2a470ee94ec3 |
| BALIK HASTALIKLARI > BAKTERİYEL HASTALIKLAR | e41d3138-b934-481d-8929-5d20f901fd4d |
| BALIK HASTALIKLARI > PARAZİTER HASTALIKLAR | 1ccf1aa3-3ad1-4160-81e4-b2dcd18d5331 |
| BALIK HASTALIKLARI > DİĞER | 770d390d-4caa-4675-87a1-cd7e91aeeb65 |

Kategori GUID'leri (seçilemez, gruplama için): İHBARI ZORUNLU `2045c974-fb2a-40cb-9785-c2fb2c4da175`, SOLUNUM `64a8bb52-5e35-4364-8f5f-3f70bd20cba9`, SİNDİRİM `53fe7807-e179-4a57-b4c2-fb5d59f1882d`, ÜRO-GENİTAL `bf8a4ebf-6827-46df-940e-4947875b1cb7`, SİNİR `dda7c9cc-840f-41c8-8e2c-acab91c95f46`, KAS ve İSKELET `6fb53b79-3e4a-403a-90f3-56c3b3ad4e00`, KARDİYO-VASKÜLER `0a3ef696-7117-4038-a602-a9eed9d0c393`, DERİ `76f634a7-9dbc-4f8b-b8a0-411b71f40fb9`, DUYU `0e859585-e0b9-4d0b-8192-d0436f9ad81b`, ARI `29377d15-17b4-4a87-9f5d-606e98770e64`, BALIK `2ad4fe05-2af3-44a3-ad6a-3f862367b915`.
