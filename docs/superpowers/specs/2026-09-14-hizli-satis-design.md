# Hızlı Satış (Retail POS) — Tasarım Dokümanı

**Tarih:** 2026-09-14
**Durum:** Onaylandı, implementasyon bekleniyor
**İlgili modül:** `modules/billing`, `modules/inventory`, `modules/patient` (Owner), frontend `pages/finance`, `pages/dashboard`

## 1. Bağlam ve Amaç

`docs/requirements.md` 4.14'te "Barkodlu satış (retail POS): Mama, aksesuar gibi retail ürünler için hızlı satış akışı — muayene/reçete akışından ayrı, kasa ile entegre" Faz 2 kapsamında listeli. `frontend/src/pages/dashboard/QuickAddMenu.tsx` içinde de bunu doğrulayan bir kod yorumu var: "Yeni satış" (retail POS) ... henüz backend'i yok, bu yüzden menüden çıkarıldı.

Şu an mevcut olan: bir müşteriye manuel fatura açma (`NewInvoiceModal` → `InvoiceDetailModal`), fatura kalemi ekleme (ama sadece **hizmet tipleri**, `AddInvoiceLineRequest`'te `inventoryItemId` yok), ödeme alma — hepsi ayrı adımlarda, ayrı modallarda, ve **zorunlu olarak kayıtlı bir müşteri gerektiriyor**.

Bu doküman iki eksiği kapatıyor:
1. Kayıtsız/yoldan geçen ("Anonim") bir müşteriye de satış yapılabilmesi.
2. Stok kalemlerinin (ürünlerin) fatura kalemi olarak eklenebilmesi + otomatik stok düşümü.

Ve bunları **tek ekranlı, tek adımlı** bir "Hızlı Satış" akışında birleştiriyor: ürün(ler) seç → ödeme al → bitti.

## 2. Kapsam

**Bu turda yapılacak:**
- `owners` tablosuna tenant başına tek bir "Anonim Müşteri" sentinel kaydı (lazy oluşturulur).
- `AddInvoiceLineRequest/Command/UseCase`'e opsiyonel `inventoryItemId` desteği + stok yetersizse engelleme (yeni davranış, sadece bu akışa özel).
- Yeni `CompleteQuickSaleUseCase` + `POST /api/v1/invoices/quick-sale`: tek transaction'da fatura aç + kalemleri ekle (stok düş) + kes + tam ödeme al.
- Yeni `GetTodaySalesSummaryUseCase` + `GET /api/v1/invoices/today-summary`: tüm rollere açık, sadece `{totalAmount, saleCount}`.
- Frontend: `QuickSaleModal.tsx` (müşteri seç/anonim, ürün sepeti, ödeme), Finans sayfasına buton, `QuickAddMenu`'ye "Yeni satış" seçeneği, `DashboardPage` üst KPI şeridine "Bugünkü Satış" kartı.

**Kapsam dışı (bilinçli olarak):**
- Barkod okuyucu donanım entegrasyonu (fiziksel tarayıcı) — şimdilik ürün adından arama/seçim yeterli, `skuBarcode` alanı zaten var, ileride input'a odaklanıp barkod okutmak aynı arama kutusuna yazı yazmakla aynı şekilde çalışacak (donanım açısından ek iş gerekmiyor).
- Stok kalemlerine ayrı bir "satış fiyatı" alanı (kullanıcı kararı: tüm kalemler satılabilir, fiyat elle girilir).
- Kısmi ödeme ile hızlı satış tamamlama (kullanıcı kararı: tek adımda tam ödeme).
- Anonim müşteriye özel raporlama/segmentasyon (mevcut "Müşteriye göre" raporlarda "Anonim Müşteri" adıyla normal bir müşteri gibi görünür, ekstra bir ayrım yapılmaz).

## 3. Veri Modeli

### 3.1 `owners.is_anonymous_placeholder` (yeni kolon, migration `V35`)

```sql
ALTER TABLE owners ADD COLUMN is_anonymous_placeholder BOOLEAN NOT NULL DEFAULT FALSE;
```

`Owner` entity'sine `isAnonymousPlaceholder` alanı + `Owner.createAnonymousPlaceholder(tenantId)` factory metodu eklenir (fullName="Anonim Müşteri", phone="0000000000", diğer alanlar null/varsayılan — bu kayıt hiçbir zaman normal owner formundan düzenlenmez).

`OwnerRepository`'e `findAnonymousPlaceholder(UUID tenantId)` eklenir (native query, `WHERE tenant_id = :tenantId AND is_anonymous_placeholder = true LIMIT 1`).

### 3.2 Inventory — şema değişikliği YOK

Kullanıcı kararı gereği satış fiyatı alanı eklenmiyor; mevcut `InventoryItem.unitCost` sadece maliyet takibi için kalır, satış fiyatı her seferinde fatura kaleminde elle girilir.

## 4. Backend Akışı

### 4.1 `GetOrCreateAnonymousOwnerUseCase` (yeni, `modules/patient/application`)

```java
@Transactional
public UUID execute(UUID tenantId) {
    return ownerRepository.findAnonymousPlaceholder(tenantId)
        .map(Owner::getId)
        .orElseGet(() -> ownerRepository.save(Owner.createAnonymousPlaceholder(tenantId)).getId());
}
```

Nadir bir race koşulunda (aynı anda iki ilk-satış) iki sentinel owner oluşabilir — bu, veri bütünlüğünü bozmaz (sadece "Anonim Müşteri" adında iki kayıt olur), bu yüzden ekstra kilitleme/unique index eklenmiyor (YAGNI).

**Modül sınırı:** `billing`, bu use case'e (patient'ın `application` katmanı) doğrudan erişemez — aynı §4.3'teki gerekçeyle. `OwnerLookupPort`'a (zaten billing→patient köprüsü olarak kullanılıyor) yeni bir metot eklenir:

