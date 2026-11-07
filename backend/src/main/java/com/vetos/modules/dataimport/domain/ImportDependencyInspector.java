package com.vetos.modules.dataimport.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Silinecek kayitlara (owners / patients) yabanci anahtarla bagli, partiye ait OLMAYAN satirlari bulur.
 * Cagiranin transaction'inda calisir.
 */
public interface ImportDependencyInspector {

    /**
     * @param table       yalnizca "owners" ya da "patients" (baska deger IllegalArgumentException)
     * @param ids         silinecek kayitlarin kimlikleri
     * @param excludedIds referans veren satirlardan sayilmayacak kimlikler (ayni partinin olusturdugu kayitlar)
     */
    List<Dependent> findForeignDependents(String table, Collection<UUID> ids, Collection<UUID> excludedIds);
}
