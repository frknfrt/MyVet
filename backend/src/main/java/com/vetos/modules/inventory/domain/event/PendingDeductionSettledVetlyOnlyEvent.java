package com.vetos.modules.inventory.domain.event;

import com.vetos.modules.inventory.domain.StockReferenceType;

import java.util.UUID;

/** Hekim TARBIL'e gitmeden yalniz Vetly'den dustu; integration bekleyen aktarimi reddeder (spec 2026-10-09 S5.1). */
public record PendingDeductionSettledVetlyOnlyEvent(StockReferenceType referenceType, UUID referenceId, UUID staffId) {}
