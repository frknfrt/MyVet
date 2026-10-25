# Açık stok (ilaç) ve parça stok (aşı) — tasarım

Tarih: 2026-10-24 · Durum: taslak, kullanıcı onayı bekliyor

## 1. Amaç ve neden

TARBİL stoğu iki parçalı tutuyor, Vetly tek tam sayıyla tutuyor. Sonuç:

- **İlaç:** Clavon (50 ml şişe) açılıp 1 ml kullanılınca TARBİL'de "Miktar 0, Açılmış Kalan Miktar 49,00" olur. Vetly'de reçete/muayene yalnız tam adet alır; eklenti TARBİL'deki satıra aynı sayıyı hem "Kullanılacak Adet" hem "Kullanılacak Miktarı" alanına yazar. 1 ml kullanım TARBİL'de **bütün şişe** olarak düşülür. Eşitleme Clavon'u "TARBİL 0, Vetly 1 → Miktar farklı" gösterir; işlenirse Vetly 0 olur, eldeki 49 ml görünmez.
- **Aşı:** TARBİL'de bütün stok **kutu** sayar (Biocan DHPPI 1 kutu = 20 flakon), parça stok açık kutuda kalan **flakon** sayar (Biocan R 18). Vetly ikisini aynı birim sayar (eşitleme ikisini toplayıp tek sayıya yazar), aşı kaydı ise her seferinde 1 **flakon** düşer. Vetly'de "1" görünen Biocan DHPPI ilk aşıda 0 olur; elde 19 flakon vardır.

Amaç: Vetly stoğu TARBİL'in iki parçalı yapısını birebir yansıtsın; kullanım doğru birimde girilsin, doğru parçadan düşsün, TARBİL'e doğru listeden gönderilsin ve eşitleme iki parçayı ayrı karşılaştırsın.

## 2. TARBİL'de görülenler (2026-10-23/24 canlı okuma)

**İlaç** — reçete stok penceresi `ModalPages/UcPharmacyStockSearchForReceiptModalPage.aspx?animalid=<türGUID>&typeid=1&date=dd/MM/yyyy`:
- `rbtnStockType_0` "Bütün Stok" (varsayılan) → tablo `radGridStock_ctl00`: Ürün Adı, Takdim Şekli, Seri Numarası, Son Kullanma Tarihi, **Ürün Miktarı** (kapalı adet). Clavon 63016 bu listede **yok** (tek şişesi açıldı). Tenidur 251227: 2.
- `rbtnStockType_1` "Açık Stok" → tablo **`radGridOpenStock_ctl00`**: aynı kolonlar ama son kolon **"Açık Stok Miktarı"**: Clavon 63016 **49,00**, Tenidur 251227 **18,00**.
- İlaç Stok Ara (vetilac `/Pages/StockSearch.aspx`) kolonları "Miktar" ve "Açılmış Kalan Miktar"; eklenti bunları zaten `quantity` ve `openedQuantity` olarak okuyup gönderiyor (yerel veride Tenidur `2 / 18.000`). Kullanıcının gördüğü canlı satır: Clavon "Miktar 0, Açılmış Kalan Miktar 49,00".
- Kural: **"Miktar" yalnız kapalı adet**; ilk kullanımda bir adet bütün stoktan çıkar, kalanı açık stoka geçer.
- Reçete satırı alanları: `txtStockQuantity` "Kullanılacak Adet" (tam sayı), `txtStockAmount` "Kullanılacak Miktarı" (2 ondalık, en az 0,01).
- **Bilinmeyen:** Açık stoktan seçilen satırda Adet/Miktar alanlarının davranışı (Adet gizlenir mi, Miktar ml mi). Hayvan doğrulanmadan satır açılmadığı için görülemedi; yapımın ilk adımında canlı bir reçetede doğrulanmalı.

**İlaç reçetesi — 2026-10-24 canlı doğrulama** (hiçbir satır kaydedilmedi):
- "Stok Ekle" butonu için hayvan doğrulaması gerekmez. Hayvan türü seçmek yeterli. Stok penceresi için "Tedavi Başlangıç Tarihi" dolu olmalı, yoksa `alert` çıkar.
- Ekleme satırı (`RadGridStock_ctl00_ctl02_ctl03_`) alanları:
  - `lblStockQuantityDesc`: "49,00 mL" / "2 Adet"
  - `lblStockType`: "Açık Stok" / "Bütün Stok"
  - `hiddenStockType`: 1 açık / 0 bütün
  - `txtStockQuantity` (Adet)
  - `txtStockAmount` (Miktar, en az 0,01)
