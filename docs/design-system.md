# MyVet — Tasarım Sistemi ve Stil Rehberi v1.0

> Bu doküman, React uygulamasında (component library ne olursa olsun — Tailwind config, styled-components tema dosyası veya CSS değişkenleri) doğrudan kod karşılığı üretilecek şekilde tasarlandı. Her bölüm, ilgili token/değişken adlarıyla birlikte verildi.

---

## 1. Tasarım Felsefesi

Vet klinik yazılımı **hem tıbbi güven hem operasyonel hız** iletmeli. İki uçtan kaçınıyoruz:
- Aşırı "steril hastane beyazı" (soğuk, kişiliksiz)
- Aşırı "startup pastel rengi" (güven vermeyen, oyuncaklaşan)

Bunun yerine: **koyu indigo-mavi ana renk (güven, profesyonellik) + sıcak nötr gri arka plan (yorgunluk yaratmayan, uzun süre bakılabilir) + net semantik renkler (durum bilgisini anında iletmek için).** AI üretimi içerik her zaman aynı mavi vurgu ailesinde ama ayrı bir işaretle (✦ ikonu / hafif farklı ton) gösterilir — kullanıcı neyin insan neyin AI kaynaklı olduğunu bir bakışta ayırt edebilmeli.

---

## 2. Renk Paleti (v1.3 — imza renkleri: mor + altın)

> **Revizyon notu:** v1.2'deki mavi+yeşil ikilisi "beklenen" duruyordu. Kullanıcı tercihiyle **"Premium Güven"** yönüne geçildi: koyu mor + altın amber. Bu, hem daha akılda kalıcı hem de Kolayvet/BulutVet gibi işlevsel-ama-sade görünen yerel rakiplerin karşısında bilinçli bir "butik/premium klinik" konumlandırması sağlıyor. Ayrıca artık marka renklerinde mavi kullanılmadığı için, **mavi tamamen AI göstergesine ayrıldı** — kullanıcı "mor/altın = marka" ve "mavi = AI" ayrımını hiç çaba harcamadan öğrenir, iki anlam asla karışmaz.

### 2.1 Marka Renkleri
| Token | Hex | Kullanım |
|---|---|---|
| `--color-ink-900` | `#241531` | Sidebar, header — koyu mor-siyah |
| `--color-brand-600` | `#6D3FA6` | **Ana marka/CTA rengi** — net, kendinden emin mor. Primary butonlar, aktif sekme, linkler |
| `--color-brand-100` | `#F0E7F7` | Marka renginin soluk tonu — seçili satır arka planı |
| `--color-gold-600` | `#C2872E` | **İkinci marka rengi** — altın amber. Logo aksanı, premium/öne çıkan vurgular, "önerilen" rozetleri |
| `--color-gold-100` | `#F7ECD6` | Gold badge arka planı |

### 2.2 Nötr Skala
| Token | Hex | Kullanım |
|---|---|---|
| `--color-neutral-950` | `#16150F` | Birincil metin |
| `--color-neutral-600` | `#6B6960` | İkincil metin |
| `--color-neutral-400` | `#B6B2A4` | Görünür buton/kart kenarlıkları |
| `--color-neutral-100` | `#F5F3EE` | Sayfa arka planı, secondary buton dolgusu |
| `--color-neutral-0` | `#FFFFFF` | Kart arka planı |

### 2.3 Semantik Renkler (marka paletinden bilerek ayrık — karışmasın diye)
| Token | Hex | Kullanım |
|---|---|---|
| `--color-success-700` | `#146245` | Tamamlandı, ödendi, sağlıklı — zümrüt yeşili (markadan tamamen ayrık hue) |
| `--color-success-100` | `#DDEFE6` | Badge arka planı |
| `--color-warning-700` | `#A65022` | Beklemede, düşük stok, risk — **kasıtlı olarak gold'dan farklı hue** (rust/terrakota), aksi halde "marka vurgusu" ile "uyarı" karışırdı |
| `--color-warning-100` | `#F1E0D2` | Badge arka planı |
| `--color-danger-700` | `#932E27` | Acil, kritik, gecikmiş |
| `--color-danger-100` | `#F5DEDB` | Badge arka planı |
| `--color-ai-600` | `#2554C7` | **AI göstergesi — artık markadan boşalan mavi, tamamen AI'a özel.** Kullanıcı bu maviyi gördüğünde otomatik "bu AI üretimi" diye okur |
| `--color-ai-100` | `#E3EBFB` | AI kutusu arka planı |

