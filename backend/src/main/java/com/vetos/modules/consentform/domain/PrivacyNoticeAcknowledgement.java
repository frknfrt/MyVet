package com.vetos.modules.consentform.domain;

/** İmzalı snapshot içinde korunur; hasta sahibinin açık rızası olarak yorumlanmaz. */
public record PrivacyNoticeAcknowledgement(long version, String text, String hash, String statement, boolean sms, boolean whatsapp, boolean email) {
    public static final String STATEMENT = "Kişisel verilerimin işlenmesine ilişkin aydınlatma metni tarafıma sunuldu ve bilgilendirildim.";
    public static PrivacyNoticeAcknowledgement of(PrivacyNotice notice, boolean sms, boolean whatsapp, boolean email) {
        return new PrivacyNoticeAcknowledgement(notice.version(), notice.text(), notice.hash(), STATEMENT, sms, whatsapp, email);
    }
}
