package com.vetos.modules.encounter.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Recete yazildi (spec 2026-10-09 S5.6). stockItems: "klinigin stogundan ver" kalemleri; inventory bekleyen dusum acar,
 * integration bos degilse stoktan dusen recete aktarimini kuyruga alir.
 */
public record PrescriptionIssuedEvent(UUID prescriptionId, UUID patientId, UUID tarbilDiseaseId, List<StockItem> stockItems) {
    public record StockItem(UUID inventoryItemId, int quantity) {}
}
