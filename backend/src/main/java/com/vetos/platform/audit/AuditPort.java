package com.vetos.platform.audit;

/** Klinik denetim kaydi yazma (spec 2026-10-15 §5). Olay, islem BASARIYLA bittikten sonra cagrilir. */
public interface AuditPort {

    /** Aktor guvenlik baglamindan; cagiranin transaction'ina katilir (is geri alinirsa kayit da geri alinir). */
    void record(AuditRecord record);

    /** Aktor acikca verilir (kimliksiz uclar: davet kabulu, eklenti eslestirme; impersonate baslangici). */
    void record(AuditRecord record, AuditActor actor);

    /** Kendi transaction'inda (REQUIRES_NEW): giris olaylari -- read-only tx ve istisnayla geri alinan tx'e ragmen kalir. */
    void recordStandalone(AuditRecord record, AuditActor actor);
}
