package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.Size;

public record DismissRequest(@Size(max = 200) String reason) {}
