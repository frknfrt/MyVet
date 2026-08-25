# Ekran Öncelikleri — P0 Özeti (Faz 1)

Tam liste (P0/P1/P2/P3, notlarla): `docs/screen-priorities.xlsx`

## P0 Ekranlar (MVP kapsamı)


### 0. Global Sistem (Auth)
- [ ] Splash Screen
- [x] Login
- [x] Register Clinic
- [ ] Forgot Password
- [x] Clinic Setup Wizard
- [ ] Invite Team Members — *(Kullanıcı Yönetimi admin'in doğrudan şifre belirleyerek hesap açmasını sağlıyor; e-posta davet linki akışı yok)*
- [ ] Subscription Plan Selection — *(kayıtta otomatik 14 günlük deneme atanıyor; plan seçim ekranı yok, bkz. Ayarlar > Abonelik'in salt-okunur görünümü)*

### 1. Dashboard
- [ ] Ana Panel (rol bazli render: Doktor/Resepsiyon/Yonetici) — *(Dashboard tüm roller için aynı görünümü render ediyor, rol bazlı dallanma yok)*
- [x] Gunluk Operasyon Panosu (Clinic Flow Board)

### 2. Hasta (Pet) Modulu
- [x] Pet List
- [x] Pet Search
- [x] New Patient Create
- [x] Patient Profile
- [ ] Patient Health Summary — *(profil kartında vital/kimlik bilgileri var ama ayrı bir "sağlık özeti" — aktif problemler, vital trend — yok)*
- [ ] Patient Timeline / Clinical Timeline — *(Muayene/Aşı/Reçete/Lab/Görüntüleme ayrı sekmeler halinde kronolojik listeleniyor; tek birleşik zaman çizelgesi yok)*
- [x] Medical History
- [x] Vaccination History

### 3. Owner / Musteri CRM
- [ ] Owner List — *(bağımsız gezilebilir sahip listesi ekranı yok; sahip arama sadece diğer formlarda otomatik-tamamlama olarak kullanılıyor)*
- [x] Owner Profile
- [x] Owner Pets
- [x] KVKK Riza Yonetimi — *(Yeni Sahip formunda zorunlu "KVKK Açık Rıza" checkbox'ı + kayıt anında otomatik ConsentRecord; OwnerDetailPage > KVKK sekmesinde 3 onay türü için ver/geri çek + geçmiş — bkz. implementation-plan.md)*

### 4. Randevu ve Takvim
- [x] Calendar Main View
- [ ] Daily Calendar — *(takvim sadece haftalık grid; günlük görünüm/toggle yok)*
- [x] Weekly Calendar
- [ ] Doctor Schedule — *(hekim bazlı filtre/görünüm yok, tüm randevular kliniğe göre tek listede)*
- [x] Appointment Create
- [x] Appointment Detail

### 5. Klinik / Muayene (urunun kalbi)
- [x] Examination Start
- [x] SOAP Note Screen (S/O/A/P)
- [ ] Voice Recording AI — *(tarayıcı yerleşik Web Speech API ile gerçek sesli dikte çalışıyor; özel/barındırılan bir ses-AI servisi değil)*
- [ ] AI SOAP Generator — *(uçtan uca bağlı ama backend adaptörü stub — gerçek model henüz bağlı değil, transkripti Subjective alanına kopyalıyor; UI bunu açıkça uyarıyor)*
- [ ] Physical Examination Form — *(sadece genel Vital Bulgular kartı var; yapılandırılmış fiziksel muayene formu yok, bulgular serbest metin Objective alanına giriyor)*
- [ ] Diagnosis Selection (+ AI oneri) — *(Assessment serbest metin; tanı seçim UI'ı veya AI öneri endpoint'i yok)*
- [ ] Treatment Plan (+ AI oneri) — *(Plan serbest metin; ayrı bir tedavi planı UI'ı veya AI öneri endpoint'i yok)*
- [x] Prescription Create — *(ilaç etkileşim uyarısı: kural tabanlı çapraz kontrol eklendi, bkz. implementation-plan.md — etkileşim verisi gerçek bir farmakolojik referans kaynağından uydurulmadı, Ayarlar > İlaç Kataloğu'nda klinik/hekim tarafından girilir)*
- [x] Prescription History

### 6. Laboratuvar
- [x] Lab Result List
- [x] Yeni Tahlil İsteği
- [x] Lab Result Detail (sonuç girişi, dosya eki)
- [x] Kural Tabanlı Ön-Değerlendirme (AI)

### 7. Görüntüleme
- [x] Imaging Record List
- [x] Yeni Görüntüleme Kaydı
- [x] Imaging Record Detail (dosya yükleme/indirme)

### 8. Stok ve Envanter
- [ ] Inventory Dashboard — *(tek düz ürün listesi + düşük stok/SKT rozetleri; ayrı bir KPI/özet dashboard'u yok)*
- [x] Product List
- [x] Product Detail
- [x] Add Product
- [ ] Medicine Management — *(stok kalemlerinde hâlâ sadece serbest metin "kategori" alanı var, stokla entegre değil; ancak `DrugCatalog`/`DrugsController` artık kullanılıyor — Ayarlar > İlaç Kataloğu ekranından ilaç CRUD + etkileşim işaretleme yapılıyor, bkz. Prescription Create notu)*
- [ ] Vaccine Management — *(ayrı bir aşı-stok kataloğu yok, genel envanter kategorisine giriyor — aşı takvimi/kayıtları farklı bir konsept olarak zaten var, bkz. Vaccination History)*
- [x] Stock Movement

### 9. Finans ve Muhasebe
- [ ] Finance Dashboard — *(Finans sayfası Faturalar/Kasa/Borç Listesi sekmeli bir liste ekranı; ayrı markalı bir KPI dashboard'u yok — Raporlar sayfası ve Dashboard'daki İşletme Özeti buna en yakın)*
- [x] Invoice List
- [ ] Create Invoice — *(manuel "yeni fatura" oluşturma yok, `POST /api/v1/invoices` endpoint'i de yok; faturalar encounter tamamlanınca otomatik taslak olarak açılıyor, UI sadece mevcut taslağa kalem ekleyip kesiyor)*
- [ ] Payment Screen — *(bağımsız bir ekran değil, fatura detay modalı içinde "Ödeme Al" bölümü olarak var)*
- [x] Subscription Management (klinik SaaS aboneligi)
- [ ] Payment Integration — *(gerçek ödeme sağlayıcı — iyzico/PayTR — entegrasyonu yok; tüm ödemeler CARD/CASH/TEXT_TO_PAY/INSTALLMENT olarak elle kaydediliyor)*
- [x] e-Fatura / e-Arsiv Durum Ekrani
- [x] TARBIL Senkron Ekrani
- [x] Kasa Yonetimi (gunluk acilis/kapanis, nakit mutabakati)
- [x] Borc Listesi / Cari Hesap Takibi
- [x] Hekim Bazlı Performans Raporu
- [x] Şube Karşılaştırma Raporu

### 10. Yatış (Boarding)
- [x] Konaklama Listesi / Oda Durumu
- [x] Yeni Konaklama Kaydı
- [x] Oda Yönetimi

### 11. AI Merkezi
- [ ] Voice To SOAP
- [ ] Diagnosis Assistant
- [ ] Treatment Recommendation
- [ ] Drug Interaction Check

### 12. Yonetim ve Ayarlar
- [ ] Clinic Settings — *(kapsam dışı bırakıldı; bkz. implementation-plan.md)*
- [x] Branch Management
- [x] User Management — *(bkz. implementation-plan.md, Kullanıcı Yönetimi ile birleştirildi)*
- [x] Role Permission — *(salt-okunur görünüm)*
- [x] Doctor Profile — *(bkz. implementation-plan.md, Kullanıcı Yönetimi ile birleştirildi)*
- [x] Employee Management — *(bkz. implementation-plan.md, Kullanıcı Yönetimi ile birleştirildi)*
- [x] Working Hours
- [x] Integration Settings
- [x] Subscription — *(salt-okunur görünüm)*
- [ ] Billing — *(kapsam dışı bırakıldı; bkz. implementation-plan.md)*

### 13. Platform Admin Paneli (SaaS taraf)
- [x] Tenant / Klinik Listesi
- [x] Tenant Detay ve Kullanim — *(ucuz kullanım metrikleri: şube/personel sayısı — hasta/randevu sayısı gibi cross-module metrikler kapsam dışı, bkz. implementation-plan.md)*
- [x] Plan ve Fiyatlandirma Yonetimi
- [x] Platform Faturalama — *(salt-okunur billingStatus dağılım özeti — gerçek ödeme tahsilatı yok, bkz. implementation-plan.md)*

### 14. Website ve Online Varlik
- [x] Klinik Web Sitesi Olusturucu (otomatik tanitim sitesi)
- [ ] Online Randevu Widget'i (web sitesine gomulu) — *(rezervasyon akışı gerçekten çalışıyor ama tam klinik sitesi sayfasının içine gömülü — üçüncü taraf sitelere iframe/script ile gömülebilir bağımsız bir widget değil)*