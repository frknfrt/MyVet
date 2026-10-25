package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.DrugRoute;

import java.util.UUID;

/**
 * inventoryItemId + stockQuantity: "klinigin stogundan ver" (birlikte dolu ya da birlikte bos). Stoktan verilen kalemde ilac
 * stok urunudur: drugName dolu, drugId bos; digerlerinde drugId dolu, drugName bos (spec 2026-10-12).
 */
public record PrescriptionItemInput(UUID drugId, String drugName, String dosage, String frequency, int durationDays, DrugRoute route,
                                    UUID inventoryItemId, java.math.BigDecimal stockQuantity) {}
