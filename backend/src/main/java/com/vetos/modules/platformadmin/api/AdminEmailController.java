package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.AdminSentEmailResponse;
import com.vetos.modules.platformadmin.api.dto.EmailTemplateOptionResponse;
import com.vetos.modules.platformadmin.api.dto.PreviewAdminEmailRequest;
import com.vetos.modules.platformadmin.api.dto.PreviewAdminEmailResponse;
import com.vetos.modules.platformadmin.api.dto.SendAdminEmailRequest;
import com.vetos.modules.platformadmin.application.ListSentAdminEmailsUseCase;
import com.vetos.modules.platformadmin.application.PreviewAdminEmailUseCase;
import com.vetos.modules.platformadmin.application.SendAdminEmailUseCase;
import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.platform.security.AuthenticatedPlatformAdmin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Platform admin "E-posta Gonder" paneli -- markali 4 sablonu (Satis, Deneme
 * Takip, Hos Geldin, Fatura Hatirlatma) cPanel'deki gercek kutulardan elle
 * tetikler. Bkz. SendAdminEmailUseCase / AdminEmailPort.
 */
@RestController
@RequestMapping("/api/v1/platform-admin/emails")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminEmailController {

    private final PreviewAdminEmailUseCase previewAdminEmailUseCase;
    private final SendAdminEmailUseCase sendAdminEmailUseCase;
    private final ListSentAdminEmailsUseCase listSentAdminEmailsUseCase;

    @GetMapping("/templates")
    public List<EmailTemplateOptionResponse> templates() {
        return List.of(EmailTemplateType.values()).stream().map(EmailTemplateOptionResponse::from).toList();
    }

    @PostMapping("/preview")
    public PreviewAdminEmailResponse preview(@RequestBody @Valid PreviewAdminEmailRequest request) {
        return PreviewAdminEmailResponse.from(previewAdminEmailUseCase.execute(request.templateType(), request.fieldsOrEmpty()));
    }

    @PostMapping("/send")
    public ResponseEntity<AdminSentEmailResponse> send(
        @RequestBody @Valid SendAdminEmailRequest request,
        @AuthenticationPrincipal AuthenticatedPlatformAdmin principal
    ) {
        var sent = sendAdminEmailUseCase.execute(
            request.templateType(), request.recipientEmail(), request.fieldsOrEmpty(), principal.platformAdminId(), principal.email()
        );
        return ResponseEntity.status(201).body(AdminSentEmailResponse.from(sent));
    }

    @GetMapping("/sent")
    public List<AdminSentEmailResponse> sent() {
        return listSentAdminEmailsUseCase.execute().stream().map(AdminSentEmailResponse::from).toList();
    }
}
