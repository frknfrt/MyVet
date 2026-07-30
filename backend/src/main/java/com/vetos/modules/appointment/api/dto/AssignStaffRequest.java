package com.vetos.modules.appointment.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignStaffRequest(@NotNull UUID staffId) {}
