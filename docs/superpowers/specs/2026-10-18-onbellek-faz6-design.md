# Faz 6 — Önbellek (caching) ve özet sorguları (tasarım)

**Tarih:** 2026-10-18
**Yol haritası:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` §Faz 6
**Durum:** Onaylandı (bölüm bölüm, 2026-10-18)

## 1. Amaç ve kapsam

Kullanıcı kararı: **"ölç-düzelt" kapsamı (A)**. Tek sunucu ve küçük-orta klinikler için önbelleğin bugünkü getirisi sınırlı. En büyük kazanç verimsiz özet sorgularını düzeltmekte. Bu yüzden:

- **Önbellek** yalnızca gerçekten işe yarayan üç yere eklenir:
  - kiracının paket kodu,
  - paketin modül ve kota bilgisi,
  - TARBİL hastalık listesi.
- **Ana sayfa özetleri önbelleğe alınmaz.** Bütün satırları belleğe çekmek yerine veritabanında toplanır ve sayılır. Ay ve gün sınırları İstanbul saatine alınır.

Kapsam dışı:
- Özet uçlarına süreli önbellek (yeni fatura ana sayfaya gecikmeli yansırdı; reddedildi).
- HTTP `Cache-Control` başlıkları ve ön yüz istemci önbelleği (React Query).
- Redis ve çok sunucu.
- Kişisel veri içeren herhangi bir önbellek.

## 2. Altyapı (`platform.cache`)

- Bağımlılık: `org.springframework.boot:spring-boot-starter-cache`. Sürüm Boot BOM'undan gelir. Caffeine Faz 4'ten beri zaten projede.
- Yöntem: Spring Cache etiketleri (`@Cacheable`, `@CacheEvict`). Kullanıcı kararı, açık Caffeine alternatifine karşı.
- `com.vetos.platform.cache.CacheConfig`:
  - `@EnableCaching`.
  - `CaffeineCacheManager` kurulur ve **`TransactionAwareCacheManagerProxy`** ile sarılarak `CacheManager` bean'i olarak verilir. Böylece put ve evict işlemleri veritabanı işlemi commit olduktan sonra uygulanır; rollback olursa önbelleğe dokunulmaz. (Plan aşamasında düzeltildi: Spring 6.2'de `CaffeineCacheManager`'da `setTransactionAware` yok; aynı davranış bu sarmalayıcıyla elde edilir. Boot'un önbellek metrikleri sarmalayıcıyı açıp Caffeine'e ulaşıyor (`CacheMetricsRegistrar.unwrapIfNecessary`), yani metrikler etkilenmez.)
  - Her önbellek `registerCustomCache` ile kendi Caffeine ayarıyla kaydedilir. Tanımsız ada dinamik önbellek açılmaz: `setCacheNames` veya eşdeğeri kullanılır. Yanlış yazılmış ad sessizce sınırsız önbellek yaratmasın.
  - `recordStats()` açık.
- `com.vetos.platform.cache.CacheNames` sabitleri: `TENANT_PLAN_CODE = "tenantPlanCode"`, `PLAN_BY_CODE = "planByCode"`, `TARBIL_DISEASES = "tarbilDiseases"`.
- Modulith: `platform.cache` için named interface tanımlanır. `CacheNames` kullanan modüllerin (platformadmin, integration/tarbil) `allowedDependencies` listesi varsa `platform::cache` eklenir. `ApplicationModulesTest` yeşil kalmalı.

| Önbellek | İçerik | Anahtar | Süre (`expireAfterWrite`) | `maximumSize` |
|---|---|---|---|---|
| `tenantPlanCode` | Kiracının abonelikteki paket kodu (`String`) | `#tenantId` | 5 dk | 10 000 |
| `planByCode` | `PlanSnapshot` | `#code` | 5 dk | 200 |
| `tarbilDiseases` | `List<TarbilDiseaseSummary>` (değiştirilemez) | sabit (parametresiz) | yok | 1 |

- Metrikler: Spring Boot, `CacheManager` önbelleklerini Micrometer'a bağlar. Faz 3'ün 8081 Prometheus ucunda `cache_gets_total{cache=…,result=hit|miss}`, `cache_size`, `cache_evictions_total` vb. görünür. Bağlantı testinde doğrulanır.

### 2.1 Kurallar (her yeni önbellek için geçerli)

