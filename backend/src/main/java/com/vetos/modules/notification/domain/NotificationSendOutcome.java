package com.vetos.modules.notification.domain;

public record NotificationSendOutcome(boolean success, String message) {

    public static NotificationSendOutcome success(String message) {
        return new NotificationSendOutcome(true, message);
    }

    public static NotificationSendOutcome failure(String message) {
        return new NotificationSendOutcome(false, message);
    }
}
