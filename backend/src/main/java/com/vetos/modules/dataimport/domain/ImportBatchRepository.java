package com.vetos.modules.dataimport.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository {

    ImportBatch save(ImportBatch batch);

    Optional<ImportBatch> findById(UUID id);

    /** Satiri kilitler (SELECT ... FOR UPDATE): iki eszamanli "aktar" istegi ayni partiyi iki kez baslatamaz. */
    Optional<ImportBatch> findByIdForUpdate(UUID id);

    List<ImportBatch> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Kiracinin partilerinde kullanilmis farkli kaynak sistem yazimlari, ilk kullanima gore sirali (dosya yuklenmez). */
    List<String> findSourceSystems(UUID tenantId);

    List<ImportBatch> findByStatus(ImportBatchStatus status);

    /** Yalnizca kimlikler: cagiran her partiyi kendi transaction'inda kilitli yeniden okur. */
    List<UUID> findIdsByStatusAndCreatedAtBefore(ImportBatchStatus status, Instant before);
}
