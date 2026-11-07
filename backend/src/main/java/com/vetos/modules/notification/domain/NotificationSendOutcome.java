package com.vetos.modules.notification.domain;

public record NotificationSendOutcome(boolean success, String message, boolean uncertain) {

    public static NotificationSendOutcome success(String message) {
        return new NotificationSendOutcome(true, message, false);
    }

    public static NotificationSendOutcome unknown() {
        return new NotificationSendOutcome(false, "Sağlayıcı sonucu doğrulanamadı", true);
    }

    public static NotificationSendOutcome failure(String message) {
        return new NotificationSendOutcome(false, message, false);
    }
}
