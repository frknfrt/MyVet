package com.vetos.modules.integration.tarbil.domain;

/**
 * VACCINE: normalize asi adi. SPECIES: Vetly speciesId (UUID metni). DISEASE: Vetly teshis anahtari -> TARBIL hastalik
 * dugumu. DRUG_ROUTE: Vetly DrugRoute adi -> TARBIL kullanim yolu. STOCK_PRODUCT: Vetly stok kalemi kimligi -> TARBIL
 * urun adi + takdim sekli. (spec 2026-10-04 S5.1)
 */
public enum TarbilMappingKind { VACCINE, SPECIES, DISEASE, DRUG_ROUTE, STOCK_PRODUCT }
