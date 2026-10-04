# Vetly TARBİL Yardımcısı (Chrome eklentisi)

Tasarım: `docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md`.

## Derleme

    npm ci
    npm run build          # prod: https://uygulama.vetly.com.tr
    npm run build:dev      # .env.development'taki VITE_VETLY_API_BASE (ör. http://localhost:8080)

## Kurulum (paketlenmemiş)

Chrome > `chrome://extensions` > Geliştirici modu > "Paketlenmemiş öğe yükle" > `extension/dist`.

## Eklenti kimliği

`npm run extension-id` — çıktı, frontend'deki `VITE_TARBIL_EXTENSION_ID` değeridir. Kimlik `public/manifest.json`'daki
`key` alanından türetilir; `key.pem` repoda tutulmaz (Web Store'a geçişte gerekir, güvenli yedekte saklanır).

## TARBİL'de otomatik doldurma (Faz 2a)

1. Vetly'de aşının yanındaki **TARBİL'e aktar**'a basın (ya da yan panelde **TARBİL'de doldur**). TARBİL "Aşı Uygulama Belgesi Ekle" sayfası açılır.
2. TARBİL oturumu kapalıysa e-Devlet ile giriş yapın; giriş sonrası eklenti aşı sayfasına kendisi geçer.
3. Eklenti uygulama tarihini ve türü (kedi/köpek) girer, **PetVet'ten Hayvan Ara ve Ekle** penceresinde çip numarasıyla arar; çipi birebir eşleşen tek ve `CANLI` hayvanı forma aktarır.
4. Siz **Ürün Ekle**'den aşıyı stoktan seçip kontrol eder ve **Onayla**'ya basarsınız. Eklenti Onayla'ya asla basmaz.
5. TARBİL kaydı onaylayınca eklenti aşıyı Vetly'de "gönderildi" olarak işaretler. Yakalayamazsa kartta **Kaydedildi olarak işaretle** her zaman var.

**Açılır pencere izni:** Arama penceresi açılmazsa Chrome adres çubuğundaki engellenen pencere simgesinden `hbsapp.tarbil.gov.tr` için açılır pencerelere izin verin.

Teknik not: `page.js` sayfanın kendi dünyasında (`world: "MAIN"`) çalışır ve yalnız Telerik bileşenlerini tetikler; Vetly API'sine ve eklenti anahtarına erişimi yoktur. TARBİL'e özgü tüm id'ler `src/tarbil/selectors.ts`'tedir.

## TARBİL oturumunu açık tutma

TARBİL boş kalan oturumu kendisi kapatır (`hbs.tarbil.gov.tr/?T=TimeOut`). Eklenti, açık bir TARBİL sekmesi (`hbsapp` ya da `vetilac`) varken 4 dakikada bir aynı siteden sade bir sayfa ister (`/Default.aspx`, `vetilac`'ta `/Pages/PharmacyDefault.aspx`). Veri ya da form göndermez, hiçbir butona basmaz. Yan paneldeki **TARBİL oturumunu açık tut** kutusuyla kapatılabilir (varsayılan açık). Oturum bir kez düştüyse e-Devlet girişi yine hekimdedir.

## TARBİL stoğunu Vetly'ye aktarma (P1a)

TARBİL'de **Aşı > Stok > Ara** (`hbsapp`) ya da **İlaç Takip Sistemi > Stok Ara** (`vetilac`) sayfasını açın; Vetly kartındaki **TARBİL stoğunu Vetly'ye gönder** butonuna basın. Eklenti yalnız "Ara"ya basar ve tabloyu tek sayfaya alır, okur ve Vetly'ye gönderir. Ardından Vetly'de **Stok** sayfasının altındaki **TARBİL Eşitleme** bölümünden farkları görüp seçtiğiniz satırları Vetly stoğuna işleyin.

## Aşı ürünü (P2)

Aşı Vetly'de stoktan seçildiyse eklenti hayvandan sonra **Ürün Ekle**'ye basar; açılan stok penceresinde Vetly serisini arar ve tek eşleşen satırı **Seç**er; formdaki **Ürün Adet**'i 1 yapar. Satırdaki **Kaydet** (TARBİL stoğundan düşer), Detay alanları ve **Onayla** hekimdedir. Stok penceresi açılmıyorsa TARBİL için açılır pencerelere izin verin.
