package com.vetos.modules.inventory.domain;

import java.util.UUID;

/** Bekleyen dusum satiri, kalem bilgisiyle (spec 2026-10-09 S4). */
public record PendingDeductionLine(UUID id, UUID inventoryItemId, String itemName, String tarbilProductName,
                                   String lotNumber, java.math.BigDecimal quantity, PendingDeductionStatus status,
                                   java.math.BigDecimal settledQuantity,
                                   /** Kalemin kullanim birimi; plan icin acik miktar: kuyrukta bu satirdan once bekleyen ayni kalem dusumleri uygulanmis
                                    *  (spec 2026-10-24, 2026-10-25 §4.3/9). */
                                   String unit, java.math.BigDecimal plannedOpenedQuantity) {}
