package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AdminEmailPort;
import com.vetos.modules.platformadmin.domain.AdminMailbox;
import com.vetos.modules.platformadmin.domain.AdminSentEmail;
import com.vetos.modules.platformadmin.domain.AdminSentEmailRepository;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.modules.platformadmin.infrastructure.email.EmailTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendAdminEmailUseCaseTest {

    // Gercek render mantigi kullanilir (mock degil) -- boylece use case'in
    // AdminEmailPort.send'e GECERLI subject/html gecirdigi dogrulanir.
    private final EmailTemplateRenderer emailTemplateRenderer = new EmailTemplateRenderer();

    @Mock private AdminEmailPort adminEmailPort;
    @Mock private AdminSentEmailRepository adminSentEmailRepository;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    private SendAdminEmailUseCase useCase() {
        return new SendAdminEmailUseCase(emailTemplateRenderer, adminEmailPort, adminSentEmailRepository, recordAuditLogUseCase);
    }

    private Map<String, String> fieldsFor(EmailTemplateType type) {
        Map<String, String> fields = new HashMap<>();
        type.fields().forEach(f -> fields.put(f.key(), "deger"));
        return fields;
    }

    @Test
    void should_sendFromCorrectMailbox_andRecordSentEmailAndAuditLog() {
        UUID adminId = UUID.randomUUID();
        when(adminSentEmailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminSentEmail saved = useCase().execute(
            EmailTemplateType.INVOICE_REMINDER, "klinik@pati.com", fieldsFor(EmailTemplateType.INVOICE_REMINDER),
            adminId, "admin@vetly.com.tr"
        );

        verify(adminEmailPort).send(eq(AdminMailbox.BILDIRIM), eq("klinik@pati.com"), any(), any());
        assertThat(saved.getTemplateType()).isEqualTo("INVOICE_REMINDER");
        assertThat(saved.getRecipientEmail()).isEqualTo("klinik@pati.com");

        verify(recordAuditLogUseCase).execute(
            eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.ADMIN_EMAIL_SENT), eq("ADMIN_SENT_EMAIL"), any(), any()
        );
    }

    @Test
    void should_sendSalesTemplate_fromInfoMailbox() {
        when(adminSentEmailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase().execute(EmailTemplateType.SALES, "aday@klinik.com", fieldsFor(EmailTemplateType.SALES), UUID.randomUUID(), "admin@vetly.com.tr");

        verify(adminEmailPort).send(eq(AdminMailbox.INFO), eq("aday@klinik.com"), any(), any());
    }

    @Test
    void should_notSendOrRecord_when_requiredFieldMissing() {
        Map<String, String> incomplete = new HashMap<>();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            useCase().execute(EmailTemplateType.SALES, "aday@klinik.com", incomplete, UUID.randomUUID(), "admin@vetly.com.tr")
        ).isInstanceOf(com.vetos.modules.platformadmin.domain.exception.MissingTemplateFieldException.class);

        verifyNoInteractions(adminEmailPort, adminSentEmailRepository, recordAuditLogUseCase);
    }
}
