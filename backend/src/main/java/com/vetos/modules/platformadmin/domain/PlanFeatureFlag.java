package com.vetos.modules.platformadmin.domain;

/**
 * Bir planin GERCEKTEN hangi modullere erisim verdigi -- Plan.features
 * (pazarlama sayfasindaki madde listesi, serbest metin) ile KARISTIRILMAMALI.
 * Su an icin sadece platform admin tarafinda goruntuleniyor/yapilandiriliyor;
 * ilgili modullerde (ai, imaging, lab, boarding, inventory) fiili engelleme
 * henuz uygulanmadi -- bu, modul sinirlari arasinda ayri bir tasarim karari
 * gerektirir (bkz. JWT claim'lerine tasima tartismasi).
 */
public enum PlanFeatureFlag {
    AI_ASSISTANT,
    IMAGING,
    LAB_INTEGRATION,
    BOARDING,
    INVENTORY,
    E_FATURA
}
