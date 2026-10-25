package com.vetos.modules.ai.domain;

/** Tanı ve tedavi talimatlarına eklenen hasta geçmişi cümleleri (spec 2026-10-25-ai-hasta-gecmisi). Claude ve Ollama ortak. */
public final class PatientHistoryPrompt {

    public static final String INSTRUCTION = " Sana ayrica hastanin son 1 yillik gecmisi (hasta bilgisi, gecmis muayeneler, ilaclar, "
        + "asilar, lab sonuclari, goruntuleme bulgulari) verilecek. Bunlari dikkate al: ozellikle kritik uyari/alerji, normal disi "
        + "lab degerleri ve hala kullaniliyor olabilecek ilaclarla etkilesim. Eski tanilari oldugu gibi tekrarlama; bugunku "
        + "bulgularla birlikte degerlendir.";

    /** Cevap kısa kalsın, uzunluk sınırına yaklaşmasın (2026-10-25). */
    public static final String CONCISE = " Kisa ve oz yaz: madde madde, gereksiz aciklama yapma.";

    public static final String DOSE_BY_WEIGHT = " Doz onerirken hastanin kilosunu kullan.";

    private PatientHistoryPrompt() {}
}
