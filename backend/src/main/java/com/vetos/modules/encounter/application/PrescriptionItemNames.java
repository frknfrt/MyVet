package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.DrugCatalog;
import com.vetos.modules.encounter.domain.DrugCatalogRepository;
import com.vetos.modules.encounter.domain.PrescriptionItem;

/** Recete kaleminin gosterilen adi: katalog ilaci varsa onun adi, yoksa stoktan verilen urunun adi (spec 2026-10-12). */
final class PrescriptionItemNames {

    private PrescriptionItemNames() {}

    static String of(PrescriptionItem item, DrugCatalogRepository catalog) {
        if (item.getDrugId() == null) return item.getDrugName();
        return catalog.findById(item.getDrugId()).map(DrugCatalog::getName).orElse(null);
    }
}
