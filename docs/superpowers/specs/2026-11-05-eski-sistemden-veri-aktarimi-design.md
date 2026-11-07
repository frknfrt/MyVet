# Eski Sistemden Veri Aktarımı — Tasarım

Tarih: 2026-11-05
Durum: Taslak (onay bekliyor)

## 1. Amaç ve neden

Başka bir klinik programından (örnek: KolayVet) Vetly'ye geçen bir kliniğin, eski sistemdeki **bütün** bilgilerini Vetly'ye taşıyabilmek. Klinik geçiş sonrası Vetly'yi açtığında hastalarını, sahiplerini, geçmiş muayene/aşı kayıtlarını, stoklarını, faturalarını ve borçlarını görmeli.

Neden önemli: Rakipler (KolayVet "ücretsiz taşıma" sunuyor, Excel şablonuyla içe aktarma yapıyor) bunu satış argümanı olarak kullanıyor. "Verilerin kaybolmaz" diyemeyen bir sistemle klinik geçişi zor.

Şu an bekleyen bir klinik yok; bu bir **altyapı hazırlığı**. Elde rakip programlardan örnek dosya da yok. Bu yüzden tasarım belirli bir programa değil, her kaynağa uyacak şekilde kurulur; ilk gerçek dosya geldiğinde yalnızca o kaynağın eşleştirmesi eklenir.

## 2. Kullanıcı kararları (konuşmada netleşenler)

- **Aktarılacak kapsam: hepsi.** Sahipler, hastalar, muayeneler, aşılar, reçeteler, laboratuvar/görüntüleme kayıtları, randevular, konaklamalar, stok kalemleri ve hareketleri, faturalar, ödemeler — eski sistemde olan her şey.
- **İki kaynak biçimi desteklenmeli:**
  1. Excel dosyaları (eski programın dışa aktarma çıktısı).
  2. `.bak` veritabanı yedeği (genelde Microsoft SQL Server yedeği).