- **Adet her durumda kapalı ve 0.** Açık stoktan seçilen Clavon'da, bütün stoktan seçilen Tenidur'da (ml'li) ve Baytril Flavour'da (tablet) aynı. Miktar yazılınca Adet sayfada hesaplanmıyor. TARBİL yalnız **Miktar**'ı kullanıyor; şişeyi açmayı kendisi yapıyor (Clavon'un "1 ml kullanıldı → 49 ml açık" olması bununla tutarlı).
- Açık stok penceresinde "Seç" bağlantısı `runLinkButton2(...)` çağırıyor. Ana sayfada `hiddenSelectedStockId` doldurulup `updaRadgridField2` tetikleniyor. Bütün stokta ise `runLinkButton` → `hiddenSelectedProductId` / `updaRadgridField` kullanılıyor. Bağlantıya tıklamak ikisinde de yeterli.
- Açık stok tablosu `UcPharmacyStockSearchForReceipt_radGridOpenStock_ctl00`, son kolon "Açık Stok Miktarı". Radyo düğmesi `UcPharmacyStockSearchForReceipt_rbtnStockType_1`.
- Kaydedilen satırlar tablosunun kolonları: Detay, Ürün, Takdim Şekli, Seri Numarası, Son Kullanım Tarihi, Stok Miktarı, **Stok Tipi**, Kullanılacak Adet, **Kullanılacak Miktarı**, Sil.
- Aşı belgesi stok penceresi:
  - Radyolar: `rbtnStockType_0` (değer 0) Bütün Stok, **`rbtnStockType_1` (değer 2) Parça Stok**, `_2` (değer 1) Kendi Açık Stoklarım, `_3` (değer 3) Tüm Açık Stoklar.
  - Parça listesi **ayrı tabloda**: `radGridPieceStock_ctl00` (Aşı Adı, Takdim Şekli, Seri Numarası, Son Kullanma Tarihi, Ürün Miktarı, …). "Seç" yine `runLinkButton`.

**Aşı** — Aşı > Stok > Aşı Stok Ara (`/Modules/RECEIPT/Pages/ATS/VaccineStock/VaccineStockSearch.aspx`):
- "Ara" → `radGridStock_ctl00` (bütün stok): … Seri Numarası, Son Kullanma Tarihi, **Ürün Miktarı** (kutu), **Üretilen Doz-Küçük Hayvan**, **Üretilen Doz-Büyük Hayvan**, …, Stok Tipi. Biocan DHPPI 145932: 1 kutu, 20/20 doz; takdim "…Kutusunda 20 adet flakon".
- "Parça Stok Ara" → `radGridStockInbox_ctl00`: aynı kolonlar ama doz kolonları yok; Ürün Miktarı = **flakon**. Biocan R 665932: 18 (takdim "…20 adet flakon"); Felocell CVR 823287: 23 (takdim "…plastik kutusunda 25 adet").
- Açılan kutu bütün stoktan tamamen çıkar (Biocan R bütün stokta yok).
- "Açık Stok Ara" → `radGridOpenStock_ctl00` (Açık Stok Miktarı, Oluşturan, Oluşturma Tarihi): bu klinikte boş. Kapsam dışı.
- Aşı belgesi stok penceresi (`UcVaccineStockSearchModalPage.aspx`) `rbtnStockType`: 0 Bütün Stok (varsayılan), 2 Parça Stok, 1 Kendi Açık Stoklarım, 3 Tüm Açık Stoklar.

## 3. Hedefler

1. **Stok kalemi iki parçalı:** kapalı adet (ilaçta şişe/kutu, aşıda kutu) + açık miktar (ilaçta ml/g, aşıda flakon) + paket büyüklüğü (bir adet kaç ml / kaç flakon).
2. **Kullanım birimi:** ilaçta "ml ile kullanılan" ürünlerde reçete ve muayenede **ondalıklı ml** girilir; diğer ilaçlar bugünkü gibi **adet**. Aşıda her kayıt **1 flakon** (kullanıcı: "aşılar hep flakon olarak 1'er adet düşüyor").
3. **Düşüm kuralı:** kullanım önce açık miktardan düşer; yetmezse ya da açık yoksa yeni adet açılır (kapalı −1, açık + paket büyüklüğü) ve kalan oradan düşer. Düşüm bugünkü gibi **TARBİL onayından sonra** yazılır (bekleyen düşüm modeli); TARBİL'e bağlı olmayan kalemde hemen.
4. **Otomatik tahmin:** ilaçta "ml ile kullanılır mı" ve paket büyüklüğü TARBİL takdim şeklinden tahmin edilir ("50 ml'lik renksiz cam şişeler" → ml, 50; "20 ml amber renkli … flakonlarda" → ml, 20; tablet/pipet/kutu → adet). Aşıda paket büyüklüğü "Üretilen Doz-Küçük Hayvan" kolonundan, yoksa takdimdeki "N adet flakon"dan. **Kullanıcı stok ekranında ürün bazında düzeltebilir** (kullanım birimi, paket büyüklüğü).
5. **Stok ekranı:** iki kolon: kapalı adet ve açık miktar (birimiyle: "49 ml", "18 flakon").
6. **Eklenti, TARBİL'e gönderim:** ilaçta ml ürünü için açık miktar varsa stok penceresinde "Açık Stok" seçilip ml yazılır; yoksa "Bütün Stok"tan seçilir. Aşıda açık kutu varsa "Parça Stok", yoksa "Bütün Stok". Eklenti resmi butonlara (Satır Kaydet, Reçeteyi Onayla, belge kaydı) **asla** basmaz — mevcut kural.
7. **Eşitleme:** eklenti ilaçta `openedQuantity`'yi, aşıda parça stoğu ayrı gönderir (bugün de gönderiyor). Eşitleme kapalı adeti ve açık miktarı **ayrı** karşılaştırır, ikisini de işler; aşıda bütün + parça satırları artık toplanmaz. Ekranda "TARBİL: 0 adet + 49 ml / Vetly: 1 adet" gibi gösterilir.
8. **Mevcut veri:** yeni alanlar eklenirken mevcut kalemlerin bugünkü miktarı kapalı adet sayılır, açık miktar 0. Doğrusu ilk eşitlemeyle gelir. Ayrı bir veri düzeltme betiği yazılmaz.

