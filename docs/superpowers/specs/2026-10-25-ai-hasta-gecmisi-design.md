# AI tanı ve tedavi önerisine hasta geçmişi — tasarım

Tarih: 2026-10-25
Durum: kullanıcı onayı bekliyor

## Amaç ve neden

AI tanı önerisi ve AI tedavi önerisi bugün yalnızca o günkü muayeneye (SOAP, vital
bulgular, fizik muayene) ve son 5 muayenenin değerlendirme/planına bakıyor. Hekim
(kullanıcı) önerilerin hastanın geçmişini de bilerek daha sağlıklı olmasını istiyor:
tür, yaş, kilo, kullandığı ilaçlar, aşılar, kan/lab değerleri ve görüntüleme bulguları.

Başarı: Claude'a giden metinde hastanın son 1 yıllık geçmişi yer alır; öneriler
bunu dikkate alır (ör. böbrek değeri yüksek hastada nefrotoksik ilaçtan kaçınma,
kiloya göre doz, son aşı tarihleri).

## Kullanıcının verdiği kararlar

- **Yalnızca metin gönderilir.** Röntgen görseli ve lab PDF'leri gönderilmez
  (canlıda Claude kullanılıyor; dosyalar her tıklamada maliyeti ve süreyi artırır,
  lab değerleri zaten tek tek kayıtlı).
- **Zaman aralığı: son 1 yıl.**
- **Görsel için ayrı buton bu işin parçası değil** (ileride ayrı iş olabilir).

## Gönderilecek bilgiler

Hepsi Vetly'de zaten kayıtlı. Her iki öneri türü (tanı ve tedavi) aynı geçmiş
bloğunu alır.

1. **Hasta kartı:** tür, ırk, yaş (doğum tarihinden; yıl + ay), cinsiyet,
   kısırlaştırılmış mı, kilo, kan grubu, kritik uyarı (alerji vb. buraya yazılıyor).
   - Kilo: o günkü muayenenin kilosu varsa o, yoksa karttaki kilo.
2. **Geçmiş muayeneler:** tarih, değerlendirme, plan (bugün olduğu gibi;
   yalnızca sonuçlandırılmış/düzeltilmiş muayeneler, o günkü muayene hariç).
3. **İlaçlar:** reçete tarihi + her kalem için ilaç adı, doz, sıklık, süre (gün),
   uygulama yolu. İptal edilmiş reçeteler hariç. O günkü muayenede yazılmış reçete
   de dahil, "bu muayene" diye işaretli.
4. **Aşılar:** aşı adı, uygulama tarihi, bir sonraki tarih (varsa). Yalnızca
   yapılmış (ADMINISTERED) olanlar.
5. **Kan / lab:** tahlil adı, sonuç tarihi, özet (varsa) ve her değer:
   parametre, değer, birim, referans aralığı, yüksek/düşük işareti. Yalnızca
   tamamlanmış (COMPLETED) tahliller.
6. **Görüntüleme:** tür (röntgen, ultrason…), bölge, sonuç tarihi, hekimin
   yazdığı bulgular. Yalnızca tamamlanmış kayıtlar. Bulgusu boş olan kayıt
   "bulgu yazılmamış" diye geçer.

**Gönderilmeyecek:** hasta adı, sahip bilgileri (ad, TC, telefon, adres),
mikroçip/pasaport no, dosyalar. (Benim kararım: öneri için gereksiz, kişisel veri.)

## Zaman aralığı ve sınırlar

- "Son 1 yıl", o günkü muayenenin tarihinden geriye 365 gün. Tarih alanları:
  muayene tarihi, reçete tarihi, aşı uygulama tarihi, lab/görüntüleme sonuç tarihi
  (sonuç tarihi yoksa istek tarihi).
