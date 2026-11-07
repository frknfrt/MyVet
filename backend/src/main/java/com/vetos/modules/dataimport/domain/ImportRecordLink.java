package com.vetos.modules.dataimport.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "import_record_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportRecordLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    // Klinik kullanicisi eski bilgileri okurken baska kiracinin baglantilari filtrelensin.
    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source_system", nullable = false, updatable = false)
    private String sourceSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private ImportEntityType entityType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private String sourceId;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "created_by_import", nullable = false, updatable = false)
    private boolean createdByImport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legacy_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> legacyData = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ImportRecordLink of(UUID tenantId, UUID batchId, String sourceSystem, ImportEntityType type,
                                      String sourceId, UUID targetId, boolean createdByImport,
                                      Map<String, String> legacyData) {
        ImportRecordLink l = new ImportRecordLink();
        l.tenantId = tenantId;
        l.batchId = batchId;
        l.sourceSystem = sourceSystem;
        l.entityType = type;
        l.sourceId = sourceId;
        l.targetId = targetId;
        l.createdByImport = createdByImport;
        l.legacyData = legacyData == null ? new HashMap<>() : new HashMap<>(legacyData);
        l.createdAt = Instant.now();
        return l;
    }
}
