package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.DrugRoute;

import java.util.UUID;

public record PrescriptionItemInput(UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route) {}
