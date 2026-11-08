package com.vetos;

import com.vetos.modules.patient.application.ApplyFormWriteBacksUseCase;
import com.vetos.modules.patient.application.dto.FormWriteBackCommand;
import com.vetos.modules.patient.application.dto.FormWriteBackResult;
import com.vetos.modules.patient.domain.*;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.minimum-idle=0"})
class FormWriteBackWeightPersistenceTest extends TenantScopedTestSupport {
    @Autowired TenantRepository tenants;
    @Autowired OwnerRepository owners;
    @Autowired PatientRepository patients;
    @Autowired SpeciesRepository species;
    @Autowired ApplyFormWriteBacksUseCase useCase;
    @Autowired EntityManager entityManager;
    @MockitoBean SignedFormWriteBackLookupPort signedFormLookup;
    private UUID tenantId, ownerId, patientId, speciesId;

    @BeforeEach
    void createPatient() {
        tenantId = inRootSession(() -> tenants.save(Tenant.register("DF04 " + UUID.randomUUID(), null)).getId());
        speciesId = inRootSession(() -> species.save(Species.create("DF04 " + UUID.randomUUID())).getId());
        ownerId = asTenant(tenantId, () -> owners.save(Owner.register(tenantId, "Test Sahip", "5550000000", null, null)).getId());
        patientId = asTenant(tenantId, () -> {
            var patient = Patient.register(tenantId, ownerId, speciesId, null, "Test Hasta", Sex.FEMALE, null);
            patient.recordWeight(new BigDecimal("12.50"));
            return patients.save(patient).getId();
        });
    }

    @AfterEach
    void cleanup() {
        if (tenantId == null) return;
        inRootSessionVoid(() -> {
            entityManager.createNativeQuery("DELETE FROM patients WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM owners WHERE tenant_id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM tenants WHERE id = :t").setParameter("t", tenantId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM species WHERE id = :s").setParameter("s", speciesId).executeUpdate();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {
        "10000;false;12.50", "9999.995;false;12.50", "9999.99;true;9999.99",
        "4.555;true;4.56", "0.001;false;12.50", "0.005;true;0.01"
    }, delimiter = ';')
    void committedPhoneSurvivesInvalidWeightAndValidWeightsFit(String weight, boolean valid, String stored) {
        UUID formId = UUID.randomUUID();
        when(signedFormLookup.allowedValues(formId, patientId, ownerId)).thenReturn(Map.of(
            "owner.phone", Set.of("5551112233"), "patient.weightKg", Set.of(weight)));
        var result = asTenant(tenantId, () -> useCase.execute(new FormWriteBackCommand(
            ownerId, patientId, formId, List.of(
                new FormWriteBackCommand.Item("owner.phone", "5551112233"),
                new FormWriteBackCommand.Item("patient.weightKg", weight)))));
        assertThat(result.applied()).contains("owner.phone");
        if (valid) {
            assertThat(result.applied()).contains("patient.weightKg");
            assertThat(result.skipped()).isEmpty();
        } else {
            assertThat(result.applied()).doesNotContain("patient.weightKg");
            assertThat(result.skipped()).extracting(FormWriteBackResult.Skipped::binding).containsExactly("patient.weightKg");
        }
        asTenantVoid(tenantId, () -> {
            assertThat(owners.findById(ownerId).orElseThrow().getPhone()).isEqualTo("5551112233");
            assertThat(patients.findById(patientId).orElseThrow().getWeightKg()).isEqualByComparingTo(stored);
        });
    }
}
