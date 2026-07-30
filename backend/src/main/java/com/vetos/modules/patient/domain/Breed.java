package com.vetos.modules.patient.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "breeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Breed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "species_id", nullable = false)
    private UUID speciesId;

    @Column(nullable = false)
    private String name;

    public static Breed create(UUID speciesId, String name) {
        Breed breed = new Breed();
        breed.speciesId = speciesId;
        breed.name = name;
        return breed;
    }
}
