# Platform Abonelik Faturalama Sistemi — Tasarım Dokümanı

**Tarih:** 2026-08-28
**Durum:** Onaylandı — implementasyon planı bekleniyor
**İlgili modül:** `modules/platformadmin`, `modules/tenant` (Subscription)

## 1. Bağlam ve Amaç

Platform Admin Paneli şu an kiracı aboneliklerini (`Subscription`: plan kodu, faturalama durumu, yenileme tarihi) tamamen elle düzenlenen alanlar olarak yönetiyor — hiçbir fatura veya ödeme kaydı yok. `PlatformBillingPage` bu yüzden sadece mevcut `billingStatus` alanına göre kiracıları gruplayan salt-okunur bir özet.

Gerçek bir ödeme sağlayıcı hesabı (iyzico/PayTR/Stripe vb.) henüz yok ve bu turun kapsamı dışında. Bu doküman, sağlayıcı geldiğinde üzerine tek bir tahsilat adaptörü eklenebilecek şekilde **gerçek bir fatura/ödeme kayıt sistemi** kurmayı hedefliyor: periyodik olarak otomatik üretilen faturalar, platform admin'in bunları elle "ödendi" işaretleyebildiği bir akış, ve gecikmiş ödemelerin otomatik tespiti.

## 2. Kapsam

**Bu turda yapılacak:**
- Yeni `PlatformInvoice` / `PlatformPayment` veri modeli
- Günlük çalışan bir scheduler: (a) süresi gelen abonelikler için otomatik fatura üretimi, (b) son ödeme tarihi geçmiş faturaları `OVERDUE` işaretleyip kiracının `billingStatus`'ünü `PAST_DUE`'ya çekme
- **Fatura üretildiğinde ve gecikince kiracıya e-posta bildirimi** (bkz. §4.1) — gerçek sağlayıcı olmadığı için ödeme **otomatik kart çekimi ile değil**, kiracının (banka havalesi vb.) ödemesi ve platform admin'in elle kaydetmesiyle tamamlanır; bildirim bu manuel akışın kiracı tarafını tetikler
- Platform admin'in bir faturayı elle "ödendi" işaretlemesi (yöntem/tutar/tarih/not) — bu işlem faturayı `PAID` yapar ve kiracının `billingStatus`'ünü `ACTIVE`'e döndürür
- Tenant detay sayfasına yeni bir "Faturalar" kartı: geçmiş faturalar + ödeme kaydetme
- `PlatformBillingPage`'in bu gerçek veriyi yansıtacak şekilde güncellenmesi

