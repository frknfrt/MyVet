# Dijital Onay Formları (kâğıt formdan e-form + tablette imza) — Tasarım

Tarih: 2026-11-07
Durum: Taslak (kullanıcı onayı bekliyor) — okuyucu incelemesi yapıldı, sorular cevaplandı

## 1. Amaç ve neden

Klinikler bugün "Muayene, Müdahale ve Tedavi Onay Formu", "Anestezi ve Operasyon İzin Formu" gibi onay formlarını A4 kâğıda kalemle doldurtup imzalatıyor ve klasörde saklıyor. Her kliniğin formu farklı. Amaç bu süreci kâğıtsız hale getirmek:

1. **Şablon oluşturma (bir kez, klinik yöneticisi):** Kâğıt formun fotoğrafı çekilir ya da görseli yüklenir; yapay zekâ başlığı, metinleri ve alanları çıkarıp bir **taslak** sunar; yönetici düzeltip onaylar → kliniğin form şablonu olur.
2. **Form doldurtma (her seferinde, personel):** Hasta kartından veya muayene ekranından şablon seçilir; sistemde olan bilgiler dolu gelir; tablet müşteriye uzatılır; müşteri metni okur ve **elle (parmak/kalem) imzalar**; imzalı form hastanın ve sahibin kaydında saklanır, istenirse PDF olarak çıktı alınır.

Örnek formlar (kullanıcının paylaştığı): `C:\Users\Furkan\Desktop\TedaviForum.jpeg` (tablo düzeninde hasta bilgileri + 4 paragraf onay metni + sahip ad/soyad, kimlik no, doğum tarihi, telefon, imza), `C:\Users\Furkan\Desktop\anestezi formu .jpeg` (giriş tarihi; hasta yakını ad/ev tel/cep tel/adres; hasta adı, tür kutucukları Kedi/Köpek/Kuş/Diğer, cinsiyet Dişi/Erkek, yaş, ırk, renk; 4 paragraf metin, birinin ortasında "………… muayenehanesi" boşluğu; sahip adı, TC no, tarih, imza).

## 2. Hedefler / hedef olmayanlar / anti-hedefler

**Hedefler**
- Klinik yöneticisi kendi kâğıt formunu fotoğraftan birkaç dakikada e-forma çevirebilsin.
- Personel formu tablette birkaç dokunuşla açsın; bilinen bilgiler otomatik dolsun; müşteri elle imzalasın.
- İmzalı formlar klasör yerine sistemde, hasta/sahip kaydına bağlı, değiştirilemez şekilde saklansın; PDF çıktısı alınabilsin.
- Saklama maliyeti minimum olsun.

**Hedef olmayanlar (kapsam dışı)**
- Müşterinin formu kendi telefonundan uzaktan (link/SMS ile) doldurması.
- Hekimin/personelin de imza atması (form başına tek imza: müşteri).
- Çok sayfalı form tasarımı, serbest görsel tasarım (sütun, renk, yazı tipi, sürükle-bırak).
- Hazır (sistemle gelen) form şablonları kütüphanesi.
- e-İmza / nitelikli elektronik imza entegrasyonu.

**Anti-hedefler (çalışsa bile başarısızlık sayılır)**
- Yapay zekânın çıkardığı şablonun yönetici onayı olmadan kullanıma girmesi.
- İmzalanmış bir formun içeriğinin (metin, değerler, imza) sonradan değiştirilebilmesi veya şablon değişince eski imzalı formun farklı metinle görünmesi.
- Müşterinin onaylamadığı bir bilginin sahibin/hastanın kaydına yazılması.
- Bir kliniğin şablonunun veya imzalı formunun başka bir klinikte görünmesi.

## 3. Kısıtlar

