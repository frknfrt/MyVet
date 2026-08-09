package com.vetos.modules.notification.application.dto;

public record CampaignSendSummary(int queued, int skippedNoPhone, int skippedNoConsent) {}
