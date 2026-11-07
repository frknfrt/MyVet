package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.AllergyAcknowledgement;
import com.vetos.modules.encounter.application.dto.AllergyCheckResult;
import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.StockItemNameLookupPort;
import com.vetos.modules.encounter.domain.exception.AllergyConfirmationRequiredConflictException;
import com.vetos.modules.encounter.domain.exception.AllergyOverrideReasonRequiredConflictException;
import com.vetos.modules.patient.domain.PatientClinicalProfile;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrugAllergyGuardTest {

    private static final String ALERT = "Penisilin grubu antibiyotiklere karsi bilinen alerjisi var.";

    private final PatientLookupPort patients = mock(PatientLookupPort.class);
    private final DrugCatalogRepository catalog = mock(DrugCatalogRepository.class);
    private final StockItemNameLookupPort stockNames = mock(StockItemNameLookupPort.class);
    private final AuditPort audit = mock(AuditPort.class);
    private final DrugAllergyGuard guard = new DrugAllergyGuard(patients, catalog, stockNames, audit);

    private final UUID patient = UUID.randomUUID();

    private void alert(String text) {
        when(patients.findClinicalProfile(patient)).thenReturn(Optional.of(
            new PatientClinicalProfile("Kedi", null, null, false, null, null, null, text)));
    }

    @Test
    void check_resolvesCatalogAndStockNames() {
        alert(ALERT);
        UUID drug = UUID.randomUUID();
        UUID stock = UUID.randomUUID();
        when(catalog.findById(drug)).thenReturn(Optional.of(DrugCatalog.create("Synulox", "amoksisilin + klavulanik asit", false)));
        when(stockNames.findName(stock)).thenReturn(Optional.of("CLAVON"));

        AllergyCheckResult result = guard.check(patient, List.of(drug), List.of(stock));

        assertThat(result.criticalAlert()).isEqualTo(ALERT);
        assertThat(result.matches()).extracting(m -> m.drugLabel()).containsExactly("Synulox (amoksisilin + klavulanik asit)");
    }

    @Test
    void enforce_passesSilently_whenPatientHasNoAlert() {
        alert(null);
        assertThatCode(() -> guard.enforce(patient, List.of("Amoksisilin"), null)).doesNotThrowAnyException();
        verify(audit, never()).record(any());
    }

    @Test
    void enforce_requiresAcknowledgement_whenAlertExists_evenWithoutMatch() {
        alert(ALERT);
        assertThatThrownBy(() -> guard.enforce(patient, List.of("Ketamin"), null))
            .isInstanceOf(AllergyConfirmationRequiredConflictException.class);
        assertThatCode(() -> guard.enforce(patient, List.of("Ketamin"), new AllergyAcknowledgement(true, null)))
            .doesNotThrowAnyException();
        verify(audit, never()).record(any());
    }

    @Test
    void enforce_requiresReason_forMatchingDrug() {
        alert(ALERT);
        assertThatThrownBy(() -> guard.enforce(patient, List.of("Amoksisilin"), new AllergyAcknowledgement(true, "  ")))
            .isInstanceOf(AllergyOverrideReasonRequiredConflictException.class)
            .hasMessageContaining("Amoksisilin");
    }

    @Test
    void enforce_auditsOverride_withReason() {
        alert(ALERT);

        guard.enforce(patient, List.of("Amoksisilin", "Ketamin"), new AllergyAcknowledgement(true, "Alerji kaydı hatalı, sahibiyle doğrulandı"));

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(audit).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.ALLERGY_WARNING_OVERRIDDEN);
        assertThat(rec.targetId()).isEqualTo(patient);
        assertThat(rec.targetLabel()).isEqualTo("Amoksisilin");
        assertThat(rec.summary()).contains("Penisilin grubu").contains("Alerji kaydı hatalı");
    }
}
