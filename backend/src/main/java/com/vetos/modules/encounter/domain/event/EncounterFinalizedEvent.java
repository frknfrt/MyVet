package com.vetos.modules.encounter.domain.event;

import java.util.List;
import java.util.UUID;

/**
 * Encounter tamamlandiginda yayinlanir. billing modulu bunu dinleyerek
 * otomatik charge capture yapar, inventory modulu usedItems'i dinleyerek
 * kullanilan malzemeyi stoktan duser (@docs/architecture.md Bolum 4).
 */
public record EncounterFinalizedEvent(UUID encounterId, UUID patientId, UUID staffUserId, List<UsedItem> usedItems) {

    public record UsedItem(UUID inventoryItemId, int quantity) {}
}