**Kural:** Mor+altın **sadece** marka/CTA anlarında; mavi **sadece** AI anlarında; yeşil/kırmızı/turuncu **sadece** durum bildiriminde. Dört renk ailesi asla birbirinin alanına girmez — bu netlik, "vav" hissini kalıcı kılan şey.

---

## 3. Tipografi

**Font ailesi:** `Inter` (gövde metin, form, tablo) + `Plus Jakarta Sans` (başlıklar, 700-800 ağırlık) — ikisi de Google Fonts, Türkçe karakter desteği tam.

> **Revizyon notu:** İlk mockup'larda başlıklar için editoryal bir serif (Fraunces, italik vurgularla) kullanılmıştı — bu "yapay zeka ile tasarlanmış, generic" bir his verdiği için kaldırıldı. Artık tüm tipografi düz sans-serif: başlıklarda kalın Plus Jakarta Sans, gövdede Inter. İtalik hiçbir yerde kullanılmıyor.

```css
--font-heading: 'Plus Jakarta Sans', sans-serif; /* font-weight: 700-800 */
--font-body: 'Inter', system-ui, -apple-system, sans-serif; /* font-weight: 400-600 */
```

| Token | Boyut / Satır Yük. | Ağırlık | Kullanım |
|---|---|---|---|
| `--text-display` | 28px / 34px | 600 | Sayfa başlıkları (nadiren) |
| `--text-h1` | 22px / 28px | 600 | Modül başlığı (örn. "İşletme Paneli") |
| `--text-h2` | 17px / 24px | 600 | Kart/bölüm başlığı |
| `--text-body` | 14px / 20px | 400 | Standart metin, tablo hücresi |
| `--text-body-medium` | 14px / 20px | 500 | Vurgulanan metin, hasta/sahip adı |
| `--text-small` | 12.5px / 18px | 400 | İkincil bilgi, tarih, meta veri |
| `--text-micro` | 11px / 14px | 500 | Badge/etiket metni, uppercase kullanılabilir |

**Not:** SOAP not alanları gibi uzun metin girişlerinde satır yüksekliği 1.6 alınmalı (yukarıdaki `--text-body`den farklı) — okunabilirlik için klinik metinlerde daha ferah satır aralığı gerekir.

---

## 4. Boşluk (Spacing) ve Grid

Temel birim **4px**. Tüm padding/margin değerleri bu birimin katı olmalı:

```
--space-1: 4px   --space-2: 8px   --space-3: 12px
--space-4: 16px  --space-6: 24px  --space-8: 32px  --space-12: 48px
```

