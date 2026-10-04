package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.application.dto.RecordVaccinationCommand;
import com.vetos.modules.encounter.domain.VaccinationRecord;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaccinationStockEventsTest {

    @Mock private VaccinationRecordRepository repository;
    @Mock private DomainEventPublisher publisher;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    private RecordVaccinationCommand command(VaccinationStatus status) {
        return new RecordVaccinationCommand(tenantId, UUID.randomUUID(), null, "Biocan R", "665932",
            LocalDate.of(2026, 10, 4), null, UUID.randomUUID(), status, null, itemId);
    }

    @Test
    void should_storeItemAndAnnounceIt_when_administeredFromStock() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new RecordVaccinationUseCase(repository, publisher).execute(command(VaccinationStatus.ADMINISTERED));

        ArgumentCaptor<VaccinationRecord> record = ArgumentCaptor.forClass(VaccinationRecord.class);
        verify(repository).save(record.capture());
        assertThat(record.getValue().getInventoryItemId()).isEqualTo(itemId);
        ArgumentCaptor<VaccinationRecordedEvent> event = ArgumentCaptor.forClass(VaccinationRecordedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().inventoryItemId()).isEqualTo(itemId);
    }

    @Test
    void should_notAnnounce_when_onlyScheduled() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new RecordVaccinationUseCase(repository, publisher).execute(command(VaccinationStatus.SCHEDULED));

        verify(publisher, never()).publish(any());
    }

    @Test
    void should_announceItem_when_scheduledVaccinationMarkedAdministered() {
        UUID id = UUID.randomUUID();
        VaccinationRecord record = VaccinationRecord.record(tenantId, UUID.randomUUID(), null, "Biocan R", "665932",
            LocalDate.of(2026, 10, 4), null, UUID.randomUUID(), VaccinationStatus.SCHEDULED, null, itemId);
        when(repository.findById(id)).thenReturn(Optional.of(record));

        new MarkVaccinationAdministeredUseCase(repository, publisher).execute(id, null);

        ArgumentCaptor<VaccinationRecordedEvent> event = ArgumentCaptor.forClass(VaccinationRecordedEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().inventoryItemId()).isEqualTo(itemId);
    }

    @Test
    void should_announceCancellationWithItem() {
        UUID id = UUID.randomUUID();
        VaccinationRecord record = VaccinationRecord.record(tenantId, UUID.randomUUID(), null, "Biocan R", "665932",
            LocalDate.of(2026, 10, 4), null, UUID.randomUUID(), VaccinationStatus.ADMINISTERED, null, itemId);
        when(repository.findById(id)).thenReturn(Optional.of(record));

        new CancelVaccinationUseCase(repository, publisher).execute(id);

        assertThat(record.getStatus()).isEqualTo(VaccinationStatus.CANCELLED);
        ArgumentCaptor<VaccinationCancelledEvent> event = ArgumentCaptor.forClass(VaccinationCancelledEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().inventoryItemId()).isEqualTo(itemId);
    }
}
