# AI Karar Denetim İzi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `modules/ai` içine, klinik öneri niteliğindeki AI görevleri (önce Tedavi Önerisi, sonra Tanı Desteği) için her önerinin ve hekimin nihai kararının/isabet geri bildiriminin değiştirilemez şekilde kaydedildiği bir denetim izi altyapısı (entity + port + use-case + migration) kurmak.

**Architecture:** Hexagonal/modüler monolit deseninin aynısı (`docs/reference-module.md`) — `ai/domain`'de `AiJob` (öneri) ve ona 1:1 bağlı `AiJobDecision` (karar + geri bildirim) entity'leri, domain kuralları (`decide()`/`recordFeedback()` her biri tam bir kez) entity'nin kendi içinde. `ai/application`'da üç use-case, `ai/infrastructure/persistence`'da JPA adaptörleri. Bu turda `api/` katmanı YOK — henüz bunu çağıracak bir özellik (Tedavi Önerisi) yok.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL/Flyway, Lombok, JUnit 5 + Mockito + AssertJ (mevcut backend yığını, yeni bağımlılık yok).

**Spec:** `docs/superpowers/specs/2026-09-07-ai-karar-denetim-izi-design.md`

## Global Constraints

- Entity'lerde public setter YOK — değişiklik sadece anlamlı isimli davranış metotlarıyla (`decide()`, `recordFeedback()`), reference-module.md §1.
- Use-case sınıfı SADECE `domain` paketindeki port arayüzlerini bilir, `infrastructure`'ı import etmez (reference-module.md §5).
- JPA repository interface'leri package-private (`interface`, `public` değil) — dışarıdan sadece adapter üzerinden erişilir (reference-module.md §6).
- Domain exception'lar `com.vetos.platform.exception.DomainException`'dan türer, `(errorCode, message)` constructor'ı ile (reference-module.md §9).
- Bu tur `api/` katmanı içermez — `AiTaskType` şu an sadece `TREATMENT_RECOMMENDATION` değerine sahip, kullanılmayan `DIAGNOSIS_SUPPORT` değeri EKLENMEZ (YAGNI).
- Migration dosya numarası `V31` (mevcut son migration `V30__signup_requests.sql`'den devam).
- Test komutu: `cd backend && ./mvnw test -Dtest=<SinifAdi>` (tek sınıf) veya `./mvnw test` (tümü).

---

### Task 1: `AiJob` domain entity + `AiTaskType` enum + `AiJobRepository` port

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/AiTaskType.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/AiJob.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/AiJobRepository.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/domain/AiJobTest.java`

**Interfaces:**
- Produces: `AiJob.create(UUID tenantId, AiTaskType taskType, UUID encounterId, String suggestionText, String modelName, String modelVersion, UUID requestedByStaffUserId) -> AiJob`; getter'lar `getId()`, `getTenantId()`, `getTaskType()`, `getEncounterId()`, `getSuggestionText()`, `getModelName()`, `getModelVersion()`, `getRequestedByStaffUserId()`, `getCreatedAt()`. `AiJobRepository.save(AiJob) -> AiJob`, `AiJobRepository.findById(UUID) -> Optional<AiJob>`. `AiTaskType.TREATMENT_RECOMMENDATION`.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.ai.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AiJobTest {

    @Test
    void should_setAllFieldsAndCreatedAt_when_created() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();

        AiJob job = AiJob.create(
            tenantId, AiTaskType.TREATMENT_RECOMMENDATION, encounterId,
            "Sivi tedavisi onerilir", "ollama", "llama3.1:8b", staffUserId
        );

        assertThat(job.getTenantId()).isEqualTo(tenantId);
        assertThat(job.getTaskType()).isEqualTo(AiTaskType.TREATMENT_RECOMMENDATION);
        assertThat(job.getEncounterId()).isEqualTo(encounterId);
        assertThat(job.getSuggestionText()).isEqualTo("Sivi tedavisi onerilir");
        assertThat(job.getModelName()).isEqualTo("ollama");
        assertThat(job.getModelVersion()).isEqualTo("llama3.1:8b");
        assertThat(job.getRequestedByStaffUserId()).isEqualTo(staffUserId);
        assertThat(job.getCreatedAt()).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AiJobTest`
Expected: FAIL — compile error, `AiJob`/`AiTaskType` sınıfları bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`AiTaskType.java`:

```java
package com.vetos.modules.ai.domain;

public enum AiTaskType { TREATMENT_RECOMMENDATION }
```

`AiJob.java`:

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

`AiJobRepository.java`:

```java
package com.vetos.modules.ai.domain;

import java.util.Optional;
import java.util.UUID;

public interface AiJobRepository {
    AiJob save(AiJob job);
    Optional<AiJob> findById(UUID id);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AiJobTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/domain/AiTaskType.java \
        backend/src/main/java/com/vetos/modules/ai/domain/AiJob.java \
        backend/src/main/java/com/vetos/modules/ai/domain/AiJobRepository.java \
        backend/src/test/java/com/vetos/modules/ai/domain/AiJobTest.java
git commit -m "feat(ai): AiJob entity ve AiJobRepository portu ekle"
```

---

### Task 2: `AiJobDecision` domain entity + `DecisionStatus`/`AccuracyFeedback` enum'ları + istisnalar + `AiJobDecisionRepository` port

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/DecisionStatus.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/AccuracyFeedback.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/exception/AiDecisionAlreadyRecordedException.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/exception/AiDecisionNotYetMadeException.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/AiJobDecision.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/AiJobDecisionRepository.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/domain/AiJobDecisionTest.java`

**Interfaces:**
- Consumes: (bağımsız — Task 1'deki `AiJob`'a sadece `aiJobId` (UUID) üzerinden referans verir, entity'yi import etmez)
- Produces: `AiJobDecision.createPending(UUID aiJobId) -> AiJobDecision`; `decision.decide(DecisionStatus, String appliedContent, UUID decidedByStaffUserId)`; `decision.recordFeedback(AccuracyFeedback)`; getter'lar `getDecisionStatus()`, `getAppliedContent()`, `getDecidedByStaffUserId()`, `getDecidedAt()`, `getAccuracyFeedback()`, `getFeedbackAt()`. `AiJobDecisionRepository.save(AiJobDecision) -> AiJobDecision`, `findByAiJobId(UUID) -> Optional<AiJobDecision>`.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.ai.domain;

import com.vetos.modules.ai.domain.exception.AiDecisionAlreadyRecordedException;
import com.vetos.modules.ai.domain.exception.AiDecisionNotYetMadeException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiJobDecisionTest {

    @Test
    void should_recordDecision_when_decidedFirstTime() {
        UUID staffUserId = UUID.randomUUID();
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID());

        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, staffUserId);

        assertThat(decision.getDecisionStatus()).isEqualTo(DecisionStatus.ACCEPTED_AS_IS);
        assertThat(decision.getAppliedContent()).isNull();
        assertThat(decision.getDecidedByStaffUserId()).isEqualTo(staffUserId);
        assertThat(decision.getDecidedAt()).isNotNull();
    }

    @Test
    void should_storeAppliedContent_when_acceptedWithEdits() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID());

        decision.decide(DecisionStatus.ACCEPTED_WITH_EDITS, "Duzenlenmis tedavi plani", UUID.randomUUID());

        assertThat(decision.getDecisionStatus()).isEqualTo(DecisionStatus.ACCEPTED_WITH_EDITS);
        assertThat(decision.getAppliedContent()).isEqualTo("Duzenlenmis tedavi plani");
    }

    @Test
    void should_throwAlreadyRecorded_when_decidingTwice() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID());
        decision.decide(DecisionStatus.REJECTED, null, UUID.randomUUID());

        assertThatThrownBy(() -> decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID()))
            .isInstanceOf(AiDecisionAlreadyRecordedException.class);
    }

    @Test
    void should_recordFeedback_when_decisionAlreadyMade() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID());
        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());

        decision.recordFeedback(AccuracyFeedback.ACCURATE);

        assertThat(decision.getAccuracyFeedback()).isEqualTo(AccuracyFeedback.ACCURATE);
        assertThat(decision.getFeedbackAt()).isNotNull();
    }

    @Test
    void should_throwNotYetMade_when_recordingFeedbackBeforeDecision() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID());

        assertThatThrownBy(() -> decision.recordFeedback(AccuracyFeedback.ACCURATE))
            .isInstanceOf(AiDecisionNotYetMadeException.class);
    }

    @Test
    void should_throwAlreadyRecorded_when_recordingFeedbackTwice() {
        AiJobDecision decision = AiJobDecision.createPending(UUID.randomUUID());
        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        decision.recordFeedback(AccuracyFeedback.INACCURATE);

        assertThatThrownBy(() -> decision.recordFeedback(AccuracyFeedback.ACCURATE))
            .isInstanceOf(AiDecisionAlreadyRecordedException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=AiJobDecisionTest`
Expected: FAIL — compile error, sınıflar bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`DecisionStatus.java`:

```java
package com.vetos.modules.ai.domain;

public enum DecisionStatus { ACCEPTED_AS_IS, ACCEPTED_WITH_EDITS, REJECTED }
```

`AccuracyFeedback.java`:

```java
package com.vetos.modules.ai.domain;

public enum AccuracyFeedback { ACCURATE, INACCURATE }
```

`AiDecisionAlreadyRecordedException.java`:

```java
package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiDecisionAlreadyRecordedException extends DomainException {
    public AiDecisionAlreadyRecordedException(UUID aiJobId) {
        super("AI_DECISION_ALREADY_RECORDED", "Bu AI onerisi icin karar/geri bildirim zaten kaydedilmis: " + aiJobId);
    }
}
```

`AiDecisionNotYetMadeException.java`:

```java
package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiDecisionNotYetMadeException extends DomainException {
    public AiDecisionNotYetMadeException(UUID aiJobId) {
        super("AI_DECISION_NOT_YET_MADE", "Bu AI onerisi icin henuz hekim karari verilmemis: " + aiJobId);
    }
}
```

`AiJobDecision.java`:

```java
package com.vetos.modules.ai.domain;

import com.vetos.modules.ai.domain.exception.AiDecisionAlreadyRecordedException;
import com.vetos.modules.ai.domain.exception.AiDecisionNotYetMadeException;
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
    private DecisionStatus decisionStatus;

    @Column(columnDefinition = "text")
    private String appliedContent;

    private UUID decidedByStaffUserId;
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    private AccuracyFeedback accuracyFeedback;
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

`AiJobDecisionRepository.java`:

```java
package com.vetos.modules.ai.domain;

import java.util.Optional;
import java.util.UUID;

public interface AiJobDecisionRepository {
    AiJobDecision save(AiJobDecision decision);
    Optional<AiJobDecision> findByAiJobId(UUID aiJobId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=AiJobDecisionTest`
Expected: PASS (6 test — decide/appliedContent/twice-decide/feedback/feedback-before-decide/twice-feedback)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/domain/DecisionStatus.java \
        backend/src/main/java/com/vetos/modules/ai/domain/AccuracyFeedback.java \
        backend/src/main/java/com/vetos/modules/ai/domain/exception/AiDecisionAlreadyRecordedException.java \
        backend/src/main/java/com/vetos/modules/ai/domain/exception/AiDecisionNotYetMadeException.java \
        backend/src/main/java/com/vetos/modules/ai/domain/AiJobDecision.java \
        backend/src/main/java/com/vetos/modules/ai/domain/AiJobDecisionRepository.java \
        backend/src/test/java/com/vetos/modules/ai/domain/AiJobDecisionTest.java
git commit -m "feat(ai): AiJobDecision entity ile degistirilemez karar/geri bildirim kurali ekle"
```

---

### Task 3: `RecordAiJobUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobUseCaseTest.java`

**Interfaces:**
- Consumes: `AiJob.create(...)` (Task 1), `AiJobRepository` (Task 1)
- Produces: `RecordAiJobUseCase.execute(RecordAiJobCommand) -> UUID`

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobRepository;
import com.vetos.modules.ai.domain.AiTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordAiJobUseCaseTest {

    @Mock private AiJobRepository aiJobRepository;

    private RecordAiJobUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordAiJobUseCase(aiJobRepository);
    }

    @Test
    void should_saveAiJobWithGivenFields_when_executed() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        RecordAiJobCommand command = new RecordAiJobCommand(
            tenantId, AiTaskType.TREATMENT_RECOMMENDATION, encounterId,
            "Sivi tedavisi onerilir", "ollama", "llama3.1:8b", staffUserId
        );
        when(aiJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID resultId = useCase.execute(command);

        ArgumentCaptor<AiJob> captor = ArgumentCaptor.forClass(AiJob.class);
        verify(aiJobRepository).save(captor.capture());
        AiJob saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getEncounterId()).isEqualTo(encounterId);
        assertThat(saved.getSuggestionText()).isEqualTo("Sivi tedavisi onerilir");
        assertThat(resultId).isEqualTo(saved.getId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RecordAiJobUseCaseTest`
Expected: FAIL — compile error, `RecordAiJobCommand`/`RecordAiJobUseCase` bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`RecordAiJobCommand.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import java.util.UUID;

public record RecordAiJobCommand(
    UUID tenantId, AiTaskType taskType, UUID encounterId,
    String suggestionText, String modelName, String modelVersion, UUID requestedByStaffUserId
) {}
```

`RecordAiJobUseCase.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordAiJobUseCase {

    private final AiJobRepository aiJobRepository;

    public UUID execute(RecordAiJobCommand command) {
        AiJob job = AiJob.create(
            command.tenantId(), command.taskType(), command.encounterId(),
            command.suggestionText(), command.modelName(), command.modelVersion(), command.requestedByStaffUserId()
        );
        aiJobRepository.save(job);
        return job.getId();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RecordAiJobUseCaseTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobCommand.java \
        backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobUseCase.java \
        backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobUseCaseTest.java
git commit -m "feat(ai): RecordAiJobUseCase ekle"
```

---

### Task 4: `RecordAiJobDecisionUseCase` + `AiJobNotFoundException`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/exception/AiJobNotFoundException.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCaseTest.java`

**Interfaces:**
- Consumes: `AiJobRepository.findById` (Task 1), `AiJobDecisionRepository.findByAiJobId`/`save` (Task 2), `AiJobDecision.createPending`/`decide` (Task 2)
- Produces: `RecordAiJobDecisionUseCase.execute(RecordAiJobDecisionCommand)` (void)

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AiDecisionAlreadyRecordedException;
import com.vetos.modules.ai.domain.exception.AiJobNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordAiJobDecisionUseCaseTest {

    @Mock private AiJobRepository aiJobRepository;
    @Mock private AiJobDecisionRepository aiJobDecisionRepository;

    private RecordAiJobDecisionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordAiJobDecisionUseCase(aiJobRepository, aiJobDecisionRepository);
    }

    private AiJob anAiJob() {
        return AiJob.create(
            UUID.randomUUID(), AiTaskType.TREATMENT_RECOMMENDATION, UUID.randomUUID(),
            "Sivi tedavisi onerilir", "ollama", "llama3.1:8b", UUID.randomUUID()
        );
    }

    @Test
    void should_createAndSaveDecision_when_noDecisionExistsYet() {
        UUID aiJobId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.of(anAiJob()));
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.empty());
        when(aiJobDecisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RecordAiJobDecisionCommand(aiJobId, DecisionStatus.ACCEPTED_AS_IS, null, staffUserId));

        verify(aiJobDecisionRepository).save(argThat(d ->
            d.getDecisionStatus() == DecisionStatus.ACCEPTED_AS_IS && d.getDecidedByStaffUserId().equals(staffUserId)
        ));
    }

    @Test
    void should_throwAiJobNotFound_when_aiJobDoesNotExist() {
        UUID aiJobId = UUID.randomUUID();
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
            new RecordAiJobDecisionCommand(aiJobId, DecisionStatus.REJECTED, null, UUID.randomUUID())
        )).isInstanceOf(AiJobNotFoundException.class);
    }

    @Test
    void should_throwAlreadyRecorded_when_decisionAlreadyMade() {
        UUID aiJobId = UUID.randomUUID();
        AiJobDecision existing = AiJobDecision.createPending(aiJobId);
        existing.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.of(anAiJob()));
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(
            new RecordAiJobDecisionCommand(aiJobId, DecisionStatus.REJECTED, null, UUID.randomUUID())
        )).isInstanceOf(AiDecisionAlreadyRecordedException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RecordAiJobDecisionUseCaseTest`
Expected: FAIL — compile error, `RecordAiJobDecisionCommand`/`RecordAiJobDecisionUseCase`/`AiJobNotFoundException` bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`AiJobNotFoundException.java`:

```java
package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiJobNotFoundException extends DomainException {
    public AiJobNotFoundException(UUID id) {
        super("AI_JOB_NOT_FOUND", "AI is kaydi bulunamadi: " + id);
    }
}
```

`RecordAiJobDecisionCommand.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.DecisionStatus;
import java.util.UUID;

public record RecordAiJobDecisionCommand(
    UUID aiJobId, DecisionStatus status, String appliedContent, UUID decidedByStaffUserId
) {}
```

`RecordAiJobDecisionUseCase.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import com.vetos.modules.ai.domain.AiJobRepository;
import com.vetos.modules.ai.domain.exception.AiJobNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordAiJobDecisionUseCase {

    private final AiJobRepository aiJobRepository;
    private final AiJobDecisionRepository aiJobDecisionRepository;

    public void execute(RecordAiJobDecisionCommand command) {
        aiJobRepository.findById(command.aiJobId())
            .orElseThrow(() -> new AiJobNotFoundException(command.aiJobId()));

        AiJobDecision decision = aiJobDecisionRepository.findByAiJobId(command.aiJobId())
            .orElseGet(() -> AiJobDecision.createPending(command.aiJobId()));

        decision.decide(command.status(), command.appliedContent(), command.decidedByStaffUserId());
        aiJobDecisionRepository.save(decision);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RecordAiJobDecisionUseCaseTest`
Expected: PASS (3 test)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/domain/exception/AiJobNotFoundException.java \
        backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionCommand.java \
        backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCase.java \
        backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCaseTest.java
git commit -m "feat(ai): RecordAiJobDecisionUseCase ekle"
```

---

### Task 5: `RecordAiJobFeedbackUseCase` + `AiJobDecisionNotFoundException`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/exception/AiJobDecisionNotFoundException.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobFeedbackCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobFeedbackUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobFeedbackUseCaseTest.java`

**Interfaces:**
- Consumes: `AiJobDecisionRepository.findByAiJobId`/`save` (Task 2), `AiJobDecision.recordFeedback` (Task 2)
- Produces: `RecordAiJobFeedbackUseCase.execute(RecordAiJobFeedbackCommand)` (void)

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AiDecisionNotYetMadeException;
import com.vetos.modules.ai.domain.exception.AiJobDecisionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordAiJobFeedbackUseCaseTest {

    @Mock private AiJobDecisionRepository aiJobDecisionRepository;

    private RecordAiJobFeedbackUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordAiJobFeedbackUseCase(aiJobDecisionRepository);
    }

    @Test
    void should_saveFeedback_when_decisionAlreadyMade() {
        UUID aiJobId = UUID.randomUUID();
        AiJobDecision decision = AiJobDecision.createPending(aiJobId);
        decision.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.of(decision));

        useCase.execute(new RecordAiJobFeedbackCommand(aiJobId, AccuracyFeedback.ACCURATE));

        assertThat(decision.getAccuracyFeedback()).isEqualTo(AccuracyFeedback.ACCURATE);
        verify(aiJobDecisionRepository).save(decision);
    }

    @Test
    void should_throwAiJobDecisionNotFound_when_noDecisionRecordExists() {
        UUID aiJobId = UUID.randomUUID();
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RecordAiJobFeedbackCommand(aiJobId, AccuracyFeedback.ACCURATE)))
            .isInstanceOf(AiJobDecisionNotFoundException.class);
    }

    @Test
    void should_throwNotYetMade_when_decisionIsPending() {
        UUID aiJobId = UUID.randomUUID();
        AiJobDecision pending = AiJobDecision.createPending(aiJobId);
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> useCase.execute(new RecordAiJobFeedbackCommand(aiJobId, AccuracyFeedback.ACCURATE)))
            .isInstanceOf(AiDecisionNotYetMadeException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RecordAiJobFeedbackUseCaseTest`
Expected: FAIL — compile error, sınıflar bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`AiJobDecisionNotFoundException.java`:

```java
package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AiJobDecisionNotFoundException extends DomainException {
    public AiJobDecisionNotFoundException(UUID aiJobId) {
        super("AI_JOB_DECISION_NOT_FOUND", "AI is karari bulunamadi (aiJobId): " + aiJobId);
    }
}
```

`RecordAiJobFeedbackCommand.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AccuracyFeedback;
import java.util.UUID;

public record RecordAiJobFeedbackCommand(UUID aiJobId, AccuracyFeedback feedback) {}
```

`RecordAiJobFeedbackUseCase.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import com.vetos.modules.ai.domain.exception.AiJobDecisionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordAiJobFeedbackUseCase {

    private final AiJobDecisionRepository aiJobDecisionRepository;

    public void execute(RecordAiJobFeedbackCommand command) {
        AiJobDecision decision = aiJobDecisionRepository.findByAiJobId(command.aiJobId())
            .orElseThrow(() -> new AiJobDecisionNotFoundException(command.aiJobId()));

        decision.recordFeedback(command.feedback());
        aiJobDecisionRepository.save(decision);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RecordAiJobFeedbackUseCaseTest`
Expected: PASS (3 test)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/domain/exception/AiJobDecisionNotFoundException.java \
        backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobFeedbackCommand.java \
        backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobFeedbackUseCase.java \
        backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobFeedbackUseCaseTest.java
git commit -m "feat(ai): RecordAiJobFeedbackUseCase ekle"
```

---

### Task 6: Migration + JPA persistence adaptörleri + uçtan uca doğrulama

**Files:**
- Create: `backend/src/main/resources/db/migration/V31__ai_decision_audit.sql`
- Create: `backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobJpaRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobRepositoryAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobDecisionJpaRepository.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobDecisionRepositoryAdapter.java`

**Interfaces:**
- Consumes: `AiJob`/`AiJobRepository` (Task 1), `AiJobDecision`/`AiJobDecisionRepository` (Task 2)
- Produces: Spring context'inde `AiJobRepository` ve `AiJobDecisionRepository` port'larının çalışan implementasyonları (`@Component` bean'leri) — Task 3/4/5'teki use-case'ler artık gerçek bir Postgres tablosuna karşı çalışabilir.

Bu görevde JPA adaptörleri (`PatientRepositoryAdapter` gibi) sadece Spring Data'ya delege eden ince pass-through sınıflar — projede bu katman için ayrı bir birim test konvansiyonu yok (bkz. `PatientRepositoryAdapter`, `AiJobRepositoryAdapter`'ın kendisi test edilmiyor). Doğrulama, migration'ın gerçekten uygulandığını ve modül sınırlarının bozulmadığını göstererek yapılır.

- [ ] **Step 1: Migration dosyasını yaz**

`backend/src/main/resources/db/migration/V31__ai_decision_audit.sql`:

```sql
CREATE TABLE ai_jobs (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL,
    task_type                  TEXT NOT NULL,
    encounter_id               UUID NOT NULL,
    suggestion_text            TEXT NOT NULL,
    model_name                 TEXT NOT NULL,
    model_version              TEXT NOT NULL,
    requested_by_staff_user_id UUID NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ai_jobs_tenant_id ON ai_jobs(tenant_id);
CREATE INDEX idx_ai_jobs_encounter_id ON ai_jobs(encounter_id);

CREATE TABLE ai_job_decisions (
    id                        UUID PRIMARY KEY,
    ai_job_id                 UUID NOT NULL UNIQUE,
    decision_status           TEXT,
    applied_content           TEXT,
    decided_by_staff_user_id  UUID,
    decided_at                TIMESTAMPTZ,
    accuracy_feedback         TEXT,
    feedback_at               TIMESTAMPTZ
);
```

- [ ] **Step 2: JPA repository + adaptörleri yaz**

`AiJobJpaRepository.java`:

```java
package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AiJobJpaRepository extends JpaRepository<AiJob, UUID> {
}
```

`AiJobRepositoryAdapter.java`:

```java
package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AiJobRepositoryAdapter implements AiJobRepository {

    private final AiJobJpaRepository jpaRepository;

    @Override
    public AiJob save(AiJob job) {
        return jpaRepository.save(job);
    }

    @Override
    public Optional<AiJob> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
```

`AiJobDecisionJpaRepository.java`:

```java
package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJobDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AiJobDecisionJpaRepository extends JpaRepository<AiJobDecision, UUID> {
    Optional<AiJobDecision> findByAiJobId(UUID aiJobId);
}
```

`AiJobDecisionRepositoryAdapter.java`:

```java
package com.vetos.modules.ai.infrastructure.persistence;

import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AiJobDecisionRepositoryAdapter implements AiJobDecisionRepository {

    private final AiJobDecisionJpaRepository jpaRepository;

    @Override
    public AiJobDecision save(AiJobDecision decision) {
        return jpaRepository.save(decision);
    }

    @Override
    public Optional<AiJobDecision> findByAiJobId(UUID aiJobId) {
        return jpaRepository.findByAiJobId(aiJobId);
    }
}
```

- [ ] **Step 3: Modül sınırı testini çalıştır**

Run: `cd backend && ./mvnw test -Dtest=ApplicationModulesTest`
Expected: PASS — yeni sınıflar `ai` modülü içinde kaldığı ve başka modülün domain'ine erişmediği için `ApplicationModules.verify()` kırılmaz.

- [ ] **Step 4: Tüm test paketini çalıştır**

Run: `cd backend && ./mvnw test`
Expected: PASS — Task 1-5'teki tüm testler + `ApplicationModulesTest` yeşil.

- [ ] **Step 5: Migration'ın gerçekten uygulandığını canlı instance'a karşı doğrula**

```bash
cd backend && docker-compose up -d
cd backend && ./mvnw spring-boot:run
```

Loglarda şu satırı ara (CLAUDE.md'nin doğrulama konvansiyonu — canlı instance'a karşı kontrol):

```
Current version of schema "public": 31
```

Expected: `31` görünür, Flyway hatasız başlar, uygulama `Started VetosApplication` log satırıyla ayağa kalkar.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V31__ai_decision_audit.sql \
        backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobJpaRepository.java \
        backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobRepositoryAdapter.java \
        backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobDecisionJpaRepository.java \
        backend/src/main/java/com/vetos/modules/ai/infrastructure/persistence/AiJobDecisionRepositoryAdapter.java
git commit -m "feat(ai): AiJob/AiJobDecision icin migration ve JPA adaptorlerini ekle"
```

---

## Bu Plan Tamamlandığında

`modules/ai` içinde, klinik öneri AI görevleri için tam çalışan, test edilmiş bir denetim izi altyapısı olacak: bir öneri `RecordAiJobUseCase` ile kaydedilebilir, hekimin kararı `RecordAiJobDecisionUseCase` ile (bir kez) kaydedilebilir, isabet geri bildirimi `RecordAiJobFeedbackUseCase` ile (bir kez) kaydedilebilir — hepsi gerçek bir Postgres tablosuna karşı çalışır. Henüz hiçbir HTTP endpoint'i veya UI yok; bir sonraki tur (AI Tedavi Önerisi, ayrı bir spec) kendi controller'ını ekleyip bu üç use-case'i çağıracak.
