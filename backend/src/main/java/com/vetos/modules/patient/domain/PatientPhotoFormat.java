package com.vetos.modules.patient.domain;

import java.util.Optional;

/** Profil fotografi icin magic-byte ile icerik turu tespiti (JPEG, PNG, WEBP). */
public final class PatientPhotoFormat {
    private PatientPhotoFormat() {}

    public static Optional<String> detectContentType(byte[] c) {
        if (c == null) return Optional.empty();
        if (c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) {
            return Optional.of("image/jpeg");
        }
        if (c.length >= 4 && (c[0] & 0xFF) == 0x89 && c[1] == 0x50 && c[2] == 0x4E && c[3] == 0x47) {
            return Optional.of("image/png");
        }
        if (c.length >= 12 && c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F'
            && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P') {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }
}