- Güvenlik tavanları (benim kararım; çok yoğun hastada metnin şişmesini önler):
  muayene en fazla 20, reçete en fazla 30, aşı en fazla 20, tahlil en fazla 15,
  görüntüleme en fazla 10. Tavan aşılırsa en yeniler alınır ve metne
  "daha eski kayıtlar kısaltıldı" notu düşülür.
- Bugünkü "son 5 muayene" kuralı kalkar, yerine 1 yıl + tavan gelir.
- Boş bölümler "kayıt yok" diye yazılır (model "bilgi yok" ile "veri gelmedi"yi
  ayırabilsin).

## Model talimatı (system prompt) değişikliği

İki öneri türünde de şunlar eklenir:
- Geçmiş bilgileri dikkate al; özellikle kritik uyarı/alerji, normal dışı lab
  değerleri ve hâlâ kullanılıyor olabilecek ilaçlarla etkileşim.
- Eski tanıları olduğu gibi tekrarlama; bugünkü bulgularla birlikte değerlendir.
- Tedavi önerisinde doz verirken hastanın kilosunu kullan.
- Kesin tanı koymama ve "hekim kararı" kuralları aynen kalır.

## Teknik çerçeve (uygulayıcının bilmesi gerekenler)

- Modül sınırları Spring Modulith ile denetleniyor (`ApplicationModulesTest`).
  `ai` modülü bugün yalnızca `modules.encounter::domain` okuyabiliyor.
  - Lab ve görüntüleme için kendi `domain` paketlerinde birer okuma portu
    (`LabResultLookupPort`, `ImagingRecordLookupPort` benzeri) eklenir; hasta için
    `PatientLookupPort`'a klinik profil metodu eklenir (mevcut `PatientTarbilProfile`
    deseni gibi, sahip verisi yok).
  - `ai/package-info.java` izin listesine `modules.patient::domain`,
    `modules.lab::domain`, `modules.imaging::domain` eklenir.
  - Reçete ve aşı `encounter` modülünde; mevcut `EncounterLookupPort` /
    `VaccinationLookupPort` genişletilir.
- Geçmiş bloğunu metne çeviren kod tek yerde olur; Claude ve Ollama adaptörleri
  aynı metni kullanır (Ollama yerelde hâlâ kullanılabiliyor, kırılmamalı).
- Kiracı (tenant) filtresi mevcut repository'lerin davranışıyla gelir; başka
  kliniğin verisi asla girmemeli.
- Ekranda değişiklik yok. API sözleşmesi değişmez.
- Kota, `AiCallGate`, `RecordAiJobUseCase` akışı aynen kalır.

## Hedef dışı

- Görsel/PDF gönderimi, görüntüye AI yorumu butonu.
- Hekimin hangi bilgilerin gideceğini seçtiği bir ekran.
- AI SOAP taslak üretimi (`GenerateSoapDraftUseCase`) — bu işe dahil değil.
- Lab değerlerinin zaman içindeki eğilimini hesaplama (ham değerler tarihli gider,
  yorumu model yapar).

## Olmaması gerekenler (anti-hedef)

- Başka kliniğin veya başka hastanın kaydının metne girmesi.
- Sahip kişisel verisinin Claude'a gitmesi.
- Geçmiş verisi okunurken hata olursa önerinin hiç verilememesi — tek bir bölüm
  okunamazsa o bölüm "okunamadı" yazılır, öneri yine üretilir (benim kararım).

## Testler

- Hasta geçmişi metni: dolu hasta, boş hasta, 1 yıldan eski kaydın dışarıda
  kalması, iptal/bekleyen kayıtların dışarıda kalması, tavan + "kısaltıldı" notu,
  kilo önceliği (muayene > kart), yaş hesabı.
- Claude adaptörüne giden istekte geçmiş bloğunun yer aldığı (mevcut adaptör
  testleri deseniyle).
- Modulith testi yeşil kalmalı.
- Sahip verisinin metinde geçmediği (ör. sahip adı/telefonu ile test).
