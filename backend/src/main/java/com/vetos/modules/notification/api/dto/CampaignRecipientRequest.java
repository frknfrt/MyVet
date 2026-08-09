package com.vetos.modules.notification.api.dto;

import java.util.Map;
import java.util.UUID;

public record CampaignRecipientRequest(
    UUID ownerId, String phone, boolean smsConsent, boolean whatsappConsent, Map<String, String> variables, String label
) {}
