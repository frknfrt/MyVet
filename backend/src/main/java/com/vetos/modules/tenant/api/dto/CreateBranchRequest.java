package com.vetos.modules.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBranchRequest(
    @NotBlank String name,
    String address,
    String city,
    String timezone,
    String tarbilBranchCode
) {}
