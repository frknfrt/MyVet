package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.ChurnReasonBreakdown;

public record ChurnReasonBreakdownResponse(String reason, int count) {
    public static ChurnReasonBreakdownResponse from(ChurnReasonBreakdown b) {
        return new ChurnReasonBreakdownResponse(b.reason(), b.count());
    }
}
