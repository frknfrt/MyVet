package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AdminEmailPort;
import com.vetos.modules.platformadmin.domain.AdminSentEmail;
import com.vetos.modules.platformadmin.domain.AdminSentEmailRepository;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.modules.platformadmin.domain.RenderedEmail;
import com.vetos.modules.platformadmin.infrastructure.email.EmailTemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Admin panelinden "Gonder" butonuna basildiginda calisir: sablonu render
 * eder, dogru kutudan (EmailTemplateType.mailbox()) gercekten gonderir,
 * kalici kayit (AdminSentEmail) + denetim kaydi (RecordAuditLogUseCase)
 * birakir. AdminEmailPort.isConfigured() false ise gonderim sadece loglanir
 * ama bu akis (kayit + denetim) degismez -- admin ne gonderdigini her
 * halukarda gorur.
 */
@Service
@RequiredArgsConstructor
public class SendAdminEmailUseCase {

    private final EmailTemplateRenderer emailTemplateRenderer;
    private final AdminEmailPort adminEmailPort;
    private final AdminSentEmailRepository adminSentEmailRepository;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public AdminSentEmail execute(EmailTemplateType type, String recipientEmail, Map<String, String> fields, UUID adminId, String adminEmail) {
        RenderedEmail rendered = emailTemplateRenderer.render(type, fields);

        adminEmailPort.send(type.mailbox(), recipientEmail, rendered.subject(), rendered.html());

        AdminSentEmail entry = adminSentEmailRepository.save(
            AdminSentEmail.record(type, recipientEmail, rendered.subject(), adminId, adminEmail)
        );

        recordAuditLogUseCase.execute(
            adminId, adminEmail, AuditAction.ADMIN_EMAIL_SENT, "ADMIN_SENT_EMAIL", entry.getId(),
            type.label() + " -> " + recipientEmail
        );

        return entry;
    }
}
