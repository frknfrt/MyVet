# Telefondan fotoğraf: hasta profil fotoğrafı + sonuç kâğıdını çekip okutma — Tasarım

Tarih: 2026-11-06
Durum: Taslak (kullanıcı onayı bekliyor)

## 1. Amaç ve neden

Klinik personeli (hekim, asistan/teknisyen) Vetly'yi giderek telefon ve tabletten kullanıyor. Masaüstünde dosya ekleme ve "Fotoğraftan Oku" (AI ile lab değerlerini / görüntüleme bulgusunu okuma) zaten çalışıyor; ama telefonda:

- hayvanı tanımak için bir **profil fotoğrafı** yok,
- kâğıt bir kan sonucunu ya da röntgeni **çekip sisteme okutmak** üç ayrı adım (kayıt oluştur → dosya ekle → kaydı açıp dosyayı seç → "Fotoğraftan Oku") ve telefon için hiç ayarlanmamış (doğrudan kamera açılmıyor, iPhone fotoğrafları OCR'a takılıyor).

Bu iş iki bağımsız parçadan oluşur, bu sırayla yapılır:

- **Bölüm A — Hasta profil fotoğrafı** (küçük, önce)
- **Bölüm B — Sonuç kâğıdını / röntgeni telefonla çekip okutma**

Ortak altyapı (Bölüm 0): tarayıcıda fotoğrafı JPEG'e çevirip küçülten tek bir yardımcı.

## 2. Hedefler / hedef olmayanlar / anti-hedefler

**Hedefler**
- Hasta kartında ve hasta listesinde hayvanın fotoğrafı görünsün (hayvanı tanımak için).
- Telefonda tek dokunuşla arka kamera açılsın; fotoğraf kayda eklensin ve okuma **kendiliğinden** başlasın; hekim/teknisyen alanlara dolan değerleri düzeltip "Tamamla" ile kesinleştirsin.
- iPhone (HEIC) dahil telefon fotoğrafları sorunsuz çalışsın.

**Hedef olmayanlar**
- Hayvan sahiplerinin fotoğraf yüklemesi (yalnızca klinik personeli).
- Hasta başına birden fazla profil fotoğrafı / fotoğraf galerisi.
- Yeni bir AI özelliği: mevcut `ReadLabResultFromImageUseCase` / `ReadImagingFindingsFromImageUseCase` (kota, `AiCallGate`, ham görüntü uyarı metni dahil) **aynen** kullanılır.
- Hasta kartından sonuç çekme kısa yolu (kullanıcı bu seçeneği değil, Lab/Görüntüleme sayfasını seçti).
- Dosya depolamanın Postgres'ten S3/R2'ye taşınması.

**Anti-hedefler (teknik olarak çalışsa bile başarısızlık sayılır)**
- AI'ın okuduğu değerlerin hekim/teknisyen görmeden kesinleşmesi. Okuma, bugünkü gibi form alanlarına yazar (2026-10-28 spec §7 kararı: "üzerine yazar — onay istenmez"); sonucu kesinleştiren tek şey kullanıcının "Tamamla"ya basmasıdır. Otomatik okuma bu kuralı değiştirmez.
- Telefon fotoğraflarının ham hâliyle (5–8 MB) veritabanına yazılması.
- Masaüstü akışının bozulması (sürükle-bırak, "Dosya Seç", DICOM yükleme aynen çalışmalı).

## 3. Kısıtlar

- Modüler monolit + hexagonal kuralları (`CLAUDE.md`, `docs/architecture.md`, `docs/reference-module.md`). Profil fotoğrafı `patient` modülünde kalır; başka modülün domain'ine erişilmez.
- Dosyalar `platform/storage/FileStoragePort` üzerinden saklanır (şu an `PostgresFileStorageAdapter`, `stored_files` tablosu).
- Multipart limiti 15 MB (`application.yml`); değişmez.
- OCR sadece JPEG/PNG/GIF/WEBP kabul eder (`OcrImageFormatDetector`); HEIC reddedilir.
- Tasarım token'ları `tokens.css`; AI içeriği mavi, kural tabanlı uyarılar terrakota.
- Rol matrisi (`docs/api-conventions.md`): hasta yazma VET/RECEPTIONIST/ADMIN; lab/görüntüleme yazma VET/TECHNICIAN/ADMIN.
- Son migration `V93`; yeni migration `V94`'ten başlar. Prod'da başka geliştiricilerin migration'ları olabilir — deploy öncesi prod migration'ları kontrol edilmeli.
- Repo kuralı: main üzerinde çalışılır, commit atılmaz.

## 4. Bölüm 0 — Ortak: tarayıcıda fotoğraf hazırlama

Frontend'de tek bir yardımcı (örn. `frontend/src/lib/prepareImage.ts`):

- Girdi: kullanıcının seçtiği/çektiği `File`.
- Görsel değilse (PDF, DICOM, Excel…) **dokunmadan** geri döner.
- Görselse: tarayıcıda decode et, EXIF yönünü uygula, uzun kenarı en fazla N piksel olacak şekilde küçült, JPEG olarak yeniden kodla ve yeni bir `File` (`.jpg`, `image/jpeg`) döndür.
  - Profil fotoğrafı: uzun kenar 800 px, kalite ~0.85.
  - Sonuç kâğıdı / röntgen: uzun kenar 2000 px, kalite ~0.9 (rakamların okunabilmesi için daha yüksek).
- HEIC: iOS Safari `<input accept="image/*">` ile çoğunlukla zaten JPEG verir; tarayıcı HEIC'i decode edemezse yardımcı açık bir Türkçe hata verir ("Bu fotoğraf biçimi desteklenmiyor, kamera ayarlarından 'En Uyumlu' seçin veya ekran görüntüsü alın"). Yeni bir HEIC çözücü kütüphanesi **eklenmez** (builder kararı: gerekirse plan aşamasında gerekçeyle önerilebilir).
- Hem yeni hem mevcut yükleme noktaları bu yardımcıdan geçer (lab/görüntüleme yeni kayıt pencereleri ve detay pencerelerindeki "+ Dosya/Görsel Ekle").

## 5. Bölüm A — Hasta profil fotoğrafı

### Kullanıcının kararları
- Görünüm: **A seçeneği** — hasta kartındaki bugünkü yuvarlak baş harf dairesi (`PatientDetailPage.tsx`, `.avatar`, 56 px) fotoğrafla yer değiştirir; fotoğraf yoksa baş harf dairesi aynen kalır.
- Hasta listesinde (`PatientsPage.tsx`) her satırın başına küçük yuvarlak fotoğraf/baş harf dairesi eklenir (bugün listede hiç görsel yok).
- Fotoğrafa (veya köşedeki 📷 rozetine) dokununca seçenekler: **Fotoğraf Çek / Galeriden Seç / Fotoğrafı Kaldır**.

### Benim kararlarım (kullanıcı ayrıca onaylamadı)
- Fotoğraf ekleme/değiştirme/kaldırma yetkisi = hasta yazma yetkisi (VET, RECEPTIONIST, ADMIN). TECHNICIAN fotoğrafı görür, değiştiremez. Rozet ve menü yalnızca yetkisi olanlara gösterilir.
- "Fotoğraf Çek" `<input type="file" accept="image/*" capture="environment">`, "Galeriden Seç" `capture`'sız `<input accept="image/*">` ile yapılır (masaüstünde ikisi de dosya seçici açar; masaüstünde "Fotoğraf Çek" seçeneği gizlenebilir — builder'a bırakıldı).
- Hasta başına tek fotoğraf; yenisi eskisinin yerine geçer (eski `stored_files` kaydı silinir veya yetim bırakılmaz — builder temizliği seçer).

