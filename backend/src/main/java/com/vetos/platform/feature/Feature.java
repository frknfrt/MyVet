package com.vetos.platform.feature;

/**
 * Bir paketin (Plan) acabildigi moduller -- TEK KAYNAK. Admin panelindeki "Modul Erisimi"
 * kutulari ve Turkce etiketleri buradan gelir (GET /api/v1/platform-admin/plans/feature-options).
 * Yeni modul: buraya bir deger + o modulun uclarina @RequiresFeature. Degerler
 * plan_feature_flags.feature sutununda STRING olarak saklanir -- yeniden adlandirma migration ister.
 */
public enum Feature {
    AI_ASSISTANT("AI Asistanı (Tanı / Tedavi Önerisi)"),
    IMAGING("Görüntüleme"),
    LAB_INTEGRATION("Laboratuvar Entegrasyonu"),
    BOARDING("Pansiyon / Konaklama"),
    INVENTORY("Stok Yönetimi"),
    E_FATURA("E-Fatura");

    private final String label;

    Feature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
