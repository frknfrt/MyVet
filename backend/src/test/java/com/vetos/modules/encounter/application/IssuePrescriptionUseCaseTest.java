package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.IssuePrescriptionCommand;
import com.vetos.modules.encounter.application.dto.PrescriptionItemInput;
import com.vetos.modules.encounter.domain.DrugRoute;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.PrescriptionItemRepository;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.encounter.domain.exception.InvalidPrescriptionItemException;
import com.vetos.modules.encounter.domain.exception.PrescriptionEncounterMismatchException;
import com.vetos.modules.encounter.domain.exception.PrescriptionTarbilDiseaseRequiredException;
import com.vetos.platform.event.DomainEventPublisher;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        return new IssuePrescriptionUseCase(prescriptions, items, encounters, publisher);
    }

    private PrescriptionItemInput stockItem(UUID inventoryItemId, Integer qty) {
        return new PrescriptionItemInput(drug, "1 tb", "2x1", 5, DrugRoute.ORAL, inventoryItemId, qty);
    }

    private PrescriptionItemInput plainItem() {
        return new PrescriptionItemInput(drug, "1 tb", "1x1", 3, DrugRoute.ORAL, null, null);
    }

    @Test
    void issuesWithoutEncounterAndAnnouncesStockItems() {
        UUID inv = UUID.randomUUID();
        UUID disease = UUID.randomUUID();

        UUID id = useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, disease, List.of(stockItem(inv, 2), plainItem())));

        verify(publisher).publish(new PrescriptionIssuedEvent(id, patient, disease, List.of(new PrescriptionIssuedEvent.StockItem(inv, 2))));
    }

    @Test
    void stockItemRequiresTarbilDisease() {
        assertThatThrownBy(() -> useCase().execute(new IssuePrescriptionCommand(patient, null, staff, false, null,
            List.of(stockItem(UUID.randomUUID(), 1))))).isInstanceOf(PrescriptionTarbilDiseaseRequiredException.class);
        verifyNoInteractions(publisher);
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
}
