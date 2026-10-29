# Lab / Görüntüleme sonuç fotoğrafından AI ile otomatik doldurma — tasarım

Tarih: 2026-10-28 · Durum: taslak, abi onayı bekliyor
Kaynak: 25 Ekim 2026 WhatsApp konuşması (Yavuz Selim FIRAT / Abimm) — "görüntüleme ve lablarda fotoğraf atınca otomatik doldurmuyor bilgileri" bulgusu.

## 1. Amaç ve neden

Laboratuvar ve Görüntüleme sonuçlarına dosya (fotoğraf) eklense bile parametre/değer/referans aralığı (lab) ve bulgular metni (görüntüleme) hâlâ elle yazılıyor. Dosyadaki bilgi hekim tarafından ikinci kez elle giriliyor — hedef, bu veriyi fotoğraftan otomatik çekip taslak olarak doldurmak.

## 2. Mevcut durum (kod okuması, 2026-10-28)

- `frontend/src/pages/laboratory/LabResultDetailModal.tsx`: Parametre/Değer/Birim/Referans/Bayrak satırları tamamen elle giriliyor. "⚡ Otomatik Ön-Değerlendirme" butonu var ama **bu bir AI çağrısı değil** — `EvaluateLabResultItemsUseCase` tamamen kural tabanlı: `LabReferenceRangeEvaluator` referans aralığı string'ini parse edip HIGH/LOW/NORMAL işaretliyor ve şablon bir özet üretiyor, hiçbir AI sağlayıcısına istek atmıyor (token harcamıyor). Yani WhatsApp'ta konuşulan "iki kere token harcanması" endişesi bugünkü mimaride zaten geçerli değildi; yeni foto-okuma adımıyla toplamda **bir** AI (vision) çağrısı eklenecek.
- `backend/src/main/java/com/vetos/modules/imaging/domain/ImagingRecord.java`: `findings` tek bir serbest metin alanı — lab gibi yapılandırılmış parametre/değer tablosu yok.
- `backend/src/main/java/com/vetos/modules/ai/infrastructure/adapter/`: mevcut tüm adaptörler (Ollama*, Claude*) yalnızca metin girdisiyle çalışıyor; görsel/vision analiz eden bir adaptör henüz yok.
- Son migration: `V87__stok_birim_duzeltmeleri.sql`. Bu özellik için migration gerekirse **V88** kullanılmalı (yeni DB alanı gerekip gerekmediği netleşmedi, bkz. §6).

## 3. Bu konuşmada netleşen kararlar

