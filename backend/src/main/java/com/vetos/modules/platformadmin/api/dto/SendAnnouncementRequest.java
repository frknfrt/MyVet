package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SendAnnouncementRequest(
    @NotBlank String title,
    @NotBlank String body
) {}
