package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.application.dto.CampaignSendSummary;

public record CampaignSendSummaryResponse(int queued, int skippedNoPhone, int skippedNoConsent) {
    public static CampaignSendSummaryResponse from(CampaignSendSummary s) {
        return new CampaignSendSummaryResponse(s.queued(), s.skippedNoPhone(), s.skippedNoConsent());
    }
}
