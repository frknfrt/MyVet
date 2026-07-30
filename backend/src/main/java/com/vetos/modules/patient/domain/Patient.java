package com.vetos.modules.patient.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "species_id", nullable = false)
    private UUID speciesId;

    @Column(name = "breed_id")
    private UUID breedId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private Sex sex;

    @Column(nullable = false)
    private boolean neutered;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "microchip_no")
    private String microchipNumber;

    @Column(name = "tarbil_animal_id")
    private String tarbilAnimalId;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "photo_url")
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatientStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Patient register(UUID ownerId, UUID speciesId, UUID breedId, String name, Sex sex, LocalDate birthDate) {
        Patient patient = new Patient();
        patient.ownerId = ownerId;
        patient.speciesId = speciesId;
        patient.breedId = breedId;
        patient.name = name;
        patient.sex = sex;
        patient.birthDate = birthDate;
        patient.neutered = false;
        patient.status = PatientStatus.ACTIVE;
        patient.createdAt = Instant.now();
        return patient;
    }

    public void updateProfile(String name, UUID breedId, Sex sex, LocalDate birthDate, boolean neutered) {
        this.name = name;
        this.breedId = breedId;
        this.sex = sex;
        this.birthDate = birthDate;
        this.neutered = neutered;
    }

    public void updateMicrochip(String microchipNumber) {
        this.microchipNumber = microchipNumber;
    }

    public void updateTarbilAnimalId(String tarbilAnimalId) {
        this.tarbilAnimalId = tarbilAnimalId;
    }

    public void recordWeight(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public void updatePhoto(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void markDeceased() {
        this.status = PatientStatus.DECEASED;
    }

    public void markTransferred() {
        this.status = PatientStatus.TRANSFERRED;
    }
}
