# Stok birimi ve TARBİL miktar düzeltmeleri — tasarım

Tarih: 2026-10-25
Durum: kullanıcı onayı bekliyor
Önceki tasarım: `2026-10-24-acik-parca-stok-design.md` (açık/parça stok). Bu belge onun değerlendirmesinden kalan 14 küçük
maddeyi ve 2026-10-25 TARBİL incelemesinde öğrenilenleri kapsar.

## 1. Amaç ve neden

Açık/parça stok canlıya çıkmadan önce Vetly'nin TARBİL'e yazdığı **Miktar** ile TARBİL'in düştüğü miktar her ürün türünde
aynı olmalı. İnceleme, iki ürün türünde Vetly'nin yanlış birim kullandığını gösterdi (pipet, tablet) ve birkaç sınır
durumunda iki tarafın sessizce ayrıştığını ortaya çıkardı.

Başarı: Hekim Vetly'de "1 pipet" ya da "2 tablet" girdiğinde TARBİL'de doğru miktar düşer, Vetly'nin stoğu TARBİL ile aynı
kalır; geçersiz miktar Vetly'de anlaşılır bir uyarıyla reddedilir, TARBİL'e hiç gitmez.

## 2. TARBİL'de görülenler (2026-10-25)

Kaynaklar: reçete sayfasının kendi ayarları (canlı, kaydetmeden), stok sayfaları, E-Reçete/İTS kılavuzu (Güncelleme III),
ATS kılavuzu, herkese açık "Ruhsatlı Veteriner İlaçları" veritabanı.

**Kullanılacak Miktar kutusu** (Telerik sayı kutusu, üründen bağımsız):
- En az 0,01; en çok yok; 2 ondalık; virgül ayraç; nokta binlik ayraç.
- Yarım yukarı yuvarlar (1,005 → 1,01; 2,675 → 2,68).
- 0,01 altını (0,004 / 0 / eksi) **hata vermeden 0,01 yapar**.
- `"1,5"` → 1,5 ✓; `"1.5"` (metin) → **15** ✗; sayı olarak 1.5 → 1,5 ✓.

**Kullanılacak Adet kutusu:** ondalıksız, en az 0, yarım yukarı; ürün seçildikten sonra kapalı (önceki tasarım §2).

**Ürün türüne göre miktar birimi:**
- Çözelti/süspansiyon (şişe, flakon) → **ml** (Clavon açık 49, Tenidur açık 18).
- **Pipet ürünleri de ml** — kutunun toplam mililitresi: FİPROES KEDİ (3 × 0,5 ml) açık 0,50 (= 1 pipet);
  ADVANTAGE (4 × 0,8 ml) açık 0,20 (biri "3" yazmış, TARBİL 3 ml düşmüş). Farmasötik şekilleri "damlatma çözelti".
- **Tablet → tablet sayısı** (kullanıcı kararı, 2026-10-25).
- Merhem/tüp (Oridermyl 10 g) → g.

**Kılavuz kuralları:**
- İTS 11.4.7.8: tam ambalaj satılmayıp bir kısmı kullanılırsa kullanılan ürün miktarı yazılır.
- İTS 11.4.7.9: açılan ürünün kalanı için sistem bir **kullanım süresi** tanımlar; süresi dolan açık ürün kullanılmaz,
  denetimde gösterilmek üzere saklanır. Stok sayfasında "Açılmış Kalan Miktar Son Kullanma Tarihi" sütunu.
- ATS 9.2.4.4: ilk kez açılan ürün **Bütün Stok**, daha önce açılmış ürün **Açık (Parça) Stok**'tan seçilir.
- ATS 9.2.5.4: doz tanımı yoksa kullanılan ml yazılır.

## 3. Kullanıcının verdiği kararlar

- Tabletler TARBİL'de **tablet adedi** olarak düşer.
- Pipetler ml olarak tutulur; hekim "pipet" sayısı girer, Vetly ml karşılığını yazar.
- Miktar: en az 0,01, en çok 2 ondalık; fazla hane **reddedilir** (yuvarlanmaz). Eklenti 0,01 altını yazmaz.
- Açılan ürünün son kullanma tarihi TARBİL'den okunup gösterilir.
- Değerlendirmeden kalan maddeler (aşağıda §4) bu işe dahil.

