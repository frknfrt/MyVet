# Ekran Öncelikleri — P0 Özeti (Faz 1)

Tam liste (P0/P1/P2/P3, notlarla): `docs/screen-priorities.xlsx`

## P0 Ekranlar (MVP kapsamı)


### 0. Global Sistem (Auth)
- [ ] Splash Screen
- [x] Login
- [x] Register Clinic
- [ ] Forgot Password
- [x] Clinic Setup Wizard
- [x] Invite Team Members — *(Ayarlar > Kullanıcılar'da "Ekip Üyesi Davet Et" — `StaffInvite` + `InviteEmailPort`/`MockInviteEmailAdapter`, TARBİL/e-Fatura ile aynı port/adapter deseni; kabul linki `/davet/:token`'da şifre belirleyip hesabı etkinleştiriyor. Gerçek bir e-posta sağlayıcısı yok, link sunucu loglarına yazılır — kullanıcı onayıyla mock bırakıldı.)*
- [ ] Subscription Plan Selection — *(kayıtta otomatik 14 günlük deneme atanıyor; plan seçim ekranı yok, bkz. Ayarlar > Abonelik'in salt-okunur görünümü)*

### 1. Dashboard
- [x] Ana Panel (rol bazli render: Doktor/Resepsiyon/Yonetici) — *("İşletme özeti" sekmesi sadece ADMIN/RECEPTIONIST'e görünüyor (`/invoices/**` yetkisiyle uyumlu, önceden VET/TECHNICIAN'a boş/403 veri gösteriyordu); VET için "Bana atanan" varsayılan açık, TECHNICIAN için "Bekleme Salonu" sekmesi varsayılan.)*
- [x] Gunluk Operasyon Panosu (Clinic Flow Board)

