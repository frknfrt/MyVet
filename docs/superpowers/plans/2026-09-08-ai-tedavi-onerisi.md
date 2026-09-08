# AI Tedavi Önerisi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hekimin bir muayenede yazdığı Assessment + hastanın son 5 muayenesine dayanan bir AI tedavi planı önerisi üretmek, `2026-09-07` turunda kurulan denetim izine (`AiJob`/`AiJobDecision`) kaydetmek, ve hekimin "Aynen Kabul Et" / "Reddet" kararıyla onaya sunmak — hem backend (yeni port/use-case/endpoint) hem frontend (yeni kart).

**Architecture:** Hexagonal desenin devamı. `encounter` modülüne yeni bir `EncounterLookupPort` metodu (klinik bağlam okuma), `ai` modülüne yeni bir `TreatmentRecommendationPort`/Ollama adaptörü/use-case, mevcut `RecordAiJobUseCase`/`RecordAiJobDecisionUseCase`'in (önceki turdan) doğrudan yeniden kullanımı. `ai` modülünün `package-info.java`'sına `"modules.encounter::domain"` eklenir (yeni izin verilen bağımlılık). **Yeni migration YOK** — `ai_jobs`/`ai_job_decisions` tabloları önceki turdan zaten var.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, JUnit 5 + Mockito + AssertJ, `com.sun.net.httpserver.HttpServer` (stub HTTP sunucu, mevcut `TwilioNotificationAdapterTest` deseni) — backend. React + TypeScript — frontend (yeni bağımlılık yok).

**Spec:** `docs/superpowers/specs/2026-09-08-ai-tedavi-onerisi-design.md`

## Global Constraints

- Entity'lerde public setter YOK — bu turda hiçbir entity'ye yeni alan eklenmiyor (mevcut `AiJob`/`AiJobDecision`/`Encounter` değişmeden kullanılıyor).
- Use-case sınıfı SADECE `domain` paketindeki port arayüzlerini bilir, `infrastructure`'ı import etmez.
- JPA repository interface'leri package-private (bu turda yeni bir JPA repository YOK, mevcut `EncounterJpaRepository.findByPatientId` kullanılıyor).
- `ai` modülü artık `modules.encounter::domain`'e bağımlı — bu **sadece** `EncounterLookupPort`/`EncounterClinicalContext`/`PastEncounterSummary` üzerinden kullanılır, `EncounterRepository` (tam CRUD) ASLA import edilmez.
- AI üretimi içerik her zaman `--color-ai-600` ailesinde gösterilir, "hekim onayı bekliyor" ilkesiyle — asla sessizce otomatik uygulanmaz (frontend).
- Bu özellik gerçek bir tıbbi referans kaynağına dayanmıyor — frontend'de model bağlantısından BAĞIMSIZ, her zaman görünen bir uyarı zorunlu.
- Test komutu: `cd backend && ./mvnw test -Dtest=<SinifAdi>` (tek sınıf) veya `./mvnw test` (tümü). Frontend: `cd frontend && npm run build`.

---

### Task 1: `encounter` modülü — klinik bağlam lookup'ı (`EncounterClinicalContext`)

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/encounter/domain/EncounterClinicalContext.java`
- Create: `backend/src/main/java/com/vetos/modules/encounter/domain/PastEncounterSummary.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/domain/EncounterLookupPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/encounter/infrastructure/persistence/EncounterLookupAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/encounter/infrastructure/persistence/EncounterLookupAdapterTest.java`

**Interfaces:**
- Produces: `EncounterLookupPort.findClinicalContext(UUID encounterId, int historyLimit) -> EncounterClinicalContext`; `EncounterClinicalContext(UUID encounterId, UUID patientId, String currentAssessment, List<PastEncounterSummary> recentHistory)`; `PastEncounterSummary(Instant date, String assessment, String plan)`.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterLookupAdapterTest {

    @Mock private EncounterJpaRepository jpaRepository;

    private EncounterLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new EncounterLookupAdapter(jpaRepository);
    }

    private Encounter anEncounter(UUID patientId, Instant date, String assessment, String plan, boolean finalize) {
        Encounter e = Encounter.start(patientId, UUID.randomUUID(), null, null);
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(e, "encounterDate", date);
        e.updateSoap("s", "o", assessment, plan);
        if (finalize) {
            e.finalizeEncounter();
        }
        return e;
    }

    @Test
    void should_returnCurrentAssessmentAndRecentFinalizedHistory_excludingCurrentAndDrafts() {
        UUID patientId = UUID.randomUUID();
        Instant now = Instant.now();
        Encounter current = anEncounter(patientId, now, "Guncel degerlendirme", "Guncel plan", false);
        Encounter past1 = anEncounter(patientId, now.minus(1, ChronoUnit.DAYS), "Gecmis 1", "Plan 1", true);
        Encounter past2 = anEncounter(patientId, now.minus(2, ChronoUnit.DAYS), "Gecmis 2", "Plan 2", true);
        Encounter draftPast = anEncounter(patientId, now.minus(3, ChronoUnit.DAYS), "Taslak", "Taslak plan", false);
        when(jpaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(jpaRepository.findByPatientId(patientId)).thenReturn(List.of(current, past1, past2, draftPast));

        EncounterClinicalContext context = adapter.findClinicalContext(current.getId(), 5);

        assertThat(context.currentAssessment()).isEqualTo("Guncel degerlendirme");
        assertThat(context.patientId()).isEqualTo(patientId);
        assertThat(context.recentHistory()).hasSize(2);
        assertThat(context.recentHistory().get(0).assessment()).isEqualTo("Gecmis 1");
        assertThat(context.recentHistory().get(1).assessment()).isEqualTo("Gecmis 2");
    }

    @Test
    void should_limitHistoryToGivenLimit() {
        UUID patientId = UUID.randomUUID();
        Instant now = Instant.now();
        Encounter current = anEncounter(patientId, now, "Guncel", "Plan", false);
        List<Encounter> all = new ArrayList<>();
        all.add(current);
        for (int i = 1; i <= 7; i++) {
            all.add(anEncounter(patientId, now.minus(i, ChronoUnit.DAYS), "Gecmis " + i, "Plan " + i, true));
        }
        when(jpaRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(jpaRepository.findByPatientId(patientId)).thenReturn(all);

        EncounterClinicalContext context = adapter.findClinicalContext(current.getId(), 3);

        assertThat(context.recentHistory()).hasSize(3);
        assertThat(context.recentHistory().get(0).assessment()).isEqualTo("Gecmis 1");
    }

    @Test
    void should_throwEncounterNotFound_when_encounterDoesNotExist() {
        UUID encounterId = UUID.randomUUID();
        when(jpaRepository.findById(encounterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.findClinicalContext(encounterId, 5))
            .isInstanceOf(EncounterNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=EncounterLookupAdapterTest`