## 4. Hedefler

### 4.1 Birimler
1. **Yeni birim TABLET.** Açılabilir birim (ML/G/FLAKON gibi): kapalı = kutu, açık = kutudan kalan tablet,
   paket büyüklüğü = kutudaki tablet. Reçete/kullanım miktarı tablet cinsinden; TARBİL'e tablet sayısı yazılır.
   Tablette ondalık yok (yarım tablet yok); tam sayı zorunlu.
2. **Pipet = ML + parça büyüklüğü.** ML kalemlerine isteğe bağlı **parça büyüklüğü** (bir pipetin ml'si) ve parça adı
   ("pipet") eklenir. Paket büyüklüğü = adet × parça ml'si (FİPROES KEDİ: 1,5 ml, parça 0,5 ml).
   Parça tanımlı kalemde hekim miktarı **parça sayısıyla** girer (tam sayı); Vetly ml'ye çevirir (1 pipet = 0,5 ml),
   stoktan ve TARBİL'e ml olarak düşer. Ekranda "1 pipet (0,5 ml)" gibi gösterilir.
3. **Birim tahmini güncellenir** (`PresentationUnitGuesser`):
   - "tablet" (ve "kapsül") geçiyorsa → TABLET; paket = "N tabletlik/kapsüllük" sayısı, yoksa ADET'e düşer.
   - Pipet/tüp ve ml birlikte: "0.5 ml'lik pipetler 3 adetlik", "0,8 ml'lik 4'er adet … tüp", "0.25 ml … 4 pipet"
     → ML, parça = pipet ml'si, paket = adet × parça. Adet bulunamazsa parça = paket = tek pipet ml'si.
   - Şişe/flakon ml'si için "N ml'lik/ml.lik" ifadesi düz "N ml"den önce gelir ("1 ml'de 50 mg … 100 ml'lik şişe" → 100).
   - "sırınga" (noktasız) da tanınır.
   - Aşı (HBSAPP_VACCINE) kuralları aynı.
4. **Mevcut kalemler:** eşitleme sırasında çalışan birim tahmini (`RefreshStockUnitGuesses`) yeni kurallarla da çalışır;
   elle ayarlanmış birim ve bekleyen düşümü olan kalem yine atlanır. ADET→TABLET geçişinde kapalı kutu sayısı aynen kalır.

### 4.2 Miktar kuralları ve eklenti
5. Vetly'deki tüm miktar girişleri (reçete stok miktarı, muayene kullanımı, paket büyüklüğü, parça büyüklüğü,
   TARBİL onay satırı): en az 0,01, en çok 2 ondalık, üst sınır 100.000. Kurala uymayan giriş 400/422 ve anlaşılır mesaj;
   sunucu hatası (500) asla. TABLET ve parça girişleri tam sayı.
6. Eklenti Miktar'ı yuvarladıktan sonra kontrol eder; 0,01 altıysa yazmaz ve hekime söyler. Miktar her zaman virgüllü
   metin olarak yazılır; noktalı metin yazılmadığı testle güvenceye alınır.

### 4.3 Hesap ve tutarlılık
7. Aşıda birim ADET kalmışsa TARBİL'e "Bütün Stok" gönderilir (açık stok değil).
8. Düşüm sırasında ürün satırı kilitlenir; aynı ürüne eşzamanlı iki düşüm birbirini ezmez.
9. TARBİL'e gönderim planı (açık/bütün) kuyruktaki satırlar için **sırayla** yapılır: önceki satırların açıktan kullandığı
   miktar sonrakinden düşülerek karar verilir.
10. ADET kalemde kesirli bekleyen miktar yukarı yuvarlanır (önceki ciddi sorun düzeltmesiyle aynı kural).
11. Açık miktarı kutu karşılığına çevirerek düşük stok kontrolü: kapalı + açık / paket < eşik ise "Düşük Stok".

### 4.4 Ekran
12. Teslim kartında doğru birim ("1,5 ml", "2 tablet", "1 pipet").
13. ADET satırında açık miktar sıfır değilse gösterilir (TARBİL farkı görünür olsun).
14. Elle stok hareketi formunda açılabilir kalemler için **açık miktar düzeltmesi** (kırılma, dökülme); stok hareketlerine
    "−3 ml açık" biçiminde yazılır.
