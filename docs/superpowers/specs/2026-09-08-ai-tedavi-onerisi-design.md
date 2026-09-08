# AI Tedavi Önerisi — Tasarım Dokümanı

**Tarih:** 2026-09-08
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modüller:** `modules/ai`, `modules/encounter` (yeni port metodu)

## 1. Bağlam ve Amaç

`2026-09-07-ai-karar-denetim-izi-design.md` ile kurulan denetim izi altyapısını (`AiJob`/`AiJobDecision`, üç use-case) kullanan **ilk somut tüketici**: hekimin bir muayenede yazdığı Assessment (değerlendirme/ön tanı) ve hastanın geçmiş muayene kayıtlarına bakarak bir tedavi planı önerisi üreten, hekim onayına sunulan bir AI özelliği.

Bu, Vetly AI yol haritasının **sub-proje #2**'si (sub-proje #1: AI karar denetim izi, tamamlandı).

**Kritik kısıtlama (kullanıcı onayıyla, 2026-09-07 oturumunda netleştirildi):** Öneri, gerçek bir tıbbi/farmakolojik referans kaynağına değil, tamamen kullanılan LLM'in kendi genel bilgisine dayanacak. Bu nedenle hekim onayı tek güvenlik katmanı — arayüzde her zaman görünen, model bağlantısından bağımsız bir uyarı zorunlu (bkz. §5).

## 2. Kapsam

**Bu turda yapılacak:**
- `encounter` modülüne, bir muayenenin güncel Assessment'ı + hastanın son 5 muayenesinin özetini dönen yeni bir lookup port metodu
- `ai` modülüne `TreatmentRecommendationPort` + Ollama adaptörü + `GenerateTreatmentRecommendationUseCase`
- `RecordAiJobDecisionUseCase`'e tenant kontrolü eklenmesi (önceki turun final review'inde "API eklenince yapılmalı" diye not düşülen düzeltme — bkz. §3.3)
- İki yeni REST endpoint'i (öneri üretme + karar kaydetme)
- Frontend: `EncounterPage.tsx`'e yeni bir "AI Tedavi Önerisi" kartı, "Aynen Kabul Et" / "Reddet" akışı

**Kapsam dışı (bilinçli olarak, 2026-09-07 oturumunda karara bağlandı):**
- **"Düzenleyerek kabul" (ACCEPTED_WITH_EDITS) UI'dan tetiklenmiyor** — backend zaten destekliyor (önceki turdan), ama bu turun UI'ı sadece "Aynen Kabul Et" / "Reddet" sunuyor. Hekim kabul ettikten sonra Plan alanını serbestçe düzenleyebilir, ama bu düzenleme audit kaydına ayrıca yansımaz (kayıt, kabul anındaki metni taşır).
- **İsabet geri bildirimi ("isabetliydi/değildi") UI'ı yok** — `RecordAiJobFeedbackUseCase` zaten hazır ama bu turda hiçbir ekrandan çağrılmıyor; ayrı bir zamanlama/ekran gerektirdiği için sonraya bırakıldı.
- **Tanı Desteği (Diagnosis Assistant)** — ayrı bir özellik, requirements.md'de bu ikisi ayrı tutuluyor; bu doküman sadece Tedavi Önerisi'ni kapsıyor.
- **`historyLimit` yapılandırılabilir değil** — kod içinde sabit `5` (bkz. §7, gerekçe: context/hız/kalite dengesi, kullanıcı onayıyla).

## 3. Backend Mimarisi

### 3.1 `encounter` modülü — yeni lookup port metodu

```java
// encounter/domain/EncounterLookupPort.java (mevcut arayüze eklenir)
public interface EncounterLookupPort {
    EncounterSummary findSummaryById(UUID encounterId);
    EncounterClinicalContext findClinicalContext(UUID encounterId, int historyLimit); // YENİ
}
```

```java
// encounter/domain/EncounterClinicalContext.java (yeni)
public record EncounterClinicalContext(
    UUID encounterId,
    UUID patientId,
    String currentAssessment,
    List<PastEncounterSummary> recentHistory // en yeni once, historyLimit ile sinirli
) {}
```

```java
// encounter/domain/PastEncounterSummary.java (yeni)
public record PastEncounterSummary(LocalDate date, String assessment, String plan) {}
```

`EncounterLookupAdapter` (mevcut, `infrastructure/persistence`), yeni metodu implemente eder: verilen `encounterId`'nin kendi `assessment`/`patientId`'sini okur (yoksa `EncounterNotFoundException`), sonra aynı `patientId`'ye ait, mevcut encounter hariç, en güncel `historyLimit` kadar **FINALIZED** durumdaki muayeneyi tarih azalan sırada döner (implementasyon sırasında mevcut `EncounterJpaRepository`'de uygun bir sorgu metodu yoksa eklenir — reference-module.md §6 deseniyle).

### 3.2 `ai` modülü — yeni port, adaptör, use-case

`package-info.java`'ya `"modules.encounter::domain"` eklenir (yeni izin verilen bağımlılık).