- Modüler monolit + hexagonal kurallar (`CLAUDE.md`, `docs/architecture.md`, `docs/reference-module.md`, `docs/api-conventions.md`, `docs/coding-conventions.md`). Yeni modül `docs/reference-module.md` sırasıyla yazılır.
- Her tablo `tenant_id` taşır (satır bazlı izolasyon, `@TenantId`).
- Dosyalar (`FileStoragePort`, şu an Postgres `stored_files`) üzerinden saklanır.
- Yapay zekâ çağrıları mevcut desenle: `ai` modülünde port + Claude/Ollama adaptörü, `AiCallGate` + `UsageQuotaPort` (`UsageKind.AI`) ile kota; sonuç her zaman taslak.
- PDF: projede zaten olan OpenPDF (`encounter/infrastructure/pdf/EncounterReportPdfRenderer.java` deseni), istek anında üretilir.
- Frontend: `tokens.css` renkleri, Ayarlar sekmeleri `SettingsPage.tsx` içindeki `SETTINGS_TABS` dizisine bir satır eklenerek; AI üretimi içerik mavi (`--color-ai-600`) ailesinde ve "onay bekliyor" ilkesiyle.
- Mevcut fotoğraf altyapısı yeniden kullanılır: `frontend/src/lib/prepareImage.ts` (`'document'` ön ayarı, 2000 px JPEG) ve `capture="environment"` kamera girişi.
- Repo kuralı: main üzerinde çalışılır, commit atılmaz.

## 4. Kullanıcının kararları

- Formlar **Ayarlar** altında yeni bir **"Formlar"** bölümünde yönetilir.
- Şablon görselden yapay zekâ ile çıkarılır; sistem "bu şekilde oluşturulsun mu?" diye taslak gösterir; onaylanırsa şablon olur.
- Taslak düzenleme **orta yol**: metinler ve alan adları düzeltilebilir; **alan eklenebilir** (bölüm sonunda "+ Alan ekle", tür listeden), **alan silinebilir**, sıra **yukarı/aşağı oklarla** değiştirilebilir. Sürükle-bırak ve görsel tasarım yok; form her zaman tek sütun sade düzende gösterilir.
- Kâğıt formun fotoğrafı **referans olarak şablonla birlikte saklanır**.
- Form **hem hasta kartından hem muayene ekranından** açılabilir.
- Sistemde olan bilgiler otomatik dolu gelir; tablet müşteriye sadece okuyup imzalaması için uzatılır; imza **elle** (parmak/kalem) atılır.
- Gönderimde, formda yazılan bilgi kayıttakinden **farklı veya kayıtta eksikse** müşteriye sorulur ("Kaydınız da güncellensin mi?"); onaylanan alanlar sahibin/hastanın kaydına yazılır.
- Tablet müşterideyken sistemin geri kalanını görebilmesi **sorun değil** (PIN/şifre kilidi yok).
- Önizlemede ve imzalı formda **"PDF olarak indir"**; saklama minimum maliyetle.
- **İmzalayan:** Varsayılan imzalayan kayıtlı hasta sahibidir. Personel **"Başka biri imzalıyor"** seçerse (ör. anestezi formundaki "hasta yakını") o kişinin **ad soyad, TC no ve telefonu** ayrıca girilir; bu bilgiler **yalnızca formda kalır**, sahibin kaydına asla yazılmaz.

## 5. Benim kararlarım (kullanıcı ayrıca onaylamadı)

- **Saklama:** İmzalı form PDF/görsel olarak saklanmaz. Saklananlar: doldurulan değerler (JSON), imza (şeffaf PNG, tipik 5–20 KB, `FileStoragePort`), formun **imza anındaki şablon içeriğinin kopyası** (JSON) ve kim/ne zaman/hangi personel bilgisi. PDF her istendiğinde bunlardan üretilir.
- **Değiştirilemezlik:** İmzalı form güncellenemez; yalnızca gerekçeyle "iptal edildi" işaretlenebilir (kayıt silinmez). Ek olarak imza anında değerler + şablon kopyası + imza PNG baytları üzerinden bir **SHA-256 özeti** hesaplanıp saklanır; PDF'in altına kısaltılmış hâli basılır. Görüntülemede özet yeniden hesaplanır, uyuşmazsa "bütünlük doğrulanamadı" uyarısı gösterilir. İmza anındaki istemci IP'si ve tarayıcı bilgisi (User-Agent) de kaydedilir (mevcut `consent_records.ip_address` gibi).
- **TC kimlik no:** İmzalı formun değerleri içinde olduğu gibi (açık) saklanır — sahip kaydındaki `national_id` ile aynı düzey. İmzalı form hukuki belge olduğu için silinmez/anonimleştirilmez; KVKK kapsamında sahip silme/anonimleştirme süreci ileride eklenirse imzalı formların saklama süresi ayrıca ele alınmalı (bu projede yok).
- **Şablon sürümü:** Şablon düzenlendiğinde eski imzalı formlar etkilenmez (kopya sayesinde). Şablon silinmez, "arşivlendi" yapılır (yeni form açılamaz, eski kayıtlar durur).
- **Tam ekran imza modu:** Form doldurma ekranı menüsüz, tam ekran açılır; bitince personel "Kapat" ile döner. Kilit yok.
- **Yetkiler:** şablon oluşturma/düzenleme/arşivleme → ADMIN; form doldurtma → VET, TECHNICIAN, RECEPTIONIST, ADMIN; imzalı formu görüntüleme/PDF → aynı roller; imzalı formu iptal → VET, ADMIN.
- **AI kotası:** Görselden şablon çıkarma bir AI kullanımıdır, kliniğin aylık AI kotasından düşer (lab okuması gibi). Model bağlı değilse/kota doluysa yönetici boş bir taslakla başlayıp alanları elle ekleyebilir.
- **Kişisel veri:** TC kimlik no gibi veriler mevcut sahip kaydı düzeniyle aynı şekilde tutulur; yeni bir KVKK veri kategorisi eklenmez.