```java
// modules/patient/domain/OwnerLookupPort.java
UUID getOrCreateAnonymousOwnerId(UUID tenantId);
```

`OwnerLookupAdapter` (patient/infrastructure) bunu `GetOrCreateAnonymousOwnerUseCase`'e delege ederek uygular (aynı modül içi çağrı, sorun yok). `billing/application/CompleteQuickSaleUseCase`, `GetOrCreateAnonymousOwnerUseCase`'i değil, `OwnerLookupPort`'u enjekte eder.

### 4.2 `InsufficientStockException` (yeni, `modules/inventory/domain/exception`)

`DomainException` alt sınıfı, errorCode `INSUFFICIENT_STOCK` → 422 (mevcut `*Exception` isimlendirme kuralına göre).

### 4.3 `AddInvoiceLineUseCase` genişletmesi

- `AddInvoiceLineRequest`/`AddInvoiceLineCommand`'a `UUID inventoryItemId` (opsiyonel) eklenir.
- **Modül sınırı:** `billing`, `inventory`'nin `application` katmanına (`RecordStockMovementUseCase`) DOĞRUDAN erişemez — her modülün sadece `domain` paketi `@NamedInterface("domain")` ile diğer modüllere açık (bkz. `OwnerLookupPort` deseni, `modules/patient/domain`). Bu yüzden yeni bir port tanımlanır:
  - `modules/inventory/domain/StockDeductionPort.java` (yeni arayüz):
    ```java
    public interface StockDeductionPort {
        /** quantityOnHand yetersizse InsufficientStockException firlatir, aksi halde OUT hareketi kaydeder. */
        void deductForSale(UUID inventoryItemId, int quantity, UUID invoiceId);
    }
    ```
  - `modules/inventory/infrastructure/StockDeductionAdapter.java` (yeni, aynı modül içi `RecordStockMovementUseCase`'i sarar): stok kontrolü yapar (`quantityOnHand < quantity` ise `InsufficientStockException`), yeterliyse `RecordStockMovementUseCase.execute(inventoryItemId, OUT, quantity, StockReferenceType.MANUAL, invoiceId)` çağırır.
  - `billing/application/AddInvoiceLineUseCase`, `com.vetos.modules.inventory.domain.StockDeductionPort`'a bağımlı olur (constructor injection, Spring otomatik `StockDeductionAdapter`'ı bağlar) — `inventory`'nin `application`/`infrastructure` paketlerini ASLA import etmez.
