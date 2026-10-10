package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.inventory.domain.StockReferenceType;

/** Aktarimin Vetly kaynagi (spec 2026-10-09 S5.6). PRESCRIPTION belgesi muayeneden ya da receteden gelebilir. */
public enum TarbilSourceKind {
    VACCINATION, ENCOUNTER, PRESCRIPTION, STOCK_RECEIPT;

    /** Bekleyen stok dusumunun referans turu; stok kabulde yok. */
    public StockReferenceType stockReference() {
        return switch (this) {
            case VACCINATION -> StockReferenceType.VACCINATION;
            case ENCOUNTER -> StockReferenceType.ENCOUNTER;
            case PRESCRIPTION -> StockReferenceType.PRESCRIPTION;
            case STOCK_RECEIPT -> null;
        };
    }
}
