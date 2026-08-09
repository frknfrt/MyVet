# Ekran Öncelikleri — P0 Özeti (Faz 1)

Tam liste (P0/P1/P2/P3, notlarla): `docs/screen-priorities.xlsx`

## P0 Ekranlar (MVP kapsamı)


### 0. Global Sistem (Auth)
- [ ] Splash Screen
- [ ] Login
- [ ] Register Clinic
- [ ] Forgot Password
- [ ] Clinic Setup Wizard
- [ ] Invite Team Members
- [ ] Subscription Plan Selection

### 1. Dashboard
- [ ] Ana Panel (rol bazli render: Doktor/Resepsiyon/Yonetici)
- [ ] Gunluk Operasyon Panosu (Clinic Flow Board)

### 2. Hasta (Pet) Modulu
- [ ] Pet List
- [ ] Pet Search
- [ ] New Patient Create
- [ ] Patient Profile
- [ ] Patient Health Summary
- [ ] Patient Timeline / Clinical Timeline
- [ ] Medical History
- [ ] Vaccination History

### 3. Owner / Musteri CRM
- [ ] Owner List
- [ ] Owner Profile
- [ ] Owner Pets
- [ ] KVKK Riza Yonetimi

### 4. Randevu ve Takvim
- [ ] Calendar Main View
- [ ] Daily Calendar
- [ ] Weekly Calendar
- [ ] Doctor Schedule
- [ ] Appointment Create
- [ ] Appointment Detail

### 5. Klinik / Muayene (urunun kalbi)
- [ ] Examination Start
- [ ] SOAP Note Screen (S/O/A/P)
- [ ] Voice Recording AI
- [ ] AI SOAP Generator
- [ ] Physical Examination Form
- [ ] Diagnosis Selection (+ AI oneri)
- [ ] Treatment Plan (+ AI oneri)
- [ ] Prescription Create (+ ilac etkilesim uyarisi)
- [ ] Prescription History

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
- [ ] Inventory Dashboard
- [ ] Product List
- [ ] Product Detail
- [ ] Add Product
- [ ] Medicine Management
- [ ] Vaccine Management
- [ ] Stock Movement

### 9. Finans ve Muhasebe
- [ ] Finance Dashboard
- [ ] Invoice List
- [ ] Create Invoice
- [ ] Payment Screen
- [ ] Subscription Management (klinik SaaS aboneligi)
- [ ] Payment Integration
- [ ] e-Fatura / e-Arsiv Durum Ekrani
- [ ] TARBIL Senkron Ekrani
- [ ] Kasa Yonetimi (gunluk acilis/kapanis, nakit mutabakati)
- [ ] Borc Listesi / Cari Hesap Takibi
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
- [ ] Tenant / Klinik Listesi
- [ ] Tenant Detay ve Kullanim
- [ ] Plan ve Fiyatlandirma Yonetimi
- [ ] Platform Faturalama

### 14. Website ve Online Varlik
- [ ] Klinik Web Sitesi Olusturucu (otomatik tanitim sitesi)
- [ ] Online Randevu Widget'i (web sitesine gomulu)