1. **Kiracı anahtarı açık yazılır.** Kiracıya özel önbellekte `key = "#tenantId"` zorunlu. Kiracı kimliği metoda parametre olarak gelir. Önbellekli metodun içinde `TenantContext` okunmaz.
2. **Ayrı sınıf.** Önbellekli metodlar yalnızca başka bean'lerden çağrılan bir sınıfta durur. Sınıf kendi metodunu çağırırsa proxy atlanır ve önbellek sessizce devre dışı kalır.
3. **Entity önbelleğe konmaz.** Önbelleğe yalnızca sade ve değiştirilemez kayıtlar (record, `List.copyOf` / `Set.copyOf`) konur.
4. **Kişisel veri önbelleğe alınmaz:** hasta, sahip, personel, TCKN, telefon, e-posta.
5. **"Bulunamadı" önbelleğe yazılmaz:** `unless = "#result == null"`. `Optional` dönen metodda Spring değeri açar, yani boş `Optional` da yazılmaz. Yeni açılan klinik ya da paket eski bir "yok" cevabına takılmaz.

Güvenlik notu: Önbellek uygulamanın kendi belleğinde, şifresiz Java nesnesi olarak durur. Diske yazılmaz, ağdan erişilemez. `heapdump` uç noktası dışarıya açık değil (`management.endpoints.web.exposure.include: health,metrics,prometheus`). Bu ayar değiştirilmemeli.

## 3. Paket bilgisi önbelleği (platformadmin)

- Yeni bean: `com.vetos.modules.platformadmin.infrastructure.adapter.PlanLookup`. Paket görünürlüğü yalnızca modül içinde; aynı paketteki iki adaptör kullanır.
  - `@Cacheable(cacheNames = TENANT_PLAN_CODE, key = "#tenantId", unless = "#result == null") Optional<String> planCodeOf(UUID tenantId)`: `TenantAdminPort.findPlanCode` çağırır.
  - `@Cacheable(cacheNames = PLAN_BY_CODE, key = "#code", unless = "#result == null") Optional<PlanSnapshot> planByCode(String code)`: `PlanRepository.findByCode` sonucunu `PlanSnapshot`'a çevirir.
- `PlanSnapshot` (record, aynı paket): `String code`, `Set<Feature> enabledFeatures` (`Set.copyOf`), `Integer monthlyAiQuota`, `Integer monthlyMessageQuota`. `null` kota "sınırsız" anlamına gelir. `null` kota korunmalı; `Optional.map` ile kayıp yaşanmaz, çünkü snapshot alanı nullable.
- `PlanFeatureAccessAdapter` ve `PlanUsageQuotaAdapter`, `TenantAdminPort.findPlanCode` ve `PlanRepository.findByCode` yerine `PlanLookup` kullanır. Karar sırası **birebir aynı**:
  - kısıtlama kapalı → hepsi açık / say ama engelleme;
  - abonelik yok → hepsi kapalı / kota 0 (+ WARN);
  - TRIAL → hepsi açık / yml kotası;
  - paket → işaretler / sütun;
  - paket satırı yok → hepsi kapalı / 0 (+ WARN).
- İki adaptörün Javadoc'undaki "Önbellek yok" cümlesi güncellenir.
- Temizleme (`@CacheEvict`, commit sonrası):

| Use case | Etiket |
|---|---|
| `CreatePlanUseCase.execute` | `@CacheEvict(cacheNames = PLAN_BY_CODE, allEntries = true)` |
| `UpdatePlanUseCase.execute` | `@CacheEvict(cacheNames = PLAN_BY_CODE, allEntries = true)` (paket kodu değişse de güvenli) |
| `DeletePlanUseCase.execute` | `@CacheEvict(cacheNames = PLAN_BY_CODE, allEntries = true)` |
| `UpdateTenantSubscriptionUseCase.execute` | `@CacheEvict(cacheNames = TENANT_PLAN_CODE, key = "#command.tenantId()")` |

  Kiracı oluşturma akışları (`CreatePlatformTenantUseCase`, `HandleSignupPaymentCallbackUseCase`) temizlik gerektirmez: "bulunamadı" önbelleğe yazılmaz.
- Sonuç: admin değişikliği sonraki istekte görünür. Veritabanı elle değiştirilirse değer en geç 5 dakikada tazelenir. `GetEnabledFeaturesUseCase` (ön yüz menüsü) da aynı porttan okuduğu için önbellekten yararlanır.

## 4. TARBİL hastalık listesi

- `ListTarbilDiseasesUseCase.execute()` → `@Cacheable(TARBIL_DISEASES)`. Parametresiz; anahtar Spring'in boş anahtarı. Kiracıya özel değil, bütün kliniklerde aynı referans tablosu.
- Dönen liste zaten `Stream.toList()` ile değiştirilemez. Elemanlar record.
- Tablo yalnızca migration ile değişir. Deploy uygulamayı yeniden başlattığı için süre ya da temizlik yok.
- Reçete doğrulamasındaki kimlikle tek kayıt sorgusu (`TarbilDiseaseRepository` diğer kullanımları) değişmez.

## 5. Ana sayfa özetleri (önbelleksiz)

