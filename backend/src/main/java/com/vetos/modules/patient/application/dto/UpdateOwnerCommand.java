package com.vetos.modules.patient.application.dto;

import java.util.UUID;

public record UpdateOwnerCommand(UUID ownerId, String phone, String email, String address) {}
