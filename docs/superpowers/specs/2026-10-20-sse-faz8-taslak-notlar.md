# Faz 8 (SSE) — tasarım konuşması notları (TASLAK, onaylı spec değil)

Durum: 2026-10-20'de konuşma yarıda bırakıldı; kullanıcı önce canlıya bir sürüm çıkmak istedi. Kaldığı yerden devam edilecek.

## Amaç
Henüz kullanıcı şikâyeti yok (test aşaması). Amaç uygulamanın kullanımı rahat ("user friendly") olması.

## Parça 1 — AI SOAP taslağı akışı (onaylandı, playback'e itiraz yok)
- Bugün: "SOAP Taslağı Oluştur" → "Oluşturuluyor..." + "~30-40 saniye sürebilir" → tek seferde cevap. Hekim bu sürede ekran başında bekliyor.
- İstenen: yapay zekâ yazdıkça metin ekrana akar (Subjective → Objective → Assessment → Plan). Toplam süre aynı; boş bekleme biter.
- Önizleme kutusu dört alanı başlıklarıyla ayrı ayrı gösterir (bugün yalnız Subjective görünüyor: EncounterPage.tsx `draft.subjective`); sığmazsa kutu içinde kaydırılır. (Kullanıcı kararı) — **2026-10-20 yapıldı** (Faz 1–7 sürümüne alındı; akış kısmı Faz 8'de).
- "Alanlara Uygula" demeden SOAP kartına bir şey yazılmaz; kota ve AI meşgul kapısı bugünkü gibi.
- Tahmin (kullanıcı söylemedi): bağlantı yarıda koparsa o ana kadar yazılan kalır + "tekrar dene".
- Kapsam dışı: hızlandırma / model değişikliği.
- Not (builder): Claude adaptörü yapılandırılmış "tool use" ile JSON üretiyor; akış için kısmi JSON (input_json_delta) parçalanmalı. Ollama adaptörü de var.

## Parça 2 — Ana sayfada canlı randevular (kapsam kararlaştırıldı)
- Kod taraması: Vetly'de hiçbir ekran kendiliğinden yenilenmiyor (polling yok).
- Kullanıcı seçimi: üç aday (randevular / TARBİL'den dönüş / e-fatura) arasından yalnız **ana sayfadaki randevular**.
- Güncelleme tetikleri: siteden yeni talep, başka personelin ekleme/iptal/saat değişikliği, durum değişiklikleri (geldi, muayenede, tamamlandı).
- Görünüm: **sessizce** listeye yansır (ses/açılır pencere yok); sekme sayıları ("Onay Bekleyen (2)") güncellenir. (Kullanıcı kararı)
- Hekimlerin bir kısmı ana sayfayı gün boyu açık tutuyor, bir kısmı açıp kapatıyor.
- Tahminler (kullanıcı söylemedi): yalnız kendi kliniğinin olayları; koparsa kendiliğinden yeniden bağlanıp listeyi baştan yükler; açık randevu penceresi bozulmaz.
- Kapsam dışı (kullanıcı kararı): TARBİL kartı, e-fatura durumu, takvim sayfası ("şimdilik o da durabilir"), eklenti yan paneli.

## Kalan
- Teknik yaklaşım (EventSource + kısa ömürlü token mı, fetch akışı mı; nginx `proxy_buffering off`; sanal thread) henüz konuşulmadı.
- Yazılı spec + builder check + plan.