## 4. Kapsam dışı / istenmeyenler

- **Fiyatlandırma:** faturada ml başına ücret yok; bugünkü gibi devam eder (kullanıcı kararı).
- Aşıda "Açık Stok" (çok dozluk tek flakon) ve "Bitmiş/Kabul Bekleyen" tabloları.
- Aşıda flakon dışında kullanım (ml, birden çok doz).
- Stok hareket raporlarının yeniden tasarımı; yalnız yeni hareketler doğru miktarı yazmalı.
- **İstenmeyen (başarısızlık sayılır):** Eşitlemenin açık miktarı silmesi (Clavon'u 0'a çekmesi); 1 ml kullanımın TARBİL'de bütün şişe düşmesi; kullanıcının her ürün için elle ayar yapmak zorunda kalması; eklentinin resmi butonlara basması.

## 5. Kısıtlar

- Çalışma doğrudan `main`'de; commit kullanıcıda.
- Veritabanı değişikliği yeni Flyway migration'ıyla. Son numara canlıda **V84** (bkz. `docs/deployment.md` "Her çıkıştan önce"); başka geliştiricinin V83'ü depoya gelmeden yeni numara alınırken çakışma kontrol edilmeli.
- Bekleyen düşüm, TARBİL onayı ve eşitleme kilidi (bekleyen işi olan kalem eşitlenemez) aynen korunur.
- Eklenti eski sürümle de çalışmalı: alanı göndermeyen eklentiden gelen eşitleme satırı "açık miktar bilinmiyor" sayılır, açık miktar değiştirilmez.
- Eklentinin TARBİL'e bildirdiği onaylanmış satırlar bugün `ReportedDeductionLine(productName, serialNumber, int quantity)`; ml için ondalık ve hangi listeden (bütün/açık) düşüldüğü bilgisi gerekecek.

## 6. Kullanıcının verdiği kararlar

- ml ile kullanılan ilaçlar Vetly'de ml olarak girilsin.
- Hangi ürünün ml ile kullanıldığı TARBİL bilgisinden **otomatik tahmin** edilsin; ürün bazında düzeltilebilsin.
- Aşılarda her kayıt 1 flakon.
- Fiyatlandırma şimdilik kapsam dışı.

## 7. Yapımcıya bırakılanlar

- Veri modeli ayrıntısı (alan adları, miktarların tutulma biçimi: ör. tek ondalıklı "toplam en küçük birim" mi, "kapalı adet + açık miktar" mı) — §3'teki davranışı karşıladığı sürece.
- Takdim şeklinden tahmin kuralları (düzenli ifadeler) ve tahmin edilemeyen ürünlerde varsayılan (adet).
- Reçete ve muayene ekranlarında ml girişi için arayüz ayrıntısı.
- Bir kullanım açık miktarı aştığında TARBİL'e tek mi iki satır mı gideceği — §2'deki bilinmeyen doğrulanınca karar verilir.

## 8. Benim kararlarım (kullanıcı kontrol etsin)

- **Mevcut veri:** Mevcut miktarlar kapalı adet sayılır, düzeltme ilk eşitlemeyle yapılır (§3.8).
- **Eski eklenti:** Eski eklentiden gelen veride açık miktar bilinmediği için olduğu gibi bırakılır (§5).
- **Aşıdaki diğer tablolar:** "Açık Stok" tablosu kapsam dışı; bu klinikte boş.
- **TARBİL esas:** TARBİL'e bağlı kalemde Vetly, düşümü kendi kuralına göre değil, eklentinin bildirdiği onaylı satıra göre yazar: hangi liste (bütün/açık) ve hangi miktar. Hekim TARBİL'de başka seçim yaptıysa Vetly onu izler. §3.3'teki kural iki yerde kullanılır: eklentiye verilen planda ve TARBİL'e bağlı olmayan kalemlerde.
- **Canlı doğrulama:** §2'deki bilinmeyen (açık stoktan seçilen satırın Adet/Miktar davranışı) yapımın ilk adımıdır. Bunun için kullanıcının TARBİL'de hayvanı doğrulanmış bir reçete açması gerekir; eklenti ve Vetly yalnız okur, satır kaydedilmez.
