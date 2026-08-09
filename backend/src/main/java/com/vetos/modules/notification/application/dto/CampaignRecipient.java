package com.vetos.modules.notification.application.dto;

import java.util.Map;
import java.util.UUID;

public record CampaignRecipient(
    UUID ownerId, String phone, boolean smsConsent, boolean whatsappConsent, Map<String, String> variables, String label
) {}
