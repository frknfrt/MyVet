package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.domain.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SendCampaignRequest(
    @NotNull NotificationChannel channel,
    @NotBlank String messageBody,
    @NotNull List<CampaignRecipientRequest> recipients
) {}
