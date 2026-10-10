package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.PrescriptionStatus;
import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.exception.PrescriptionNotCancellableConflictException;
import com.vetos.platform.event.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private Prescription active() {
        Prescription p = Prescription.issue(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), false, null);
        when(prescriptions.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void cancelsAnActivePrescriptionAndAnnouncesIt() {
        Prescription p = active();

        new CancelPrescriptionUseCase(prescriptions, publisher).execute(p.getId());

        assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.CANCELLED);
        verify(prescriptions).save(p);
        verify(publisher).publish(new PrescriptionCancelledEvent(p.getId()));
    }

    @Test
    void cancelledPrescriptionCannotBeCancelledAgain() {
        Prescription p = active();
        p.cancel();

        assertThatThrownBy(() -> new CancelPrescriptionUseCase(prescriptions, publisher).execute(p.getId()))
            .isInstanceOf(PrescriptionNotCancellableConflictException.class);
        verifyNoInteractions(publisher);
    }
}