Expected: FAIL — compile error, `findClinicalContext` metodu `EncounterLookupAdapter`'da yok, `EncounterClinicalContext` sınıfı bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`EncounterClinicalContext.java`:

```java
package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.UUID;

public record EncounterClinicalContext(
    UUID encounterId, UUID patientId, String currentAssessment, List<PastEncounterSummary> recentHistory
) {}
```

`PastEncounterSummary.java`:

```java
package com.vetos.modules.encounter.domain;

import java.time.Instant;

public record PastEncounterSummary(Instant date, String assessment, String plan) {}
```

`EncounterLookupPort.java` (mevcut dosyayı düzenle):

```java
package com.vetos.modules.encounter.domain;

import java.util.UUID;

/**
 * Diger moduller (billing, inventory) muayene bilgisine SADECE bu port
 * uzerinden erisir. EncounterRepository'yi ASLA import etmezler.
 */
public interface EncounterLookupPort {
    EncounterSummary findSummaryById(UUID encounterId);
    EncounterClinicalContext findClinicalContext(UUID encounterId, int historyLimit);
}
```

`EncounterLookupAdapter.java` (mevcut dosyayı düzenle):

```java
package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.*;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class EncounterLookupAdapter implements EncounterLookupPort {

    private final EncounterJpaRepository jpaRepository;

    @Override
    public EncounterSummary findSummaryById(UUID encounterId) {
        Encounter e = jpaRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));
        return new EncounterSummary(e.getId(), e.getPatientId(), e.getStaffUserId(), e.getStatus());
    }

    @Override
    public EncounterClinicalContext findClinicalContext(UUID encounterId, int historyLimit) {
        Encounter current = jpaRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));

        List<PastEncounterSummary> history = jpaRepository.findByPatientId(current.getPatientId()).stream()
            .filter(e -> !e.getId().equals(encounterId))
            .filter(e -> e.getStatus() == EncounterStatus.FINALIZED || e.getStatus() == EncounterStatus.AMENDED)
            .sorted(Comparator.comparing(Encounter::getEncounterDate).reversed())
            .limit(historyLimit)
            .map(e -> new PastEncounterSummary(e.getEncounterDate(), e.getAssessment(), e.getPlan()))
            .toList();

        return new EncounterClinicalContext(current.getId(), current.getPatientId(), current.getAssessment(), history);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=EncounterLookupAdapterTest`
Expected: PASS (3 test)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/encounter/domain/EncounterClinicalContext.java \
        backend/src/main/java/com/vetos/modules/encounter/domain/PastEncounterSummary.java \
        backend/src/main/java/com/vetos/modules/encounter/domain/EncounterLookupPort.java \
        backend/src/main/java/com/vetos/modules/encounter/infrastructure/persistence/EncounterLookupAdapter.java \
        backend/src/test/java/com/vetos/modules/encounter/infrastructure/persistence/EncounterLookupAdapterTest.java
git commit -m "feat(encounter): findClinicalContext ile hasta gecmisi lookup'i ekle"
```

---

### Task 2: `TreatmentRecommendationPort` + `OllamaTreatmentRecommendationAdapter`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/TreatmentRecommendationPort.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/TreatmentRecommendationInput.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/HistoryEntry.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/TreatmentRecommendationDraft.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/infrastructure/adapter/OllamaTreatmentRecommendationAdapter.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/infrastructure/adapter/OllamaTreatmentRecommendationAdapterTest.java`

**Interfaces:**
- Produces: `TreatmentRecommendationPort.generate(TreatmentRecommendationInput) -> TreatmentRecommendationDraft`; `TreatmentRecommendationInput(String currentAssessment, List<HistoryEntry> history)`; `HistoryEntry(String date, String assessment, String plan)`; `TreatmentRecommendationDraft(String suggestionText, boolean modelConnected)`.