1. **Vision sağlayıcı:** Yerel Ollama görsel modeli (ör. `llama3.2-vision` veya `llava`) — Claude değil, tamamen ücretsiz/yerel kalınacak. (Not: model indirme + CPU'da hız gerçek kullanımda test edilmeli, metin modellerinden daha yavaş/ağır olabilir.)
   - **REVİZE (2026-10-29):** Bu karar, `LabResultOcrPort`/`ImagingFindingsOcrPort` için SADECE Ollama adaptörü yazılarak uygulanmıştı. Ancak canlıda `AI_PROVIDER=claude` kullanıldığı ortaya çıktı (1 CPU/2GB RAM'lik sunucuda yerel Ollama pratik değil) — bu portların `@ConditionalOnProperty(havingValue="ollama")` dışında hiçbir implementasyonu olmadığından, bu kod `ai.provider=claude` olan bir ortamda deploy edilseydi Spring context AÇILIRKEN ÇÖKERDİ (NoSuchBeanDefinitionException) — "özellik çalışmıyor" değil "backend hiç açılmıyor" seviyesinde bir sorun. Düzeltme: `ClaudeLabResultOcrAdapter`/`ClaudeImagingFindingsOcrAdapter` eklendi (Anthropic Messages API, vision/görsel girdi desteğiyle — ayrı bir "vision model" konfigürasyonuna ihtiyaç yok, mevcut `ai.anthropic.model` kullanılıyor). Sonuç: vision artık tamamen ücretsiz/yerel DEĞİL, `ai.provider=claude` olan ortamlarda (canlı dahil) Claude API üzerinden ücretli çalışıyor. Bu, maliyet açısından orijinal kararla çelişir ama backend'in ayakta kalması için zorunluydu.
2. **Görüntüleme kapsamı bilinçli olarak dar tutuldu:** AI yalnızca **dış merkezden gelen yazılı radyolog raporu fotoğrafını okuyup** `findings` alanına taslak metin aktarır (OCR + düzenleme). **Ham röntgen/USG görüntüsünü AI'ın kendisinin tıbbi yorumlaması kapsam dışı** — bu çok daha riskli bir iddia (yanlış/aşırı özgüvenli "bulgu" üretme riski), ileride ayrı bir karar gerektirir.
3. **Tek çağrı tasarımı:** Vision AI **sadece okuma/yapılandırma (OCR)** yapar — lab'da parametre adı/değer/birim/referans aralığı listesi, görüntülemede serbest metin. Flag (HIGH/LOW/NORMAL) ve özet hesaplaması **mevcut ücretsiz kural motoruna** (`LabReferenceRangeEvaluator` / `EvaluateLabResultItemsUseCase`) bırakılır — vision çağrısının çıktısı doğrudan bu motora girdi olur. Böylece gerçek bir "çifte AI maliyeti" oluşmaz: tek vision çağrısı (OCR) + ücretsiz kural hesaplaması + (çok daha sonra, tamamen farklı bir amaçla) muayene ekranındaki ayrı "AI Tanı Desteği" çağrısı — o da zaten kaydedilmiş metni okur, fotoğrafı ikinci kez analiz etmez.

## 4. Önerilen akış (UX)

**Laboratuvar:** Dosya yüklendikten sonra hekim "📷 Fotoğraftan Oku" butonuna basar (mevcut "⚡ Otomatik Ön-Değerlendirme" butonunun dosya varken genişlemiş hali olabilir, ya da yanına ayrı buton) → vision AI çağrısı → parametre satırları + referans aralığı taslak olarak dolar (tamamen düzenlenebilir) → arkasından (aynı tık ya da ayrı "Otomatik Ön-Değerlendirme" tıkıyla) mevcut kural motoru flag/özet üretir → hekim gözden geçirip düzeltir → "Sonucu Kaydet" ile onaylamadan hiçbir şey kalıcı olmaz (mevcut ilke korunur).

**Görüntüleme:** Benzer akış; `findings` alanı taslak metinle dolar, hekim onaylar.

**Tetikleme manuel kalır** (dosya yüklenince otomatik tetiklenmez) — mevcut "Otomatik Ön-Değerlendirme" paternine uygun: hekim dosya yüklese bile isterse hiç AI kullanmadan tamamen elle girebilmeli (abinin "ilk sonuç yüklemeden açılmasın" fikrine karşı netleşen cevap: zorunlu değil, opsiyonel kolaylık).

## 5. Riskler / dikkat noktaları

- Vision modelin OCR doğruluğu düşükse yanlış değer/referans aralığı önerebilir — tüm alanlar düzenlenebilir kalmalı, hiçbiri salt-okunur olmamalı.
- Birden fazla dosya/sayfa yüklendiğinde (lab sonucu tek kayıtta çoklu dosyayı destekliyor) hangi dosyanın/dosyaların okunacağı netleşmeli (hepsi mi, hekimin seçtiği mi).
- Taslak her zaman "[AI önerisi, kesin değil]" tonunda sunulmalı — mevcut `AI_FIELD_HINT` / Tanı Desteği panelindeki uyarı deseniyle tutarlı.

## 6. Açık sorular / sonraki adımlar

- Ollama'da tam model adı ve yerel kurulum/performans testi abi tarafından yapılmalı (`OLLAMA_VISION_MODEL`, bkz. `docs/deployment.md`).
- Vision prompt tasarımı + yapılandırılmış JSON çıktı şeması — **tamamlandı** (2026-10-29): `OllamaLabResultOcrAdapter`/`OllamaImagingFindingsOcrAdapter`, bkz. plan Task 4.
- DB şeması: `LabResultItem` alanları için migration gerekmedi (teyit edildi) — AMA `ai_jobs.encounter_id` NOT NULL kısıtı lab/imaging sonuçlarının muayeneye bağlı olmaması nedeniyle **V88** ile nullable yapıldı (kod okumasıyla 2026-10-29'da ortaya çıktı, bkz. plan Architecture notu).
- Tam implementation plan yazıldı ve uygulandı: `docs/superpowers/plans/2026-10-29-lab-goruntuleme-ai-foto-okuma.md` (7 task, backend+frontend). Kalan: abi'nin kendi lokalinde Ollama vision modelini kurup (`ollama pull <model>`) `./mvnw test`/`npx vitest run` ile doğrulaması ve manuel UI testi.

## 7. UI karar detayları (2026-10-29, birlikte netleştirildi)

- **Tetikleme butonu:** Yeni buton eklenmiyor. Dosya yüklenmiş bir sonuçta mevcut "⚡ Otomatik Ön-Değerlendirme" butonu "📷 Oku + Değerlendir" (veya benzer) etiketine dönüşür; tek tıkla hem vision OCR hem de mevcut kural motoru (flag/özet) sırayla çalışır. Dosya yoksa buton eski davranışında kalır (sadece elle girilmiş değerleri değerlendirir).
- **Çoklu dosya:** Bir sonuca birden fazla dosya yüklenmişse hepsi otomatik okunmaz. Hekim, hangi dosyanın/dosyaların okunacağını işaretler (checkbox/seçim adımı), sadece seçilenler vision çağrısına gönderilir. Bu, eski/yanlış bir dosyanın yanlışlıkla okunmasını önler.
- **Mevcut veriyle çakışma:** AI okuma sonucu, parametre satırları (lab) veya `findings` (görüntüleme) alanında halihazırda yazılı veri olsa bile her zaman üzerine yazar — ayrıca onay istenmez. "Kaydet"e basılmadan hiçbir şey kalıcı olmadığı için (mevcut ilke, §4) hekim taslağı inceleyip isterse elle geri düzeltebilir; ekstra bir onay diyalogu akışı yavaşlatmayacak şekilde bilinçli olarak atlandı.
- **AI altyapı entegrasyonu (kod okumasıyla netleşti, 2026-10-29):** Mevcut tüm AI çağrıları `AiCallGate` üzerinden tek bir eşzamanlılık havuzundan ("ai" bulkhead, en fazla 4 eşzamanlı çağrı, 5. en fazla 10sn bekler, yer açılmazsa 503 AI_BUSY) geçiyor. Yeni vision/OCR çağrısı da aynı kapıdan geçmeli — ayrı bir havuz/kota mekanizması kurulmayacak. `AiTaskType` enum'ına (şu an sadece `TREATMENT_RECOMMENDATION`, `DIAGNOSIS_SUGGESTION`) yeni bir değer (örn. `LAB_IMAGING_OCR`) eklenmesi gerekecek, iş kaydı/kullanım takibi için.
- **Dosya tipi filtresi (kod okumasıyla netleşti, 2026-10-29):** Görüntüleme dosya yükleme alanı `image/*,.dcm,application/dicom` kabul ediyor. AI okuma seçeneği sadece `image/*` tipi dosyalarda gösterilecek — `.dcm`/DICOM dosyaları ham görüntü olduğu için kapsam dışı (zaten §3.2'deki karara uygun).
- **Migration teyidi:** `LabResultItem` zaten `parameterName/value/unit/referenceRange/flag` alanlarına sahip (§6'daki açık soru artık kapalı) — bu özellik için yeni bir DB alanı/migration gerekmiyor, V88 kullanılmayacak.

## 8. Referans aralığı netliği (2026-10-29)

AI okuma sadece "Parametre" ve "Değer"i değil, dış laboratuvarın rapor üzerinde bastığı **Referans aralığını da OCR ile doğrudan okuyup** `referenceRange` alanına taslak olarak yazacak (dış labın kendi referans aralığı, uyduruk/varsayılan bir değer değil). Akış: AI okuma → Parametre/Değer/Birim/Referans dört alan da taslak doldurulur → ardından mevcut kural motoru (`LabReferenceRangeEvaluator`) bu OCR'dan gelen değer+referans çiftini kullanıp flag (HIGH/LOW/NORMAL) ve özet üretir. Yani referans aralığı iki kere (hem AI hem hekim) elle girilmeyecek — rapor fotoğrafında yazılıysa doğrudan oradan gelecek, hekim sadece gözden geçirip "Kaydet" ile onaylayacak.