## 6. Şablon modeli

Bir şablon: `başlık`, sıralı **bölümler**; her bölüm sıralı **öğeler** içerir. Öğe türleri:

| Tür | Açıklama | Örnek |
|---|---|---|
| `TEXT_BLOCK` | Okunacak paragraf (doldurulmaz). İçinde satır içi boşluk olabilir: `{{alanKodu}}` yer tutucusu, aynı bölümdeki bir alana bağlanır. | Onay paragrafları; "………… muayenehanesi" |
| `SHORT_TEXT` | Tek satır metin | Adresi, Rengi |
| `LONG_TEXT` | Çok satır metin | Adres |
| `DATE` | Tarih | Giriş tarihi, Doğum tarihi |
| `PHONE` | Telefon | Cep telefonu |
| `NUMBER` | Sayı (birimli olabilir) | C.A (Kg) |
| `CHOICE` | Seçenekler, tek veya çok seçimli | Kedi/Köpek/Kuş/Diğer; Dişi/Erkek |
| `SIGNATURE` | İmza alanı (şablonda en az bir tane zorunlu) | İmza |

Her doldurulabilir alan: `kod` (şablon içinde benzersiz), `etiket`, `zorunlu mu`, isteğe bağlı **sistem bağlantısı** (`binding`). Bağlantı listesi sabittir:

- Sahip: `owner.fullName`, `owner.phone`, `owner.secondaryPhone`, `owner.nationalId`, `owner.birthDate`, `owner.address`, `owner.email`
- Hasta: `patient.name`, `patient.species`, `patient.breed`, `patient.sex`, `patient.age` (doğum tarihinden hesaplanır, salt okunur), `patient.color`, `patient.weightKg`, `patient.microchipNumber`
- Klinik: `clinic.name`
- Bağlam: `today` (form tarihi)

**Geri yazılabilir bağlantılar (benim kararım):** `owner.phone`, `owner.secondaryPhone`, `owner.nationalId`, `owner.birthDate`, `owner.address`, `owner.email`, `patient.color`, `patient.weightKg`, `patient.microchipNumber`. **Yalnız okunur (asla geri yazılmaz):** `owner.fullName`, `patient.name`, `patient.species`, `patient.breed`, `patient.sex`, `patient.age`, `clinic.name`, `today` — kimlik/sınıflandırma bilgileri form üzerinden değişmez, hasta/sahip ekranından düzeltilir. Geri yazmada mevcut doğrulamalar uygulanır (ör. `TcKimlikValidator`); geçersiz değer formda kalır ama kayda yazılmaz ve kullanıcıya söylenir.

**CHOICE eşleştirme (benim kararım):** `patient.species`/`patient.sex`'e bağlı bir CHOICE alanında seçenek etiketi sistemdeki değerle büyük/küçük harf ve Türkçe karakter duyarsız **tam ad** eşleşmesiyle seçili gelir (ör. "Köpek" ↔ tür "Köpek"; "Dişi" ↔ `FEMALE`). Eşleşme yoksa (ör. tür "Tavşan" ama seçenekler Kedi/Köpek/Kuş/Diğer) hiçbir seçenek işaretlenmez, personel elle seçer. "Diğer" özel anlam taşımaz. CHOICE alanları hiçbir zaman geri yazılmaz.

**İmza sayısı (benim kararım):** Şablonda **tam olarak bir** `SIGNATURE` alanı bulunur (§2 "form başına tek imza"). AI birden fazla imza yeri çıkarırsa taslakta ilki tutulur, diğerleri silinmek üzere işaretlenir.