- `inventoryItemId` doluysa: `stockDeductionPort.deductForSale(...)` çağrılır (yetersizse exception, transaction rollback), sonra `InvoiceLine.create(...)`'a `inventoryItemId` geçirilir.
- Plan aşamasında `ApplicationModules.verify()` testinin yeşil kaldığı doğrulanacak.

### 4.4 `CompleteQuickSaleUseCase` (yeni, `modules/billing/application`)

```java
public record QuickSaleLineCommand(UUID inventoryItemId, String description, int quantity, BigDecimal unitPrice) {}
public record CompleteQuickSaleCommand(
    UUID branchId, UUID ownerId /* null ise anonim */, UUID staffUserId,
    List<QuickSaleLineCommand> lines, PaymentMethod paymentMethod
) {}

@Transactional
public UUID execute(CompleteQuickSaleCommand command) {
    UUID resolvedOwnerId = command.ownerId() != null
        ? command.ownerId()
        : ownerLookupPort.getOrCreateAnonymousOwnerId(TenantContext.current());

    UUID invoiceId = createManualInvoiceUseCase.execute(command.branchId(), resolvedOwnerId, command.staffUserId());

    for (QuickSaleLineCommand line : command.lines()) {
        addInvoiceLineUseCase.execute(new AddInvoiceLineCommand(
            invoiceId, line.description(), line.quantity(), line.unitPrice(),
            BigDecimal.ZERO, BigDecimal.ZERO, null, line.inventoryItemId()
        ));
    }

    issueInvoiceUseCase.execute(invoiceId);

    BigDecimal total = invoiceRepository.findById(invoiceId).orElseThrow().getTotalAmount();
    recordPaymentUseCase.execute(new RecordPaymentCommand(invoiceId, command.paymentMethod(), total, null));

    return invoiceId;
}
```

Herhangi bir satırda stok yetersizse (`InsufficientStockException`) tüm transaction rollback olur — yarım kalan fatura/stok hareketi kalmaz.

`@PostMapping("/quick-sale")` → `InvoicesController`'a eklenir (class-level `RECEPTIONIST/ADMIN` yetkisi zaten uygun).

### 4.5 `GetTodaySalesSummaryUseCase` (yeni, `modules/billing/application`)

`GetRevenueSummaryUseCase` ile aynı desen (`invoiceRepository.findByTenantId` + Java'da filtrele), ama sadece bugünün (UTC gün sınırı) `ISSUED/PARTIALLY_PAID/PAID` faturalarını toplar:

```java
public record TodaySalesSummary(BigDecimal totalAmount, int saleCount) {}
```

`GET /api/v1/invoices/today-summary` → `InvoicesController`'da **method-level** `@PreAuthorize("hasAnyRole('VET','TECHNICIAN','RECEPTIONIST','ADMIN')")` ile class-level kısıtlamayı bu tek uç nokta için gevşetir (diğer tüm `/invoices/**` uçları RECEPTIONIST/ADMIN'e kapalı kalmaya devam eder).

## 5. Frontend

### 5.1 `QuickSaleModal.tsx` (yeni, `pages/finance/`)