### Backend taslağı
- `V94`: `patients` tablosuna `photo_storage_ref` (nullable) kolonu.
- `patient` modülünde: `UploadPatientPhotoUseCase`, `RemovePatientPhotoUseCase`, fotoğrafı getiren bir okuma yolu.
- Endpoint'ler (api-conventions'a uygun):
  - `PUT /api/v1/patients/{id}/photo` (multipart, `file`) — yalnızca `image/jpeg|png|webp`; boyut sunucuda da sınırlanır (örn. 2 MB).
  - `DELETE /api/v1/patients/{id}/photo`
  - `GET /api/v1/patients/{id}/photo` — görsel baytları; kiracı filtresi otomatik; cache başlığı (fotoğraf değişince URL değişsin diye `?v=` sürümü veya ETag).
- `PatientProfile` ve hasta arama sonucu DTO'larına `hasPhoto` (veya `photoVersion`) alanı eklenir; frontend URL'yi buradan kurar. Liste için ayrı küçük resim üretmek gerekmez (800 px zaten küçük; builder gerekirse 128 px küçük kopya da saklayabilir).
- Sunucu yüklenen görselin gerçekten görsel olduğunu baytlardan doğrular (mevcut `OcrImageFormatDetector` mantığına benzer bir kontrol; `ai` modülüne bağımlılık kurmadan).