Ortak:
- Ay ve gün sınırları `ZoneId.of("Europe/Istanbul")` ile hesaplanır.
- Sınırlar `Instant` olarak sorguya verilir: `[başlangıç, bitiş)`.
- Cevap DTO'ları ve gelir sayılan fatura durumları (`REVENUE_STATUSES`) değişmez.
- Ön yüz değişikliği yok.

| Use case | Bugün | Yeni |
|---|---|---|
| `GetRevenueSummaryUseCase` | `findByTenantId` ile tüm faturalar, Java'da gruplama, UTC ay | (a) Son 6 ay için `issued_at` İstanbul ayına göre `SUM(total_amount)` gruplu sorgu. Native: `date_trunc('month', issued_at AT TIME ZONE 'Europe/Istanbul')`, ya da 6 ayrı aralık toplamı; plan seçer. (b) Bu ay (İstanbul) için `branch_id`'ye göre `SUM` gruplu sorgu. Boş aylar `0`. Şube adı okuma aynı. |
| `GetPatientGrowthSummaryUseCase` | İki ay liste çekip `.size()`, UTC ay | İki `COUNT` sorgusu (`tenant_id`, `created_at` aralığı), İstanbul ayı |
| `GetAppointmentActivitySummaryUseCase` | İki ay liste çekip Java'da sayma (zaten İstanbul) | Her ay için toplam `COUNT` + `status = NO_SHOW` `COUNT`. Oran hesabı aynı (toplam 0 → 0.0). |
| `GetTodaySalesSummaryUseCase` | Toplama sorgusu var, gün UTC | Yalnızca gün sınırı İstanbul |

- Sorgular repository portuna eklenir (ör. `InvoiceRepository.sumIssuedByMonth…`, `PatientRepository.countByTenantIdAndCreatedAtRange`, `AppointmentRepository.countByTenantIdAndDateRange[AndStatus]`). Kullanılmayan liste metodları başka çağıranı yoksa kaldırılabilir; plan aşamasında kontrol edilir.
- Davranış değişikliği (bilinçli): ayın 1'inde ya da bugün 00:00–03:00 (İstanbul) arasında kesilen fatura veya eklenen hasta artık doğru aya/güne sayılır.

## 6. Testler

- **Kiracı izolasyonu:** `PlanLookup.planCodeOf(A)` ve `planCodeOf(B)` farklı değerler döner. Önbellek doluyken de karışmaz. Aynı kiracı için ikinci çağrı veritabanına gitmez (spy/sayaç).
- **Commit sonrası temizlik (SpringBootTest, gerçek Postgres):**
  - Abonelik güncellenince sonraki `isEnabled` / kota yeni paketi görür.
  - Paket güncellenince (modül işareti, kota) sonraki okuma yeni değeri görür.
  - Temizleyen işlem rollback olursa önbellek eski değeri korur.
- **"Bulunamadı" yazılmaz:** aboneliği olmayan kiracı → kapalı/0. Abonelik eklenince (temizlik çağrılmadan) sonraki okuma yeni değeri görür.
- **Süre:** `tenantPlanCode` ve `planByCode` süre ayarı 5 dk. Bağlantı testinde önbellek ayarlarından okunur (Caffeine policy), beklenmez.
- **Adaptör davranışı:** `PlanFeatureAccessAdapter` ve `PlanUsageQuotaAdapter` mevcut testleri aynen yeşil (TRIAL, null kota = sınırsız, paket yok, kısıtlama kapalı).
- **TARBİL:** iki çağrıda repository bir kez çağrılır. Dönen liste değiştirilemez.
- **Özetler:** mevcut testler yeşil, ayrıca:
  - İstanbul sınırı: 1'i 00:30 (İstanbul) faturası bu aya, son günün 23:30 faturası o aya;
  - boş aylar 0;
  - şube kırılımı;
  - gelir sayılmayan durumdaki fatura hariç;
  - randevu "gelmedi" oranı.
- **Bağlantı:** `CacheManager` üç önbelleği yml/config değerleriyle kurulu; tanımsız ad için önbellek açılmıyor; `cache_gets_total` metriği kayıtlı.
- `ApplicationModulesTest` yeşil. Tüm backend testleri (825+) yeşil. Ön yüz derlemesi gerekmez (ön yüz değişikliği yok).

## 7. Dağıtım

- DB değişikliği yok (yeni migration yok). Özet sorguları için mevcut indeksler plan aşamasında kontrol edilir; gerekirse `(tenant_id, issued_at)` indeksi önerilir, migration ancak gerekiyorsa.
- `docs/deployment.md`: "Önbellek (Faz 6)" bölümü:
  - hangi önbellekler var;
  - veritabanından elle paket değişikliğinin en geç 5 dk'da yansıdığı;
  - TARBİL listesinin deploy ile tazelendiği;
  - metrik adları ve örnek sorgu.
- Yol haritası: Faz 6 ✅, sıradaki Faz 7.
- Commit/push yok.
