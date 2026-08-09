package com.vetos.modules.tenant.application.dto;

import java.util.UUID;

public record CreateBranchCommand(
    UUID tenantId,
    String name,
    String address,
    String city,
    String timezone,
    String tarbilBranchCode
) {}
