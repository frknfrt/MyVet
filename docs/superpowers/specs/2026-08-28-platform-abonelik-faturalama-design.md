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
- Günlük çalışan bir scheduler: süresi gelen abonelikler için otomatik fatura üretimi + son ödeme tarihi geçen faturaların otomatik askıya almaya kadar takibi
- **Fatura döngüsü toplam 7 gün** (bkz. §4): gün 0 fatura kesilir, gün 5 (son ödemeye 2 gün kala) hatırlatma, gün 7 hâlâ ödenmediyse `OVERDUE` + kiracı otomatik `SUSPENDED` — ekstra bir grace süresi yok
- **3 dokunuşluk e-posta + SMS bildirimi** (bkz. §4.1): fatura kesildi / son gün yaklaşıyor / askıya alındı — gerçek sağlayıcı olmadığı için ödeme **otomatik kart çekimi ile değil**, kiracının (banka havalesi vb.) ödemesi ve platform admin'in elle kaydetmesiyle tamamlanır; bildirimler bu manuel akışın kiracı tarafını tetikler
- Platform admin'in bir faturayı elle "ödendi" işaretlemesi (yöntem/tutar/tarih/not) — bu işlem faturayı `PAID` yapar, kiracının `billingStatus`'ünü `ACTIVE`'e döndürür ve (askıya alınmışsa) otomatik yeniden aktif eder
- **Login enforcement düzeltmesi:** `Tenant.status == SUSPENDED` artık girişi gerçekten engelliyor (şu an bu kontrol hiç yok — mevcut "Askıya Al" butonu bile kozmetik)
- Tenant detay sayfasına yeni bir "Faturalar" kartı: geçmiş faturalar + ödeme kaydetme
- `PlatformBillingPage`'in bu gerçek veriyi yansıtacak şekilde güncellenmesi

