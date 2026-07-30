package com.vetos.modules.encounter.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "drug_catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DrugCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "active_ingredient")
    private String activeIngredient;

    @Column(name = "is_controlled", nullable = false)
    private boolean isControlled;

    /** JSON-encoded dizi (orn. etkilesen ilac adlari) -- AI uyari kaynagi, Faz 2. */
    @Column(name = "interaction_flags", columnDefinition = "text")
    private String interactionFlags;

    public static DrugCatalog create(String name, String activeIngredient, boolean isControlled) {
        DrugCatalog drug = new DrugCatalog();
        drug.name = name;
        drug.activeIngredient = activeIngredient;
        drug.isControlled = isControlled;
        return drug;
    }
}
