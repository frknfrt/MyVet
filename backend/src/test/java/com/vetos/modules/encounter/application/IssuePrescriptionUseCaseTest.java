package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.IssuePrescriptionCommand;
import com.vetos.modules.encounter.application.dto.PrescriptionItemInput;
import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.PrescriptionItem;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.PrescriptionItemRepository;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.encounter.domain.exception.InvalidPrescriptionItemException;
import com.vetos.modules.encounter.domain.exception.PrescriptionEncounterMismatchException;
import com.vetos.modules.encounter.domain.exception.PrescriptionTarbilDiseaseRequiredException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.event.DomainEventPublisher;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Muayeneli/muayenesiz recete ve "klinigin stogundan ver" (spec 2026-10-09 S5.6). */
@ExtendWith(MockitoExtension.class)
class IssuePrescriptionUseCaseTest {

    @Mock private PrescriptionRepository prescriptions;
    @Mock private PrescriptionItemRepository items;
    @Mock private EncounterRepository encounters;
    @Mock private DomainEventPublisher publisher;
    @Mock private AuditPort auditPort;

    private final UUID patient = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final UUID drug = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(UUID.randomUUID());
        lenient().when(prescriptions.save(any())).thenAnswer(a -> a.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private IssuePrescriptionUseCase useCase() {
        return new IssuePrescriptionUseCase(prescriptions, items, encounters, publisher, auditPort);
    }

    private PrescriptionItemInput stockItem(UUID inventoryItemId, Integer qty) {
        return new PrescriptionItemInput(null, "Metacam", "1 tb", "2x1", 5, DrugRoute.ORAL, inventoryItemId, qty == null ? null : java.math.BigDecimal.valueOf(qty));
    }

    private PrescriptionItemInput plainItem() {
        return new PrescriptionItemInput(drug, null, "1 tb", "1x1", 3, DrugRoute.ORAL, null, null);
    }

    @Test
    void issuesWithoutEncounterAndAnnouncesStockItems() {
        UUID inv = UUID.randomUUID();
        UUID disease = UUID.randomUUID();

        UUID id = useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, disease, List.of(stockItem(inv, 2), plainItem())));

        verify(publisher).publish(new PrescriptionIssuedEvent(id, patient, disease, List.of(new PrescriptionIssuedEvent.StockItem(inv, java.math.BigDecimal.valueOf(2), "Metacam"))));
    }

    @Test
    void stockItemRequiresTarbilDisease() {
        assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, null,
            List.of(stockItem(UUID.randomUUID(), 1))))).isInstanceOf(PrescriptionTarbilDiseaseRequiredException.class);
        verifyNoInteractions(publisher);
        verifyNoInteractions(auditPort);
    }

    @Test
    void stockItemNeedsBothItemAndQuantity() {
        assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, UUID.randomUUID(),
            List.of(stockItem(UUID.randomUUID(), null))))).isInstanceOf(InvalidPrescriptionItemException.class);
        assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, UUID.randomUUID(),
            List.of(stockItem(UUID.randomUUID(), 0))))).isInstanceOf(InvalidPrescriptionItemException.class);
    }

    @Test
    void encounterMustBelongToThePatient() {
        UUID encounterId = UUID.randomUUID();
        Encounter other = mock(Encounter.class);
        when(other.getPatientId()).thenReturn(UUID.randomUUID());
        when(encounters.findById(encounterId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, encounterId, staff, false, null, List.of(plainItem()))))
            .isInstanceOf(PrescriptionEncounterMismatchException.class);
    }

    @Test
    void stockItemTakesItsNameInsteadOfACatalogDrug() {
        UUID inv = UUID.randomUUID();
        PrescriptionItemInput withCatalogDrug = new PrescriptionItemInput(drug, "Metacam", "1", "1", 1, DrugRoute.ORAL, inv, java.math.BigDecimal.valueOf(1));
        PrescriptionItemInput withoutName = new PrescriptionItemInput(null, "  ", "1", "1", 1, DrugRoute.ORAL, inv, java.math.BigDecimal.valueOf(1));

        for (PrescriptionItemInput item : List.of(withCatalogDrug, withoutName)) {
            assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, UUID.randomUUID(), List.of(item))))
                .isInstanceOf(InvalidPrescriptionItemException.class);
        }
        verifyNoInteractions(publisher);
    }

    @Test
    void plainItemNeedsACatalogDrugAndNoName() {
        PrescriptionItemInput noDrug = new PrescriptionItemInput(null, null, "1", "1", 1, DrugRoute.ORAL, null, null);
        PrescriptionItemInput named = new PrescriptionItemInput(drug, "Metacam", "1", "1", 1, DrugRoute.ORAL, null, null);

        for (PrescriptionItemInput item : List.of(noDrug, named)) {
            assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, null, List.of(item))))
                .isInstanceOf(InvalidPrescriptionItemException.class);
        }
    }

    @Test
    void storesTheTrimmedStockNameOnTheItem() {
        UUID inv = UUID.randomUUID();
        ArgumentCaptor<PrescriptionItem> saved = ArgumentCaptor.forClass(PrescriptionItem.class);

        UUID id = useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, UUID.randomUUID(),
            List.of(new PrescriptionItemInput(null, " Metacam ", "1", "1", 1, DrugRoute.ORAL, inv, java.math.BigDecimal.valueOf(1)))));

        verify(items).save(saved.capture());
        assertThat(saved.getValue().getDrugId()).isNull();
        assertThat(saved.getValue().getDrugName()).isEqualTo("Metacam");
        ArgumentCaptor<PrescriptionIssuedEvent> event = ArgumentCaptor.forClass(PrescriptionIssuedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().prescriptionId()).isEqualTo(id);
        assertThat(event.getValue().stockItems()).containsExactly(new PrescriptionIssuedEvent.StockItem(inv, java.math.BigDecimal.valueOf(1), "Metacam"));
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        UUID inv = UUID.randomUUID();
        PrescriptionItemInput second = new PrescriptionItemInput(drug, null, "1 tb", "1x1", 3, DrugRoute.ORAL, null, null);

        UUID id = useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, UUID.randomUUID(),
            List.of(stockItem(inv, 2), plainItem(), second)));

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.PRESCRIPTION_ISSUED);
        assertThat(rec.targetType()).isEqualTo("PRESCRIPTION");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.targetLabel()).isEqualTo(id.toString().substring(0, 8));
        assertThat(rec.summary()).isEqualTo("3 kalem");
        assertThat(rec.summary()).doesNotContain("Metacam");
    }
}