```java
// ai/domain/TreatmentRecommendationPort.java
public interface TreatmentRecommendationPort {
    TreatmentRecommendationDraft generate(TreatmentRecommendationInput input);
}
public record TreatmentRecommendationInput(String currentAssessment, List<HistoryEntry> history) {}
public record HistoryEntry(String date, String assessment, String plan) {} // encounter.domain'e bagli degil, ai kendi tipini tasir
public record TreatmentRecommendationDraft(String suggestionText, boolean modelConnected) {}
```

`OllamaTreatmentRecommendationAdapter` (`ai/infrastructure/adapter`), `OllamaSoapGenerationAdapter` ile **aynı desen**: `OLLAMA_BASE_URL` boşsa simüle davranış (`modelConnected=false`, metin: "AI modeli henüz bağlı değil" notu). Doluysa gerçek çağrı, ama `format: json` DEĞİL — düz metin cevap (S/O/A/P gibi alan ayrıştırması yok, tek bir öneri metni).

**Sistem promptu (Türkçe):** "Sen bir veteriner klinik karar desteği asistanısın. Sana hekimin bir hasta için yazdığı değerlendirme (assessment) ve hastanın geçmiş muayene özetleri verilecek. Bu bilgilere dayanarak olası bir tedavi planı öner. Kesin tanı koyma, sadece tedavi seçenekleri sun. Yanıtın sade bir paragraf/madde listesi olsun, JSON veya markdown kullanma."

```java
// ai/application/GenerateTreatmentRecommendationCommand.java
public record GenerateTreatmentRecommendationCommand(UUID tenantId, UUID encounterId, UUID requestedByStaffUserId) {}

// ai/application/TreatmentRecommendationResult.java
public record TreatmentRecommendationResult(UUID aiJobId, String suggestionText, boolean modelConnected) {}
```

```java
// ai/application/GenerateTreatmentRecommendationUseCase.java
@Service
public class GenerateTreatmentRecommendationUseCase {
    private final EncounterLookupPort encounterLookupPort;
    private final TreatmentRecommendationPort treatmentRecommendationPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String ollamaModelName; // @Value("${ai.ollama.model:llama3.1}"), OllamaSoapGenerationAdapter ile ayni config

    private static final int HISTORY_LIMIT = 5;

    public GenerateTreatmentRecommendationUseCase(
        EncounterLookupPort encounterLookupPort,
        TreatmentRecommendationPort treatmentRecommendationPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.ollama.model:llama3.1}") String ollamaModelName
    ) {
        this.encounterLookupPort = encounterLookupPort;
        this.treatmentRecommendationPort = treatmentRecommendationPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.ollamaModelName = ollamaModelName;
    }

    public TreatmentRecommendationResult execute(GenerateTreatmentRecommendationCommand command) {
        EncounterClinicalContext context = encounterLookupPort.findClinicalContext(command.encounterId(), HISTORY_LIMIT);
        if (context.currentAssessment() == null || context.currentAssessment().isBlank()) {
            throw new AssessmentRequiredForRecommendationException(command.encounterId());
        }
        var input = new TreatmentRecommendationInput(
            context.currentAssessment(),
            context.recentHistory().stream()
                .map(h -> new HistoryEntry(h.date().toString(), h.assessment(), h.plan()))
                .toList()
        );
        TreatmentRecommendationDraft draft = treatmentRecommendationPort.generate(input);
        UUID aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.TREATMENT_RECOMMENDATION, command.encounterId(),
            draft.suggestionText(), "ollama", ollamaModelName, command.requestedByStaffUserId()
        ));
        return new TreatmentRecommendationResult(aiJobId, draft.suggestionText(), draft.modelConnected());
    }
}
```

`AssessmentRequiredForRecommendationException` — yeni domain istisnası, `DomainException`'dan türer, `AiJobDecisionNotFoundException` gibi özel bir suffix taşımaz (422'ye düşer — doğru, çünkü bu bir doğrulama hatası, "not found" veya "conflict" değil).

### 3.3 `RecordAiJobDecisionUseCase` — tenant kontrolü eklenmesi (mevcut use-case'e değişiklik)

Önceki turun final review'inde parked bırakılan gözlem: `RecordAiJobDecisionUseCase` bir `aiJobId` alıp doğrudan işliyor, çağıranın tenant'ına ait olup olmadığını kontrol etmiyordu — API olmadığı için o zaman risksizdi, şimdi risk haline geliyor.

**Değişiklik:** `RecordAiJobDecisionCommand`'a `tenantId` alanı eklenir. Use-case, `aiJobRepository.findById(aiJobId)` sonrası `job.getTenantId().equals(command.tenantId())` kontrolü yapar — eşleşmezse (bilgi sızdırmamak için "yetkisiz" değil) **aynı `AiJobNotFoundException`** fırlatılır (reference-module.md'nin `PatientLookupAdapter` deseniyle tutarlı: var olmayan ile başkasına ait olan, dışarıya aynı görünür).

## 4. API

