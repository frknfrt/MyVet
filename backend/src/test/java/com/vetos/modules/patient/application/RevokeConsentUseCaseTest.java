package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.ConsentRecord;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import com.vetos.modules.patient.domain.ConsentType;
import com.vetos.modules.patient.domain.exception.ConsentRecordNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeConsentUseCaseTest {

    @Mock private ConsentRecordRepository consents;
    @Mock private AuditPort auditPort;

    private RevokeConsentUseCase useCase() {
        return new RevokeConsentUseCase(consents, auditPort);
    }

    private AuditRecord revoke(ConsentType type) {
        ConsentRecord record = ConsentRecord.grant(UUID.randomUUID(), UUID.randomUUID(), type, "1.2.3.4");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(record, "id", id);
        when(consents.findById(id)).thenReturn(Optional.of(record));

        useCase().execute(id);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.CONSENT_REVOKED);
        assertThat(rec.targetType()).isEqualTo("CONSENT");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.summary()).isNull();
        return rec;
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        assertThat(revoke(ConsentType.KVKK_ACIK_RIZA).targetLabel()).isEqualTo("KVKK açık rıza");
    }

    @Test
    void should_mapPazarlamaLabel() {
        assertThat(revoke(ConsentType.PAZARLAMA).targetLabel()).isEqualTo("Pazarlama");
    }

    @Test
    void should_notRecord_whenConsentNotFound() {
        UUID id = UUID.randomUUID();
        when(consents.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(id)).isInstanceOf(ConsentRecordNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
