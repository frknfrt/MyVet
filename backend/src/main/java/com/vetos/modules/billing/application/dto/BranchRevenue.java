package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BranchRevenue(UUID branchId, String branchName, BigDecimal revenue) {}
