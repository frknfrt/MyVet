package com.vetos.modules.tenant.application.dto;

import java.util.UUID;

public record UpdateBranchDetailsCommand(
    UUID branchId,
    String address,
    String city,
    String timezone,
    String tarbilBranchCode
) {}
