package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Hekimin TARBIL'de ilk kez elle sectigi degerlerden ogrenilen esletirme (klinik bazli).
 * Hicbir deger onceden uydurulmaz (CLAUDE.md "Yapma" kurali).
 */
@Entity
@Table(name = "tarbil_value_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilValueMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TarbilMappingKind kind;

    @Column(name = "vetly_key", nullable = false)
    private String vetlyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tarbil_fields", nullable = false, columnDefinition = "jsonb")
    private String tarbilFields;

    @Column(name = "learned_by_staff_id", nullable = false)
    private UUID learnedByStaffId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TarbilValueMapping create(
        UUID tenantId, TarbilMappingKind kind, String vetlyKey, String tarbilFieldsJson, UUID staffId, Instant now
    ) {
        TarbilValueMapping m = new TarbilValueMapping();
        m.tenantId = tenantId;
        m.kind = kind;
        m.vetlyKey = vetlyKey;
        m.tarbilFields = tarbilFieldsJson;
        m.learnedByStaffId = staffId;
        m.updatedAt = now;
        return m;
    }

    public void update(String tarbilFieldsJson, UUID staffId, Instant now) {
        this.tarbilFields = tarbilFieldsJson;
        this.learnedByStaffId = staffId;
        this.updatedAt = now;
    }
}