**Kapsam dışı (bilinçli olarak):**
- Gerçek ödeme sağlayıcı entegrasyonu (API çağrısı, webhook, kart saklama, **otomatik kart çekimi**) — bu olmadan otomatik tahsilat teknik olarak mümkün değil
- Kısmi ödeme desteği — bir fatura ya tam ödenir ya ödenmez (klinik seviyesi `Invoice`'daki `PARTIALLY_PAID` deseni burada yok)
- PDF fatura çıktısı
- Dinamik/çoklu banka hesabı yönetimi — ödeme talimatı (banka bilgisi) e-postada sabit bir metin/config değeri olarak yer alır
- Mevcut "Plan / Durum Değiştir" elle-düzenleme modalı — **kaldırılmıyor**, istisnai durumlar (kompliman hesap, manuel düzeltme) için kaçış kapısı olarak kalıyor

## 3. Veri Modeli

### 3.1 `PlatformInvoice` (yeni entity, `modules/platformadmin/domain`)

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | UUID | PK |
| `tenantId` | UUID | Hangi kiracı (FK zorlanmıyor, `Subscription.tenantId` ile aynı desen) |
| `planCode` | String | Fatura kesildiği andaki plan kodu (anlık görüntü — plan sonradan değişse bile fatura değişmez) |
| `amount` | BigDecimal | Fatura kesildiği andaki `Plan.monthlyPrice` (anlık görüntü) |
| `periodStart` | LocalDate | Faturalanan dönemin başlangıcı |
| `periodEnd` | LocalDate | Faturalanan dönemin bitişi (`periodStart` + 1 ay) |
| `dueDate` | LocalDate | Son ödeme tarihi (`issuedAt` + 7 gün) |
| `status` | enum | `ISSUED`, `PAID`, `OVERDUE`, `VOID` |
| `issuedAt` | Instant | Fatura üretilme zamanı |
| `paidAt` | Instant (nullable) | Ödendi işaretlendiği zaman |

Domain metodları: `issue(tenantId, planCode, amount, periodStart, periodEnd)` (statik factory — `dueDate` otomatik hesaplanır), `markPaid()`, `markOverdue()`, `void_()` (Java'da `void` anahtar kelime olduğu için `voidInvoice()` adlandırılacak).

**Durum geçişleri:** `ISSUED → PAID` (ödeme kaydı), `ISSUED → OVERDUE` (scheduler), `OVERDUE → PAID` (gecikmeli ödeme), `ISSUED|OVERDUE → VOID` (admin elle iptal — örn. yanlışlıkla üretilmiş fatura).

### 3.2 `PlatformPayment` (yeni entity, `modules/platformadmin/domain`)

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | UUID | PK |
| `invoiceId` | UUID | Hangi faturaya ait |
| `amount` | BigDecimal | Kaydedilen tutar |
| `method` | enum | `BANK_TRANSFER`, `CARD`, `OTHER` (gerçek sağlayıcı yok, sadece kayıt amaçlı) |
| `paidAt` | LocalDate | Ödemenin fiilen alındığı tarih (admin'in girdiği, kayıt anı değil) |
| `recordedByAdminId` | UUID | Kaydı giren platform admin (`PlatformAdminUser.id`) |
| `notes` | String (nullable) | Örn. "Havale referans no: ..." |

Bire-bir ilişki DB seviyesinde zorlanmıyor (basitlik için) ama use-case seviyesinde: bir fatura zaten `PAID` ise ikinci bir ödeme kaydına izin verilmez.

### 3.3 Migration

`V26__platform_invoices_and_payments.sql` — iki yeni tablo (`platform_invoices`, `platform_payments`), `platform_invoices.tenant_id` ve `platform_payments.invoice_id` üzerinde index.

## 4. Fatura Yaşam Döngüsü (Scheduler)

Yeni `PlatformBillingScheduler` (`modules/platformadmin/infrastructure/scheduling`), `AppointmentReminderScheduler` ile aynı desen — günde bir kez, `06:00 Europe/Istanbul`:

1. **`generateDueInvoices()`:** `planCode != 'TRIAL'` olan tüm abonelikler taranır. `renewsAt <= bugün` olan her biri için: aynı `tenantId` + `periodStart = renewsAt` için zaten bir fatura var mı kontrol edilir (idempotency — aynı gün scheduler iki kez çalışsa bile mükerrer fatura üretilmez), yoksa `Plan` tablosundan güncel `monthlyPrice` çekilip yeni bir `PlatformInvoice` üretilir ve `Subscription.renewsAt` bir sonraki döneme (`periodEnd`) ilerletilir.
2. **`flagOverdueInvoices()`:** `status = ISSUED` ve `dueDate < bugün` olan faturalar `OVERDUE` işaretlenir, ilgili kiracının `Subscription.billingStatus`'ü `TenantAdminPort` üzerinden `PAST_DUE`'ya çekilir (mevcut cross-module yazma deseniyle aynı).

Trial abonelikler (`planCode == 'TRIAL'`) hiç faturalanmaz — sadece gerçek bir plana geçildiğinde devreye girer.

### 4.1 Kiracı Bildirimi (e-posta)

Otomatik kart çekimi olmadığı için kiracının fatura kesildiğini/geciktiğini bilmesi gerekiyor. Yeni bir port, TARBİL/davet e-postası ile birebir aynı desen:

- `PlatformBillingEmailPort` (`modules/platformadmin/domain`): `sendInvoiceIssued(invoice, tenantName, recipientEmail)` ve `sendInvoiceOverdue(invoice, tenantName, recipientEmail)`.
- `MockPlatformBillingEmailAdapter`: gerçek bir e-posta sağlayıcısı (SendGrid/SMTP) hesabı yok — `MockInviteEmailAdapter` ile aynı şekilde, gönderim sunucu loglarına yazılır.
- **Alıcı e-postası:** `Tenant`'ta e-posta alanı yok — kiracının `ADMIN` rolündeki (kayıt sırasında oluşturulan) `StaffUser`'ının e-postası kullanılır. `TenantAdminPort`'a yeni bir salt-okuma metodu eklenir: `findBillingContactEmail(tenantId): Optional<String>`.
- **Tetikleyiciler:** `generateDueInvoices()` her yeni fatura için `sendInvoiceIssued` çağırır (tutar, dönem, son ödeme tarihi, sabit banka/ödeme talimatı metni içerir); `flagOverdueInvoices()` her `OVERDUE` işaretlenen fatura için `sendInvoiceOverdue` çağırır. Alıcı e-postası bulunamazsa (örn. silinmiş kullanıcı) gönderim atlanır, işlem durmaz — sadece loglanır.
- Ödeme talimatı metni (banka hesap bilgisi vb.) `application.yml`'de sabit bir config değeri (`platform-billing.payment-instructions`) olarak tutulur — dinamik/çoklu hesap yönetimi kapsam dışı.

## 5. Application Katmanı (Use Case'ler)

- `GenerateDueInvoicesUseCase` — scheduler adım 1, `PlatformBillingEmailPort.sendInvoiceIssued` çağrısını da içerir
- `FlagOverdueInvoicesUseCase` — scheduler adım 2, `PlatformBillingEmailPort.sendInvoiceOverdue` çağrısını da içerir
- `RecordPlatformPaymentUseCase(invoiceId, amount, method, paidAt, notes, recordedByAdminId)` — fatura zaten `PAID`/`VOID` ise hata, değilse `PlatformPayment` oluşturur + `PlatformInvoice.markPaid()` + `TenantAdminPort` üzerinden `billingStatus = ACTIVE`
- `VoidPlatformInvoiceUseCase(invoiceId)` — admin'in yanlış üretilmiş bir faturayı iptal etmesi. Sadece `ISSUED`/`OVERDUE` durumundaki faturalar iptal edilebilir; `PAID` bir fatura void edilemez (önce ödeme kaydının geri alınması ayrı ve kapsam dışı bir konu)
- `ListPlatformInvoicesForTenantUseCase(tenantId)` — tenant detay sayfası için

## 6. API

Yeni `PlatformInvoicesController` (`/api/v1/platform-admin/tenants/{tenantId}/invoices`, class-level `hasRole('PLATFORM_ADMIN')` — mevcut `PlatformAdminTenantsController`/`PlanController` ile aynı desen):

| Endpoint | Açıklama |
|---|---|
| `GET /` | Tenant'ın fatura geçmişi |
| `POST /{invoiceId}/payments` | Ödeme kaydet |
| `POST /{invoiceId}/void` | Faturayı iptal et |

## 7. Frontend

**`TenantDetailPage.tsx`:** Mevcut "Kiracı Bilgileri" / "Abonelik" kartlarının altına yeni bir "Faturalar" kartı — dönem/tutar/durum/son ödeme tarihi listesi, `ISSUED`/`OVERDUE` satırlarda "Ödeme Kaydet" (yöntem/tutar/tarih/not formu açan küçük bir modal, mevcut `InvoiceDetailModal`'daki ödeme formuyla aynı ruhta ama basitleştirilmiş — kısmi ödeme yok).

**`PlatformBillingPage.tsx`:** Mevcut "Gerçek bir ödeme tahsilat entegrasyonu henüz yok..." notu güncellenir — artık gerçek fatura/ödeme kayıtlarına dayanıyor, sadece otomatik online tahsilat yok. Sayfa hâlâ `billingStatus`'e göre gruplanmış özet olarak kalır (bu veri artık scheduler'dan geliyor, elle girilmiyor).

## 8. Test Stratejisi

TDD ile: `PlatformInvoice`/`PlatformPayment` domain metodları için birim testleri (durum geçişi kuralları — örn. `PAID` bir faturaya tekrar ödeme kaydedilemez), `RecordPlatformPaymentUseCase`/`GenerateDueInvoicesUseCase`/`FlagOverdueInvoicesUseCase` için Mockito ile port mock'lanan use-case testleri (`PlatformBillingEmailPort`'un doğru parametrelerle çağrıldığının doğrulanması dahil, alıcı e-postası bulunamama senaryosu — gönderim atlanır, akış durmaz — ayrıca test edilir). Ardından gerçek Postgres'e karşı curl ile uçtan uca doğrulama (fatura üretimi → ödeme kaydı → `billingStatus` geçişi).

## 9. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