**Kapsam dışı (bilinçli olarak):**
- Gerçek ödeme sağlayıcı entegrasyonu (API çağrısı, webhook, kart saklama, **otomatik kart çekimi**) — bu olmadan otomatik tahsilat teknik olarak mümkün değil
- Kısmi ödeme desteği — bir fatura ya tam ödenir ya ödenmez (klinik seviyesi `Invoice`'daki `PARTIALLY_PAID` deseni burada yok)
- PDF fatura çıktısı
- Dinamik/çoklu banka hesabı yönetimi — ödeme talimatı (banka bilgisi) bildirimlerde sabit bir metin/config değeri olarak yer alır
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

**Toplam pencere 7 gün, ekstra grace süresi yok:** gün 0 fatura kesilir (`dueDate = issuedAt + 7 gün`), gün 5'te (son ödemeye 2 gün kala) hatırlatma gider, gün 7'de (`dueDate` geçtiğinde) hâlâ ödenmediyse fatura `OVERDUE` işaretlenir **ve kiracı aynı anda otomatik `SUSPENDED` olur** — "önce OVERDUE, birkaç gün sonra askıya al" gibi ayrı bir ek bekleme yok.

Yeni `PlatformBillingScheduler` (`modules/platformadmin/infrastructure/scheduling`), `AppointmentReminderScheduler` ile aynı desen — günde bir kez, `06:00 Europe/Istanbul`, üç adım:

1. **`generateDueInvoices()`:** `planCode != 'TRIAL'` olan tüm abonelikler taranır. `renewsAt <= bugün` olan her biri için: aynı `tenantId` + `periodStart = renewsAt` için zaten bir fatura var mı kontrol edilir (idempotency — aynı gün scheduler iki kez çalışsa bile mükerrer fatura üretilmez), yoksa `Plan` tablosundan güncel `monthlyPrice` çekilip yeni bir `PlatformInvoice` üretilir, `Subscription.renewsAt` bir sonraki döneme (`periodEnd`) ilerletilir ve **"fatura kesildi" bildirimi** gönderilir.
2. **`remindDueSoonInvoices()`:** `status = ISSUED` ve `dueDate == bugün + 2 gün` olan faturalar için **"son ödeme tarihiniz yaklaşıyor" bildirimi** gönderilir (tek seferlik — koşul sadece o gün doğru olduğu için doğal idempotent).
3. **`flagOverdueAndSuspend()`:** `status = ISSUED` ve `dueDate < bugün` olan faturalar `OVERDUE` işaretlenir; aynı anda ilgili kiracının `Subscription.billingStatus`'ü `PAST_DUE`'ya, `Tenant.status`'ü `SUSPENDED`'a çekilir (`TenantAdminPort` üzerinden, mevcut "Askıya Al" ile aynı yol) ve **"askıya alındı" bildirimi** gönderilir.

Trial abonelikler (`planCode == 'TRIAL'`) hiç faturalanmaz — sadece gerçek bir plana geçildiğinde devreye girer.

### 4.1 Kiracı Bildirimi (e-posta + SMS)

Otomatik kart çekimi olmadığı için kiracının fatura kesildiğini/gecikmek üzere olduğunu/askıya alındığını bilmesi gerekiyor. İki yeni port, TARBİL/davet e-postası ile birebir aynı desen — biri e-posta biri SMS için (tek bir port yerine ayrı, çünkü iki farklı dış yetenek: mevcut kod tabanı konvansiyonu — `TarbilSyncPort`, `InviteEmailPort` — her dış yeteneğe kendi portu):

- `PlatformBillingEmailPort` (`modules/platformadmin/domain`): `sendInvoiceIssued(invoice, tenantName, recipientEmail)`, `sendInvoiceDueSoon(invoice, tenantName, recipientEmail)`, `sendTenantSuspended(tenantName, recipientEmail)`. `MockPlatformBillingEmailAdapter` — gerçek e-posta sağlayıcısı yok, `MockInviteEmailAdapter` ile aynı şekilde sunucu loglarına yazılır.
- `PlatformBillingSmsPort` (`modules/platformadmin/domain`): aynı üç metod, `recipientPhone` parametresiyle. `MockPlatformBillingSmsAdapter` — gerçek SMS sağlayıcısı yok (mevcut `notification` modülündeki SMS de zaten simüle), aynı şekilde loglanır. **Bilinçli tasarım kararı:** mevcut `modules/notification`'ın `NotificationSendPort`'unu yeniden kullanmak yerine ayrı bir port tercih edildi — o modül kiracı-içi (klinik → hayvan sahibi) mesajlaşma için tasarlanmış ve `TenantContext`'e bağımlı; platform admin aksiyonları ise kiracılar-arası/kiracı-dışı, `platformadmin` modülünün zaten kendi izole altyapısını kurduğu felsefesiyle (bkz. `architecture.md` §6.1) tutarlı.
- **Alıcı iletişim bilgisi:** `Tenant`'ta e-posta/telefon alanı yok — kiracının `ADMIN` rolündeki (kayıt sırasında oluşturulan) `StaffUser`'ının e-posta ve telefonu kullanılır. `TenantAdminPort`'a yeni salt-okuma metodları: `findBillingContactEmail(tenantId): Optional<String>`, `findBillingContactPhone(tenantId): Optional<String>`.
- **Tetikleyiciler:** yukarıdaki 3 scheduler adımının her biri hem e-posta hem SMS gönderir (tutar, dönem, son ödeme tarihi, sabit ödeme talimatı içerir). İletişim bilgisi bulunamazsa (örn. silinmiş kullanıcı) o kanal atlanır, işlem durmaz — sadece loglanır.
- Ödeme talimatı metni (banka hesap bilgisi vb.) `application.yml`'de sabit bir config değeri (`platform-billing.payment-instructions`) olarak tutulur — dinamik/çoklu hesap yönetimi kapsam dışı.

### 4.2 Otomatik Askıya Alma ve Geri Açılma

Askıya alma artık §4 adım 3'ün bir parçası (ayrı bir ek grace süresi yok — bkz. yukarıdaki "Toplam pencere 7 gün" notu).

**Geri açılma:** `RecordPlatformPaymentUseCase`, faturayı `PAID` yapıp `billingStatus = ACTIVE` çekmenin yanı sıra, kiracı `SUSPENDED` durumdaysa `TenantAdminPort.activate()` da çağırır — geç ödeyen bir klinik, admin'in ayrıca "Aktif Et"e basmasını beklemeden anında erişimine kavuşur.

**Önemli düzeltme (bu işin bir parçası):** Şu an `LoginUseCase`, `Tenant.status`'e hiç bakmıyor — yani mevcut elle "Askıya Al" butonu bile fonksiyonel olarak hiçbir şeyi engellemiyor (kozmetik). Bu turda düzeltiliyor: `LoginUseCase`, kimlik bilgileri doğrulandıktan sonra `Tenant.status == SUSPENDED` ise ayrı ve açık bir hata (`TenantSuspendedException` — "Kliniğinizin aboneliği askıya alınmış, ödeme sonrası otomatik olarak yeniden aktifleşir" gibi anlaşılır bir mesajla, genel "geçersiz kimlik bilgisi" hatasından ayrı) fırlatır.

## 5. Application Katmanı (Use Case'ler)

- `GenerateDueInvoicesUseCase` — scheduler adım 1, fatura üretir + `PlatformBillingEmailPort`/`PlatformBillingSmsPort`'un `sendInvoiceIssued` metodlarını çağırır
- `RemindDueSoonInvoicesUseCase` — scheduler adım 2, `sendInvoiceDueSoon` çağırır
- `FlagOverdueAndSuspendUseCase` — scheduler adım 3: faturayı `OVERDUE` yapar + `TenantAdminPort.suspend()` + `billingStatus = PAST_DUE` + `sendTenantSuspended`
- `RecordPlatformPaymentUseCase(invoiceId, amount, method, paidAt, notes, recordedByAdminId)` — fatura zaten `PAID`/`VOID` ise hata, değilse `PlatformPayment` oluşturur + `PlatformInvoice.markPaid()` + `TenantAdminPort` üzerinden `billingStatus = ACTIVE` + (kiracı `SUSPENDED` ise) `TenantAdminPort.activate()`
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

TDD ile: `PlatformInvoice`/`PlatformPayment` domain metodları için birim testleri (durum geçişi kuralları — örn. `PAID` bir faturaya tekrar ödeme kaydedilemez), `RecordPlatformPaymentUseCase`/`GenerateDueInvoicesUseCase`/`RemindDueSoonInvoicesUseCase`/`FlagOverdueAndSuspendUseCase` için Mockito ile port mock'lanan use-case testleri (`PlatformBillingEmailPort`/`PlatformBillingSmsPort`'un doğru parametrelerle çağrıldığının doğrulanması dahil, iletişim bilgisi bulunamama senaryosu — o kanal atlanır, akış durmaz — ayrıca test edilir). `LoginUseCase` için yeni bir test: `SUSPENDED` tenant'ın personeli `TenantSuspendedException` alır, `ACTIVE`/`TRIAL` tenant'lar etkilenmez. Ardından gerçek Postgres'e karşı curl ile uçtan uca doğrulama (fatura üretimi → dueSoon hatırlatması → 7. gün otomatik OVERDUE+SUSPENDED → login reddi → ödeme kaydı → otomatik yeniden aktifleşme → login başarılı).

## 9. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
