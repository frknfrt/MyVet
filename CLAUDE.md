# MyVet — Proje Talimatları

## Ne inşa ediyoruz
Türkiye pazarına özel, AI destekli veteriner klinik yönetim SaaS'ı. Spring Boot (backend) + React/TypeScript (frontend). Detaylar: @docs/requirements.md

## Mimari (ZORUNLU KURAL)
Modüler monolit + hexagonal mimari. Her modül (`patient`, `appointment`, `encounter`, `billing`, `inventory`, `ai`, `notification`, `integration/tarbil`) kendi `domain/`, `application/`, `infrastructure/`, `api/` paketine sahip.

**Bir modül başka bir modülün `domain` sınıfına (entity/repository) DOĞRUDAN erişemez.** Sadece:
- Diğer modülün `application` katmanındaki port arayüzü üzerinden, veya
- Domain event dinleyerek (Spring `@EventListener`)

Detaylı gerekçe ve kod örnekleri: @docs/architecture.md

## Yeni bir modül yazarken (ZORUNLU SIRA)
1. @docs/reference-module.md dosyasını aç — `patient` modülünün 13 adımlık TAM kodunu içerir.
2. Aynı katman sırasını, aynı isimlendirmeyi (@docs/coding-conventions.md) birebir uygula.
3. Controller/DTO/hata formatı için @docs/api-conventions.md'deki kuralları uygula.
4. Rol/yetki kontrolü için @docs/api-conventions.md'deki matrise bak.

Bu sıra atlanmaz — modüller arası tutarlılığın tek garantisi bu.

## Veri modeli
ER diyagramı ve tüm entity'ler: @docs/er-diagram.mermaid
Multi-tenant: her tablo `tenant_id`/`branch_id` taşır (satır bazlı izolasyon).

## Frontend kuralları
- Tasarım token'ları TEK kaynak: `frontend/src/styles/tokens.css` — bileşenlerde ham hex değeri YAZILMAZ, her zaman `var(--color-*)` kullanılır.
- Sidebar navigasyonu TEK yerde tanımlı: `frontend/src/components/layout/navConfig.tsx`. Yeni sayfa eklerken SADECE buraya satır eklenir, sidebar hiçbir sayfada kopyalanmaz.
- Buton varyantları: `primary` (sayfada en fazla 1 tane), `secondary`, `tertiary`, `danger`, `ai`. Kural detayı: @docs/design-system.md
- AI üretimi içerik her zaman mavi (`--color-ai-600`) ailesinde gösterilir ve "hekim onayı bekliyor" ilkesiyle sunulur — asla sessizce otomatik uygulanmaz.

## Komutlar
```
# Backend
cd backend && ./mvnw spring-boot:run
cd backend && ./mvnw test

# Frontend
cd frontend && npm install
cd frontend && npm run dev
cd frontend && npm run build
```

## Şu anki faz
Faz 1 (MVP) geliştiriliyor. Kapsam ve sıralı görev listesi: @docs/implementation-plan.md
Ekran önceliklendirmesi (P0/P1/P2/P3): @docs/screen-priorities.md

## Konvansiyon dışı bir durumla karşılaşırsan
`coding-conventions.md`, `reference-module.md` veya `api-conventions.md`'de karşılığı olmayan bir karar vermen gerekiyorsa (örn. yeni bir senaryo, öngörülmemiş bir kenar durum):
1. Önce bana sor, kendi başına karar verip ilerleme.
2. Karar netleşince, ilgili doküman dosyasına **kısa bir madde olarak ekle** (mevcut formatı koru, kod bloğu şişirme).
3. Aynı kararı bir daha sormama gerek kalmasın diye bunu değişiklik özetinde belirt.

Bu üç dosya, proje ilerledikçe güncellenen canlı kaynaklardır — statik/bitmiş belgeler değildir.

## Yapma
- CLAUDE.md dosyasına kod bloğu / uzun açıklama EKLEME — yeni bilgi `docs/` altına gider.
- Yeni bir domain modülü oluştururken `docs/architecture.md`'deki port/adapter desenini atlama.
- Sidebar, buton veya badge rengini `docs/design-system.md` ile çelişecek şekilde değiştirme.