- Kart iç boşluğu: `--space-4` (16px)
- Form alanları arası: `--space-3` (12px)
- Bölümler arası (dashboard widget'ları): `--space-6` (24px)
- Sayfa kenar boşluğu: `--space-8` (32px, masaüstü) / `--space-4` (mobil)

**Grid:** 12 kolonlu, masaüstünde max-width `1440px`, kenar boşluklarıyla ortalanmış.

---

## 5. Köşe Yuvarlaklığı ve Gölge

```
--radius-sm: 6px    /* input, buton */
--radius-md: 10px   /* kart, panel */
--radius-full: 999px /* badge, avatar */

--shadow-sm: 0 1px 2px rgba(31,30,27,0.06);          /* kart */
--shadow-md: 0 4px 12px rgba(31,30,27,0.10);         /* dropdown, modal */
```

Gölgeler sade tutulur — vurgu renk/kontrastla verilir, ağır gölge/blur kullanılmaz (klinik yazılımında "oyuncak" hissi yaratmamak için).

---

## 6. Bileşen Kütüphanesi (Component Spec)

### 6.1 Buton
| Varyant | Arka plan | Metin | Kullanım |
|---|---|---|---|
| Primary | `brand-600` (mor) dolgu | beyaz, 600 ağırlık | Ana eylem (Kaydet, Randevu Oluştur) — sayfada tek |
| Secondary | `neutral-100` dolgu + `neutral-400` kenarlık | `neutral-950`, 500 ağırlık | İkincil eylem — görünür, dolgulu |
| Tertiary (metin linki) | transparan, hover'da altı çizili | `brand-600` | Sadece satır içi metin linkleri |
| Danger | `danger-700` dolgu | beyaz | Sadece onay modalı içinde |
| AI | `ai-100` dolgu + `ai-600` (mavi) kenarlık | `ai-600`, 600 ağırlık | AI tetikleyen eylemler — mor marka renginden bilerek ayrık, mavi |
| Gold (öne çıkan/premium rozet) | `gold-100` dolgu + `gold-600` kenarlık | `gold-600` | "Önerilen plan", "Yeni özellik" gibi öne çıkarma anları — nadiren kullanılır |

Yükseklik: 36px (standart), 32px (kompakt tablo içi), köşe `--radius-sm`. **Kural:** Bir ekranda en fazla 1 primary (mor) buton olur.

### 6.2 Durum Etiketi (Badge)
Pill şeklinde (`--radius-full`), `--text-micro`, 600 ağırlık, soluk arka plan + net metin rengi:
- `Tamamlandı / Ödendi` → success (zümrüt yeşili)
- `Bekliyor / Onay Bekliyor` → nötr
- `Risk / Düşük Stok / Gecikmiş` → warning (rust/terrakota — gold'dan bilerek farklı hue)
- `Acil / Kritik / Etkileşim Uyarısı` → danger
- `AI Önerisi / AI Taslak` → ai (✦ ikonuyla, mavi)
- `Önerilen / Premium` → gold (altın, çok nadir kullanılır, gerçekten öne çıkarmak istediğinde)

### 6.3 Kart
`neutral-0` arka plan, `neutral-300` %50 opaklıkta ince kenarlık (border yerine gölge de kullanılabilir, ikisi birden değil), `--radius-md`, iç boşluk `--space-4`.

### 6.4 Tablo / Liste Satırı
- Satır yüksekliği: 48px (standart), 56px (avatar içeren hasta satırı)
- Hover: `neutral-100` arka plan
- Seçili satır: `primary-100` arka plan + sol kenarda 3px `primary-500` şerit
- Sıralanabilir başlıklar: `neutral-600`, hover'da `neutral-950`

### 6.5 Form Alanı (Input/Select)
40px yükseklik, `neutral-300` border, focus durumunda `primary-500` border + `primary-100` glow (2px). Hata durumunda `danger-600` border + altında `--text-small` kırmızı hata mesajı.

### 6.6 Sekme (Tab) — Hasta Kuyruğu / Worklist için
Alt çizgi stili (underline tabs): aktif sekme `primary-500` alt çizgi + `primary-500` metin + kalın (500); pasif sekme `neutral-600` metin. Her sekmenin yanında sayaç: pill badge, nötr arka plan (aciliyet varsa warning/danger).

### 6.7 Sol Navigasyon (Sidebar)
`primary-900` arka plan (koyu), aktif öğe `primary-700` arka plan + sol kenarda `primary-500` şerit, pasif öğe metin rengi `neutral-300`'ün %70 opaklığı, ikon + etiket yan yana, 44px öğe yüksekliği.

---

## 7. İkonografi

Tutarlı bir set kullanılmalı (öneri: **Tabler Icons** veya **Lucide** — ikisi de outline stilinde, tıbbi/kurumsal SaaS için nötr ve geniş kapsamlı). Çizgi kalınlığı sabit (1.5-2px), 16-20px boyut aralığı. Renkli/dolu (filled) icon kullanılmaz — sadece durum renginde outline icon (örn. kritik stok ikonu `danger-600` renginde ama filled değil).

---

## 8. Erişilebilirlik Notları

- Metin/arka plan kontrast oranı en az **4.5:1** (WCAG AA) — özellikle `neutral-600` ikincil metin `neutral-0` üzerinde bu oranı sağlıyor, koyulaştırma gerekmedi.
- Semantik renkler **tek başına** anlam taşımamalı — badge'lerde her zaman metin/ikon eşlik etmeli (renk körlüğü için).
- Focus göstergesi (klavye navigasyonu) her interaktif öğede görünür olmalı — `primary-500` 2px outline.

---

## 9. Sonraki Adım

Bu sistemi görsel bir bileşen showcase'i olarak da göstereyim, ardından P0 ekranlarını bu sisteme göre tek tek tasarlamaya geçebiliriz.
