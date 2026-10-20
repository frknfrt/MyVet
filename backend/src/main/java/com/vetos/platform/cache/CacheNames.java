package com.vetos.platform.cache;

/**
 * Uygulamadaki onbellek adlarinin TEK listesi (spec 2026-10-18 §2). CacheConfig yalnizca bunlari
 * kurar; listede olmayan bir ad icin onbellek ACILMAZ (yanlis yazilmis ad sessizce sinirsiz
 * onbellek yaratmasin).
 */
public final class CacheNames {

    /** Kiracinin abonelikteki paket kodu. Anahtar: tenantId. */
    public static final String TENANT_PLAN_CODE = "tenantPlanCode";
    /** Paketin sade kopyasi (acik moduller + kotalar). Anahtar: paket kodu; paketler herkese ortak. */
    public static final String PLAN_BY_CODE = "planByCode";
    /** TARBIL hastalik listesi; tum kliniklerde ayni referans tablosu. */
    public static final String TARBIL_DISEASES = "tarbilDiseases";

    private CacheNames() {
    }
}
