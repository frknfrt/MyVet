package com.vetos.modules.encounter.domain;

/**
 * Reçete kaleminin gösterilen adı: katalog ilacı varsa onun adı, yoksa stoktan verilen ürünün adı (spec 2026-10-12).
 * Reçete ekranı, rapor ve AI hasta geçmişi aynı kuralı kullanır.
 */
public final class PrescriptionItemNames {

    private PrescriptionItemNames() {}

    public static String of(PrescriptionItem item, DrugCatalogRepository catalog) {
        if (item.getDrugId() == null) return item.getDrugName();
        return catalog.findById(item.getDrugId()).map(DrugCatalog::getName).orElse(null);
    }
}
