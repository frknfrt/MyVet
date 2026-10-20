package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterInventoryUsageRepository;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalizeEncounterUseCaseTest {

    @Mock private EncounterRepository encounters;
    @Mock private EncounterInventoryUsageRepository usages;
    @Mock private DomainEventPublisher publisher;
    @Mock private AuditPort auditPort;

    private FinalizeEncounterUseCase useCase() {
        return new FinalizeEncounterUseCase(encounters, usages, publisher, auditPort);
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        Encounter encounter = Encounter.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(encounter, "id", id);
        when(encounters.findById(id)).thenReturn(Optional.of(encounter));
        when(usages.findByEncounterId(id)).thenReturn(List.of());

        useCase().execute(id);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.ENCOUNTER_FINALIZED);
        assertThat(rec.targetType()).isEqualTo("ENCOUNTER");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.targetLabel()).isEqualTo(id.toString().substring(0, 8));
        assertThat(rec.summary()).isNull();
    }

    @Test
    void should_notRecord_whenEncounterNotFound() {
        UUID id = UUID.randomUUID();
        when(encounters.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(id)).isInstanceOf(EncounterNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
