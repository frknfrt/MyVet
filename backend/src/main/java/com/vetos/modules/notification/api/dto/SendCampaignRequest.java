package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.domain.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SendCampaignRequest(
    @NotNull NotificationChannel channel,
    @NotBlank String messageBody,
    @NotNull @Size(max = SendCampaignRequest.MAX_RECIPIENTS, message = "Tek seferde en fazla 1000 alıcıya gönderilebilir")
    List<CampaignRecipientRequest> recipients
) {
    /** Kodda sabit: anotasyon degeri yml'den okunamaz (spec 2026-10-15 1.5). */
    public static final int MAX_RECIPIENTS = 1000;
}
