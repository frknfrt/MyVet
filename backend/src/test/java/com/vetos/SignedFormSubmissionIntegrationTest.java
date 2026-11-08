package com.vetos;

import com.vetos.modules.consentform.application.CreateSignedFormUseCase;
import com.vetos.modules.consentform.application.dto.*;
import com.vetos.modules.consentform.domain.*;
import com.vetos.modules.consentform.domain.exception.SignedFormRequestConflictException;
import com.vetos.modules.patient.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.minimum-idle=0"})
class SignedFormSubmissionIntegrationTest extends TenantScopedTestSupport {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 13, 10, 26, 10, 9, 8};
    @Autowired CreateSignedFormUseCase create;
    @Autowired FormTemplateRepository templates;
    @Autowired SignedFormRepository forms;
    @Autowired EntityManager entityManager;
    @MockitoBean PatientLookupPort patientLookup;
    @MockitoBean OwnerLookupPort ownerLookup;

    private final UUID tenant = UUID.randomUUID(), patient = UUID.randomUUID(), owner = UUID.randomUUID(), staff = UUID.randomUUID();
    private final List<UUID> createdTenants = new ArrayList<>();
    private FormTemplate template;
    private long initialFiles;

    @BeforeEach
    void setup() {
        when(patientLookup.findSummaryById(patient)).thenReturn(new PatientSummary(patient, "Test", owner, "Kedi"));
        when(ownerLookup.findSummaryById(owner)).thenReturn(new OwnerSummary(owner, "Test Sahip", null, null, null, null, null, false, false));
        template = createTemplate(tenant);
        initialFiles = filesCount();
    }

    private FormTemplate createTemplate(UUID tenantId) {
        createdTenants.add(tenantId);
        var content = new FormTemplateContent("Onam", List.of(new FormSection(null, List.of(
            new FormItem(FormItemType.SHORT_TEXT, "ad", "Ad", null, true, null, null, null, false),
            new FormItem(FormItemType.SHORT_TEXT, "note", "Not", null, false, null, null, null, false),
            new FormItem(FormItemType.SIGNATURE, "imza", "İmza", null, true, null, null, null, false)))));
        return asTenant(tenantId, () -> templates.save(FormTemplate.create(tenantId, "Onam",
            FormTemplateContentCodec.toJson(content), null, staff)));
    }

    private CreateSignedFormCommand command(FormTemplate source, UUID key, Map<String, Object> values) {
        return new CreateSignedFormCommand(source.getId(), patient, null, values,
            new SignerInput(SignerType.OWNER, null, null, null), Base64.getEncoder().encodeToString(PNG),
            "127.0.0.1", "JUnit", source.contentHash(), key);
    }

    @AfterEach
    void cleanup() {
        for (UUID tenantId : createdTenants) {
            inRootSessionVoid(() -> {
                entityManager.createNativeQuery("DELETE FROM signed_form_submissions WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
                entityManager.createNativeQuery("""
                    DELETE FROM stored_files WHERE id IN
                    (SELECT CAST(signature_storage_ref AS uuid) FROM signed_forms WHERE tenant_id = :t)
                    """).setParameter("t", tenantId).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM signed_forms WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM form_templates WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            });
        }
    }

    private long filesCount() {
        return inRootSession(() -> ((Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM stored_files").getSingleResult()).longValue());
    }

    private void assertSingleDocument() {
        assertThat(asTenant(tenant, () -> forms.findByPatientIdOrderBySignedAtDesc(patient))).hasSize(1);
        assertThat(filesCount()).isEqualTo(initialFiles + 1);
    }

    @Test
    void retryReturnsOriginalAfterTemplateChangesAndIgnoresMapKeyOrder() {
        UUID key = UUID.randomUUID();
        Map<String, Object> firstValues = new LinkedHashMap<>();
        firstValues.put("note", "Not");
        firstValues.put("ad", "Test");
        var original = command(template, key, firstValues);
        UUID firstId = asTenant(tenant, () -> create.execute(staff, original));
        asTenantVoid(tenant, () -> {
            var current = templates.findById(template.getId()).orElseThrow();
            current.update("Değişti", current.getContent().replace("Onam", "Değişti"), staff);
            current.archive(staff);
            templates.save(current);
        });
        var retry = command(template, key, Map.of("ad", "Test", "note", "Not"));
        assertThat(asTenant(tenant, () -> create.execute(staff, retry))).isEqualTo(firstId);
        assertSingleDocument();
    }

    @Test
    void changedContentCannotReuseCompletedKey() {
        UUID key = UUID.randomUUID();
        asTenant(tenant, () -> create.execute(staff, command(template, key, Map.of("ad", "Test"))));
        assertThatThrownBy(() -> asTenant(tenant, () -> create.execute(staff,
            command(template, key, Map.of("ad", "Başka"))))).isInstanceOf(SignedFormRequestConflictException.class);
        assertSingleDocument();
    }

    @Test
    void anotherStaffMemberCannotReuseCompletedKey() {
        var cmd = command(template, UUID.randomUUID(), Map.of("ad", "Test"));
        asTenant(tenant, () -> create.execute(staff, cmd));
        assertThatThrownBy(() -> asTenant(tenant, () -> create.execute(UUID.randomUUID(), cmd)))
            .isInstanceOf(SignedFormRequestConflictException.class);
        assertSingleDocument();
    }

    @Test
    void sameKeyIsIsolatedBetweenTenants() {
        UUID key = UUID.randomUUID(), otherTenant = UUID.randomUUID();
        var otherTemplate = createTemplate(otherTenant);
        UUID first = asTenant(tenant, () -> create.execute(staff, command(template, key, Map.of("ad", "Test"))));
        UUID second = asTenant(otherTenant, () -> create.execute(staff, command(otherTemplate, key, Map.of("ad", "Test"))));
        assertThat(second).isNotEqualTo(first);
        assertThat(asTenant(otherTenant, () -> forms.findById(first))).isEmpty();
        assertThat(filesCount()).isEqualTo(initialFiles + 2);
    }

    @Test
    void concurrentIdenticalRequestsCreateOneDocumentAndOneFile() throws Exception {
        race(false);
    }

    @Test
    void waitingRetryCanSucceedAfterFirstTransactionRollsBack() throws Exception {
        race(true);
    }

    private void race(boolean rollback) throws Exception {
        var cmd = command(template, UUID.randomUUID(), Map.of("ad", "Test"));
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
        AtomicReference<UUID> attempted = new AtomicReference<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<UUID> first = executor.submit(() -> asTenant(tenant, () -> {
                UUID id = create.execute(staff, cmd);
                attempted.set(id);
                held.countDown();
                await(release);
                if (rollback) throw new ForcedRollback();
                return id;
            }));
            try {
                if (!held.await(10, TimeUnit.SECONDS)) {
                    first.get(1, TimeUnit.SECONDS);
                    throw new AssertionError("First transaction did not reach reservation");
                }
                Future<UUID> second = executor.submit(() -> {
                    secondStarted.countDown();
                    return asTenant(tenant, () -> create.execute(staff, cmd));
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                if (rollback) {
                    assertThatThrownBy(() -> first.get(10, TimeUnit.SECONDS)).hasRootCauseInstanceOf(ForcedRollback.class);
                } else {
                    assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(attempted.get());
                }
                UUID result = second.get(10, TimeUnit.SECONDS);
                if (rollback) {
                    assertThat(result).isNotEqualTo(attempted.get());
                    assertThat(asTenant(tenant, () -> forms.findById(attempted.get()))).isEmpty();
                } else {
                    assertThat(result).isEqualTo(attempted.get());
                }
                assertSingleDocument();
            } finally {
                release.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Reservation wait expired");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static class ForcedRollback extends RuntimeException {}
}
