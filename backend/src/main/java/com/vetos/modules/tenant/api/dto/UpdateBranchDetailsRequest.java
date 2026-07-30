package com.vetos.modules.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateBranchDetailsRequest(
    @NotBlank String address,
    @NotBlank String city,
    @NotBlank String timezone,
    String tarbilBranchCode
) {}
