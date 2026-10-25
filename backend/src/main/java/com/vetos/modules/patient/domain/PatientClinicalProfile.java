package com.vetos.modules.patient.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI önerisi için hasta kartı (spec 2026-10-25-ai-hasta-gecmisi).
 * Hasta adı, sahip verisi, mikroçip ve pasaport BİLİNÇLİ olarak yok: AI'ya gitmemeli.
 */
public record PatientClinicalProfile(
    String speciesName, String breedName, Sex sex, boolean neutered, LocalDate birthDate,
    BigDecimal weightKg, String bloodType, String criticalAlert
) {}
