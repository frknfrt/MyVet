package com.vetos.modules.tenant.domain;

import java.time.LocalDate;
import java.util.UUID;

/** Belirli bir gun sona eren deneme aboneligi -- platform admin hatirlatma zamanlayicisi icin. */
public record EndingTrial(UUID tenantId, LocalDate endsAt) {}
