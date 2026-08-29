# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Ne inşa ediyoruz
Türkiye pazarına özel, AI destekli veteriner klinik yönetim SaaS'ı. Spring Boot (backend) + React/TypeScript (frontend). Detaylar: @docs/requirements.md

## Komutlar

```
# Backend (Java 21, Maven)
cd backend && docker-compose up -d        # Postgres, host port 5433 (5432 lokal bir Windows Postgres'le çakışabiliyor)
cd backend && ./mvnw spring-boot:run      # http://localhost:8080
cd backend && ./mvnw test                 # tüm testler
cd backend && ./mvnw test -Dtest=ApplicationModulesTest   # tek sınıf çalıştır

# Frontend
cd frontend && npm install
cd frontend && npm run dev      # http://localhost:5173 (Vite)
cd frontend && npm run build    # tsc -b && vite build — tip kontrolü build'e dahil, ayrı bir lint/test script'i yok
```

**Test kapsamı gerçeği:** Backend'de şu an tek otomatik test var: `ApplicationModulesTest` (Spring Modulith `ApplicationModules.verify()` — modül sınırı ihlali olursa build kırılır). Use-case/repository seviyesinde birim/entegrasyon testi yok. Yeni bir özellik bittiğinde doğrulama şekli: backend için çalışan bir instance'a karşı curl ile uçtan uca (platform-admin girişi → klinik oluşturma → login → ilgili endpoint), frontend için `npm run build` (tip hatası yakalar) + mümkünse tarayıcıda gerçek kullanım. Bu, geçmiş commit'lerin de izlediği yöntem — yeni kod eklerken de aynı şekilde doğrula, "testler geçti" varsayımına güvenme.

## Mimari (ZORUNLU KURAL)
Modüler monolit + hexagonal mimari (ports & adapters). Disk üzerindeki gerçek backend modülleri (`backend/src/main/java/com/vetos/modules/`): `tenant`, `patient`, `appointment`, `encounter`, `billing`, `inventory`, `lab`, `imaging`, `boarding`, `ai`, `notification`, `integration/tarbil`, `integration/efatura`, `platformadmin`. Her modül kendi `domain/`, `application/`, `infrastructure/`, `api/` paketine sahip.

**Bir modül başka bir modülün `domain` sınıfına (entity/repository) DOĞRUDAN erişemez.** Sadece:
- Diğer modülün `application` katmanındaki port arayüzü üzerinden, veya
- Domain event dinleyerek (Spring `@EventListener`)

**Bilinen tek istisna:** `modules/tenant/domain/TenantAdminPort.java` — normal `*LookupPort`'lardan farklı olarak yazma da içerir, sadece `modules.platformadmin` kullanır (platform admin'in herhangi bir kiracının aboneliğini/durumunu değiştirebilmesi için). Yeni bir modül için asla bu deseni kopyalama.

`platformadmin` modülü ayrıca mimari olarak tamamen paralel bir auth yığınına sahip: normal `JwtTokenProvider`/`AuthenticatedStaffUser`/`JwtAuthenticationFilter`/`TenantContext` dörtlüsü her zaman bir `tenantId` varsayar, platform admin ise hiçbir kiracıya ait değildir. Bu yüzden `platform/security/`'de `PlatformAdminJwtTokenProvider`/`PlatformAdminAuthenticationFilter`/`PlatformAdminSecurityConfig` (`@Order(1)`, `/api/v1/platform-admin/**`'e izole ikinci bir `SecurityFilterChain`) mevcut — mevcut `SecurityConfig`/`JwtTokenProvider` hiç değiştirilmedi. Frontend'de de aynı paralellik var: `src/auth/*` (klinik personeli) ile `src/platformAdmin/*` (SaaS operatörü) birbirinden bağımsız iki ayrı auth context + router (`App.tsx` klinik tarafını, `PlatformAdminApp.tsx` kendi alt-rotalarını yönetir).

Detaylı gerekçe ve kod örnekleri: @docs/architecture.md

## Yeni bir modül yazarken (ZORUNLU SIRA)
1. @docs/reference-module.md dosyasını aç — `patient` modülünün 13 adımlık TAM kodunu içerir.
2. Aynı katman sırasını, aynı isimlendirmeyi (@docs/coding-conventions.md) birebir uygula.
3. Controller/DTO/hata formatı için @docs/api-conventions.md'deki kuralları uygula.
4. Rol/yetki kontrolü için @docs/api-conventions.md'deki matrise bak.

Bu sıra atlanmaz — modüller arası tutarlılığın tek garantisi bu.

## Veri modeli
ER diyagramı ve tüm entity'ler: @docs/er-diagram.mermaid
Multi-tenant: her tablo `tenant_id`/`branch_id` taşır (satır bazlı izolasyon), `TenantContext` (ThreadLocal) üzerinden JWT'den çözülüp otomatik filtre olarak eklenir. İstisna: `DrugCatalog` gibi birkaç referans/katalog tablosu kiracı-bağımsız (global) tutulur — yeni bir entity için bunu varsayma, aksi belirtilmedikçe tenant-scoped olması beklenir.

