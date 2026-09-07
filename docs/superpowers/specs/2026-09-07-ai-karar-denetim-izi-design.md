# AI Karar Denetim İzi — Tasarım Dokümanı

**Tarih:** 2026-09-07
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `modules/ai`

## 1. Bağlam ve Amaç

requirements.md §5, klinik yargı gerektiren AI özellikleri (Tedavi Önerisi, ileride Tanı Desteği) için şunu zorunlu koşuyor: *"Her AI önerisi (öneri metni, kullanılan model/versiyon, zaman damgası) ile hekimin nihai kararı (aynen kabul / düzenleyerek kabul / reddetti) ayrı ayrı ve değiştirilemez şekilde loglanır"* + hekimin isabetli/isabetli değil şeklinde geri bildirim verebilmesi. Aynı doküman, Tanı Desteği'nin bu altyapı kurulmadan devreye alınamayacağını da açıkça belirtiyor.

Bu doküman, **AI Tedavi Önerisi** özelliğinin (ayrı bir sonraki tur) üzerine kurulacağı bu denetim izi altyapısını tanımlıyor. Bu, Vetly AI yol haritasının **sub-proje #1**'i — sub-proje #2 (AI Tedavi Önerisi) bu altyapıyı kullanacak, o turda ayrı bir tasarım dokümanı yazılacak.

