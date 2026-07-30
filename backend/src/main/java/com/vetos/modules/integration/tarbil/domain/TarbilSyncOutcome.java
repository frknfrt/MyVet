package com.vetos.modules.integration.tarbil.domain;

public record TarbilSyncOutcome(boolean success, String message) {

    public static TarbilSyncOutcome success(String message) {
        return new TarbilSyncOutcome(true, message);
    }

    public static TarbilSyncOutcome failure(String message) {
        return new TarbilSyncOutcome(false, message);
    }
}
