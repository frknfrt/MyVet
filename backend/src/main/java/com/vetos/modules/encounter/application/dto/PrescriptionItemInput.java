package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.DrugRoute;

import java.util.UUID;

/** inventoryItemId + stockQuantity: "klinigin stogundan ver" (birlikte dolu ya da birlikte bos). */
public record PrescriptionItemInput(UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route,
                                    UUID inventoryItemId, Integer stockQuantity) {}
