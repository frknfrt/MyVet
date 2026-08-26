package com.vetos.modules.tenant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptStaffInviteRequest(@NotBlank @Size(min = 8) String password) {}
