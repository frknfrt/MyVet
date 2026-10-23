package com.vetos.modules.platformadmin.domain;

import java.util.List;

/**
 * Admin panelinden elle gonderilebilen 4 markali e-posta sablonu -- gercek
 * gonderim altyapisi (SendGrid/SES/Mailgun) yok, cPanel'deki 3 kutunun kendi
 * SMTP'si kullanilir (bkz. AdminEmailPort). Her tip tek bir kutuya sabit:
 * satis/deneme info@'dan, kurulum destek@'dan, fatura bildirim@'dan gider --
 * Genel Yapi tablosundaki (Vetly E-posta Taslaklari dokumani) "Kimden"
 * sutunuyla birebir ayni.
 *
 * Govde HTML'leri src/main/resources/email-templates/*.html altinda, {{TOKEN}}
 * bicimli yer tutucularla -- bkz. EmailTemplateRenderer. fields() listesi iki
 * amaca hizmet eder: (1) admin panelindeki formun hangi alanlari gosterecegini
 * belirler, (2) gonderim/onizleme oncesi hangi alanlarin dolu olmasi
 * gerektigini tanimlar.
 */
public enum EmailTemplateType {

    SALES(
        "Satış / İlk Temas",
        AdminMailbox.INFO,
        "email-templates/sales.html",
        "{{KLINIK_ADI}} için randevu, aşı ve stok takibi tek yerde",
        List.of(
            new EmailTemplateField("KLINIK_ADI", "Klinik Adı"),
            new EmailTemplateField("YETKILI_ADI", "Yetkili Adı"),
            new EmailTemplateField("TELEFON", "Telefon"),
            new EmailTemplateField("DEMO_LINK", "Demo/takvim bağlantısı (ör. Calendly veya mailto:)")
        )
    ),
    TRIAL_FOLLOWUP(
        "Deneme/Demo Sonrası Takip",
        AdminMailbox.INFO,
        "email-templates/trial-followup.html",
        "Vetly denemeniz nasıl gidiyor?",
        List.of(
            new EmailTemplateField("YETKILI_ADI", "Yetkili Adı"),
            new EmailTemplateField("KLINIK_ADI", "Klinik Adı"),
            new EmailTemplateField("TELEFON", "Telefon"),
            new EmailTemplateField("BITIS_TARIHI", "Deneme Bitiş Tarihi")
        )
    ),
    WELCOME(
        "Hoş Geldin / Kurulum",
        AdminMailbox.DESTEK,
        "email-templates/welcome.html",
        "Vetly'ye hoş geldiniz — hesabınız hazır",
        List.of(
            new EmailTemplateField("YETKILI_ADI", "Yetkili Adı"),
            new EmailTemplateField("KLINIK_ADI", "Klinik Adı"),
            new EmailTemplateField("E_POSTA", "Kullanıcı Adı (E-posta)"),
            new EmailTemplateField("GECICI_SIFRE", "Geçici Şifre"),
            new EmailTemplateField("TELEFON", "Telefon")
        )
    ),
    INVOICE_REMINDER(
        "Fatura / Ödeme Hatırlatma",
        AdminMailbox.BILDIRIM,
        "email-templates/invoice-reminder.html",
        "Vetly {{AY_YIL}} Aylık Fatura Bildirimi — {{KLINIK_ADI}}",
        List.of(
            new EmailTemplateField("YETKILI_ADI", "Yetkili Adı"),
            new EmailTemplateField("KLINIK_ADI", "Klinik Adı"),
            new EmailTemplateField("AY_YIL", "Dönem (Ay/Yıl)"),
            new EmailTemplateField("TUTAR", "Tutar (TL)"),
            new EmailTemplateField("SON_ODEME_TARIHI", "Son Ödeme Tarihi"),
            new EmailTemplateField("IBAN_NUMARASI", "IBAN Numarası"),
            new EmailTemplateField("UNVAN", "Şirket Unvanı")
        )
    );

    private final String label;
    private final AdminMailbox mailbox;
    private final String templateResourcePath;
    private final String subjectTemplate;
    private final List<EmailTemplateField> fields;

    EmailTemplateType(String label, AdminMailbox mailbox, String templateResourcePath, String subjectTemplate, List<EmailTemplateField> fields) {
        this.label = label;
        this.mailbox = mailbox;
        this.templateResourcePath = templateResourcePath;
        this.subjectTemplate = subjectTemplate;
        this.fields = fields;
    }

    public String label() {
        return label;
    }

    public AdminMailbox mailbox() {
        return mailbox;
    }

    public String templateResourcePath() {
        return templateResourcePath;
    }

    public String subjectTemplate() {
        return subjectTemplate;
    }

    public List<EmailTemplateField> fields() {
        return fields;
    }
}