## 7. Akış A — Şablon oluşturma (Ayarlar > Formlar)

1. Yönetici "Yeni Form" der; "📷 Fotoğraf Çek" (`capture="environment"`) veya "Görsel Yükle". Görsel `prepareImage(…, 'document')` ile hazırlanır, sunucuya yüklenir (referans olarak saklanacak).
2. Sistem görseli yapay zekâya okutur → §6 modelinde bir **taslak** döner. Taslak mavi "AI taslağı — onayınızı bekliyor" çerçevesinde, solda/üstte kâğıt formun görseli, yanında e-form önizlemesiyle gösterilir.
3. Yönetici: başlığı, metinleri, alan etiketlerini düzeltir; alan türünü değiştirir; sistem bağlantısını seçer/değiştirir; "+ Alan ekle"; alan sil; yukarı/aşağı ok; satır içi boşlukları kontrol eder. Önizleme anlık güncellenir. "PDF önizleme" de buradan alınabilir (boş form).
4. "Formu Oluştur" → şablon aktif olur. Taslak kaydedilmeden çıkılırsa uyarı ("kaydedilmemiş değişiklik").
5. Ayarlar > Formlar listesi: şablon adı, son düzenleme, imzalı form sayısı; düzenle / arşivle / referans görseli gör.

AI'dan beklenen: başlığı, bölüm başlıklarını, paragrafları (TEXT_BLOCK), etiket+boşluk çiftlerini alan olarak, kutucuk gruplarını CHOICE olarak, imza yerini SIGNATURE olarak çıkarmak; uygun görürse bağlantı önermek (ör. "Cep Telefonu" → `owner.phone`). Çıktı katı bir JSON şemasına uymalı; uymazsa bir kez yeniden denenir, yine uymazsa "okunamadı, elle oluşturun" ile boş taslak.

## 8. Akış B — Form doldurtma ve imza

1. Hasta kartında ve muayene ekranında "📝 Form Doldur" → aktif şablonlardan biri seçilir. (Muayene ekranından açılırsa form o muayeneye de bağlanır.)
2. Tam ekran form açılır: bağlantılı alanlar sistemden dolu, diğerleri boş. Personel veya müşteri doldurur. Zorunlu alanlar işaretli.
3. İmza alanının hemen üstünde **"İmzalayan"** seçimi (her formda sistemce eklenir, şablonda alan olarak tanımlanmaz): varsayılan "Hasta sahibi — <sahip adı>". "Başka biri imzalıyor" seçilirse ad soyad (zorunlu), TC no (isteğe bağlı, girilirse `TcKimlikValidator` ile doğrulanır) ve telefon (isteğe bağlı) girilir; bunlar imzalı forma `signer` bilgisi olarak kaydedilir, hiçbir kayda geri yazılmaz. "Başka biri" seçildiğinde, şablonda `owner.*` bağlantılı alanların geri yazma önerisi (adım 4) **devre dışı** kalır — o alanlara yazılanlar imzalayan kişinin bilgisi olabileceği için yalnız formda kalır.
   İmza alanı: parmak/kalemle çizilen bir tuval; "Temizle" ile yeniden atılabilir. Boş imza ile gönderilemez.
4. "Onayla ve Gönder" → kayıttan **farklı** veya kayıtta **boş** olan, geri yazılabilir bağlantılı alanlar bir özet olarak gösterilir: "Telefon: 0532… → 0555… — Kaydınız da güncellensin mi? [Evet/Hayır]" (her satır ayrı). Fark yoksa bu adım atlanır.
5. Gönderim: imzalı form kaydı oluşur (değerler, imza PNG, şablon kopyası, seçilen geri yazmalar). "Evet" denen alanlar sahibin/hastanın kaydına yazılır. Ekranda "Teşekkürler" + personel için "PDF indir" ve "Kapat".
6. İmzalı formlar hasta kartında ve sahip kartında "Formlar" bölümünde listelenir: şablon adı, tarih, imzalayan, durum (geçerli/iptal). Açınca salt okunur görünüm + "PDF olarak indir" + (yetkiliyse) "İptal et" (gerekçe zorunlu).

## 9. PDF

