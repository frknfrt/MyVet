package com.vetos.modules.notification.domain;

import java.util.List;
import java.util.Map;

/**
 * Otomatik/zamanlanmis bildirim tipleri icin varsayilan metin ve kullanilabilir
 * degisken (placeholder) listesi. Klinik SMS/WhatsApp Ayarlar sekmesinden
 * kendi metnini yazmazsa buradaki varsayilan kullanilir (bkz.
 * GetNotificationTemplatesUseCase). CAMPAIGN_MESSAGE burada YOK -- o her
 * gonderimde elle yazilir (bkz. SendCampaignUseCase, CampaignTab.tsx),
 * onceden tanimli bir otomatik sablonu yok.
 */
public final class NotificationTemplateDefaults {

    private NotificationTemplateDefaults() {
    }

    public static final List<NotificationType> CUSTOMIZABLE_TYPES = List.of(
        NotificationType.APPOINTMENT_CONFIRMATION,
        NotificationType.APPOINTMENT_REMINDER,
        NotificationType.VACCINATION_REMINDER
    );

    private static final Map<NotificationType, String> DEFAULT_TEXT = Map.of(
        NotificationType.APPOINTMENT_CONFIRMATION,
        "Sayın {sahipAdi}, {hastaAdi} için {tarihSaat} tarihli randevunuz oluşturuldu.",
        NotificationType.APPOINTMENT_REMINDER,
        "Sayın {sahipAdi}, {hastaAdi} için yarın {tarihSaat} randevunuz var. Bu bir hatırlatma mesajıdır.",
        NotificationType.VACCINATION_REMINDER,
        "Sayın {sahipAdi}, {hastaAdi} için {asiAdi} aşısının zamanı geldi ({tarih}). Kliniğimizle iletişime geçebilirsiniz."
    );

    private static final Map<NotificationType, List<String>> PLACEHOLDERS = Map.of(
        NotificationType.APPOINTMENT_CONFIRMATION, List.of("sahipAdi", "hastaAdi", "tarihSaat"),
        NotificationType.APPOINTMENT_REMINDER, List.of("sahipAdi", "hastaAdi", "tarihSaat"),
        NotificationType.VACCINATION_REMINDER, List.of("sahipAdi", "hastaAdi", "asiAdi", "tarih")
    );

    public static String defaultTextFor(NotificationType type) {
        String text = DEFAULT_TEXT.get(type);
        if (text == null) {
            throw new IllegalArgumentException("Bu bildirim tipi icin ozellestirilebilir sablon yok: " + type);
        }
        return text;
    }

    public static List<String> placeholdersFor(NotificationType type) {
        List<String> placeholders = PLACEHOLDERS.get(type);
        if (placeholders == null) {
            throw new IllegalArgumentException("Bu bildirim tipi icin ozellestirilebilir sablon yok: " + type);
        }
        return placeholders;
    }

    /** {anahtar} placeholder'larini verilen degerlerle degistirir -- bilinmeyen token'lar oldugu gibi kalir. */
    public static String render(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
