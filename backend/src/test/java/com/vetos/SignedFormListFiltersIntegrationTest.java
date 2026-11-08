package com.vetos;

import com.vetos.modules.consentform.application.ListSignedFormsUseCase;
import com.vetos.modules.consentform.application.dto.SignedFormSummary;
import com.vetos.modules.consentform.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SignedFormListFiltersIntegrationTest extends TenantScopedTestSupport {
    @Autowired ListSignedFormsUseCase list;
    @Autowired SignedFormRepository forms;
    @Autowired FormTemplateRepository templates;
    @Autowired EntityManager entityManager;
    private final UUID tenantA = UUID.randomUUID(), tenantB = UUID.randomUUID(), staff = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        inRootSessionVoid(() -> {
            for (UUID tenant : List.of(tenantA, tenantB)) {
                entityManager.createNativeQuery("DELETE FROM signed_forms WHERE tenant_id = :t").setParameter("t", tenant).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM form_templates WHERE tenant_id = :t").setParameter("t", tenant).executeUpdate();
            }
        });
    }

    private UUID template(UUID tenant) {
        return asTenant(tenant, () -> templates.save(FormTemplate.create(tenant, "Form", "{}", null, staff)).getId());
    }

    private UUID signed(UUID tenant, UUID template, UUID patient, UUID owner, Instant signedAt, boolean voided) {
        return asTenant(tenant, () -> {
            var f = SignedForm.create(tenant, template, "Form", "{}", "{}", "fixture-ref", patient, owner,
                null, SignerType.OWNER, "Sahip", null, null, staff, signedAt, null, null, "a".repeat(64));
            if (voided) f.voidForm("Test", staff);
            return forms.save(f).getId();
        });
    }

    @Test
    void filtersIntersectWithoutChangingSingleFilterOrderingStatusOrTenantIsolation() {
        UUID templateA = template(tenantA), templateB = template(tenantB);
        UUID patient = UUID.randomUUID(), otherPatient = UUID.randomUUID();
        UUID owner = UUID.randomUUID(), previousOwner = UUID.randomUUID();
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        UUID old = signed(tenantA, templateA, patient, previousOwner, time, true);
        UUID matching = signed(tenantA, templateA, patient, owner, time.plusSeconds(60), false);
        UUID other = signed(tenantA, templateA, otherPatient, owner, time.plusSeconds(120), false);
        UUID foreign = signed(tenantB, templateB, patient, owner, time.plusSeconds(180), false);
        assertThat(asTenant(tenantA, () -> list.execute(patient, owner))).extracting(SignedFormSummary::id).containsExactly(matching);
        assertThat(asTenant(tenantA, () -> list.execute(patient, previousOwner))).extracting(SignedFormSummary::id).containsExactly(old);
        assertThat(asTenant(tenantA, () -> list.execute(patient, UUID.randomUUID()))).isEmpty();
        var patientOnly = asTenant(tenantA, () -> list.execute(patient, null));
        assertThat(patientOnly).extracting(SignedFormSummary::id).containsExactly(matching, old);
        assertThat(patientOnly.get(1).status()).isEqualTo(SignedFormStatus.VOIDED);
        assertThat(asTenant(tenantA, () -> list.execute(null, owner))).extracting(SignedFormSummary::id).containsExactly(other, matching);
        assertThat(asTenant(tenantB, () -> list.execute(patient, owner))).extracting(SignedFormSummary::id).containsExactly(foreign);
    }
}