- OpenPDF ile, A4, istek anında: klinik adı, form başlığı, bölümler, paragraflar (satır içi boşluklar doldurulmuş değerle), alan etiket/değerleri, imza görseli, imza tarihi-saati, imzalayan ("İmzalayan: Ayşe Yılmaz (hasta sahibi)" veya "İmzalayan: Mehmet Kaya (hasta yakını), TC: …"), içerik özetinin (SHA-256) kısaltılmış hâli; iptal edilmişse belirgin "İPTAL EDİLDİ" damgası ve gerekçe.
- Boş şablon PDF'i (önizleme) de aynı renderer ile, değerler boş.
- Türkçe karakterler doğru basılmalı (mevcut `EncounterReportPdfRenderer`'daki font çözümü kullanılır).

## 10. Mimari taslak (builder ayrıntıları seçer)

- **Yeni modül** `modules/consentform` (isim builder'ın; `docs/reference-module.md` sırası): `FormTemplate` (+ referans görsel ref, durum AKTİF/ARŞİV, şablon içeriği JSON), `SignedForm` (şablon id, şablon içeriği kopyası JSON, değerler JSON, imza storage ref, patient_id, owner_id, encounter_id nullable, imzalayan türü SAHİP/BAŞKASI + başkasıysa ad/TC/telefon, imzalatan personel, imza zamanı, IP, User-Agent, içerik SHA-256 özeti, durum GEÇERLİ/İPTAL, iptal gerekçesi/zamanı/eden).
- **Sahip/hasta verisi okuma ve geri yazma** `patient` modülünün application katmanındaki port(lar) üzerinden (doğrudan entity erişimi yok — `CLAUDE.md` kuralı). Geri yazma için `patient` modülünde dar kapsamlı bir use case (yalnız §6 listesindeki yazılabilir alanlar) gerekir.
- **AI:** `ai` modülünde yeni bir port + Claude/Ollama adaptörü (lab OCR deseni: `AiCallGate`, `UsageQuotaPort`, `RecordAiJobUseCase`). Görsel `consentform` modülünden bir lookup portuyla okunur (lab'daki `LabResultFileLookupPort` deseni).
- **API** (api-conventions'a uygun, örnek): `POST /form-templates/drafts` (görsel → taslak), `POST /form-templates`, `PUT /form-templates/{id}`, `POST /form-templates/{id}/archive`, `GET /form-templates`, `GET /form-templates/{id}/reference-image`, `GET /form-templates/{id}/pdf`; `POST /signed-forms`, `GET /signed-forms?patientId=|ownerId=`, `GET /signed-forms/{id}`, `GET /signed-forms/{id}/pdf`, `POST /signed-forms/{id}/void`.
- **Frontend:** Ayarlar'a `SETTINGS_TABS` satırı ("Formlar", ADMIN); şablon düzenleyici; tam ekran doldurma/imza ekranı (imza tuvali harici kütüphane gerektirmeden `<canvas>` + pointer events ile yazılabilir); hasta/sahip kartında ve muayene ekranında giriş noktaları ve liste.
- Yeni migration'lar `V95`'ten başlar (deploy öncesi prod Flyway sürümleri kontrol edilmeli — `V94` aynı riski taşıyor).

## 11. Bırakılanlar (builder'ın kararı)

Modül/sınıf/dosya adları, JSON şemasının alan adları, imza tuvalinin çizim yumuşatması, şablon düzenleyicinin bileşen yapısı, PDF tipografisi, AI prompt metni, liste sayfalama.

## 12. Doğrulama

- Backend: use case birim testleri (şablon oluşturma doğrulamaları: en az bir SIGNATURE, benzersiz kodlar; imzalı form değiştirilemez; iptal yetkisi; geri yazmanın yalnız onaylanan alanlara uygulanması), güvenlik testleri (rol matrisi, başka kiracının şablonu/formu 404), PDF renderer testi (Türkçe karakter, iptal damgası). `ApplicationModulesTest` geçmeli.
- Frontend: Vitest — taslak düzenleyici (alan ekle/sil/sırala, bağlantı seçimi), bağlantılı alanların dolması, geri yazma özetinin yalnız farkları göstermesi, "Başka biri imzalıyor" seçiliyken geri yazma özetinin gösterilmemesi ve imzalayan adının zorunlu olması, boş imzayla gönderimin engellenmesi.
- Backend ek: "başkası imzaladı" formunda hiçbir `owner.*` alanının kayda yazılmadığını doğrulayan test; SHA-256 özetinin doğrulanması ve içerik değişince uyuşmazlık testi.
- Uçtan uca: iki örnek görselle (yukarıdaki iki dosya) şablon oluşturma; tablette (telefon/tablet tarayıcısı) doldurma + imza + PDF.
