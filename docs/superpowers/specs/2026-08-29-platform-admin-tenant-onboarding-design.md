# Platform Admin Tenant Onboarding — Tasarım Dokümanı

**Tarih:** 2026-08-29
**Durum:** Implementasyon tamamlandı
**İlgili modül:** `modules/tenant`, `modules/platformadmin`

## 1. Bağlam ve Amaç

Şu anda sistemde bir klinik (tenant) oluşturmanın tek yolu, login sayfasından erişilen self-servis "Klinik kaydı oluştur" akışıdır (`/kayit`, `POST /api/v1/auth/register-clinic`). Herkes bu formu doldurarak kendi kliniğini ücretsiz/kontrolsüz şekilde oluşturabiliyor.

Ürün kararı: MyVet artık self-servis bir SaaS değil — yeni klinikler, platformu işleten firma tarafından (platform admin paneli üzerinden), ücret karşılığı ve kontrollü şekilde oluşturulacak. Login sayfası sadece mevcut kullanıcıların giriş yaptığı bir ekran olmalı; ilk kayıt/kurulum adımı tamamen kaldırılmalı.

## 2. Kapsam

**Kaldırılacak (self-servis kayıt):**
- `frontend/src/pages/LoginPage.tsx` — "Kliniğiniz için ilk kez mi kayıt oluyorsunuz? Klinik kaydı oluştur" linki
- `frontend/src/App.tsx` — `/kayit` route'u
- `frontend/src/pages/auth/RegisterClinicPage.tsx` — tüm dosya
- `frontend/src/api/authApi.ts` — `registerClinic` metodu (varsa ilgili tip/payload)
- `backend/.../tenant/api/AuthController.java` — `POST /register-clinic` endpoint'i
- `backend/.../tenant/application/RegisterClinicUseCase.java` — tüm dosya
- `backend/.../tenant/application/dto/RegisterClinicCommand.java` — tüm dosya
- `backend/.../tenant/api/dto/RegisterClinicRequest.java` — tüm dosya

**Eklenecek (platform admin tarafından klinik oluşturma):**
- `TenantAdminPort.createTenant(CreateTenantCommand): UUID` — yeni port metodu (mevcut 5+5 metodun üzerine 6.'sı)
- `TenantAdminPortAdapter` içinde implementasyon: `Tenant.register()` + `Branch.create()` + `Subscription.startTrial()` + `StaffUser.register(..., StaffRole.ADMIN)` — bugün `RegisterClinicUseCase`'in yaptığı ile birebir aynı domain işlemleri, sadece JWT/`AuthSession` üretmeden (platform admin kendi adına giriş yapmıyor, başka birinin kliniğini oluşturuyor)
- `modules.platformadmin.application.CreatePlatformTenantUseCase` — yeni use case
- `PlatformAdminTenantsController`'a `POST /api/v1/platform-admin/tenants` endpoint'i (mevcut `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` sınıf seviyesinde zaten var)
- `frontend/src/pages/platform-admin/TenantListPage.tsx`'e "Yeni Klinik Oluştur" butonu + modal (klinik adı, vergi no, şube adı, yönetici adı, yönetici e-postası, geçici şifre alanları — `RegisterClinicPage`'in eski formuyla aynı alanlar, sadece platform admin tarafından dolduruluyor)
- `frontend/src/api/platformAdminApi.ts`'e `createTenant` metodu + ilgili TS tipi

**Değişmeyecek:**
- `Tenant`, `Branch`, `Subscription`, `StaffUser` domain sınıfları ve fabrika metodları (`register()`/`create()`/`startTrial()`) — aynen yeniden kullanılıyor, sadece çağıran yer değişiyor.
- Yeni klinik yine `Subscription.startTrial()` ile TRIAL planında başlıyor — platform admin daha sonra mevcut "Plan/Durum Değiştir" modalıyla gerçek plana geçirebilir.
- Login akışı (`LoginUseCase`, `POST /api/v1/auth/login`) hiç değişmiyor.

## 3. Mimari Gerekçe

`TenantAdminPort`, `platformadmin` modülünün `tenant` modülüne erişebildiği TEK sanctioned köprü (`architecture.md` §6.1, port'un kendi Javadoc'unda da belgeli bir "bilinçli sapma"). Spring Modulith `ApplicationModulesTest` bu sınırı zorluyor. Bu yüzden platform admin'in yeni bir tenant yaratabilmesinin tek mimari-tutarlı yolu, bu portu genişletmek — `platformadmin`'in `tenant.application.RegisterClinicUseCase`'i doğrudan çağırması ya da `tenant`'ın repository'lerine doğrudan erişmesi modül sınırını ihlal eder ve mevcut testte kırılır.

## 4. Test/Doküman Etkisi

- `frontend/e2e/golden-path.spec.ts`'in 1. adımı (`Klinik kaydı oluştur`) artık gerçek akışı yansıtmıyor — platform admin girişi yapıp yeni klinik oluşturacak şekilde güncellenmeli.
- `docs/architecture.md`'ye kısa bir not: tenant oluşturmanın tek yolu artık platform admin (§6.1'e eklenecek).
- `backend/src/main/resources/db/migration` — şema değişikliği YOK, sadece kod tarafı.

## 5. Spec Self-Review

- **Placeholder taraması:** Yok.
- **İç tutarlılık:** Kaldırılan/eklenen dosya listeleri birbiriyle çelişmiyor; `TenantAdminPort`'un mevcut Javadoc'unda "kiracıyı görüntüleyip DEĞİŞTİREBİLME" ifadesi var — "yaratma" da bu bilinçli sapmanın doğal bir uzantısı, port'un Javadoc'u planlama aşamasında güncellenecek.
- **Kapsam:** Tek, odaklı bir değişiklik — tenant onboarding sorumluluğunun taşınması. Ödeme tahsilatı (platform admin'in klinik sahibinden nasıl ücret alacağı) bu kapsamın dışında; mevcut platform billing sistemi (abonelik/fatura) zaten tenant oluşturulduktan SONRAKİ süreci kapsıyor.
- **Belirsizlik:** Yok — kullanıcı iki netleştirici soruda kapsamı (tam kapsam: admin panelinde oluşturma eklensin) ve şifre modelini (admin geçici şifre belirler) net şekilde onayladı.