15. **Açılmış ürün son kullanma tarihi:** ilaç stok eşitlemesi "Açılmış Kalan Miktar Son Kullanma Tarihi"ni okur;
    kalemde saklanır, stok ekranında gösterilir. Tarih geçtiyse kırmızı "açık ürünün süresi doldu", 3 gün içindeyse sarı uyarı.
    Açık miktar sıfırlanınca tarih temizlenir.

### 4.5 Belgeleme
16. Eski aşı kayıtlarının iptalinde bütün kutu geri ekleme davranışı (V77 öncesi) koda not olarak yazılır; davranış aynı.
17. Aşı onayında kaynak bilgisi gelmediğinde Vetly'nin "açık varsa önce oradan" kuralı ATS 9.2.4.4 ile aynıdır; koda not.

## 5. Kapsam dışı

- Herkese açık ilaç veritabanından farmasötik şekil okumak (bu işte tarif metni yetiyor; ileride tahmin zayıf kalırsa).
- Aşı parça stoğu için ayrı son kullanma tarihi (TARBİL parça listesinde böyle bir sütun yok).
- Yarım tablet.
- Eklentinin yeni TARBİL düğmelerine basması — resmi düğmelere (Satır Kaydet, Reçeteyi Onayla, Onayla, çıkış) asla basılmaz.

## 6. Kısıtlar

- Main üzerinde çalışılır, commit yok (kullanıcı IntelliJ'den commit eder).
- Canlıya çıkıştan önce canlıdaki migration'lar kontrol edilir (başka geliştiriciler de çıkış yapıyor).
- Eklenti sürümü yükselir (0.3.x); sunucunun kabul ettiği en düşük sürüm ancak eski eklenti yanlış miktar yazacaksa yükseltilir.
- Sır/şifre işlenmez.

## 7. Yapımcıya bırakılanlar

- Parça büyüklüğü ve son kullanma tarihinin hangi tabloda/sütunda tutulacağı (kalem ya da TARBİL lot satırı).
- Ekranda pipet/tablet girişinin tam görünümü (mevcut ondalık giriş deseni izlenir).
- Satır kilidinin yöntemi (`SELECT … FOR UPDATE` ya da sürüm alanı).

## 8. Benim kararlarım (kullanıcı kontrol etsin)

- Pipet için ayrı bir birim yerine "ML + parça büyüklüğü": TARBİL ml beklediği için stok ml'de tutulur, pipet yalnızca giriş
  ve gösterim kolaylığı.
- "10 tabletlik blisterlerde" gibi kutudaki blister sayısı yazmayan metinde paket = 10 tahmin edilir; kutuda birden çok
  blister varsa hekim stok ekranında düzeltir.
- Fazla ondalık hane reddedilir (TARBİL yuvarlar ama Vetly ile TARBİL arasında fark oluşmasın diye).
- Üst sınır 100.000 (TARBİL'de sınır yok; yalnızca hatalı girişe karşı).
- Açık ürün süresi uyarısı: geçti = kırmızı, 3 gün içinde = sarı.
- Tablet ve parça girişleri tam sayı.

## 9. Testler (özet)

- Birim tahmini: canlı veriden gerçek tarif metinleri (FİPROES, ADVANTAGE, EVICTO, Baytril Flavour, Tenifen, Clavon,
  Oridermyl, Promazin, Biocan, Felocell) ve "1 ml'de 50 mg … 100 ml'lik" örneği.
- TABLET düşümü: açık tabletten, yetmezse yeni kutu açarak; TARBİL'e tablet sayısı.
- Pipet: 1 pipet → 0,5 ml stoktan ve TARBİL Miktar'ında "0,5".
- Miktar doğrulama: 0,004 / 0 / 1,234 / 100.001 reddedilir; 0,01 ve 1,25 kabul.
- Eklenti: 0,004 yazılmaz; Miktar her zaman virgüllü metin.
- Sıralı plan, satır kilidi (eşzamanlı iki düşüm), düşük stok, açık ürün son kullanma uyarısı.
