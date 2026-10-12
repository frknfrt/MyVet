# Stoktan verilen reçete kalemi: ilaç stok ürününden — Tasarım

Tarih: 2026-10-12 · Durum: uygulandı (docs/superpowers/plans/2026-10-12-stoktan-verilen-recete-kalemi.md) · İlgili: `2026-10-09-tarbil-stok-dusum-senkronu-design.md` §5.6

## 1. Sorun

Reçete formunda her kalem için ilaç kataloğundan bir ilaç zorunlu (`prescription_items.drug_id NOT NULL`). Katalog
ortak ve yalnız yöneticinin düzenlediği bir liste; şu an içinde 3 örnek ilaç var. "Kliniğin stoğundan ver" işaretlenince
hekim ayrıca stok ürünü de seçiyor ve iki seçim birbirine bağlı değil: TARBİL'e ve stok düşümüne stok ürünü gidiyor,
Vetly'deki reçete kaydında (liste, rapor, PDF) ise katalogda seçilen ilaç yazıyor. Uyuşmazlık sessizce yanlış tıbbi kayıt
üretir.

## 2. Karar (kullanıcı onayı 2026-10-12)

Stoktan verilen kalemin ilacı stok ürününün kendisidir; katalog seçimi istenmez. Kalemde katalog ilacı boş kalır, stok
ürününün adı kaleme **ad anlık görüntüsü** olarak yazılır. Stoktan verilmeyen kalemler değişmez (katalog zorunlu).

Reddedilen seçenekler: stok ürünlerinden otomatik katalog kaydı (ortak katalog kirlenir, hekimin yetkisi yok); katalog
seçimini stok ürününe göre otomatik doldurma (katalogda karşılığı yok).

## 3. Veri modeli

Migration `V79__prescription_item_drug_name.sql`:

```sql
ALTER TABLE prescription_items ALTER COLUMN drug_id DROP NOT NULL;
ALTER TABLE prescription_items ADD COLUMN drug_name TEXT;
ALTER TABLE prescription_items ADD CONSTRAINT chk_prescription_items_drug
    CHECK (drug_id IS NOT NULL OR drug_name IS NOT NULL);
```

Mevcut satırlar `drug_id` dolu olduğu için kısıtı sağlar; veri taşıma yok. `PrescriptionItem` entity'si `drugName`
alanını alır, `drugId` boş olabilir.

## 4. Kurallar (backend)

`IssuePrescriptionRequest.Item` ve `PrescriptionItemInput`'a `drugName` eklenir. Kalem başına:

- `inventoryItemId` dolu (stoktan veriliyor): `drugName` boş olmayan metin **zorunlu**, `drugId` **boş olmalı**.
- `inventoryItemId` boş: `drugId` **zorunlu**, `drugName` boş olmalı.
- Aksi `InvalidPrescriptionItemException` (mevcut, 422).

Adın gerçekten o stok ürününe ait olduğu inventory modülünde, reçeteyle aynı işlemde doğrulanır:
`PrescriptionIssuedEvent.StockItem` `name` alır; `PrescriptionStockEventListener.onIssued` mevcut kontrolüne (kalem bu
kiracıda ve TARBİL'e bağlı) "kalem adı = `name`" ekler; uymazsa `InvalidPrescriptionStockItemException` (422) ve reçete
geri alınır. Reçete modülü stok modülünü göremez (Spring Modulith; inventory zaten encounter olaylarını dinliyor), bu
yüzden ad istemciden gelir ve stok tarafında doğrulanır.

## 5. Okuma

Kalemin gösterilen adı: `drugId` doluysa katalog adı, değilse `drug_name`. Değişen yerler:
`GetPrescriptionUseCase`, `ListPrescriptionsByPatientUseCase`, `GenerateEncounterReportUseCase` (rapor + PDF).
`PrescriptionItemDetail.drugId` boş olabilir; `drugName` her zaman dolu.

Kontrollü ilaç işareti ve etkileşim kontrolü katalog bilgisine dayanır; stoktan verilen kalemler bunlara katılmaz
(etkileşim isteğine yalnız dolu `drugId`'ler gider). Reçetenin `controlledSubstance` bayrağı mevcut haliyle kalır.

## 6. Ekran (`PrescriptionCard.tsx`, `StockGivePicker.tsx`, `clinicalApi.ts`)

- "Kliniğin stoğundan ver" işaretlenince kalemin "İlaç" seçimi gizlenir, yerine "Stok ürünü" seçimi gelir; `drugId`
  gönderilmez, seçilen stok ürününün adı `drugName` olarak gönderilir. İşaret kaldırılınca İlaç seçimi geri gelir
  (varsayılan katalog ilacı), stok alanları temizlenir.
- `StockGivePicker.onChange` seçilen kalemin id'si ile adını verir.
- Etkileşim kontrolü ve kontrollü ilaç hesabı yalnız katalog ilacı seçili kalemleri kullanır.
- Reçete listesinde ad `item.drugName` (backend artık her kalem için dolduruyor).
- `PrescriptionItem.drugId` ve `PrescriptionItemPayload.drugId` `string | null`; payload'a `drugName?: string | null`.

## 7. Etkilenmeyenler

TARBİL aktarımı (`TarbilSubmissionAssembler`) satır adlarını stok kaleminden alır; değişmez. Eklenti değişmez.

## 8. Test

- Backend birim: kural matrisi (stok + ad + id yok → geçerli; stok + id → 422; stok + ad yok → 422; stoksuz + id yok →
  422); okuma use case'lerinde `drugId` boş kalemde `drug_name`.
- Backend entegrasyon: stok kalemi adı uymayan reçete 422 ve reçete yazılmaz; geçerli stoktan reçete listede stok
  adıyla döner; migration sonrası eski kalemler okunur.
- Frontend: kutu işaretlenince İlaç seçimi kaybolur, gönderilen payload'da `drugId` yok + `drugName` var; etkileşim
  isteğine stok kalemi gitmez.

## 9. Geçiş

Migration geriye uyumlu (sütun ekleme + NOT NULL kaldırma). Geliştirme DB'si çalışan :8080 backend ile ortak: testler
migration'ı uygular, kullanıcı backend'i bir kez yeniden başlatmalıdır.
