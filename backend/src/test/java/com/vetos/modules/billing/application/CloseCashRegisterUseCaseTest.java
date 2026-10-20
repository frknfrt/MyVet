package com.vetos.modules.billing.application;

import com.vetos.modules.billing.domain.CashRegisterSession;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import com.vetos.modules.billing.domain.exception.CashRegisterSessionNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloseCashRegisterUseCaseTest {

    @Mock private CashRegisterSessionRepository sessions;
    @Mock private AuditPort auditPort;

    private CloseCashRegisterUseCase useCase() {
        return new CloseCashRegisterUseCase(sessions, auditPort);
    }

    @Test
    void should_recordAuditEvent_onSuccess() {
        CashRegisterSession session = CashRegisterSession.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, null);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(session, "id", id);
        when(sessions.findById(id)).thenReturn(Optional.of(session));

        useCase().execute(id, UUID.randomUUID(), new BigDecimal("2300"), "gizli not");

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditPort).record(captor.capture());
        AuditRecord rec = captor.getValue();
        assertThat(rec.action()).isEqualTo(AuditAction.CASH_REGISTER_CLOSED);
        assertThat(rec.targetType()).isEqualTo("CASH_REGISTER");
        assertThat(rec.targetId()).isEqualTo(id);
        assertThat(rec.targetLabel()).isNull();
        assertThat(rec.summary()).isEqualTo("Kapanış: 2.300,00 ₺");
    }

    @Test
    void should_notRecord_whenSessionNotFound() {
        UUID id = UUID.randomUUID();
        when(sessions.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(id, UUID.randomUUID(), BigDecimal.ONE, null))
            .isInstanceOf(CashRegisterSessionNotFoundException.class);
        verifyNoInteractions(auditPort);
    }
}