**Neden ayrı bir alt-proje:** Referans kaynağı olmadan (sadece LLM'in kendi bilgisine dayanan) bir tedavi önerisi özelliğinde, hekim onayı ve bu onayın değiştirilemez kaydı tek güvenlik/hesap verebilirlik katmanı. Bu nedenle altyapı, öneriyi üreten özellikten önce ve ondan bağımsız olarak sağlam kurulmalı.

## 2. Kapsam

**Bu turda yapılacak:**
- `modules/ai/domain` içine `AiJob` (öneri + model/versiyon + zaman damgası) ve `AiJobDecision` (hekim kararı + isabet geri bildirimi) entity'leri
- İkisi arasındaki 1:1 ilişki, karar/geri bildirim bir kez verildikten sonra değiştirilemez hale getiren domain kuralı
- Üç application use-case'i (`RecordAiJobUseCase`, `RecordAiJobDecisionUseCase`, `RecordAiJobFeedbackUseCase`)
- JPA persistence (infrastructure) + Flyway migration
- Birim testler (özellikle "ikinci kez karar/geri bildirim" senaryosu)

**Kapsam dışı (bilinçli olarak):**
- **`api/` katmanı (REST controller) yok** — bu use-case'leri çağıracak somut bir özellik (Tedavi Önerisi) henüz yok; controller o turda, kendi endpoint'leriyle birlikte eklenecek.
- **Görüntüleme/liste ekranı yok** — geçmiş AI kararlarını gösteren bir ekran, somut bir öneri UI'ı varken (Tedavi Önerisi turunda) doğal olarak eklenecek.
- **SOAP taslak üretimi (`GenerateSoapDraftUseCase`) bu denetim izini kullanmayacak** — o bir "klinik öneri/karar" değil, dikte edilen metni yapılandırma işlemi; mevcut kod hiç değişmiyor.
- **AI Tedavi Önerisi'nin kendisi** — ayrı bir tasarım dokümanı ve onay döngüsü gerektiriyor.

## 3. Veri Modeli

### 3.1 `AiJob` (yeni entity, `ai_jobs` tablosu)

```java
package com.vetos.modules.ai.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_jobs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiTaskType taskType;

    @Column(nullable = false)
    private UUID encounterId;

    @Column(nullable = false, columnDefinition = "text")
    private String suggestionText;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String modelVersion;

    @Column(nullable = false)
    private UUID requestedByStaffUserId;

    @Column(nullable = false)
    private Instant createdAt;

    public static AiJob create(
        UUID tenantId, AiTaskType taskType, UUID encounterId,
        String suggestionText, String modelName, String modelVersion, UUID requestedByStaffUserId
    ) {
        AiJob job = new AiJob();
        job.tenantId = tenantId;
        job.taskType = taskType;
        job.encounterId = encounterId;
        job.suggestionText = suggestionText;
        job.modelName = modelName;
        job.modelVersion = modelVersion;
        job.requestedByStaffUserId = requestedByStaffUserId;
        job.createdAt = Instant.now();
        return job;
    }
}
```

```java
package com.vetos.modules.ai.domain;

public enum AiTaskType { TREATMENT_RECOMMENDATION }
```

`AiTaskType`, ileride `DIAGNOSIS_SUPPORT` eklenecek şekilde genişletilebilir bırakılıyor — ama bu turda tek değer var (YAGNI: kullanılmayan bir değer eklenmiyor).

### 3.2 `AiJobDecision` (yeni entity, `ai_job_decisions` tablosu)

```java
package com.vetos.modules.ai.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_job_decisions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AiJobDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID aiJobId;

    @Enumerated(EnumType.STRING)
    private DecisionStatus decisionStatus; // null = henuz karar verilmedi

    @Column(columnDefinition = "text")
    private String appliedContent; // sadece ACCEPTED_WITH_EDITS'te dolu

    private UUID decidedByStaffUserId;
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    private AccuracyFeedback accuracyFeedback; // null = henuz geri bildirim yok
    private Instant feedbackAt;

    public static AiJobDecision createPending(UUID aiJobId) {
        AiJobDecision d = new AiJobDecision();
        d.aiJobId = aiJobId;
        return d;
    }

    public void decide(DecisionStatus status, String appliedContent, UUID decidedByStaffUserId) {
        if (this.decisionStatus != null) {
            throw new AiDecisionAlreadyRecordedException(this.aiJobId);
        }
        this.decisionStatus = status;
        this.appliedContent = appliedContent;
        this.decidedByStaffUserId = decidedByStaffUserId;
        this.decidedAt = Instant.now();
    }

    public void recordFeedback(AccuracyFeedback feedback) {
        if (this.decisionStatus == null) {
            throw new AiDecisionNotYetMadeException(this.aiJobId);
        }
        if (this.accuracyFeedback != null) {
            throw new AiDecisionAlreadyRecordedException(this.aiJobId);
        }
        this.accuracyFeedback = feedback;
        this.feedbackAt = Instant.now();
    }
}
```

```java
package com.vetos.modules.ai.domain;

public enum DecisionStatus { ACCEPTED_AS_IS, ACCEPTED_WITH_EDITS, REJECTED }
public enum AccuracyFeedback { ACCURATE, INACCURATE }
```

**Değiştirilemezlik kuralı:** `decide()` ve `recordFeedback()` her biri **tam olarak bir kez** çalışabilir; ikinci çağrı `AiDecisionAlreadyRecordedException` fırlatır (`domain/exception/`, `DomainException`'dan türer — reference-module.md §9 deseniyle aynı, mevcut global `@ExceptionHandler` otomatik olarak standart hata JSON'una çevirir). Bu, tüm denetim izinin dayandığı tek kural.

### 3.3 Portlar (`domain/`)

```java
public interface AiJobRepository {
    AiJob save(AiJob job);
    Optional<AiJob> findById(UUID id);
}

public interface AiJobDecisionRepository {
    AiJobDecision save(AiJobDecision decision);
    Optional<AiJobDecision> findByAiJobId(UUID aiJobId);
}
```

Dışa açık bir `LookupPort` yok — bu turda `ai` modülü dışında hiçbir modül bu veriye erişmiyor.

### 3.4 Migration

`V31__ai_decision_audit.sql` (mevcut son migration `V30`'dan devam): iki tablo yukarıdaki alanlarla, `ai_jobs(tenant_id)` ve `ai_jobs(encounter_id)` üzerinde index, `ai_job_decisions(ai_job_id)` üzerinde `UNIQUE` kısıtı (1:1 ilişkiyi veritabanı seviyesinde de garanti eder).

## 4. Application Katmanı (Use-Case'ler)

- **`RecordAiJobUseCase(tenantId, taskType, encounterId, suggestionText, modelName, modelVersion, requestedByStaffUserId)`** — `AiJob.create(...)` + kaydet, `aiJobId` döner. Bir öneri üretilir üretilmez (Tedavi Önerisi turunda) çağrılacak.
- **`RecordAiJobDecisionUseCase(aiJobId, status, appliedContent, decidedByStaffUserId)`** — `aiJobRepository.findById` (yoksa `AiJobNotFoundException`) → ilişkili `AiJobDecision` yoksa `createPending` ile oluştur → `decision.decide(...)` → kaydet.
- **`RecordAiJobFeedbackUseCase(aiJobId, feedback)`** — ilişkili `AiJobDecision`'ı bul (yoksa `AiJobDecisionNotFoundException`) → `decision.recordFeedback(...)` → kaydet.

Üç use-case de reference-module.md §5 deseniyle: sadece `domain` port'larını bilir, `infrastructure`'ı import etmez.

## 5. Test Stratejisi

- `AiJobDecision` domain testi: `decide()` sonrası ikinci `decide()` çağrısının `AiDecisionAlreadyRecordedException` fırlattığı; `recordFeedback()` karar verilmeden çağrılırsa `AiDecisionNotYetMadeException`; ikinci `recordFeedback()` çağrısının da istisna fırlattığı.
- Üç use-case için mock repository ile birim test (reference-module.md §13 deseniyle) — mutlu yol + yukarıdaki istisna senaryolarının use-case seviyesinde de doğru yayıldığı.
- Bu tur `api/` içermediği için curl/uçtan uca doğrulama yok — bir sonraki turda (Tedavi Önerisi) gerçek bir controller üzerinden uçtan uca test edilecek.

## 6. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
