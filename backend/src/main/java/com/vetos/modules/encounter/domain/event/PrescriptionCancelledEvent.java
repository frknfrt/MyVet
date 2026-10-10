package com.vetos.modules.encounter.domain.event;

import java.util.UUID;

/** Recete iptal edildi: bekleyen dusum ve bekleyen TARBIL aktarimi kapanir (spec 2026-10-09 S5.6). */
public record PrescriptionCancelledEvent(UUID prescriptionId) {}