- **Aktarımı Vetly ekibi yapar**, klinik değil. Klinik dosyayı Vetly'ye gönderir; ekip **platform admin panelinden** aktarır. Klinik arayüzüne hiçbir şey eklenmez.
- **Geçmiş faturalar ve ödemeler tek tek** aktarılır (yalnızca bakiye değil). Sahibin borcu bunlardan hesaplanır.
- **Aktarılan faturalar raporlarda ve ciroda sayılır.**
- **Hem boş hem dolu kliniğe** aktarım yapılabilmeli (Vetly'yi bir süredir kullanan, kendi kayıtları olan klinik dahil).
- Örnek dosya yok; tasarım kaynaktan bağımsız olmalı.
- **Tek son dosya, güncelleme yok.** Klinik eski programı bıraktığı gün tek bir son dosya gönderir. Daha önce aktarılmış bir kaydın değişmiş hâlini güncelleme (delta) desteklenmez; tekrar yüklemede daha önce aktarılmış kayıtlar atlanır.
- **Para tarafında sıfır hata.** Fatura/ödeme kayıtlarında tek bir hatalı satır varsa aktarım başlamaz; dosya düzeltilip tekrar yüklenir. Diğer kayıt türlerinde hatalı satırlar atlanır ve raporlanır.
- **Hatırlatmalar:** Aktarılan aşı ve randevuların yalnızca geçiş (aktarım) tarihinden sonraki tarihleri için hatırlatma gönderilir; geçmişte kalmış olanlar sessiz kalır (hatırlatma "gönderildi" sayılır).
- **Alacaklı sahip (fazla ödeme) durumu kapsam dışı.** Vetly'ye alacak bakiyesi kavramı eklenmez.

## 3. Hedefler, hedef olmayanlar, kaçınılacaklar

**Hedefler**
- Tek bir aktarım hattı: her kaynak önce ortak bir ara biçime çevrilir, oradan Vetly'ye yazılır.
- Yazmadan önce önizleme ve hata raporu.
- Her aktarım geri alınabilir.
- Aynı dosya iki kez yüklenirse çift kayıt oluşmaz.
- Bir kaynak program için yapılan eşleştirme kaydedilir ve sonraki kliniklerde tekrar kullanılır.

**Hedef olmayanlar**
- Kliniğin kendi kendine aktarım yapması (self-servis).
- Belirli bir rakip programa özel hazır dönüştürücü (örnek dosya gelene kadar).
- Eski sistemle sürekli/canlı senkronizasyon veya değişen kayıtların güncellenmesi. Aktarım, kliniğin gönderdiği tek son dosyayla yapılan bir iştir (hata düzeltip tekrar yükleme dışında).
- Alacak bakiyesi (kliniğin sahibe borçlu olması) kavramı.

**Kaçınılacaklar (çalışsa bile başarısızlık sayılır)**
- Kliniğin mevcut verisinin bozulması, silinmesi veya sessizce üzerine yazılması.
- Aktarılan faturaların e-fatura entegrasyonuna (`efatura`) gönderilmesi.
- Eski veriden bir kısmının iz bırakmadan kaybolması (Vetly'de karşılığı olmayan alanlar dahil).
- Sahip borç bakiyelerinin eski sistemdekinden farklı çıkması.
- `.bak` geri yükleme işinin canlı sunucuda yapılması.

## 4. Akış

```
Kaynak (Excel / .bak)
   │
   ▼
[1] Ham veri çıkarma ──► ham tablolar (sayfa/tablo adı + satırlar)
   │                     .bak: ayrı makinede SQL Server'a restore → tablolar dışa alınır
   ▼
[2] Eşleştirme ──► "kaynak sütun → Vetly alanı" (kaynak program adıyla kaydedilir, tekrar kullanılır)
   │
   ▼
[3] Vetly ara biçimi (Vetly standart şablonu)
   │
   ▼
[4] Doğrulama + önizleme ──► sayılar, hatalar, uyarılar, mevcut kayıtla eşleşmeler
   │                         (admin inceler, gerekiyorsa düzeltip tekrar yükler)
   ▼
[5] Aktarım (parti) ──► Vetly tabloları; her kayda kaynak kimliği + parti kimliği
   │
   ▼
[6] Gerekirse partiyi geri alma
```

### 4.1 Vetly standart şablonu
Sistemin içe almayı bildiği tek biçim. Her kayıt türü için bir sayfa/tablo, sütunları sabit ve belgelenmiş. Her satırda eski sistemdeki kimlik (`kaynak_id`) zorunlu; kayıtlar arası bağlar (hasta → sahip, muayene → hasta, ödeme → fatura) bu kimliklerle kurulur.

Bu şablon aynı zamanda tek başına da kullanılabilir: ekip, gelen veriyi elle (veya yardımcı araçlarla) bu şablona çevirip doğrudan yükleyebilir. Eşleştirme ekranı bu işi kolaylaştıran bir katmandır.

### 4.2 `.bak` dosyaları
- `.bak` canlı sunucuda açılmaz (canlı sunucu OpenVZ VPS, Docker yok, SQL Server çalıştırmaya uygun değil).
- Ayrı bir makinede (geliştirici bilgisayarı veya ayrı bir çalışma sunucusu) SQL Server'a geri yüklenir, tabloları dışa alınır ve bu çıktı [1] adımına "ham tablolar" olarak girer.
- Bu adımı yapan küçük bir araç/komut dosyası (script) tasarımın parçası. Admin paneline yalnızca dışa alınmış ham tablolar yüklenir.

### 4.3 Eşleştirme
- Admin, ham tablodaki sütunları Vetly alanlarına eşler.
- Değer eşleştirmesi de yapılır: tür/ırk adları, ödeme yöntemleri, aşı adları gibi değerler Vetly listeleriyle eşlenir (mevcut `tarbil_value_mapping` yaklaşımına benzer).
- Eşleştirme "kaynak program" adıyla (ör. "KolayVet") kaydedilir; aynı programdan gelen ikinci klinikte hazır gelir.

### 4.4 Doğrulama ve önizleme
Hiçbir şey yazılmadan önce gösterilir:
- Kayıt türü başına sayılar (ör. 3.412 hasta, 2.980 sahip).
- Hatalar (satır bazında: zorunlu alan boş, tarih okunamadı, bağlı kayıt bulunamadı…). Hatalı satırlar aktarılmaz; listesi indirilebilir.
- Uyarılar (aynı telefonla birden fazla sahip, eşleşmeyen tür adı…).
- Dolu klinikte: mevcut kayıtlarla eşleşenler.
- Kontrol toplamları: sahip bazında hesaplanan borç bakiyesinin toplamı (eski sistemdeki toplamla karşılaştırılabilsin diye).

### 4.5 Aktarım partisi
- Her aktarım bir **parti**dir: kim yaptı, hangi klinik, hangi kaynak, ne zaman, kaç kayıt.
- Aktarılan her kayıtta: parti kimliği + kaynak sistem + eski sistemdeki kimlik. Bu üçlü sayesinde aynı dosya tekrar yüklenirse kayıt yeniden oluşturulmaz.
- Büyük veri olabilir (on binlerce satır); aktarım arka planda çalışır, admin ilerlemeyi görür.

### 4.6 Geri alma
- Bir parti geri alındığında yalnızca **o partinin oluşturduğu** kayıtlar silinir.
- Partiden önce var olan kayıtlara (kliniğin kendi girdikleri, eşleşip üzerine geçmiş eklenen sahipler) dokunulmaz; yalnızca partinin eklediği bağlı kayıtlar kaldırılır.
- *(Tasarımcının kararı)* Geri alma yalnızca partinin kayıtlarına aktarımdan sonra bağlanmış yeni kayıt yoksa yapılabilir (ör. aktarılan hastaya yeni muayene açılmışsa, aktarılan faturaya yeni ödeme alınmışsa). Varsa geri alma engellenir ve admin'e bu bağlı kayıtların listesi gösterilir. Kliniğin kendi girdiği kayıtlar hiçbir koşulda otomatik silinmez.

## 5. Alanlara özel kurallar

### 5.1 Faturalar ve ödemeler
- Geçmiş faturalar ve ödemeler tek tek aktarılır; borç bakiyesi bunlardan hesaplanır (Vetly'de ayrı bir borç alanı yok; borç = faturalar − ödemeler).
- Aktarılan faturalar **"aktarıldı"** olarak işaretlenir:
  - E-fatura entegrasyonu bunları asla göndermez.
  - Düzenlenemez/iptal edilemez (salt okunur).
  - Raporlarda ve ciroda normal faturalar gibi sayılır.
- Eski fatura numarası korunur ve görünür. (Vetly'nin `invoices` tablosunda şu an fatura numarası sütunu yok; aktarılan numara için yer açılmalı. Vetly'nin kendi numaralandırmasıyla çakışmamalı.)
- Vetly'de `payments` her zaman bir faturaya bağlı (`invoice_id` zorunlu). Eski sistemde faturaya bağlı olmayan tahsilatlar (ör. "hesaba ödeme") olabilir; bunların borç bakiyesini doğru düşürecek şekilde temsil edilmesi gerekir.
- Kabul ölçütü: aktarım sonrası her sahibin Vetly'deki bakiyesi eski sistemdeki bakiyesine eşit.
- Para zincirinde (fatura, fatura satırı, ödeme ve bunların bağlı olduğu sahip) tek bir hata varsa aktarım başlamaz; önizleme hataları listeler.
- Alacaklı (eksi bakiyeli) sahip kapsam dışı; kullanıcıya göre bu durum oluşmuyor. *(Tasarımcının kararı)* Yine de kaynakta görülürse önizlemede uyarı olarak raporlanır ve admin elle çözer.

### 5.1b Yan etkiler
- *(Tasarımcının kararı)* Aktarılan aşı, reçete ve muayene kayıtları normal kayıt olaylarını tetiklememeli: TARBİL'e bildirim gitmez, stoktan düşme yapılmaz (stok kendi aktarımıyla gelir), e-fatura gönderilmez.
- Hatırlatmalar: aktarılan aşıların sonraki doz tarihi ve randevular aktarım tarihinden sonraysa normal hatırlatma akışına girer; geçmişte kalmışsa hatırlatma gönderilmiş sayılır ve mesaj gitmez.

### 5.2 Dolu kliniğe aktarım
- Gelen sahip, Vetly'de aynı telefon numarasıyla kayıtlıysa yeni kayıt açılmaz; önizlemede "mevcut kayıtla eşleşti" gösterilir, admin onaylarsa geçmiş o kayda eklenir.
- Otomatik birleştirme yok; şüpheli eşleşmeler admin kararına bırakılır.

### 5.3 Personel (hekimler)
- Eski sistemdeki hekimler, muayene ve faturalarda adları görünsün diye Vetly'de **giriş yetkisi olmayan pasif personel** (`staff_users.active = false`) olarak oluşturulur. Dolu klinikte aynı isimli personel varsa eşleştirme önerilir.

### 5.3b Şubeler
- *(Tasarımcının kararı)* Kaynaktaki şubeler eşleştirme adımında Vetly'deki şubelerle eşlenir; kaynakta şube bilgisi yoksa admin tüm verinin yazılacağı tek şubeyi seçer. Aktarım otomatik yeni şube açmaz (plan sınırlarına takılmamak için); gerekiyorsa şube önce normal yoldan açılır.

### 5.4 Tür ve ırk
- Vetly'nin tür/ırk listeleriyle eşlenir.
- **Tür:** Vetly'de olmayan bir tür gelirse (ör. "Tavşan") aktarım sırasında Vetly'ye **eklenir** (kullanıcı kararı: uygulama bütün türleri kapsamalı). Vetly'de tür listesi kliniğe özel değil, tüm kliniklerin ortak listesidir; eklenen tür herkeste görünür. Önizlemede "eklenecek yeni türler" olarak listelenir ki admin yazım hatalarını aktarmadan önce görsün.
- **Irk:** Vetly'de olmayan ırk da aktarım sırasında ilgili türün altına **eklenir** ve hasta ona bağlanır (kullanıcı kararı, 2026-11-06; tür kuralıyla aynı gerekçe). Irk listesi de tüm kliniklerin ortak listesidir; önizlemede "eklenecek yeni ırklar" (ırk + tür) olarak listelenir, aynı ırkın farklı yazımları tek kayıt olur ve geri almada silinmez.

### 5.5 Stok
- Güncel stok miktarları aktarılır.
- Kaynakta varsa geçmiş stok hareketleri de aktarılır. Aktarım sonrası her kalemin miktarı eski sistemdeki güncel miktara eşit olmalı.

### 5.6 Karşılığı olmayan bilgiler
- Vetly'de alanı olmayan eski bilgiler kaybolmaz; ilgili kaydın yanında **"eski sistem bilgileri"** olarak (anahtar–değer) saklanır ve kayıt detayında okunabilir.

### 5.7 Ek dosyalar
- Eski sistemde fotoğraf/belge varsa ve kaynakta erişilebilirse `stored_files` altyapısıyla ilgili kayda bağlanır.

## 6. Kısıtlar

- Backend: mevcut modüler yapı (`com.vetos.modules.*`), Spring, Flyway migration'ları, PostgreSQL. Ekran: mevcut platform admin paneli (`frontend/src/pages/platform-admin`), en doğal yeri klinik detay sayfası (`TenantDetailPage`).
- Her kayıt `tenant_id` ile kliniğe bağlı; aktarım hedef kliniğin dışına yazamaz.
- Canlı sunucu: Güzel Hosting OpenVZ VPS, Docker yok. `.bak` işi canlıda yapılmaz.
- Migration'lar dikkatli olmalı: başka geliştiriciler de canlıya çıkıyor; geriye dönük uyumlu, mevcut sütunları bozmayan değişiklikler.
- Kişisel veri (KVKK): klinik dosyaları hasta sahiplerinin kişisel verilerini içerir. Yüklenen ham dosyalar ve ara veriler aktarım bitip onaylandıktan sonra saklanmamalı/silinebilmeli; yalnızca platform adminleri erişebilmeli; aktarım işlemleri denetim kaydına (`audit_log_entries`) yazılmalı.
- Commit yok — değişiklikler IntelliJ changelist'inde kalır, push yok.

## 7. Geliştiriciye bırakılanlar

- Ham verinin ve ara biçimin nerede tutulacağı (geçici tablolar, dosya, JSON).
- Excel okuma kütüphanesi seçimi.
- `.bak` dışa alma aracının biçimi (komut dosyası, küçük CLI) — yeter ki çıktısı admin paneline yüklenebilir ham tablolar olsun.
- Arka plan işinin nasıl çalışacağı ve ilerlemenin nasıl gösterileceği.
- "Aktarıldı" işaretinin ve kaynak kimliğinin hangi tablolara, hangi sütun adlarıyla ekleneceği.
- Faturaya bağlı olmayan ödemelerin teknik temsili (bkz. 5.1), kabul ölçütünü sağladığı sürece.
- Projenin fazlara bölünmesi ve sırası. Öneri: önce temel hat (şablon + doğrulama + parti + geri alma) sahip/hasta ile; sonra klinik kayıtları; sonra para; sonra stok; sonra eşleştirme ekranı ve `.bak` aracı.

## 8. Açık konular

- İlk gerçek kaynak dosya geldiğinde o programın eşleştirmesi hazırlanacak ve tasarım gerçek veriyle doğrulanacak.
