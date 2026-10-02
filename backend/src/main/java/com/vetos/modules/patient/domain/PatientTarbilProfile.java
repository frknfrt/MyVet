package com.vetos.modules.patient.domain;

import java.time.LocalDate;
import java.util.UUID;

/** integration/tarbil icin hasta profili -- sahip kisisel verisi (TC/adres/telefon) BILINCLI olarak yok. */
public record PatientTarbilProfile(
    UUID id, String name, String microchipNumber, UUID speciesId, String speciesName,
    String breedName, Sex sex, LocalDate birthDate
) {}
