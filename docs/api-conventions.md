# API Konvansiyonları & Güvenlik

## URL Yapısı

```
/api/v1/<kaynak-coğul>              GET (liste), POST (oluştur)
/api/v1/<kaynak-coğul>/{id}         GET (tekil), PUT (güncelle), DELETE
/api/v1/<kaynak-coğul>/{id}/<alt-kaynak>   İlişkili kaynaklar
```

- Kaynak adları İngilizce, çoğul, kebab-case değil düz İngilizce çoğul: `/patients`, `/appointments`, `/invoice-lines`.
- Fiil URL'de OLMAZ (`/patients/create` YANLIŞ). HTTP metodu fiili taşır.
- Aksiyon endpoint'leri (durum değişikliği gibi) istisna: `POST /encounters/{id}/finalize`, `POST /invoices/{id}/void`.

## HTTP Durum Kodları

| Kod | Kullanım |
|---|---|
| 200 | Başarılı GET/PUT |
| 201 | Başarılı POST (kaynak oluşturuldu), `Location` header ile birlikte |
| 204 | Başarılı DELETE |
| 400 | Validation hatası (Bean Validation) |
| 401 | Kimlik doğrulanmadı (token yok/geçersiz) |
| 403 | Kimlik doğrulandı ama yetkisi yok |
| 404 | Kaynak bulunamadı |
| 409 | Çakışma (örn. aynı saatte randevu çakışması) |
| 422 | İş kuralı ihlali (örn. stok yetersiz) |
| 500 | Beklenmeyen sunucu hatası |

## Standart Hata Formatı

Her hata cevabı (`GlobalExceptionHandler` üretir), formatı asla değişmez:

```json
{
  "errorCode": "PATIENT_NOT_FOUND",
  "message": "Hasta bulunamadı: 3f2e...",
  "timestamp": "2026-07-28T14:32:00Z",
  "path": "/api/v1/patients/3f2e..."
}
```

Validation hatalarında ek `fieldErrors` dizisi:

```json
{
  "errorCode": "VALIDATION_FAILED",
  "message": "Girdi doğrulama hatası",
  "fieldErrors": [{ "field": "name", "message": "Boş olamaz" }],
  "timestamp": "...", "path": "..."
}
```

## Pagination

Liste endpoint'leri `page` (0-bazlı) ve `size` (varsayılan 20, max 100) query param alır:

```
GET /api/v1/patients?page=0&size=20&sort=name,asc
```

Cevap zarfı:
```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 248, "totalPages": 13 }
```

## Multi-Tenancy

Her istekte JWT'den çözülen `tenantId`, `TenantContext` (ThreadLocal) üzerinden tüm repository sorgularına **otomatik** filtre olarak eklenir (Hibernate `@Filter` veya sorgu interceptor ile). Controller/use-case katmanında `tenantId`'yi elle her yerde parametre olarak geçirmeye gerek YOK — `TenantContext.current()` üzerinden okunur. İstisna: Referans modüldeki (`RegisterPatientCommand`) gibi command nesnelerinde tenantId açıkça taşınır çünkü command, use-case sınırını geçen bir DTO'dur.

## JWT Claim Yapısı

```json
{
  "sub": "<staffUserId>",
  "tenantId": "<uuid>",
  "branchIds": ["<uuid>", "..."],
  "role": "VET | TECHNICIAN | RECEPTIONIST | ADMIN",
  "exp": 1234567890
}
```

## Rol & Yetki Matrisi (Faz 1 kapsamı)

| Endpoint grubu | VET | TECHNICIAN | RECEPTIONIST | ADMIN |
|---|:---:|:---:|:---:|:---:|
| `/patients/**` (okuma) | ✅ | ✅ | ✅ | ✅ |
| `/patients/**` (yazma) | ✅ | ❌ | ✅ | ✅ |
| `/encounters/**` (SOAP yazma/onaylama) | ✅ | ❌ | ❌ | ✅ |
| `/appointments/**` | ✅ | ✅ | ✅ | ✅ |
| `/invoices/**`, `/payments/**` | ❌ | ❌ | ✅ | ✅ |
| `/inventory/**` (yazma) | ❌ | ✅ | ❌ | ✅ |
| `/settings/**`, `/users/**` | ❌ | ❌ | ❌ | ✅ |
| `/tarbil/**` (senkron tetikleme) | ❌ | ❌ | ❌ | ✅ |
| `/encounters/**` (okuma: SOAP, malzeme listesi) | ✅ | ✅ | ❌ | ✅ |
| `/message-templates/**`, `/notifications/**` (SMS/WhatsApp) | ❌ | ❌ | ✅ | ✅ |
| `**/campaign-candidates` (SMS/WhatsApp kampanya adayı listeleri) | ✅ | ❌ | ✅ | ✅ |

