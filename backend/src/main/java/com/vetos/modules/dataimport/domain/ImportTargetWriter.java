package com.vetos.modules.dataimport.domain;

import com.vetos.modules.patient.domain.Sex;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aktarimin klinik verisini yazdigi port. Cagrildigi transaction'in kiracisinda calisir
 * (cagiran TenantContext'i transaction BASLAMADAN once kurar). Kayitlar repository save ile yazilir;
 * Register*UseCase cagrilmaz, boylece kayit olaylari ve yan etkileri tetiklenmez.
 */
public interface ImportTargetWriter {

    /** Tur tablosu kiracisizdir (tum kliniklerin ortak listesi); geri almada silinmez. */
    UUID createSpecies(String name);

    /** Irk tablosu da kiracisizdir (tum kliniklerin ortak listesi); geri almada silinmez. */
    UUID createBreed(UUID speciesId, String name);

    /** Riza alanlari false kalir: aktarim SMS/WhatsApp/bildirim rizasi uydurmaz. */
    UUID createOwner(UUID tenantId, NewOwner owner);

    UUID createPatient(UUID tenantId, NewPatient patient);

    /** TC kimlik numarasi sahip kaydinin kabul edecegi bicimde mi (resmi checksum). */
    boolean isValidNationalId(String nationalId);

    record NewOwner(String fullName, String phone, String secondaryPhone, String email, String nationalId,
                    String address, String city, String district, String notes) {
    }

    record NewPatient(UUID ownerId, UUID speciesId, UUID breedId, String name, Sex sex, LocalDate birthDate,
                      boolean neutered, String microchipNumber, String passportNumber, String color, String notes) {
    }
}
