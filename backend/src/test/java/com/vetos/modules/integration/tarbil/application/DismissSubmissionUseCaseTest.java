package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSourceKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
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
class DismissSubmissionUseCaseTest {

    @Mock private TarbilSubmissionRepository repository;
    @Mock private AuditPort auditPort;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    private TarbilSubmission pending() {
        TarbilSubmission s = TarbilSubmission.queuePrescription(tenantId, UUID.randomUUID(), TarbilSourceKind.ENCOUNTER, UUID.randomUUID(), UUID.randomUUID());
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private AuditRecord recorded() {
        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        return captor.getValue();
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        TarbilSubmission s = pending();

        new DismissSubmissionUseCase(repository, auditPort).execute(tenantId, staffId, s.getId(), "Mukerrer kayit");

        AuditRecord rec = recorded();
        assertThat(rec.tenantId()).isEqualTo(tenantId);
        assertThat(rec.action()).isEqualTo(AuditAction.TARBIL_SUBMISSION_DISMISSED);
        assertThat(rec.targetType()).isEqualTo("TARBIL_SUBMISSION");
        assertThat(rec.targetId()).isEqualTo(s.getId());
        assertThat(rec.targetLabel()).isEqualTo(s.getId().toString().substring(0, 8));
        assertThat(rec.summary()).isEqualTo("Mukerrer kayit");
    }

    @Test
    void should_truncateReasonTo200_inSummary() {
        TarbilSubmission s = pending();

        new DismissSubmissionUseCase(repository, auditPort).execute(tenantId, staffId, s.getId(), "x".repeat(300));

        assertThat(recorded().summary()).hasSize(200);
    }

    @Test
    void should_recordNullSummary_when_reasonNull() {
        TarbilSubmission s = pending();

        new DismissSubmissionUseCase(repository, auditPort).execute(tenantId, staffId, s.getId(), null);

        assertThat(recorded().summary()).isNull();
    }

    @Test
    void should_notRecord_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new DismissSubmissionUseCase(repository, auditPort).execute(tenantId, staffId, id, "x"))
            .isInstanceOf(TarbilSubmissionNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