### Frontend taslağı
- Ortak bir `PatientAvatar` bileşeni (boyut prop'u: kart 64 px, liste ~36 px): fotoğraf varsa `<img>` (oturum çereziyle yüklenir), yoksa bugünkü `colorFor`/`initialsOf` dairesi.
- Hasta kartında rozet + seçenek menüsü (telefonda alttan açılan liste, masaüstünde küçük açılır menü).
- Yükleme sırasında dairede yükleniyor göstergesi; hata `ApiError` mesajıyla gösterilir.

## 6. Bölüm B — Sonuç kâğıdını / röntgeni çekip okutma

### Kullanıcının kararları
- Başlangıç noktası: **B seçeneği** — bugünkü Laboratuvar ve Görüntüleme sayfalarındaki "Yeni Sonuç/Kayıt" pencereleri (`NewLabResultModal.tsx`, `NewImagingRecordModal.tsx`). Hasta kartına kısa yol **eklenmez**.
- Pencereye "📷 Fotoğraf Çek" butonu eklenir; "Dosya Seç" yerinde kalır.
- Fotoğraf çekilip kayıt oluşturulunca okuma **kendiliğinden** başlar; değerler/bulgu alanlara taslak olarak dolar; kullanıcı düzeltip onaylar.
- Lab'da kâğıttaki değerler okunur. Görüntülemede okunan şey **röntgen/USG görüntüsünün kendisidir** (mevcut ham görüntü yorumlama + sabit uyarı metni, 2026-10-31 revizyonu); rapor kâğıdı da çekilirse o da okunur.
- Birden fazla sayfa çekilebilir ("+ Sayfa Ekle").

### Akış (her iki sayfa için aynı)
1. Kullanıcı Yeni Sonuç penceresini açar, sonuç türünü (lab) / modaliteyi + bölgeyi (görüntüleme) ve sahip + hastayı seçer — bugünkü alanlar değişmez.
2. "📷 Fotoğraf Çek" → `<input accept="image/*" capture="environment">` arka kamerayı açar. Çekilen her fotoğraf Bölüm 0 yardımcısından geçer ve bugünkü dosya listesine eklenir (önizleme küçük resmiyle). "+ Sayfa Ekle" aynı butonu tekrar tetikler.
3. "Kaydet": bugünkü gibi kayıt oluşturulur ve dosyalar yüklenir (`NewLabResultModal.tsx:122` döngüsü).
4. Bugün `onCreated(id)` detay penceresini açıyor (`LaboratoryPage.tsx:218`, `ImagingPage.tsx:234`). Yeni davranış: pencere **kamerayla çekilen** dosyaların id'leriyle birlikte açılır, bu dosyalar seçili gelir ve detay penceresi açılır açılmaz mevcut `handleOcr()` kendiliğinden çalışır ("Okunuyor..." göstergesi).
   - Otomatik okuma yalnızca kamerayla çekilen (veya görsel olan) dosyalar için tetiklenir; "Dosya Seç" ile PDF/DICOM eklendiyse tetiklenmez — bugünkü manuel "📷 Fotoğraftan Oku" butonu yerinde kalır.
   - Bunun için yüklenen dosyaların id'leri `onCreated`'a taşınır (örn. `onCreated(id, { autoOcrFileIds })`). `POST /{id}/files` bugün gövdesiz `201 Created` döner ama dosya id'si `Location` başlığında var (`/api/v1/lab-results/files/{fileId}`, görüntülemede aynı desen). Frontend id'yi bu başlıktan okur; bunun için `apiClient.postMultipart` yanıt başlığını da verecek şekilde genişletilebilir. Başlık okunamıyorsa (CORS vb.) yedek çözüm: uç noktaya `{ id }` gövdesi eklemek — geriye uyumlu, mevcut çağıranlar gövdeyi zaten yok sayıyor. (Benim kararım.)
5. Okuma sonucu **bugünkü gibi doğrudan form alanlarına yazılır** (lab: değer + referans aralığı + bayrak; görüntüleme: bulgu metni, başında sabit uyarı); ayrı bir önizleme/onay kutusu **yapılmaz** (kullanıcı kararı, 2026-11-06). Yeni kayıtta alanlar boş olduğundan üzerine yazma sorunu yoktur. Kullanıcı değerleri kontrol edip düzeltir ve bugünkü "Tamamla" ile kesinleştirir.
6. Hata/kota/AI kapalı durumları bugünkü mesajlarla gösterilir (örn. "[AI modeli fotoğrafı okuyamadı — değerleri elle girin.]"); kayıt ve fotoğraf yine de kaydedilmiş olur.

### Benim kararlarım
- Detay pencerelerindeki "+ Dosya Ekle" / "+ Görsel Ekle" butonlarına da telefonda kamera seçeneği gelir (`accept="image/*"` ile tarayıcının kendi "Fotoğraf Çek / Arşiv / Dosyalar" menüsü yeterli; ayrıca Bölüm 0'dan geçer). Bu butonlardan eklenen fotoğraf için okuma **otomatik değil**, mevcut buton ile.
- Lab yeni kayıt penceresinde `accept` sınırı yok (lab PDF'leri de eklenebiliyor); bu korunur, sadece görsel dosyalar dönüştürülür.
- Backend'de değişiklik beklenmiyor (uç noktalar ve OCR hazır); en fazla upload yanıtına geriye uyumlu `{ id }` gövdesi (bkz. adım 4).

### Kullanıcının yetki kararı
- Onay yetkisi bugünkü gibi kalır: lab/görüntüleme "Tamamla" VET, TECHNICIAN, ADMIN (kullanıcı kararı, 2026-11-06). Yetki değişikliği yapılmaz.

## 7. Telefon/tablet görünümü

- Lab ve Görüntüleme sayfaları, yeni kayıt ve detay pencereleri, hasta listesi ve hasta kartı 375 px (telefon) ve 768 px (tablet) genişlikte kullanılabilir olmalı: pencereler telefonda tam ekran, butonlar parmakla basılabilir (≥44 px), yatay kaydırma yok.
- Mevcut 880 px kırılımı (`AppShell`, `Sidebar`) temel alınır; yeni kırılım eklemek builder'a kalmış.

## 8. Builder'a kalanlar

- Bileşen/dosya isimleri, küçültme parametrelerinin ince ayarı, liste için ayrı küçük resim gerekip gerekmediği, masaüstünde "Fotoğraf Çek" seçeneğinin gizlenmesi, `?v=` mi ETag mi.
- Eski profil fotoğrafının temizlenmesi: `FileStoragePort`'ta bugün silme metodu yok; eklenmesi (Postgres adaptöründe `stored_files` satırını silmek) builder'a kalmış.
- Fotoğraflar `<img>` ile oturum çereziyle (`vetly_session`, HttpOnly) yüklenir; API ile frontend aynı sitede olduğu sürece ek iş gerekmez.

## 9. Doğrulama

- Backend: `UploadPatientPhotoUseCase` için birim test (mevcut `UploadImagingRecordFileUseCaseTest` deseni), uç noktalar için güvenlik testi (TECHNICIAN fotoğraf yükleyemez, başka kiracının hastasının fotoğrafı okunamaz). `ApplicationModulesTest` geçmeli.
- Frontend: `npm run build`; `prepareImage` için birim test (görsel olmayan dosya dokunulmadan döner, büyük görsel küçülür, çıktı JPEG). Otomatik okuma tetiklemesi için `LabResultDetailModal.test.tsx` / `ImagingRecordDetailModal.test.tsx` desenine test.
- Uçtan uca: çalışan backend'e karşı curl (giriş → hasta → fotoğraf yükle/getir/sil); gerçek bir iPhone ve Android telefonda: profil fotoğrafı çekme, kâğıt hemogram çekip okutma, röntgen ekranı/filmi çekip okutma.
