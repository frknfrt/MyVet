# Faz 7 — API sözleşmesi ve versiyonlama (tasarım)

**Tarih:** 2026-10-18
**Yol haritası:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` §Faz 7
**Durum:** Kullanıcıyla bölüm bölüm onaylandı; commit yok (IntelliJ changelist'te kalır).

## 0. Amaç ve kapsam

Vetly'nin API'sini, sunucuyla birlikte yayınlanmayan tek istemci olan Chrome eklentisini bozmadan değiştirebilmek.

**Kullanıcı kararları:**
- **Kapsam A:** eklenti sürüm kontrolü, yazılı versiyonlama kuralları ve yalnızca geliştirmede açık OpenAPI sayfası.
- **Dağıtım B:** eklenti Vetly web uygulamasından (Ayarlar > Entegrasyonlar, TARBİL kartı) indirilir.

**Kapsam dışı (bilerek):**
- Web uygulaması ile backend arasında sürüm kontrolü yapılmaz, çünkü ikisi her zaman birlikte yayınlanır.
- Adreslere tek tek OpenAPI açıklaması (anotasyon) eklenmez.
- `Deprecation`/`Sunset` başlıkları koda eklenmez. Bugün kalkacak bir adres olmadığı için yalnızca yazılı kural olarak kalırlar.
- `/api/v2` açılmaz.

**Başarı ölçütü:**
- Sunucuda en düşük eklenti sürümü yükseltildiğinde eski eklenti anlaşılır bir "güncelleyin" mesajı alır.
- Klinik yeni sürümü Vetly'den indirip eşleştirmeyi kaybetmeden kurar.
- Canlıda API dokümantasyon sayfası açılmaz.

## 1. Backend: eklenti sürüm kontrolü

- **Kapsam:** Kontrol yalnızca `/api/v1/tarbil-extension/**` altındaki isteklere uygulanır, `/pair` dahil. Diğer adresler etkilenmez.
- **Başlık:** Eklenti her isteğe `X-Client-Version: x.y.z` başlığını ekler.
- **Ayar:** `vetly.extension.min-version` değeri `application.yml` dosyasındaki `vetly:` bloğunda tanımlanır. Ortam değişkeni `VETLY_EXTENSION_MIN_VERSION`, varsayılan `0.1.0`.
- **Karşılaştırma:**
  - Sürümler `x.y.z` biçiminde, sayısal olarak karşılaştırılır. Örneğin `0.10.0` > `0.9.0`.
  - Başlık yoksa istek `0.1.0` sayılır. Bugünkü eklentiler başlık göndermiyor, varsayılan ayarla çalışmaya devam ederler.
  - Başlık bozuksa istek en eski sürüm sayılır. Bozuk demek: üç sayılı biçimde değil, ya da negatif. Böyle bir istek, en düşük sürüm `0.0.0`'dan büyük olduğu sürece reddedilir.
  - Ayardaki değerin kendisi bozuksa uygulama açılışta hata verip başlamaz.
- **Ret cevabı:**
  - Durum kodu HTTP **426 Upgrade Required**.
  - Gövde standart `ErrorResponse` biçimindedir:
    - `errorCode`: `CLIENT_UPGRADE_REQUIRED`
    - `message`: "Vetly TARBİL eklentinizin sürümü artık desteklenmiyor. Vetly'de Ayarlar > Entegrasyonlar bölümünden yeni sürümü indirin."
    - `path`, `requestId` her hatada olduğu gibi doldurulur.
  - En düşük sürüm `X-Min-Client-Version` cevap başlığında bildirilir. `ErrorResponse`'a yeni alan eklenmez.
- **Yer:**
  - Kontrol `platform/security` altında yeni bir `ClientVersionFilter` filtresidir. Karşılaştırma mantığı ayrı, saf bir `ClientVersion` sınıfında durur. Bu sınıf sürüm metnini ayrıştırır ve `Comparable` arayüzünü uygular.
  - Filtre `TarbilExtensionSecurityConfig` zincirine `RateLimitFilter`'dan sonra, `ExtensionTokenAuthenticationFilter`'dan önce eklenir. Böylece `requestId` hazır olur, `/pair` hız sınırı korunur, eski eklenti token doğrulamasına hiç ulaşmaz.
  - Gövdeyi `JsonAuthenticationEntryPoint`'in kullandığı yolla, aynı `ObjectMapper` ile JSON olarak yazar.
- **CORS:** Eklentinin arka plan betiği (service worker) `host_permissions` sayesinde CORS'a takılmaz. Bu zincirde CORS zaten kapalı, yeni başlık için ek ayar gerekmez.
- **Bilinen sınır:** Bugünkü 0.1.0 eklenti 426 kodunu tanımadığı için ret anında her listelemede "Beklenmeyen yanıt: 426" gösterir. Bu yalnızca en düşük sürüm 0.1.0'ın üstüne çıkarıldığında olur. Ayarlar ekranındaki "Yeni sürüm var" uyarısı (§3) bunu önceden haber verir.
- **Testler:**
  - `ClientVersion` birim testleri: ayrıştırma, sayısal sıralama, bozuk girdi.
  - Filtre testleri:
    - eski sürüm → 426 ve gövde/başlık;
    - eşit ya da yeni sürüm → geçer;
    - başlık yok → 0.1.0 sayılır;
    - bozuk başlık → 426;
    - zincir dışındaki adresler etkilenmez;
    - `/pair` da kontrol edilir.

## 2. Eklenti tarafı (sürüm 0.2.0)

- **Tek sürüm kaynağı:**
  - `router.ts` içindeki sabit `VERSION = '0.1.0'` kaldırılır.
  - Sürüm derleme sırasında `package.json`'dan gelir. `src/shared/version.ts` dosyası `import { version } from '../../package.json'` ile okur ve `EXTENSION_VERSION` olarak dışa verir. Vite yalnızca bu alanı pakete koyar. Tsconfig'e `resolveJsonModule` eklenir.
  - Bir vitest testi `public/manifest.json` ile `package.json` sürümlerinin aynı olduğunu doğrular.
  - Sürüm **0.2.0** olur: `package.json`, `manifest.json`.
- **Başlık:** `vetlyApi.ts` içindeki `request()` her isteğe `X-Client-Version: <sürüm>` başlığını ekler.
- **426 eşlemesi:**
  - `messages.ts` dosyasındaki `ErrorCode` türüne `'UPGRADE_REQUIRED'` eklenir.
  - 426 cevabında mesaj gövdedeki `message` alanından alınır. Gövde okunamazsa §1'deki metin kullanılır.
  - Token **silinmez**. Bu 401'den farklıdır: güncelleme sonrası yeniden eşleştirme gerekmez.
- **Yan panel:** `UPGRADE_REQUIRED` geldiğinde eşleştirme ekranında da bekleyen işler listesinde de kalıcı bir uyarı kutusu mesajı gösterir. Kullanıcı kapatamaz, yeniden deneme bir sonraki istekte yapılır.
- **PING:** Cevaptaki `version` gerçek sürümdür. Web uygulaması bunu §3'te kullanır.
- **Paketleme:**
  - Yeni `npm run package` betiği önce `build` çalıştırır, ardından `dist/` içeriğini `frontend/public/downloads/vetly-tarbil-eklenti.zip` dosyasına sıkıştırır.
  - Yanına `frontend/public/downloads/vetly-tarbil-eklenti.json` (`{"version":"0.2.0"}`) yazar.
  - Betik Windows'ta da çalışır: Node ile yazılır, kabuk komutlarına bağlı değildir.
- **Testler:**
  - `vetlyApi.test.ts`: başlık gönderiliyor; 426 → `UPGRADE_REQUIRED` ve gövde mesajı; 426'da token korunuyor.
  - `router.test.ts`: PING gerçek sürümü döndürüyor.
  - Sürüm eşitliği testi.
  - Yan panelde uyarı görünümü için bileşen testi, mevcut test düzeni elveriyorsa.

## 3. Web uygulaması: indirme (Ayarlar > Entegrasyonlar)

- **İndirme düğmesi:** `TarbilExtensionCard.tsx` kartına `/downloads/vetly-tarbil-eklenti.zip` dosyasını indiren bir "Eklentiyi indir" düğmesi eklenir.
- **Sürüm satırı:** Kart açılınca iki bilgi okunur:
  - en yeni sürüm: `/downloads/vetly-tarbil-eklenti.json`;
  - yüklü sürüm: `lib/tarbilExtension.ts` üzerinden PING.

  Kartta tek satır görünür:
  - Eklenti yüklü değil: "Eklenti yüklü değil. İndirip kurun."
  - Yüklü ve güncel ya da daha yeni: "Yüklü sürüm: 0.2.0 (güncel)"
  - Yüklü ama eski: "Yüklü sürüm: 0.1.0. Yeni sürüm var (0.2.0), indirip güncelleyin." Uyarı renginde gösterilir.
  - Json okunamazsa indirme düğmesinin yerinde "Eklenti dosyası şu an hazır değil" yazar ve sürüm karşılaştırması yapılmaz.
- **Sürüm karşılaştırma:** Frontend'de küçük, saf bir `compareVersions` yardımcısı bulunur. Kuralı §1 ile aynıdır: sayısal x.y.z.
- **Talimat:** Kartta açılır-kapanır bir kurulum/güncelleme talimatı olur.
  - Kurulum: zip'i bir klasöre açın, `chrome://extensions` sayfasına gidin, "Geliştirici modu"nu açın, "Paketlenmemiş öğe yükle" ile klasörü seçin.
  - Güncelleme: eski klasörün içeriğini yenisiyle değiştirin, eklentiler sayfasında "Yenile" düğmesine basın. Eklenti kimliği değişmez, eşleştirme korunur.
- **Git:** `frontend/public/downloads/` klasörü `.gitignore`'a eklenir, çünkü içindekiler derleme çıktısıdır.
- **Erişim:** Dosya giriş gerektirmeyen sabit bir dosyadır. Eklenti kodu gizli değildir; koruma eşleştirme kodundadır.
- **Dağıtım dokümanı:** `docs/deployment.md` dosyasına bir "## Eklenti dağıtımı (Faz 7)" bölümü eklenir:
  - Frontend derlenmeden önce `extension/` içinde `npm ci && npm run package` çalıştırılır.
  - Anahtar (`key.pem`) gereksizdir. `manifest.json` içindeki `key` alanı kimliği sabit tutar; alanın var olduğu doğrulandı.
- **Testler:**
  - Web uygulamasında birim testi altyapısı yok; `vitest` + `jsdom` eklenir (eklentideki sürümlerle aynı) ve `npm test` betiği tanımlanır.
  - `compareVersions` birim testi.
  - `TarbilExtensionCard` için dört durumun bileşen testi: yüklü değil, güncel, eski, json yok.

## 4. Kurallar dokümanı ve yalnızca geliştirmede OpenAPI

### 4.1 `docs/api-conventions.md` — "Versiyonlama" bölümü

Mevcut tek paragrafın yerini şu alt başlıklar alır:
- **Kırıcı değişiklik:** bir alanı silmek ya da adını değiştirmek, alanın tipini değiştirmek, zorunlu yeni bir girdi eklemek, bir adresi kaldırmak, durum kodlarının ya da hata kodlarının anlamını değiştirmek.
- **Kırıcı olmayan değişiklik:** yeni bir alan, isteğe bağlı yeni bir girdi ya da yeni bir adres eklemek. Sürüm gerektirmez. İstemciler tanımadıkları alanları görmezden gelmek zorundadır.
- **v2 kuralı:** Kırıcı değişiklik gerekirse yalnızca etkilenen adresler için `/api/v2/...` açılır. `v1` en az 6 ay daha çalışır.
- **Kullanımdan kaldırma:** Kalkacak bir adres cevaplarında `Deprecation` başlığını ve `Sunset: <tarih>` başlığını (kapanış tarihi) taşır. Bugün uygulanmıyor, kural olarak duruyor.
- **Eklenti zorunlu güncelleme:** `X-Client-Version`, `VETLY_EXTENSION_MIN_VERSION` ve 426/`CLIENT_UPGRADE_REQUIRED` açıklanır. En düşük sürümü yükseltme kontrol listesi:
  1. Yeni sürümü `npm run package` ile paketleyip web uygulamasını yayınlayın.
  2. Klinikler güncellesin diye bekleyin. Ayarlar ekranındaki "Yeni sürüm var" uyarısı onları yönlendirir.
  3. Ancak bundan sonra `VETLY_EXTENSION_MIN_VERSION` değerini yükseltip backend'i yeniden başlatın.
- Yetki matrisindeki `/tarbil-extension/**` satırına bir not düşülür: "sürüm kontrolü: bkz. Versiyonlama".

### 4.2 OpenAPI sayfası

- **Bağımlılık:** `pom.xml` dosyasına `org.springdoc:springdoc-openapi-starter-webmvc-ui` eklenir. Sürüm **2.8.17** (Boot 3.5.13 üzerine kurulu; 3.x serisi Boot 4 içindir).
- **Adresler:** `/v3/api-docs` (JSON) ve `/swagger-ui.html`. Anotasyon eklenmez, içerik koddan otomatik çıkar.
- **Canlı:** `application-prod.yml` dosyasında `springdoc.api-docs.enabled: false` ve `springdoc.swagger-ui.enabled: false` ayarlanır.
- **Güvenlik:**
  - `SecurityConfig` ana zincirinde `/v3/api-docs/**`, `/swagger-ui/**` ve `/swagger-ui.html` için serbest geçiş izni yalnızca `springdoc.api-docs.enabled` değeri `true` iken eklenir. Varsayılan `true`, canlıda `false`.
  - Böylece canlıda bu adresler hem üretilmez hem de kimliksiz isteğe kapalı kalır. Kapı kapanır ve kilitlenir.
- **Testler:**
  - Varsayılan profil: `/v3/api-docs` kimliksiz istekte 200 döner ve gövdesi `"openapi"` içerir.
  - Canlı ayarları, yani iki springdoc bayrağı `false` iken: `/v3/api-docs` ve `/swagger-ui.html` 200 dönmez.
  - Modulith yapı testi geçmeye devam eder.

## 5. Dokümantasyon ve yol haritası

- `docs/api-conventions.md` (§4.1) ve `docs/deployment.md` (§3) güncellenir.
- `extension/README.md` dosyasının kurulum bölümü Vetly'den indirme yoluna göre güncellenir. `npm run package` komutu da anlatılır.
- Yol haritasında Faz 7 ✅ ile işaretlenir, sıradaki faz belirtilir.

## 6. Riskler

| Risk | Önlem |
|---|---|
| En düşük sürüm erken yükseltilir, klinikler TARBİL aktarımı yapamaz | Kontrol listesi (§4.1); varsayılan 0.1.0; yükseltme yalnızca ortam değişkeni ve yeniden başlatma ile |
| `npm run package` unutulur, indirme 404 olur | Kart json bulunamazsa "hazır değil" der; adım dağıtım dokümanında |
| Güncellemede eklenti kimliği değişir, web uygulaması eklentiye ulaşamaz | `manifest.json` `key` alanı kimliği sabitler (mevcut) |
| springdoc sürümü Boot 3.5 ile uyumsuz çıkar | Sürüm planda doğrulanıp sabitlenir; uygulama açılış testleri yakalar |