- Müşteri alanı: mevcut owner-picker deseni (ara/seç) + üstte tek tıkla "Anonim / Günlük Müşteri" butonu (seçilince picker'ı atlar, `ownerId=null` kalır).
- Ürün ekleme satırı: `inventoryApi.list()`'ten çekilen kalemler arasında arama/seçim (isim + `skuBarcode` ile filtrelenebilir arama kutusu) → seçilince adet ve birim fiyat (elle girilir, boş başlar) girilir → "Sepete Ekle".
- Sepet listesi: eklenen kalemler, her biri silinebilir, toplam tutar canlı hesaplanır.
- Ödeme yöntemi seçimi (mevcut `PAYMENT_LABELS` ile aynı: Nakit/Kart/Text-to-Pay/Taksit).
- "Satışı Tamamla" → `billingApi.quickSale(...)` çağrısı, başarılıysa modal kapanır + `onCompleted(invoiceId)`.
- Stok yetersizliği hatası (`INSUFFICIENT_STOCK`) kullanıcıya o satır için açık bir hata mesajıyla gösterilir.

### 5.2 Giriş noktaları

- `FinancePage.tsx`'e "Hızlı Satış" birincil butonu (kasa/fatura listesinin yanına).
- `QuickAddMenu.tsx`'teki yorum satırı kaldırılır, "Yeni satış" seçeneği eklenir (ikonla birlikte, `onSelect` modalı açar — dashboard'dan direkt route değişmeden).

### 5.3 `DashboardPage.tsx` — "Bugünkü Satış" KPI kartı

Üst `kpiStrip`'e (herkese görünen, operasyonel şerit) yeni bir kart: `billingApi.todaySalesSummary()` ile `{totalAmount, saleCount}` çekilir, "Bugünkü Satış" başlığıyla `₺{totalAmount}` gösterilir (mevcut "Bugünkü randevu" kartıyla aynı stil).

## 6. Hata Durumları

| Durum | Davranış |
|---|---|
| Stok yetersiz | `InsufficientStockException` (422) → tüm satış rollback, kullanıcı hangi üründe yetersiz olduğunu görür |
| Anonim müşteri ilk kez oluşturuluyor | Sessizce lazy-create, kullanıcı fark etmez |
| Sepet boşken "Satışı Tamamla" | Frontend'de buton disabled (mevcut `InvoiceDetailModal`'daki "Faturayı Kes" ile aynı desen) |
| Ödeme tutarı fatura toplamından farklı olamaz | Quick-sale akışında tutar backend'de hesaplanan toplamdan alınır, kullanıcı elle giremez (mevcut "Ödeme Al" formundan farklı olarak burada kısmi ödeme seçeneği yok) |

## 7. Test Planı

**Backend:**
- `CompleteQuickSaleUseCaseTest`: anonim müşteri ile satış (yeni owner oluşturulduğunu doğrula), kayıtlı müşteri ile satış, stok yetersizken tüm transaction'ın rollback olduğunu doğrula (fatura/stok hareketi kalmamalı).
- `GetOrCreateAnonymousOwnerUseCaseTest`: ilk çağrıda oluşturma, ikinci çağrıda aynı kaydı döndürme.
- `AddInvoiceLineUseCaseTest` (mevcut varsa genişlet, yoksa yeni): `inventoryItemId` ile ekleme + stok düşümü, yetersiz stokta exception.
- `GetTodaySalesSummaryUseCaseTest`: bugün/dün ayrımı, sadece gelir sayılan statüler.

**Frontend:**
- Yeni bir e2e adımı (`quick-sale.spec.ts` veya golden-path'e ek): anonim müşteriyle bir ürün satışı, sepete ekleme, ödeme, dashboard'da "Bugünkü Satış" rakamının arttığını doğrulama.

## 8. Modül Bağımlılığı Notu

`modules/billing` → `modules/patient` bağımlılığı zaten mevcut (`OwnerLookupPort` ile, `GetOwnerBalancesUseCase` vb.) — bu turda sadece `OwnerLookupPort`'a bir metot ekleniyor, yeni bir bağımlılık değil. `modules/billing` → `modules/inventory` bağımlılığı YENİ ama §4.3'te tarif edilen `StockDeductionPort` deseniyle (domain-paketi-üzerinden-port, `OwnerLookupPort` ile birebir aynı yaklaşım) kuruluyor. Her iki durumda da `billing`, hedef modülün `application`/`infrastructure` paketlerine doğrudan erişmiyor — sadece `domain` paketindeki (`@NamedInterface("domain")`) port arayüzlerini kullanıyor. Plan aşamasında `ApplicationModules.verify()` testinin bu yeni port ile birlikte yeşil kaldığı doğrulanacak.
