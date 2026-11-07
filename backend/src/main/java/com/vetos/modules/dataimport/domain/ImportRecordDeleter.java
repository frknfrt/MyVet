package com.vetos.modules.dataimport.domain;

import java.util.UUID;

/**
 * Bir partinin olusturdugu klinik kayitlarini siler (native SQL; patient modulune silme metodu eklenmez).
 * Yalnizca created_by_import=true baglantilarin hedefleri silinir; eslesen (mevcut) sahiplere ve turlere dokunulmaz.
 */
public interface ImportRecordDeleter {

    int deleteCreatedPatients(UUID tenantId, UUID batchId);

    int deleteCreatedOwners(UUID tenantId, UUID batchId);

    int deleteLinks(UUID tenantId, UUID batchId);
}
