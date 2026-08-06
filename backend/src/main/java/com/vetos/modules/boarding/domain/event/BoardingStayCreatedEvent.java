package com.vetos.modules.boarding.domain.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Konaklama kaydi olusturuldugunda yayinlanir. billing modulu bunu dinleyerek
 * konaklama icin taslak (DRAFT) fatura ve baslangic kalemini otomatik olusturur
 * (@docs/architecture.md Bolum 4 EncounterFinalizedEvent orunutuyle ayni desen).
 */
public record BoardingStayCreatedEvent(
    UUID boardingStayId,
    UUID tenantId,
    UUID branchId,
    UUID ownerId,
    String roomLabel,
    BigDecimal dailyRate,
    LocalDate checkInDate,
    LocalDate expectedCheckOutDate
) {}
