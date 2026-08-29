package com.vetos.modules.platformadmin.api.dto;

import java.util.List;

public record TenantBillingOverviewResponse(String paymentInstructions, List<PlatformInvoiceResponse> invoices) {}
