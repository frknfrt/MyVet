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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestoreSubmissionUseCaseTest {

    @Mock private TarbilSubmissionRepository repository;
    @Mock private AuditPort auditPort;
    private final UUID tenantId = UUID.randomUUID();

    @Test
    void should_recordAuditEvent_onSuccess() {
        TarbilSubmission s = TarbilSubmission.queuePrescription(tenantId, UUID.randomUUID(), TarbilSourceKind.ENCOUNTER, UUID.randomUUID(), UUID.randomUUID());
        s.dismiss(UUID.randomUUID(), "x", Instant.now());
        when(repository.findById(s.getId())).thenReturn(Optional.of(s));

        new RestoreSubmissionUseCase(repository, auditPort).execute(tenantId, s.getId());

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.tenantId()).isEqualTo(tenantId);
        assertThat(rec.action()).isEqualTo(AuditAction.TARBIL_SUBMISSION_RESTORED);
        assertThat(rec.targetType()).isEqualTo("TARBIL_SUBMISSION");
        assertThat(rec.targetId()).isEqualTo(s.getId());
        assertThat(rec.targetLabel()).isEqualTo(s.getId().toString().substring(0, 8));
        assertThat(rec.summary()).isNull();
    }

    @Test
    void should_notRecord_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new RestoreSubmissionUseCase(repository, auditPort).execute(tenantId, id))
            .isInstanceOf(TarbilSubmissionNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
