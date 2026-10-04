package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** TARBIL recete hastalik agaci (kuresel referans, kiraci yok). id = TARBIL'deki dugum degeri. */
@Entity
@Table(name = "tarbil_disease")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilDisease {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean selectable;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static TarbilDisease of(UUID id, UUID parentId, String name, boolean selectable, int sortOrder) {
        TarbilDisease disease = new TarbilDisease();
        disease.id = id;
        disease.parentId = parentId;
        disease.name = name;
        disease.selectable = selectable;
        disease.sortOrder = sortOrder;
        return disease;
    }
}