Mevcut `AiController`'a (`/api/v1/ai`, `@PreAuthorize("hasAnyRole('VET','ADMIN')")`) eklenir:

| Endpoint | Body | Dönen | Hata |
|---|---|---|---|
| `POST /treatment-recommendations` | `{encounterId}` | `{aiJobId, suggestionText, modelConnected}` | 422 (assessment boş), 404 (encounter yok/başka kiracı) |
| `POST /treatment-recommendations/{aiJobId}/decision` | `{status: ACCEPTED_AS_IS \| REJECTED, appliedContent?}` | 200 | 404 (aiJob yok/başka kiracı) |

İkinci endpoint doğrudan mevcut `RecordAiJobDecisionUseCase`'i (tenant kontrolü eklenmiş haliyle) çağırır — yeni bir use-case yazılmaz.

## 5. Frontend

**`aiApi.ts`:**
```typescript
generateTreatmentRecommendation: (encounterId: string) =>
  apiClient.post<{ aiJobId: string; suggestionText: string; modelConnected: boolean }>(
    '/api/v1/ai/treatment-recommendations', { encounterId }
  ),
decideTreatmentRecommendation: (aiJobId: string, status: 'ACCEPTED_AS_IS' | 'REJECTED', appliedContent?: string) =>
  apiClient.post<void>(`/api/v1/ai/treatment-recommendations/${aiJobId}/decision`, { status, appliedContent }),
```

**`EncounterPage.tsx`** — yeni kart, **sol sütunda**, "SOAP Notu" kartının hemen altında:
- Başlık: "✦ AI Tedavi Önerisi" (mevcut AI Scribe kart başlığıyla aynı stil).
- "Tedavi Önerisi Al" butonu (`variant="ai"`) — `!soap.assessment.trim()` iken disabled, altında "Önce Assessment alanını doldurup kaydedin" ipucu (`aiCopy` stili).
- Tıklanınca: `generatingRecommendation` state, buton "Oluşturuluyor..." + "~30-60 saniye sürebilir" metni (SOAP kartındaki desenin birebir aynısı).
- Öneri geldiğinde: `aiCopy` stilinde önizleme metni + **HER ZAMAN görünen** (modelConnected'dan bağımsız) güçlü uyarı: *"Bu öneri gerçek bir tıbbi referans kaynağına dayanmaz, yalnızca genel AI bilgisine dayanır. Bağımsız olarak değerlendirin."* (`aiWarning` stili, ama SOAP'taki "model bağlı değil" uyarısından farklı olarak koşulsuz gösterilir).
- İki buton: **"Aynen Kabul Et"** — `soap.plan`'ı öneri metniyle değiştirir + `decideTreatmentRecommendation(aiJobId, 'ACCEPTED_AS_IS')` çağırır + kısa bir onay mesajı gösterir (AI Scribe'ın `draftApplied` deseniyle aynı). **"Reddet"** — öneriyi ekrandan kaldırır + `decideTreatmentRecommendation(aiJobId, 'REJECTED')` çağırır.
- Kabul/reddet sonrası öneri state'i temizlenir (`treatmentSuggestion = null`) — yeniden "Tedavi Önerisi Al" ile taze bir öneri istenebilir.

## 6. Config

Yeni config eklenmiyor — mevcut `ai.ollama.base-url` / `ai.ollama.model` (`application.yml`, zaten var) aynen kullanılıyor.

## 7. `historyLimit = 5` — Gerekçe (2026-09-08 oturumunda karara bağlandı)

"Tüm geçmiş" yerine sabit 5 seçildi: (1) context penceresi teorik olarak yeterli (128K) ama kronik/uzun süreli hastalarda büyüyebilir, (2) bu donanımda (kısmi CPU offload, ~40-60sn/öneri) ekstra context süreyi daha da uzatır, (3) küçük modellerde (8B) uzun context "ortadaki bilgiyi kaybetme" riski taşır — alakasız eski kayıtlar öneriyi bulanıklaştırabilir. Güncel tedavi kararı için en alakalı olan yakın geçmiştir.

## 8. Test Stratejisi

- `GenerateTreatmentRecommendationUseCase` birim testi (mock port'lar): mutlu yol (doğru `RecordAiJobCommand` ile `RecordAiJobUseCase`'in çağrıldığı doğrulanır), boş assessment → `AssessmentRequiredForRecommendationException`.
- `RecordAiJobDecisionUseCase`'e eklenen tenant kontrolü için yeni test: farklı tenant'ın `aiJobId`'siyle çağrılınca `AiJobNotFoundException`.
- `EncounterLookupAdapter.findClinicalContext` — bu turda gerçek bir API olduğu için, CLAUDE.md'nin standart yöntemiyle uçtan uca doğrulama: klinik oluştur → muayene aç → Assessment yaz+kaydet → tedavi önerisi al (curl) → kabul et/reddet (curl) → `ai_jobs`/`ai_job_decisions` tablolarında doğru kayıt oluştuğunu kontrol et.
- Frontend: `npm run build` (tip kontrolü) + mümkünse tarayıcıda gerçek kullanım.

## 9. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