### 2. Hasta (Pet) Modulu
- [x] Pet List
- [x] Pet Search
- [x] New Patient Create
- [x] Patient Profile
- [x] Patient Health Summary — *(Hasta detayına yeni "Genel Bakış" sekmesi — varsayılan/ilk sekme — eklendi: son muayenenin vital özeti (kilo/ateş/nabız/solunum) + kilo trendi grafiği (mevcut bağımsız `LineChart` bileşeniyle, ≥2 veri noktası varsa). Aktif problem listesi (kronik hastalık etiketleri) bilinçli olarak kapsam dışı bırakıldı — mevcut veri modelinde karşılığı yok, kullanıcı onayıyla ertelendi.)*
- [x] Patient Timeline / Clinical Timeline — *(Aynı "Genel Bakış" sekmesinde, mevcut 5 sekmenin (Muayene/Aşı/Reçete/Lab/Görüntüleme) yerine geçmeden ek olarak: tüm kayıt türleri tek tarihe göre sıralı listede, tür etiketi + özet + durum badge'iyle. Sayfada zaten yüklü olan veriler birleştirildiği için backend değişikliği gerekmedi (frontend-only).)*
- [x] Medical History
- [x] Vaccination History

### 3. Owner / Musteri CRM
- [x] Owner List — *(`/musteriler` — `OwnersListPage`, "Hastalar & Sahipler" sayfasının yeni "Sahipler" sekmesi; mevcut `GET /api/v1/owners?query=` aynen kullanıldı, backend değişikliği gerekmedi)*
- [x] Owner Profile
- [x] Owner Pets
- [x] KVKK Riza Yonetimi — *(Yeni Sahip formunda zorunlu "KVKK Açık Rıza" checkbox'ı + kayıt anında otomatik ConsentRecord; OwnerDetailPage > KVKK sekmesinde 3 onay türü için ver/geri çek + geçmiş — bkz. implementation-plan.md)*

### 4. Randevu ve Takvim
- [x] Calendar Main View
- [x] Daily Calendar — *("Haftalık / Günlük / Hekim" sekmesi eklendi, aynı `weeklyCalendar` verisi client-side güne/hekime göre filtreleniyor)*
- [x] Weekly Calendar
- [x] Doctor Schedule — *(Günlük moddaki grid gün sütunları yerine hekim sütunlarına dönüyor (VET rolündeki `staff-users`), üstte ayrıca tüm görünümleri filtreleyen "Hekim" seçici var)*
- [x] Appointment Create
- [x] Appointment Detail

### 5. Klinik / Muayene (urunun kalbi)
- [x] Examination Start
- [x] SOAP Note Screen (S/O/A/P)
- [ ] Voice Recording AI — *(tarayıcı yerleşik Web Speech API ile gerçek sesli dikte çalışıyor; özel/barındırılan bir ses-AI servisi değil)*
- [ ] AI SOAP Generator — *(uçtan uca bağlı ama backend adaptörü stub — gerçek model henüz bağlı değil, transkripti Subjective alanına kopyalıyor; UI bunu açıkça uyarıyor)*
- [x] Physical Examination Form — *(SOAP sayfasına Vital Bulgular ile SOAP Notu arasında "Fiziksel Muayene" kartı eklendi: 10 sabit vücut sistemi — Genel Görünüm, Deri/Kürk, Göz-Kulak-Ağız, Kardiyovasküler, Solunum, Gastrointestinal, Ürogenital, Kas-İskelet, Nörolojik, Lenf Nodları — her biri Muayene Edilmedi/Normal/Anormal durumu + Anormal seçilince açılan not alanı taşır. `Encounter.physicalExamFindings` jsonb kolonunda tutulur, Objective serbest metin alanına dokunulmadı, ikisi birbirini tamamlıyor.)*
- [x] Diagnosis Selection (+ AI oneri) — *(Assessment serbest metin kalmaya devam ediyor — ayrı bir "tanı seçim" UI'ı yok, ama "AI Tanı Desteği" kartı: Subjective/Objective/vital/fiziksel muayeneye dayanarak olası tanı/ayırıcı tanı önerisi üretiyor, hekim onayıyla Assessment'e uygulanıyor. Bkz. implementation-plan.md.)*
- [x] Treatment Plan (+ AI oneri) — *(Plan serbest metin kalmaya devam ediyor — "AI Tedavi Önerisi" kartı: Assessment + geçmiş muayenelere dayanarak tedavi önerisi üretiyor, hekim onayıyla Plan'a uygulanıyor. Bkz. implementation-plan.md.)*
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
- [ ] Medicine Management — *(stok kalemlerinde hâlâ sadece serbest metin "kategori" alanı var, stokla entegre değil; `DrugCatalog`/`DrugsController` Ayarlar > İlaç Kataloğu sekmesinden ilaç CRUD + etkileşim işaretleme yapılıyor (kısa bir süre bağımsız üst-seviye sayfa denendi, kullanıcıyla diğer katalog ekranlarıyla — Tür/Irk, Hizmetler — tutarlılık için Ayarlar'a geri alındı). Stokla entegrasyon hâlâ kapsam dışı.)*
- [ ] Vaccine Management — *(ayrı bir aşı-stok kataloğu yok, genel envanter kategorisine giriyor — aşı takvimi/kayıtları farklı bir konsept olarak zaten var, bkz. Vaccination History)*
- [x] Stock Movement

### 9. Finans ve Muhasebe
- [ ] Finance Dashboard — *(Finans sayfası Faturalar/Kasa/Borç Listesi sekmeli bir liste ekranı; ayrı markalı bir KPI dashboard'u yok — Raporlar sayfası ve Dashboard'daki İşletme Özeti buna en yakın)*
- [x] Invoice List
- [x] Create Invoice — *(Finans > Faturalar'da "Yeni Fatura" — `CreateManualInvoiceUseCase` + `POST /api/v1/invoices` (owner seçilip boş DRAFT açılıyor), ardından mevcut kalem ekleme/kesme akışı aynen kullanılıyor)*
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
- [x] Voice To SOAP — *(Ayarlar > AI Merkezi'nde bağlantı — bir muayeneye yönlendiriyor, kendisi zaten var olan sesli dikte/taslak akışı)*
- [x] Diagnosis Assistant — *(Ayarlar > AI Merkezi'nde "Tanı Desteği" — AI Tedavi Önerisi ile aynı Claude/Ollama port-adaptör mimarisi, bkz. implementation-plan.md)*
- [x] Treatment Recommendation — *(Ayarlar > AI Merkezi'nde "Tedavi Önerisi" — bkz. implementation-plan.md)*
- [x] Drug Interaction Check — *(Ayarlar > AI Merkezi'nde bağlantı — İlaç Kataloğu'na yönlendiriyor; kural tabanlı olduğu için AI mavisi değil, uyarı tonuyla ayrıca işaretlendi)*

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