## Frontend kuralları
- Router tek dosyada: `frontend/src/App.tsx` (`RequireAuth` ile korunan rotalar). Sayfalar `src/pages/<özellik>/` altında, her modülün genelde kendi `src/api/<özellik>Api.ts` istemcisi var (bazı yakın modüller `clinicalApi.ts` gibi paylaşılan bir dosyayı kullanır), hepsi `src/api/client.ts`'teki tek `apiClient` (get/post/put/delete/postForId/postMultipart + `ApiError`) üzerinden gider.
- Tasarım token'ları TEK kaynak: `frontend/src/styles/tokens.css` — bileşenlerde ham hex değeri YAZILMAZ, her zaman `var(--color-*)` kullanılır.
- Sidebar navigasyonu TEK yerde tanımlı: `frontend/src/components/layout/navConfig.tsx`. Yeni sayfa eklerken SADECE buraya satır eklenir, sidebar hiçbir sayfada kopyalanmaz.
- `Ayarlar` sayfası (`src/pages/settings/SettingsPage.tsx`) aynı "tek kaynak" desenini kendi içinde tekrarlar: `SETTINGS_TABS` dizisine bir satır eklemek hem sekme çubuğunu hem `<Routes>` girdisini oluşturur — yeni bir ayar ekranı eklerken bu dosyanın dışında hiçbir yeri değiştirme.
- Buton varyantları: `primary` (sayfada en fazla 1 tane), `secondary`, `tertiary`, `danger`, `ai`. Kural detayı: @docs/design-system.md
- AI üretimi içerik her zaman mavi (`--color-ai-600`) ailesinde gösterilir ve "hekim onayı bekliyor" ilkesiyle sunulur — asla sessizce otomatik uygulanmaz. Kural tabanlı (AI olmayan) risk/durum bildirimleri (örn. düşük stok, ilaç etkileşim uyarısı) bu maviyi KULLANMAZ, `warning` (rust/terrakota) tonunu kullanır — iki renk ailesi birbirine karışmaz.

## Şu anki faz
Faz 1 (MVP) tamamlandı, Faz 2 sürüyor. Yapılanların ve bilinen boşlukların güncel dökümü: @docs/implementation-plan.md dosyasının sonundaki "Faz 1 Kapsam Dışı" bölümü (her Faz 2 turu burada gerekçesiyle birlikte kayıtlı). Ekran bazlı P0/P1/P2/P3 önceliklendirmesi ve hangi ekranın tamamlanıp tamamlanmadığı: @docs/screen-priorities.md.

## Sağlayıcı entegrasyonları (port/adapter deseni)
Gerçek üçüncü taraf hesabı/API anahtarı olmayan her entegrasyon (TARBİL, e-Fatura, AI SOAP üretimi, bildirim gönderimi) aynı desenle çözülüyor: `domain`'de bir port arayüzü + `infrastructure/adapter`'da tek bir gerçek/mock karma adaptör. Kimlik bilgisi ortam değişkeninden boşsa adaptör simüle davranışa düşer, hata fırlatmaz — bu sayede kimlik bilgisi olmayan ortamlarda (CI, yeni bir geliştirici makinesi) uygulama sorunsuz çalışmaya devam eder. Örnek: `modules/notification/infrastructure/adapter/TwilioNotificationAdapter.java` — WhatsApp kanalı `TWILIO_ACCOUNT_SID`/`TWILIO_AUTH_TOKEN` tanımlıysa gerçek Twilio API'sine gider, SMS kanalı ve kimlik bilgisi eksikse ikisi de simüle edilir. Yeni bir sağlayıcı eklerken önceki mock'u silip yerine aynı deseni izleyen tek bir adaptör yaz — port arayüzünü ve onu çağıran kodu değiştirme.

## Konvansiyon dışı bir durumla karşılaşırsan
`coding-conventions.md`, `reference-module.md` veya `api-conventions.md`'de karşılığı olmayan bir karar vermen gerekiyorsa (örn. yeni bir senaryo, öngörülmemiş bir kenar durum), özellikle gerçek bir farmakolojik/tıbbi/finansal veri uydurmayı gerektiren durumlarda:
1. Önce bana sor, kendi başına karar verip ilerleme.
2. Karar netleşince, ilgili doküman dosyasına **kısa bir madde olarak ekle** (mevcut formatı koru, kod bloğu şişirme).
3. Aynı kararı bir daha sormama gerek kalmasın diye bunu değişiklik özetinde belirt.

Bu üç dosya, proje ilerledikçe güncellenen canlı kaynaklardır — statik/bitmiş belgeler değildir.

## Yapma
- CLAUDE.md dosyasına kod bloğu / uzun açıklama EKLEME — yeni bilgi `docs/` altına gider.
- Yeni bir domain modülü oluştururken `docs/architecture.md`'deki port/adapter desenini atlama.
- Sidebar, buton veya badge rengini `docs/design-system.md` ile çelişecek şekilde değiştirme.
- Gerçek bir sağlayıcı/referans veri kaynağı yokken (ilaç etkileşimi, tıbbi tanı önerisi, ödeme vb.) veri uydurma — kullanıcıya sor veya veri girişini kullanıcıya/kliniğe bırak.