Bu görev tamamen `ai` modülü içinde bağımsız — `encounter` modülüne dokunmuyor (dönüşüm Task 3'te use-case katmanında yapılacak).

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.HistoryEntry;
import com.vetos.modules.ai.domain.TreatmentRecommendationDraft;
import com.vetos.modules.ai.domain.TreatmentRecommendationInput;
import com.vetos.modules.ai.domain.TreatmentRecommendationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaTreatmentRecommendationAdapterTest {

    private HttpServer stubServer;
    private String stubResponseBody;

    @BeforeEach
    void startStubServer() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/api/generate", ex -> {
            byte[] payload = stubResponseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, payload.length);
            ex.getResponseBody().write(payload);
            ex.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    private String stubUrl() {
        return "http://127.0.0.1:" + stubServer.getAddress().getPort();
    }

    private TreatmentRecommendationInput anInput() {
        return new TreatmentRecommendationInput(
            "Hafif gastrit supheli",
            List.of(new HistoryEntry(Instant.now().toString(), "Gecmis degerlendirme", "Gecmis plan"))
        );
    }

    @Test
    void should_returnSimulatedDraft_when_baseUrlIsBlank() {
        TreatmentRecommendationPort adapter = new OllamaTreatmentRecommendationAdapter("", "llama3.1", new ObjectMapper());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
        assertThat(draft.suggestionText()).contains("AI modeli henuz baglanmadi");
    }

    @Test
    void should_returnParsedSuggestion_when_ollamaRespondsSuccessfully() {
        stubResponseBody = "{\"response\":\"Diyet degisikligi ve 1 hafta kontrol onerilir.\"}";
        TreatmentRecommendationPort adapter = new OllamaTreatmentRecommendationAdapter(stubUrl(), "llama3.1", new ObjectMapper());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isTrue();
        assertThat(draft.suggestionText()).isEqualTo("Diyet degisikligi ve 1 hafta kontrol onerilir.");
    }

    @Test
    void should_returnSimulatedDraft_when_ollamaCallFails() {
        stubServer.stop(0);
        TreatmentRecommendationPort adapter = new OllamaTreatmentRecommendationAdapter(stubUrl(), "llama3.1", new ObjectMapper());

        TreatmentRecommendationDraft draft = adapter.generate(anInput());

        assertThat(draft.modelConnected()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=OllamaTreatmentRecommendationAdapterTest`
Expected: FAIL — compile error, sınıflar bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`TreatmentRecommendationPort.java`:

```java
package com.vetos.modules.ai.domain;

public interface TreatmentRecommendationPort {
    TreatmentRecommendationDraft generate(TreatmentRecommendationInput input);
}
```

`TreatmentRecommendationInput.java`:

```java
package com.vetos.modules.ai.domain;

import java.util.List;

public record TreatmentRecommendationInput(String currentAssessment, List<HistoryEntry> history) {}
```

`HistoryEntry.java`:

```java
package com.vetos.modules.ai.domain;

public record HistoryEntry(String date, String assessment, String plan) {}
```

`TreatmentRecommendationDraft.java`:

```java
package com.vetos.modules.ai.domain;

public record TreatmentRecommendationDraft(String suggestionText, boolean modelConnected) {}
```

`OllamaTreatmentRecommendationAdapter.java`:

```java
package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.ai.domain.TreatmentRecommendationDraft;
import com.vetos.modules.ai.domain.TreatmentRecommendationInput;
import com.vetos.modules.ai.domain.TreatmentRecommendationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lokal Ollama uzerinden tedavi onerisi uretimi -- OllamaSoapGenerationAdapter
 * ile ayni desen (OLLAMA_BASE_URL bos ise simule davranisa duser). SOAP'tan
 * farkli olarak format:json ISTEMIYOR -- tek bir serbest metin oneri yeterli,
 * S/O/A/P gibi alan ayristirmasi yok.
 */
@Component
@Slf4j
class OllamaTreatmentRecommendationAdapter implements TreatmentRecommendationPort {

    private static final String STATUS_NOTE =
        "[AI modeli henuz baglanmadi -- gercek bir tedavi onerisi uretilemedi. Lutfen tedavi planini elle girin.]";

    private static final String SYSTEM_PROMPT = """
        Sen bir veteriner klinik karar destegi asistanisin. Sana hekimin bir hasta icin yazdigi \
        degerlendirme (assessment) ve hastanin gecmis muayene ozetleri verilecek. Bu bilgilere \
        dayanarak olasi bir tedavi plani oner. Kesin tani koyma, sadece tedavi secenekleri sun. \
        Yanitin sade bir paragraf/madde listesi olsun, JSON veya markdown kullanma.""";

    private final String baseUrl;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    OllamaTreatmentRecommendationAdapter(
        @Value("${ai.ollama.base-url:}") String baseUrl,
        @Value("${ai.ollama.model:llama3.1}") String model,
        ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(120_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public TreatmentRecommendationDraft generate(TreatmentRecommendationInput input) {
        if (baseUrl.isBlank()) {
            return fallback();
        }
        try {
            return generateViaOllama(input);
        } catch (Exception e) {
            log.warn("Ollama tedavi onerisi uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback();
        }
    }

    private TreatmentRecommendationDraft generateViaOllama(TreatmentRecommendationInput input) throws Exception {
        String prompt = buildPrompt(input);
        Map<String, Object> body = Map.of(
            "model", model,
            "system", SYSTEM_PROMPT,
            "prompt", prompt,
            "stream", false
        );
        String raw = restClient.post()
            .uri(baseUrl + "/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);

        JsonNode root = objectMapper.readTree(raw);
        String suggestion = root.path("response").asText("").trim();
        return new TreatmentRecommendationDraft(suggestion, true);
    }

    private String buildPrompt(TreatmentRecommendationInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Guncel degerlendirme: ").append(input.currentAssessment()).append("\n\n");
        if (input.history().isEmpty()) {
            sb.append("Gecmis muayene kaydi yok.");
        } else {
            sb.append("Gecmis muayeneler (en yeniden eskiye):\n");
            sb.append(input.history().stream()
                .map(h -> "- " + h.date() + " | Degerlendirme: " + h.assessment() + " | Plan: " + h.plan())
                .collect(Collectors.joining("\n")));
        }
        return sb.toString();
    }

    private TreatmentRecommendationDraft fallback() {
        return new TreatmentRecommendationDraft(STATUS_NOTE, false);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=OllamaTreatmentRecommendationAdapterTest`
Expected: PASS (3 test)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/domain/TreatmentRecommendationPort.java \
        backend/src/main/java/com/vetos/modules/ai/domain/TreatmentRecommendationInput.java \
        backend/src/main/java/com/vetos/modules/ai/domain/HistoryEntry.java \
        backend/src/main/java/com/vetos/modules/ai/domain/TreatmentRecommendationDraft.java \
        backend/src/main/java/com/vetos/modules/ai/infrastructure/adapter/OllamaTreatmentRecommendationAdapter.java \
        backend/src/test/java/com/vetos/modules/ai/infrastructure/adapter/OllamaTreatmentRecommendationAdapterTest.java
git commit -m "feat(ai): TreatmentRecommendationPort ve Ollama adaptorunu ekle"
```

---

### Task 3: `GenerateTreatmentRecommendationUseCase`

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/exception/AssessmentRequiredForRecommendationException.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationCommand.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/TreatmentRecommendationResult.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/package-info.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationUseCaseTest.java`

**Interfaces:**
- Consumes: `EncounterLookupPort.findClinicalContext` (Task 1), `TreatmentRecommendationPort.generate` (Task 2), `RecordAiJobUseCase.execute` (önceki tur, `2026-09-07-ai-karar-denetim-izi.md`)
- Produces: `GenerateTreatmentRecommendationUseCase.execute(GenerateTreatmentRecommendationCommand) -> TreatmentRecommendationResult`

Bu görev, `ai` modülünün ilk kez `encounter.domain` paketini import ettiği yer — `package-info.java` güncellenmezse derleme geçse bile `ApplicationModulesTest` kırılır.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AssessmentRequiredForRecommendationException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import com.vetos.modules.encounter.domain.PastEncounterSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateTreatmentRecommendationUseCaseTest {

    @Mock private EncounterLookupPort encounterLookupPort;
    @Mock private TreatmentRecommendationPort treatmentRecommendationPort;
    @Mock private RecordAiJobUseCase recordAiJobUseCase;

    private GenerateTreatmentRecommendationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateTreatmentRecommendationUseCase(
            encounterLookupPort, treatmentRecommendationPort, recordAiJobUseCase, "llama3.1:8b"
        );
    }

    @Test
    void should_recordAiJobAndReturnResult_when_assessmentPresent() {
        UUID tenantId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        UUID aiJobId = UUID.randomUUID();
        EncounterClinicalContext context = new EncounterClinicalContext(
            encounterId, UUID.randomUUID(), "Hafif gastrit supheli",
            List.of(new PastEncounterSummary(Instant.now(), "Gecmis degerlendirme", "Gecmis plan"))
        );
        when(encounterLookupPort.findClinicalContext(encounterId, 5)).thenReturn(context);
        TreatmentRecommendationDraft draft = new TreatmentRecommendationDraft("Diyet degisikligi onerilir", true);
        when(treatmentRecommendationPort.generate(any())).thenReturn(draft);
        when(recordAiJobUseCase.execute(any())).thenReturn(aiJobId);

        TreatmentRecommendationResult result = useCase.execute(
            new GenerateTreatmentRecommendationCommand(tenantId, encounterId, staffUserId)
        );

        assertThat(result.aiJobId()).isEqualTo(aiJobId);
        assertThat(result.suggestionText()).isEqualTo("Diyet degisikligi onerilir");
        assertThat(result.modelConnected()).isTrue();

        ArgumentCaptor<RecordAiJobCommand> captor = ArgumentCaptor.forClass(RecordAiJobCommand.class);
        verify(recordAiJobUseCase).execute(captor.capture());
        RecordAiJobCommand recorded = captor.getValue();
        assertThat(recorded.tenantId()).isEqualTo(tenantId);
        assertThat(recorded.taskType()).isEqualTo(AiTaskType.TREATMENT_RECOMMENDATION);
        assertThat(recorded.encounterId()).isEqualTo(encounterId);
        assertThat(recorded.suggestionText()).isEqualTo("Diyet degisikligi onerilir");
        assertThat(recorded.modelName()).isEqualTo("ollama");
        assertThat(recorded.modelVersion()).isEqualTo("llama3.1:8b");
        assertThat(recorded.requestedByStaffUserId()).isEqualTo(staffUserId);
    }

    @Test
    void should_throwAssessmentRequired_when_assessmentIsBlank() {
        UUID encounterId = UUID.randomUUID();
        EncounterClinicalContext context = new EncounterClinicalContext(
            encounterId, UUID.randomUUID(), "   ", List.of()
        );
        when(encounterLookupPort.findClinicalContext(encounterId, 5)).thenReturn(context);

        assertThatThrownBy(() -> useCase.execute(
            new GenerateTreatmentRecommendationCommand(UUID.randomUUID(), encounterId, UUID.randomUUID())
        )).isInstanceOf(AssessmentRequiredForRecommendationException.class);

        verifyNoInteractions(treatmentRecommendationPort, recordAiJobUseCase);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GenerateTreatmentRecommendationUseCaseTest`
Expected: FAIL — compile error, sınıflar bulunamıyor.

- [ ] **Step 3: Write minimal implementation**

`AssessmentRequiredForRecommendationException.java`:

```java
package com.vetos.modules.ai.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AssessmentRequiredForRecommendationException extends DomainException {
    public AssessmentRequiredForRecommendationException(UUID encounterId) {
        super("ASSESSMENT_REQUIRED_FOR_RECOMMENDATION",
            "Tedavi onerisi icin once Assessment alani doldurulup kaydedilmeli: " + encounterId);
    }
}
```

`GenerateTreatmentRecommendationCommand.java`:

```java
package com.vetos.modules.ai.application;

import java.util.UUID;

public record GenerateTreatmentRecommendationCommand(UUID tenantId, UUID encounterId, UUID requestedByStaffUserId) {}
```

`TreatmentRecommendationResult.java`:

```java
package com.vetos.modules.ai.application;

import java.util.UUID;

public record TreatmentRecommendationResult(UUID aiJobId, String suggestionText, boolean modelConnected) {}
```

`GenerateTreatmentRecommendationUseCase.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AssessmentRequiredForRecommendationException;
import com.vetos.modules.encounter.domain.EncounterClinicalContext;
import com.vetos.modules.encounter.domain.EncounterLookupPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GenerateTreatmentRecommendationUseCase {

    private static final int HISTORY_LIMIT = 5;

    private final EncounterLookupPort encounterLookupPort;
    private final TreatmentRecommendationPort treatmentRecommendationPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String ollamaModelName;

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

        TreatmentRecommendationInput input = new TreatmentRecommendationInput(
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

`package-info.java` (mevcut dosyayı düzenle — `allowedDependencies`e `"modules.encounter::domain"` ekle):

```java
@org.springframework.modulith.ApplicationModule(
    displayName = "AI Servisleri",
    allowedDependencies = {
        "modules.encounter::domain", "platform::security", "platform::tenancy", "platform::exception"
    }
)
package com.vetos.modules.ai;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GenerateTreatmentRecommendationUseCaseTest`
Expected: PASS (2 test)

- [ ] **Step 5: Modül sınırı testini çalıştır**

Run: `cd backend && ./mvnw test -Dtest=ApplicationModulesTest`
Expected: PASS — `ai` modülünün `encounter.domain`'e bağımlılığı `package-info.java`'da açıkça tanımlı olduğu için `ApplicationModules.verify()` kırılmaz.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/domain/exception/AssessmentRequiredForRecommendationException.java \
        backend/src/main/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationCommand.java \
        backend/src/main/java/com/vetos/modules/ai/application/TreatmentRecommendationResult.java \
        backend/src/main/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationUseCase.java \
        backend/src/main/java/com/vetos/modules/ai/package-info.java \
        backend/src/test/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationUseCaseTest.java
git commit -m "feat(ai): GenerateTreatmentRecommendationUseCase ekle (encounter modulune yeni bagimlilik)"
```

---

### Task 4: `RecordAiJobDecisionUseCase` — tenant kontrolü eklenmesi (mevcut use-case'e değişiklik)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionCommand.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCase.java`
- Modify: `backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCaseTest.java`

**Interfaces:**
- Consumes: `AiJob.getTenantId()` (önceki tur)
- Değişen: `RecordAiJobDecisionCommand(UUID tenantId, UUID aiJobId, DecisionStatus status, String appliedContent, UUID decidedByStaffUserId)` — `tenantId` yeni ilk alan. Bu, Task 5'teki controller'ın çağıracağı imza.

Bu görev bir "önceki turdan mevcut" dosyayı değiştiriyor — mevcut 3 test de yeni imzayla derlenmesi için güncellenmeli, artı yeni bir cross-tenant senaryosu eklenecek.

- [ ] **Step 1: Write the failing test (mevcut test dosyasının tamamını bu içerikle değiştir)**

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.*;
import com.vetos.modules.ai.domain.exception.AiDecisionAlreadyRecordedConflictException;
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

    private AiJob anAiJob(UUID tenantId) {
        return AiJob.create(
            tenantId, AiTaskType.TREATMENT_RECOMMENDATION, UUID.randomUUID(),
            "Sivi tedavisi onerilir", "ollama", "llama3.1:8b", UUID.randomUUID()
        );
    }

    @Test
    void should_createAndSaveDecision_when_noDecisionExistsYet() {
        UUID tenantId = UUID.randomUUID();
        UUID aiJobId = UUID.randomUUID();
        UUID staffUserId = UUID.randomUUID();
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.of(anAiJob(tenantId)));
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.empty());
        when(aiJobDecisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(new RecordAiJobDecisionCommand(tenantId, aiJobId, DecisionStatus.ACCEPTED_AS_IS, null, staffUserId));

        verify(aiJobDecisionRepository).save(argThat(d ->
            d.getDecisionStatus() == DecisionStatus.ACCEPTED_AS_IS && d.getDecidedByStaffUserId().equals(staffUserId)
        ));
    }

    @Test
    void should_throwAiJobNotFound_when_aiJobDoesNotExist() {
        UUID aiJobId = UUID.randomUUID();
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(
            new RecordAiJobDecisionCommand(UUID.randomUUID(), aiJobId, DecisionStatus.REJECTED, null, UUID.randomUUID())
        )).isInstanceOf(AiJobNotFoundException.class);
    }

    @Test
    void should_throwAiJobNotFound_when_aiJobBelongsToDifferentTenant() {
        UUID aiJobId = UUID.randomUUID();
        UUID jobTenantId = UUID.randomUUID();
        UUID callerTenantId = UUID.randomUUID();
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.of(anAiJob(jobTenantId)));

        assertThatThrownBy(() -> useCase.execute(
            new RecordAiJobDecisionCommand(callerTenantId, aiJobId, DecisionStatus.REJECTED, null, UUID.randomUUID())
        )).isInstanceOf(AiJobNotFoundException.class);

        verifyNoInteractions(aiJobDecisionRepository);
    }

    @Test
    void should_throwAlreadyRecorded_when_decisionAlreadyMade() {
        UUID tenantId = UUID.randomUUID();
        UUID aiJobId = UUID.randomUUID();
        AiJobDecision existing = AiJobDecision.createPending(aiJobId);
        existing.decide(DecisionStatus.ACCEPTED_AS_IS, null, UUID.randomUUID());
        when(aiJobRepository.findById(aiJobId)).thenReturn(Optional.of(anAiJob(tenantId)));
        when(aiJobDecisionRepository.findByAiJobId(aiJobId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(
            new RecordAiJobDecisionCommand(tenantId, aiJobId, DecisionStatus.REJECTED, null, UUID.randomUUID())
        )).isInstanceOf(AiDecisionAlreadyRecordedConflictException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RecordAiJobDecisionUseCaseTest`
Expected: FAIL — compile error (`RecordAiJobDecisionCommand`'ın 5-argümanlı constructor'ı henüz yok, mevcut kod 4 argüman alıyor).

- [ ] **Step 3: Write minimal implementation**

`RecordAiJobDecisionCommand.java` (mevcut dosyayı düzenle):

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.DecisionStatus;
import java.util.UUID;

public record RecordAiJobDecisionCommand(
    UUID tenantId, UUID aiJobId, DecisionStatus status, String appliedContent, UUID decidedByStaffUserId
) {}
```

`RecordAiJobDecisionUseCase.java` (mevcut dosyayı düzenle):

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import com.vetos.modules.ai.domain.AiJobRepository;
import com.vetos.modules.ai.domain.exception.AiJobNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecordAiJobDecisionUseCase {

    private final AiJobRepository aiJobRepository;
    private final AiJobDecisionRepository aiJobDecisionRepository;

    @Transactional
    public void execute(RecordAiJobDecisionCommand command) {
        aiJobRepository.findById(command.aiJobId())
            .filter(job -> job.getTenantId().equals(command.tenantId()))
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
Expected: PASS (4 test — yeni cross-tenant senaryosu dahil)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionCommand.java \
        backend/src/main/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCase.java \
        backend/src/test/java/com/vetos/modules/ai/application/RecordAiJobDecisionUseCaseTest.java
git commit -m "fix(ai): RecordAiJobDecisionUseCase'e tenant kontrolu ekle (API'ye aciliyor)"
```

---

### Task 5: `AiController` — yeni endpoint'ler + uçtan uca doğrulama

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/api/dto/GenerateTreatmentRecommendationRequest.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/api/dto/TreatmentRecommendationResponse.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/api/dto/DecideTreatmentRecommendationRequest.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/api/AiController.java`

**Interfaces:**
- Consumes: `GenerateTreatmentRecommendationUseCase` (Task 3), `RecordAiJobDecisionUseCase` (Task 4, artık `tenantId` alıyor), `AuthenticatedStaffUser` (`com.vetos.platform.security`, mevcut — `.tenantId()`/`.staffUserId()`)

Bu görevde yeni bir birim test dosyası YOK — controller'lar bu kod tabanında birim test edilmiyor (mevcut konvansiyon), doğrulama tam test paketi + gerçek instance'a karşı curl ile yapılır.

- [ ] **Step 1: DTO'ları yaz**

`GenerateTreatmentRecommendationRequest.java`:

```java
package com.vetos.modules.ai.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GenerateTreatmentRecommendationRequest(@NotNull UUID encounterId) {}
```

`TreatmentRecommendationResponse.java`:

```java
package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.application.TreatmentRecommendationResult;
import java.util.UUID;

public record TreatmentRecommendationResponse(UUID aiJobId, String suggestionText, boolean modelConnected) {
    public static TreatmentRecommendationResponse from(TreatmentRecommendationResult r) {
        return new TreatmentRecommendationResponse(r.aiJobId(), r.suggestionText(), r.modelConnected());
    }
}
```

`DecideTreatmentRecommendationRequest.java`:

```java
package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.domain.DecisionStatus;
import jakarta.validation.constraints.NotNull;

public record DecideTreatmentRecommendationRequest(@NotNull DecisionStatus status, String appliedContent) {}
```

- [ ] **Step 2: `AiController`'ı düzenle (mevcut dosyanın tamamını bu içerikle değiştir)**

```java
package com.vetos.modules.ai.api;

import com.vetos.modules.ai.api.dto.*;
import com.vetos.modules.ai.application.*;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VET', 'ADMIN')")
public class AiController {

    private final GenerateSoapDraftUseCase generateSoapDraftUseCase;
    private final GenerateTreatmentRecommendationUseCase generateTreatmentRecommendationUseCase;
    private final RecordAiJobDecisionUseCase recordAiJobDecisionUseCase;

    @PostMapping("/soap-drafts")
    public SoapDraftResponse generateSoapDraft(@RequestBody @Valid GenerateSoapDraftRequest request) {
        return SoapDraftResponse.from(generateSoapDraftUseCase.execute(request.transcript()));
    }

    @PostMapping("/treatment-recommendations")
    public TreatmentRecommendationResponse generateTreatmentRecommendation(
        @RequestBody @Valid GenerateTreatmentRecommendationRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        TreatmentRecommendationResult result = generateTreatmentRecommendationUseCase.execute(
            new GenerateTreatmentRecommendationCommand(principal.tenantId(), request.encounterId(), principal.staffUserId())
        );
        return TreatmentRecommendationResponse.from(result);
    }

    @PostMapping("/treatment-recommendations/{aiJobId}/decision")
    public void decideTreatmentRecommendation(
        @PathVariable UUID aiJobId,
        @RequestBody @Valid DecideTreatmentRecommendationRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        recordAiJobDecisionUseCase.execute(new RecordAiJobDecisionCommand(
            principal.tenantId(), aiJobId, request.status(), request.appliedContent(), principal.staffUserId()
        ));
    }
}
```

- [ ] **Step 3: Tüm test paketini çalıştır**

Run: `cd backend && ./mvnw test`
Expected: PASS — Task 1-4'teki tüm testler + mevcut tüm testler yeşil (121 + Task 1 (3) + Task 2 (3) + Task 3 (2) + Task 4'ün +1 yeni testi = 130 civarı; kesin sayı çalıştırınca görülür).

- [ ] **Step 4: Canlı instance'a karşı uçtan uca doğrula (CLAUDE.md konvansiyonu)**

```bash
cd backend && docker-compose up -d
cd backend && ./mvnw spring-boot:run   # arka planda, log dosyasina yonlendirerek
```

Sonra ayrı bir terminalde (platform-admin → klinik oluştur → VET girişi → hasta/muayene oluştur akışı, önceki turlarda kullanılan curl deseniyle aynı):

1. Platform-admin login → yeni klinik oluştur (`city`/`address` dahil) → klinik admin login → VET oluştur+login.
2. Hasta/sahip/tür oluştur, bir `Encounter` başlat (`POST /api/v1/encounters`).
3. `PUT /api/v1/encounters/{id}/soap` ile Assessment alanını doldur (örn. `"Hafif gastrit supheli"`).
4. `POST /api/v1/ai/treatment-recommendations` — body `{"encounterId": "..."}`. **Beklenen:** 200, `{aiJobId, suggestionText, modelConnected}`.
5. `POST /api/v1/ai/treatment-recommendations/{aiJobId}/decision` — body `{"status": "ACCEPTED_AS_IS"}`. **Beklenen:** 200.
6. Aynı isteği (adım 5) TEKRAR gönder. **Beklenen:** 409 (`AiDecisionAlreadyRecordedConflictException` → Conflict) — "tam bir kez" kuralının gerçek HTTP üzerinden de çalıştığını doğrular.
7. Assessment'ı boş bırakılmış YENİ bir encounter için adım 4'ü dene. **Beklenen:** 422.
8. (İsteğe bağlı, veritabanını göz ile doğrulamak için) `docker exec -it backend-postgres-1 psql -U myvet -d myvet -c "SELECT task_type, encounter_id, model_connected FROM ai_jobs ORDER BY created_at DESC LIMIT 3;"` benzeri bir sorguyla (gerçek kolon adları migration'a göre) `ai_jobs`/`ai_job_decisions` tablolarında beklenen kayıtların oluştuğunu gözle kontrol et.

Expected: Yukarıdaki tüm adımlar açıklanan sonuçları verir.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/ai/api/dto/GenerateTreatmentRecommendationRequest.java \
        backend/src/main/java/com/vetos/modules/ai/api/dto/TreatmentRecommendationResponse.java \
        backend/src/main/java/com/vetos/modules/ai/api/dto/DecideTreatmentRecommendationRequest.java \
        backend/src/main/java/com/vetos/modules/ai/api/AiController.java
git commit -m "feat(ai): AiController'a tedavi onerisi endpoint'lerini ekle"
```

---

### Task 6: Frontend — `aiApi.ts` + `EncounterPage.tsx` "AI Tedavi Önerisi" kartı

**Files:**
- Modify: `frontend/src/api/aiApi.ts`
- Modify: `frontend/src/pages/encounter/EncounterPage.tsx`

**Interfaces:**
- Consumes: `POST /api/v1/ai/treatment-recommendations`, `POST /api/v1/ai/treatment-recommendations/{aiJobId}/decision` (Task 5)

Bu görevde backend testi yok — doğrulama `npm run build` (tip kontrolü) ile yapılır; mümkünse tarayıcıda gerçek kullanım.

- [ ] **Step 1: `aiApi.ts`'i düzenle (mevcut dosyanın tamamını bu içerikle değiştir)**

```typescript
import { apiClient } from './client';

export interface SoapDraft {
  subjective: string;
  objective: string;
  assessment: string;
  plan: string;
  modelConnected: boolean;
}

export interface TreatmentRecommendation {
  aiJobId: string;
  suggestionText: string;
  modelConnected: boolean;
}

export type TreatmentRecommendationDecisionStatus = 'ACCEPTED_AS_IS' | 'REJECTED';

export const aiApi = {
  generateSoapDraft: (transcript: string) => apiClient.post<SoapDraft>('/api/v1/ai/soap-drafts', { transcript }),
  generateTreatmentRecommendation: (encounterId: string) =>
    apiClient.post<TreatmentRecommendation>('/api/v1/ai/treatment-recommendations', { encounterId }),
  decideTreatmentRecommendation: (aiJobId: string, status: TreatmentRecommendationDecisionStatus, appliedContent?: string) =>
    apiClient.post<void>(`/api/v1/ai/treatment-recommendations/${aiJobId}/decision`, { status, appliedContent }),
};
```

- [ ] **Step 2: `EncounterPage.tsx`'e yeni state ve fonksiyonları ekle**

`draftApplied` state'inden hemen sonra (satır 50 civarı) yeni state'ler ekle:

```typescript
  const [draftApplied, setDraftApplied] = useState(false);
  const [generatingRecommendation, setGeneratingRecommendation] = useState(false);
  const [recommendationError, setRecommendationError] = useState<string | null>(null);
  const [recommendation, setRecommendation] = useState<{ aiJobId: string; suggestionText: string; modelConnected: boolean } | null>(null);
  const [recommendationDecided, setRecommendationDecided] = useState(false);
  const recognitionRef = useRef<any>(null);
```

`applyDraft` fonksiyonundan hemen sonra (satır 138 civarı) yeni fonksiyonları ekle:

```typescript
  async function handleGenerateRecommendation() {
    if (generatingRecommendation || !soap.assessment.trim() || !encounterId) return;
    setGeneratingRecommendation(true);
    setRecommendationError(null);
    try {
      const result = await aiApi.generateTreatmentRecommendation(encounterId);
      setRecommendation(result);
      setRecommendationDecided(false);
    } catch (err) {
      setRecommendationError(errorMessageOf(err));
    } finally {
      setGeneratingRecommendation(false);
    }
  }

  async function acceptRecommendation() {
    if (!recommendation) return;
    setSoap((s) => ({ ...s, plan: recommendation.suggestionText }));
    await aiApi.decideTreatmentRecommendation(recommendation.aiJobId, 'ACCEPTED_AS_IS');
    setRecommendationDecided(true);
    setTimeout(() => setRecommendation(null), 3000);
  }

  async function rejectRecommendation() {
    if (!recommendation) return;
    await aiApi.decideTreatmentRecommendation(recommendation.aiJobId, 'REJECTED');
    setRecommendation(null);
  }
```

- [ ] **Step 3: Yeni kartı JSX'e ekle**

"SOAP Notu" kartını kapatan `</div>` ile `<MaterialsUsedCard .../>` arasına (satır 297-299 civarı) yeni bir kart ekle:

```typescript
          </div>

          <div className={styles.aiCard}>
            <div className={styles.aiTitle}>✦ AI Tedavi Önerisi</div>
            <p className={styles.aiCopy}>
              Assessment alanına ve hastanın son muayenelerine dayanarak bir tedavi planı önerisi üretir.
              Öneri hekim onayına sunulur, Plan alanına siz onaylamadan uygulanmaz.
            </p>

            {!soap.assessment.trim() && (
              <p className={styles.aiCopy}>Önce Assessment alanını doldurup kaydedin.</p>
            )}

            {!isReadOnly && (
              <div className={styles.aiActions}>
                <Button
                  variant="ai"
                  onClick={handleGenerateRecommendation}
                  disabled={generatingRecommendation || !soap.assessment.trim()}
                >
                  {generatingRecommendation ? 'Oluşturuluyor...' : 'Tedavi Önerisi Al'}
                </Button>
              </div>
            )}

            {generatingRecommendation && (
              <p className={styles.aiCopy}>AI önerisi hazırlanıyor, bu işlem ~30-60 saniye sürebilir…</p>
            )}

            {recommendationError && <div className={styles.aiError}>{recommendationError}</div>}

            {recommendation && (
              <div className={styles.draftBox}>
                <div className={styles.aiWarning}>
                  Bu öneri gerçek bir tıbbi referans kaynağına dayanmaz, yalnızca genel AI bilgisine dayanır.
                  Bağımsız olarak değerlendirin.
                </div>
                <div className={styles.draftLabel}>Öneri önizleme</div>
                <div className={styles.draftPreview}>{recommendation.suggestionText || '—'}</div>
                {!isReadOnly && !recommendationDecided && (
                  <div className={styles.aiActions}>
                    <Button variant="ai" onClick={acceptRecommendation}>
                      Aynen Kabul Et
                    </Button>
                    <Button variant="secondary" onClick={rejectRecommendation}>
                      Reddet
                    </Button>
                  </div>
                )}
                {recommendationDecided && (
                  <p className={styles.aiCopy}>
                    ✓ Plan alanına uygulandı — kontrol edip "SOAP Kaydet"e basmayı unutmayın.
                  </p>
                )}
              </div>
            )}
          </div>

          <MaterialsUsedCard encounterId={encounter.id} readOnly={isReadOnly} />
```

- [ ] **Step 4: Tip kontrolü**

Run: `cd frontend && npm run build`
Expected: Hatasız derlenir.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/aiApi.ts frontend/src/pages/encounter/EncounterPage.tsx
git commit -m "feat(ai): AI Tedavi Onerisi kartini EncounterPage'e ekle"
```

---

## Bu Plan Tamamlandığında

Hekim, bir muayenede Assessment'ı doldurup kaydettikten sonra "Tedavi Önerisi Al" ile hastanın geçmişine dayanan bir AI önerisi alabilir, "Aynen Kabul Et" ile Plan alanına uygulayıp denetim izine `ACCEPTED_AS_IS` olarak kaydedebilir, ya da "Reddet" ile `REJECTED` olarak kaydedebilir — her karar `AiJob`/`AiJobDecision` tablolarında, kim tarafından ve ne zaman verildiğiyle birlikte değiştirilemez şekilde tutulur. "Düzenleyerek kabul" ve isabet geri bildirimi UI'ı sonraki bir tura bırakıldı.