Yetki kontrolü Spring Security `@PreAuthorize("hasRole('VET')")` ile controller metodu seviyesinde yapılır — use-case katmanında rol kontrolü YAPILMAZ (rol, bir HTTP/API kavramıdır, domain'in bilmesi gerekmez).

**Kenar durum kararı (Faz 2, Yönetim ekranları turu):** Aynı kiracı içi bir dizin listesi (örn. `GET /staff-users`) birden fazla amaçla kullanılıyorsa (hem hafif bir "seçici" hem de bir yönetim ekranının tam listesi), tek endpoint zenginleştirilir ve mevcut erişim seviyesinde bırakılır — PII olmayan alanlar (ad, e-posta, rol, uzmanlık) için ayrı bir ADMIN-only endpoint AÇILMAZ. Yazma/aksiyon endpoint'leri (`POST`/`PUT`/`DELETE`) yine de ilgili role kısıtlanır.

**Karar (Faz 2, role-bazlı yetkilendirme denetimi turu, kullanıcı onayıyla):** Proje geneli bir rol/yetki denetiminde üç boşluk bulundu ve düzeltildi:
1. `EncountersController`'ın **okuma** uçları (`GET /{id}`, `GET` liste, `GET /by-appointment/{id}`, `GET /{id}/materials`) artık TECHNICIAN'a da açık — tedavi görevi/aşı uygulaması için tedavi planını görmesi gerekiyor. Yazma uçları (SOAP/vital/fizik muayene güncelleme, finalize, malzeme kaydı) hâlâ sadece VET/ADMIN. Frontend: `EncounterPage.tsx`'teki `isReadOnly`, `session.role === 'TECHNICIAN'` durumunda da true oluyor (sayfa görünür ama tüm yazma aksiyonları gizli).
2. `MessageTemplatesController` + `NotificationsController` (SMS/WhatsApp şablon/log/kampanya) ADMIN-only'den RECEPTIONIST+ADMIN'e açıldı — requirements.md'deki resepsiyonist tanımı "hatırlatma gönderimi" içeriyor, önceki kısıtlama kasıtsız bir eksiklikti.
3. `AppointmentsController.GET /campaign-candidates` ve `VaccinationRecordsController.GET /campaign-candidates` hiç rol kısıtı taşımıyordu (TECHNICIAN dahil herkese açıktı) — `OwnersController`'daki eşdeğer uç (VET/RECEPTIONIST/ADMIN) ile tutarlı hale getirildi.

Ayrıca frontend'de daha önce **hiç** rol bazlı görünürlük yoktu (`navConfig.tsx` her role aynı sidebar'ı gösteriyordu, `RequireAuth` sadece "giriş yapılmış mı" kontrolü yapıyordu). `NavItemConfig`/`RequireAuth` artık opsiyonel bir `roles` listesi taşıyor; Laboratuvar/Görüntüleme (VET/TECHNICIAN/ADMIN) ve Finans/Raporlar (RECEPTIONIST/ADMIN) hem sidebar'da hem route seviyesinde bu role göre filtreleniyor. `SettingsPage.tsx`'teki `SETTINGS_TABS` da aynı desenle `adminOnly: boolean`'dan `roles?: StaffRole[]`'e genelleştirildi (SMS/WhatsApp sekmesi artık ADMIN+RECEPTIONIST).

## Versiyonlama

`v1` şu an tek versiyon. Kırıcı bir değişiklik gerekirse `v2` eklenir, `v1` en az 6 ay paralel yaşar. Kırıcı olmayan değişiklik (yeni alan ekleme) versiyon gerektirmez.
