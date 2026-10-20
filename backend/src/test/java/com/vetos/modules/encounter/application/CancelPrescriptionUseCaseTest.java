package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.PrescriptionStatus;
import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.exception.PrescriptionNotCancellableConflictException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelPrescriptionUseCaseTest {

    @Mock private PrescriptionRepository prescriptions;
    @Mock private DomainEventPublisher publisher;
    @Mock private AuditPort auditPort;

    private Prescription active() {
        Prescription p = Prescription.issue(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), false, null);
        when(prescriptions.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void cancelsAnActivePrescriptionAndAnnouncesIt() {
        Prescription p = active();

        new CancelPrescriptionUseCase(prescriptions, publisher, auditPort).execute(p.getId());

        assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
        verify(prescriptions).save(p);
        verify(publisher).publish(new PrescriptionCancelledEvent(p.getId()));
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        Prescription p = active();

        new CancelPrescriptionUseCase(prescriptions, publisher, auditPort).execute(p.getId());

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.PRESCRIPTION_CANCELLED);
        assertThat(rec.targetType()).isEqualTo("PRESCRIPTION");
        assertThat(rec.targetId()).isEqualTo(p.getId());
        assertThat(rec.targetLabel()).isEqualTo(p.getId().toString().substring(0, 8));
        assertThat(rec.summary()).isNull();
    }

    @Test
    void cancelledPrescriptionCannotBeCancelledAgain() {
        Prescription p = active();
        p.cancel();

        assertThatThrownBy(() -> new CancelPrescriptionUseCase(prescriptions, publisher, auditPort).execute(p.getId()))
            .isInstanceOf(PrescriptionNotCancellableConflictException.class);
        verifyNoInteractions(publisher);
        verifyNoInteractions(auditPort);
    }
}
