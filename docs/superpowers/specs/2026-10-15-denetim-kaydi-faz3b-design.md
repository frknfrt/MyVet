# Faz 3b — Klinik denetim kaydı (audit log) (tasarım)

**Tarih:** 2026-10-15
**Yol haritası:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` §Faz 3b
**Durum:** Kullanıcı ilk iki soruyu cevapladı (amaç: A, olay listesi: A), sonra "önerdiğin yollardan soru sormadan devam et" dedi. Bu belgedeki diğer kararlar o yetkiyle verildi; her biri §8'de "Kullanıcı yokken verilen kararlar" altında listelenir.

## 1. Amaç

Klinik içinde yapılan, geri alınması zor ya da sorumluluk doğuran işlemlerin **değiştirilemez** kaydını tutmak:

- **Klinik yöneticisi** (ADMIN) Ayarlar → "İşlem Geçmişi" ekranında kendi kliniğinin kaydını görür ("Bu faturayı kim iptal etti?").
- **Platform admin** (sen) admin panelinde herhangi bir kliniğin kaydını görür (destek taleplerinde).
- Aynı kayıt KVKK / hukuki delil olarak kullanılabilir.

Mevcut `audit_log_entries` (platform admin işlemleri) **olduğu gibi kalır** — ayrı amaç, ayrı tablo. Yeni tablo `audit_events` klinik içi işlemler içindir.

## 2. Kapsam dışı

- Hasta dosyası görüntüleme izi ("kim hangi hastayı açtı") — seçenek C reddedildi.
- Randevu / aşı / laboratuvar / görüntüleme / konaklama iptalleri (seçenek B reddedildi).
- Saklama süresi / otomatik silme (kayıtlar şimdilik süresiz tutulur; ileride ayrı iş).
- Dışa aktarma (CSV), e-posta bildirimi.
- Mevcut platform admin denetim kaydının yeni tabloya taşınması.

## 3. Kaydedilen olaylar (22)

| Kod | Türkçe etiket | Nereden | Hedef (target_type / label) | Özet örneği |
|---|---|---|---|---|
| `STAFF_LOGIN_SUCCEEDED` | Giriş yapıldı | `LoginUseCase` | STAFF / personel adı | — |
| `STAFF_LOGIN_FAILED` | Başarısız giriş denemesi | `LoginUseCase` | STAFF / personel adı | "Hatalı şifre" / "Pasif kullanıcı" / "Askıdaki klinik" |
| `STAFF_CREATED` | Personel eklendi | `CreateStaffUserUseCase` | STAFF / ad | "Rol: VET" |
| `STAFF_UPDATED` | Personel bilgisi değişti | `UpdateStaffUserUseCase` | STAFF / ad | "Rol: VET → ADMIN" (rol değiştiyse) yoksa "Bilgiler güncellendi" |
| `STAFF_DEACTIVATED` | Personel pasife alındı | `DeactivateStaffUserUseCase` | STAFF / ad | — |
| `STAFF_REACTIVATED` | Personel yeniden aktif edildi | `ReactivateStaffUserUseCase` | STAFF / ad | — |
| `STAFF_INVITED` | Personel davet edildi | `InviteStaffMemberUseCase` | STAFF_INVITE / maskeli e-posta | "Rol: RECEPTIONIST" |
| `STAFF_INVITE_REVOKED` | Davet iptal edildi | `RevokeStaffInviteUseCase` | STAFF_INVITE / maskeli e-posta | — |
| `STAFF_INVITE_ACCEPTED` | Davet kabul edildi | `AcceptStaffInviteUseCase` | STAFF / yeni personelin adı | — |
| `INVOICE_VOIDED` | Fatura iptal edildi | `VoidInvoiceUseCase` | INVOICE / fatura no | tutar |
| `INVOICE_PAYMENT_RECORDED` | Ödeme alındı | `RecordPaymentUseCase` | INVOICE / fatura no | "450,00 ₺ — Nakit" |
| `CASH_REGISTER_CLOSED` | Kasa kapatıldı | `CloseCashRegisterUseCase` | CASH_REGISTER / — | "Kapanış: 1.250,00 ₺" |
| `PRESCRIPTION_ISSUED` | Reçete yazıldı | `IssuePrescriptionUseCase` | PRESCRIPTION / reçete no veya kısa id | "3 kalem" |
| `PRESCRIPTION_CANCELLED` | Reçete iptal edildi | `CancelPrescriptionUseCase` | PRESCRIPTION / reçete no veya kısa id | — |
| `ENCOUNTER_FINALIZED` | Muayene kesinleştirildi | `FinalizeEncounterUseCase` | ENCOUNTER / kısa id | — |
| `CONSENT_REVOKED` | KVKK onayı geri çekildi | `RevokeConsentUseCase` | CONSENT / onay türü | — |
| `TARBIL_MARKED_SUBMITTED` | TARBİL'e gönderildi işaretlendi | `MarkSubmittedUseCase` | TARBIL_SUBMISSION / kısa id | — |
| `TARBIL_SUBMISSION_DISMISSED` | TARBİL gönderimi yok sayıldı | `DismissSubmissionUseCase` | TARBIL_SUBMISSION / kısa id | gerekçe (≤200 karakter) |
| `TARBIL_SUBMISSION_RESTORED` | TARBİL gönderimi geri alındı | `RestoreSubmissionUseCase` | TARBIL_SUBMISSION / kısa id | — |
| `TARBIL_EXTENSION_PAIRED` | TARBİL eklentisi eşleştirildi | `PairExtensionUseCase` | EXTENSION_TOKEN / etiket | — |
| `TARBIL_EXTENSION_REVOKED` | TARBİL eklenti anahtarı iptal edildi | `RevokeExtensionTokenUseCase` | EXTENSION_TOKEN / etiket | — |
| `SUPPORT_IMPERSONATION_STARTED` | Vetly destek ekibi hesaba girdi | `StartImpersonationUseCase` | TENANT / — | "Yönetici hesabıyla" |

"Kısa id" = UUID'nin ilk 8 karakteri. Kesin özet metinleri ve hedef alanları plan aşamasında use case'in elindeki verilere göre netleşir; **kural:** hasta / hayvan sahibi adı, telefon, TCKN, adres, serbest klinik notu **asla** yazılmaz. Personel adı yazılabilir (çalışan, iç denetim). Personel e-postası maskeli (`LogMasking.email`).

`STAFF_LOGIN_FAILED` yalnızca e-posta bir personelle eşleşiyorsa yazılır (aksi halde hangi kliniğe ait olduğu bilinmez; o durum rate limit + uygulama loguyla izlenir).

## 4. Veri

Migration `V81__audit_events.sql`:

```sql
CREATE TABLE audit_events (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    actor_type      VARCHAR(16)  NOT NULL,   -- STAFF | PLATFORM_ADMIN
    actor_id        UUID,
    actor_name      TEXT,                    -- yazma anindaki ad (sonradan degisse de kayit sabit)
    actor_role      VARCHAR(32),
    impersonated_by TEXT,                    -- impersonate oturumunda platform admin e-postasi
    channel         VARCHAR(16)  NOT NULL,   -- WEB | EXTENSION
    action          VARCHAR(64)  NOT NULL,
    target_type     VARCHAR(32),
    target_id       UUID,
    target_label    TEXT,
    summary         TEXT,                    -- <= 500 karakter, PII yok
    ip_address      TEXT,
    request_id      TEXT
);
CREATE INDEX idx_audit_events_tenant_time ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_events_tenant_action_time ON audit_events (tenant_id, action, occurred_at DESC);
```

**Değiştirilemezlik:** `BEFORE UPDATE OR DELETE` tetikleyicisi istisna fırlatır (`audit_events kayitlari degistirilemez`). Uygulamada güncelleme/silme yolu yoktur. (`TRUNCATE` ve tetikleyiciyi kaldırma DB sahibine açıktır — kasıtlı kötüye kullanıma karşı değil, kazaya ve uygulama hatasına karşı koruma.)

Kiracı RLS'i yok; tüm sorgular `tenant_id`'yi açıkça parametre alır (Faz 4 `tenant_monthly_usage` ile aynı desen, native SQL / JDBC).

## 5. Mimari

Yeni paket `com.vetos.platform.audit` (`@NamedInterface("audit")`):

- `AuditAction` (enum): §3'teki 22 kod, her biri Türkçe `label()`.
- `AuditRecord` (record): `action`, `targetType`, `targetId`, `targetLabel`, `summary` + isteğe bağlı açık `tenantId`. Fabrika: `AuditRecord.of(action, targetType, targetId, targetLabel, summary)`, `.forTenant(UUID)`.
- `AuditActor` (record): `type` (STAFF / PLATFORM_ADMIN), `id`, `name`, `role`, `impersonatedBy`, `channel`.
- `AuditPort`:
  - `void record(AuditRecord)` — aktörü güvenlik bağlamından çözer; **çağıranın transaction'ına katılır** (REQUIRED): iş geri alınırsa kayıt da geri alınır, "iptal edildi" yazıp aslında iptal etmeme durumu oluşmaz.
  - `void record(AuditRecord, AuditActor)` — aktör açıkça verilir (giriş, eşleştirme, impersonate başlangıcı); REQUIRED.
  - `void recordStandalone(AuditRecord, AuditActor)` — **kendi transaction'ında** (REQUIRES_NEW): başarısız giriş (iş istisna fırlatıp geri alınırken kayıt kalmalı) ve başarılı giriş (`LoginUseCase` `readOnly=true` transaction'da çalışıyor; read-only transaction'da INSERT yapılamaz).
- Tenant: `AuditRecord.tenantId` verilmemişse `TenantContext.current()`; ikisi de yoksa `IllegalStateException` (sessizce kaybolmasın).
- Aktör çözümü (`SecurityContextHolder`):
  - `AuthenticatedStaffUser` → STAFF, id, rol, `impersonatedBy` (aşağıda); adı `AuditActorNames.staffName(tenantId, staffUserId)` ile yazma anında alınır. Yetki listesinde eklenti rolü (`ROLE_` + `ExtensionTokenAuthenticationFilter.ROLE`) varsa `channel=EXTENSION`, yoksa `WEB`.
  - `AuthenticatedPlatformAdmin` → PLATFORM_ADMIN, id, ad = e-posta.
  - Hiçbiri yoksa `IllegalStateException` (açık aktörlü metot kullanılmalı).
- `AuditActorNames` arayüzü platform'da, uygulaması `tenant` modülünde (personel adını okur; bulunamazsa `null`).
- IP ve requestId: `RequestContextHolder` üzerinden mevcut istekten (`getRemoteAddr()`, MDC `requestId`); istek yoksa `null`.
- Yazma: `JdbcAuditEventStore` (NamedParameterJdbcTemplate), id `UUID.randomUUID()`, zaman `Clock` (UTC).
- Okuma: `AuditEventQuery` — `list(tenantId, filter, page, size)`; filtre: `from`, `to` (Instant, isteğe bağlı), `action` (isteğe bağlı), `actorId` (isteğe bağlı). Sıra `occurred_at DESC, id DESC`. `size` varsayılan 50, en fazla 200; `size+1` satır çekilir → `hasMore`.

**Neden açık çağrı (port), olay/AOP değil:** mevcut `RecordAuditLogUseCase` deseniyle aynı; hangi işlemin hangi bilgiyle kaydedildiği use case'te görünür ve test edilir; aynı transaction'da yazmak kolay.

**Modül bağımlılıkları:** `allowedDependencies` listesi olan modüllere (`billing`, `encounter`, `patient`, `integration.tarbil`, `platformadmin` — hangilerinde liste varsa) `"platform::audit"` eklenir; `tenant` (liste yok) dokunulmaz.

## 6. Impersonate sırasında kim yaptı?

Bugün impersonate JWT'si normal personel JWT'siyle aynı; destek ekibinin klinik içinde yaptığı her işlem kayıtta **kliniğin yöneticisi** yapmış gibi görünür — denetim kaydı için kabul edilemez.

- `JwtTokenProvider`'a impersonate için ek claim: `imp` = platform admin e-postası (yeni overload; normal giriş değişmez).
- `AuthenticatedStaffUser`'a 5. alan `impersonatedBy` (String, null olabilir) + eski 4 parametreli kurucu (mevcut çağrılar derlenmeye devam eder). `parse` claim'i okur.
- `StartImpersonationUseCase` yeni overload'u kullanır ve kliniğe görünen `SUPPORT_IMPERSONATION_STARTED` olayını yazar (aktör PLATFORM_ADMIN, açık aktörlü `record`).
- Impersonate oturumunda yazılan her olayda `impersonated_by` dolu olur; ekranda "Vetly destek ekibi aracılığıyla" notu gösterilir.

## 7. Uçlar ve ekranlar

- `GET /api/v1/audit-events?from&to&action&actorId&page&size` — `@PreAuthorize("hasRole('ADMIN')")`, kiracı `TenantContext`'ten (tenant modülü, yeni controller).
- `GET /api/v1/platform-admin/tenants/{id}/audit-events?...` — aynı parametreler (`PlatformAdminTenantsController`, sınıf düzeyinde PLATFORM_ADMIN).
- `GET /api/v1/audit-events/actions` — `[{code, label}]` (filtre açılır listesi; etiketlerin tek kaynağı enum). ADMIN.
- Yanıt: `{ items: [{ id, occurredAt, actorType, actorId, actorName, actorRole, impersonatedBy, channel, action, actionLabel, targetType, targetId, targetLabel, summary, ipAddress }], page, size, hasMore }`.
- Geçersiz `size`/`page` → 400 (`@Min/@Max`).

**Klinik ekranı:** Ayarlar'a yeni sekme **"İşlem Geçmişi"** (yalnız ADMIN görür). Filtreler: tarih aralığı, işlem türü, kişi (mevcut personel listesinden). Tablo: Tarih · Kişi (impersonate ise "Vetly destek ekibi aracılığıyla", eklentiyse "(TARBİL eklentisi)" notu) · İşlem · Kayıt · Açıklama · IP. Altta "Daha fazla yükle".

**Admin paneli:** Kiracı detay sayfasında "Klinik işlem geçmişi" kartı — aynı tablo bileşeni, aynı filtreler.

## 8. Kullanıcı yokken verilen kararlar

1. Olay listesi §3'teki 22 olay (kullanıcı A'yı seçti; kesin liste benim).
2. Ayrı `audit_events` tablosu; platform admin kaydı ayrı kalır.
3. Açık port çağrısı; kayıt iş transaction'ına katılır (giriş olayları hariç — onlar kendi transaction'ında).
4. Değiştirilemezlik DB tetikleyicisiyle.
5. Aktör adı yazma anında anlık görüntü olarak saklanır.
6. Impersonate için JWT'ye `imp` claim'i ve `impersonated_by` sütunu (§6).
7. Bilinmeyen e-postayla başarısız giriş kaydedilmez.
8. Saklama süresiz; dışa aktarma yok.
9. Sayfalama `hasMore` ile (toplam sayı yok — büyük tablolarda COUNT pahalı).

## 9. Testler

- **Çekirdek (gerçek Postgres):** yazma + okuma; kiracı yalıtımı (A kiracısı B'nin kaydını göremez); filtreler (tarih, işlem, kişi); sıralama ve `hasMore`; tetikleyici UPDATE ve DELETE'i reddeder; `record` çağıranın transaction'ı geri alınınca kayıt da yok olur; `recordStandalone` geri alınan transaction'a rağmen kalır.
- **Aktör çözümü:** personel (WEB), eklenti (EXTENSION), impersonate (`impersonatedBy` dolu), platform admin, bağlam yok → istisna; tenant yok → istisna.
- **JWT:** `imp` claim'i gidiş-dönüş; normal token'da `impersonatedBy == null`.
- **Her use case:** doğru `AuditAction`, hedef ve özet; hata yolunda kayıt yok (başarısız giriş hariç); PII yok (özet ve etikette telefon/sahip adı geçmez).
- **Giriş (SpringBootTest):** gerçek başarılı giriş read-only transaction'a rağmen olay yazar; hatalı şifre olay yazar ve 401 döner.
- **Uçlar:** ADMIN 200, VET 403; başka kiracının kaydı görünmez; platform admin ucu; `size=500` → 400.
- **Ön yüz:** `npm run build`.
- Tüm backend testleri yeşil.

## 10. Dağıtım

- `V81` otomatik çalışır; eski olaylar için geçmiş kayıt yoktur (kayıt bu sürümden itibaren başlar).
- `docs/deployment.md`'ye kısa not: İşlem Geçmişi nerede, neyi kaydeder, kayıtların değiştirilemez olduğu.
- Yol haritasında Faz 3b ✅, sıradaki Faz 5.
- Commit/push yok.
