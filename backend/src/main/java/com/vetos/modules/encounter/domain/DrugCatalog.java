package com.vetos.modules.encounter.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Virgulle ayrilmis drug_catalog.id listesi -- bu ilacin etkilesime girdigi
     * diger katalog ilaclari (Ilac Katalogu Yonetimi ekraninda klinik/hekim
     * tarafindan girilir; gercek bir farmakolojik referans veritabani
     * baglanmadigindan sistem kendiliginden etkilesim VERISI uretmez/uydurmaz --
     * bkz. DrugInteractionEvaluator).
     */
    @Column(name = "interaction_flags", columnDefinition = "text")
    private String interactionFlags;

    public static DrugCatalog create(String name, String activeIngredient, boolean isControlled) {
        DrugCatalog drug = new DrugCatalog();
        drug.name = name;
        drug.activeIngredient = activeIngredient;
        drug.isControlled = isControlled;
        return drug;
    }

    public void update(String name, String activeIngredient, boolean isControlled) {
        this.name = name;
        this.activeIngredient = activeIngredient;
        this.isControlled = isControlled;
    }

    public void updateInteractingDrugIds(List<UUID> interactingDrugIds) {
        this.interactionFlags = interactingDrugIds.isEmpty()
            ? null
            : interactingDrugIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    public List<UUID> getInteractingDrugIds() {
        if (interactionFlags == null || interactionFlags.isBlank()) return List.of();
        return Arrays.stream(interactionFlags.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(UUID::fromString)
            .toList();
    }
}
