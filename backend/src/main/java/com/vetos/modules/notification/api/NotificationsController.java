package com.vetos.modules.notification.api;

import com.vetos.modules.notification.api.dto.CampaignRecipientRequest;
import com.vetos.modules.notification.api.dto.CampaignSendSummaryResponse;
import com.vetos.modules.notification.api.dto.NotificationLogResponse;
import com.vetos.modules.notification.api.dto.NotificationSettingsResponse;
import com.vetos.modules.notification.api.dto.NotificationStatusResponse;
import com.vetos.modules.notification.api.dto.NotificationTemplateResponse;
import com.vetos.modules.notification.api.dto.SendCampaignRequest;
import com.vetos.modules.notification.api.dto.UpdateNotificationSettingsRequest;
import com.vetos.modules.notification.api.dto.UpdateNotificationTemplateRequest;
import com.vetos.modules.notification.application.GetNotificationSettingsUseCase;
import com.vetos.modules.notification.application.GetNotificationStatusSummaryUseCase;
import com.vetos.modules.notification.application.GetNotificationTemplatesUseCase;
import com.vetos.modules.notification.application.ListNotificationLogsUseCase;
import com.vetos.modules.notification.application.RetryNotificationUseCase;
import com.vetos.modules.notification.application.SendCampaignUseCase;
import com.vetos.modules.notification.application.UpdateNotificationSettingsUseCase;
import com.vetos.modules.notification.application.UpdateNotificationTemplateUseCase;
import com.vetos.modules.notification.application.dto.CampaignRecipient;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * api-conventions.md rol matrisi: /notifications/** -> RECEPTIONIST + ADMIN
 * (Faz 2 role-bazli yetkilendirme turu, kullanici onayiyla -- bkz.
 * MessageTemplatesController).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
public class NotificationsController {

    private final GetNotificationStatusSummaryUseCase getNotificationStatusSummaryUseCase;
    private final ListNotificationLogsUseCase listNotificationLogsUseCase;
    private final RetryNotificationUseCase retryNotificationUseCase;
    private final SendCampaignUseCase sendCampaignUseCase;
    private final GetNotificationSettingsUseCase getNotificationSettingsUseCase;
    private final UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase;
    private final GetNotificationTemplatesUseCase getNotificationTemplatesUseCase;
    private final UpdateNotificationTemplateUseCase updateNotificationTemplateUseCase;

    @GetMapping("/status")
    public NotificationStatusResponse status() {
        return NotificationStatusResponse.from(getNotificationStatusSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/logs")
    public List<NotificationLogResponse> logs(
        @RequestParam(required = false) NotificationChannel channel,
        @RequestParam(required = false) NotificationStatus status,
        @RequestParam(required = false) NotificationType notificationType,
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String search
    ) {
        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        return listNotificationLogsUseCase.execute(TenantContext.current(), channel, status, notificationType, fromInstant, toInstant, search)
            .stream()
            .map(NotificationLogResponse::from)
            .toList();
    }

    @PostMapping("/logs/{id}/retry")
    public void retry(@PathVariable UUID id) {
        retryNotificationUseCase.execute(TenantContext.current(), id);
    }

    @PostMapping("/campaigns/send")
    public CampaignSendSummaryResponse sendCampaign(@RequestBody @Valid SendCampaignRequest request) {
        List<CampaignRecipient> recipients = request.recipients().stream()
            .map(NotificationsController::toRecipient)
            .toList();
        var summary = sendCampaignUseCase.execute(TenantContext.current(), request.channel(), request.messageBody(), recipients);
        return CampaignSendSummaryResponse.from(summary);
    }

    // Randevu onayi/hatirlatmasi mesajlarinin hangi kanaldan (SMS/WhatsApp)
    // gonderilecegi kiraci bazinda ayarlanabiliyor -- bkz. SettingsTab.tsx,
    // SendAppointmentRemindersUseCase, AppointmentScheduledEventListener.
    @GetMapping("/settings")
    public NotificationSettingsResponse settings() {
        return NotificationSettingsResponse.from(getNotificationSettingsUseCase.execute(TenantContext.current()));
    }

    @PutMapping("/settings")
    public NotificationSettingsResponse updateSettings(@RequestBody @Valid UpdateNotificationSettingsRequest request) {
        updateNotificationSettingsUseCase.execute(TenantContext.current(), request.appointmentChannel());
        return NotificationSettingsResponse.from(getNotificationSettingsUseCase.execute(TenantContext.current()));
    }

    // Otomatik bildirim mesaj metinleri (randevu onayi/hatirlatmasi, asi
    // hatirlatmasi) kiraci bazinda ozellestirilebiliyor -- bkz. SettingsTab.tsx,
    // NotificationTemplateDefaults. CAMPAIGN_MESSAGE burada YOK, bkz.
    // /campaigns/send ve MessageTemplatesController (elle secilen sablonlar).
    @GetMapping("/templates")
    public List<NotificationTemplateResponse> templates() {
        return getNotificationTemplatesUseCase.execute(TenantContext.current()).stream()
            .map(NotificationTemplateResponse::from)
            .toList();
    }

    @PutMapping("/templates/{notificationType}")
    public List<NotificationTemplateResponse> updateTemplate(
        @PathVariable NotificationType notificationType,
        @RequestBody UpdateNotificationTemplateRequest request
    ) {
        updateNotificationTemplateUseCase.execute(TenantContext.current(), notificationType, request.templateText());
        return templates();
    }

    private static CampaignRecipient toRecipient(CampaignRecipientRequest r) {
        return new CampaignRecipient(r.ownerId(), r.phone(), r.smsConsent(), r.whatsappConsent(), r.variables(), r.label());
    }
}
