package com.vetos.platform.audit;

/** Klinik denetim kaydi olaylari (spec 2026-10-15 §3). Kod DB'ye yazilir; etiket ekranda gosterilir. */
public enum AuditAction {
    STAFF_LOGIN_SUCCEEDED("Giriş yapıldı"),
    STAFF_LOGIN_FAILED("Başarısız giriş denemesi"),
    STAFF_CREATED("Personel eklendi"),
    STAFF_UPDATED("Personel bilgisi değişti"),
    STAFF_DEACTIVATED("Personel pasife alındı"),
    STAFF_REACTIVATED("Personel yeniden aktif edildi"),
    STAFF_INVITED("Personel davet edildi"),
    STAFF_INVITE_REVOKED("Davet iptal edildi"),
    STAFF_INVITE_ACCEPTED("Davet kabul edildi"),
    INVOICE_VOIDED("Fatura iptal edildi"),
    INVOICE_PAYMENT_RECORDED("Ödeme alındı"),
    CASH_REGISTER_CLOSED("Kasa kapatıldı"),
    PRESCRIPTION_ISSUED("Reçete yazıldı"),
    PRESCRIPTION_CANCELLED("Reçete iptal edildi"),
    ENCOUNTER_FINALIZED("Muayene kesinleştirildi"),
    CONSENT_REVOKED("KVKK onayı geri çekildi"),
    TARBIL_MARKED_SUBMITTED("TARBİL'e gönderildi işaretlendi"),
    TARBIL_SUBMISSION_DISMISSED("TARBİL gönderimi yok sayıldı"),
    TARBIL_SUBMISSION_RESTORED("TARBİL gönderimi geri alındı"),
    TARBIL_EXTENSION_PAIRED("TARBİL eklentisi eşleştirildi"),
    TARBIL_EXTENSION_REVOKED("TARBİL eklenti anahtarı iptal edildi"),
    SUPPORT_IMPERSONATION_STARTED("Vetly destek ekibi hesaba girdi");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** DB'deki kod artik enum'da yoksa (yeniden adlandirma) kodun kendisi gosterilir. */
    public static String labelOf(String code) {
        for (AuditAction a : values()) {
            if (a.name().equals(code)) {
                return a.label;
            }
        }
        return code;
    }
}
