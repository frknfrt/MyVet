package com.vetos.modules.inventory.domain;

import java.util.UUID;

/** Bekleyen dusum satiri, kalem bilgisiyle (spec 2026-10-09 S4). */
public record PendingDeductionLine(UUID id, UUID inventoryItemId, String itemName, String tarbilProductName,
                                   String lotNumber, int quantity, PendingDeductionStatus status, Integer settledQuantity) {}
