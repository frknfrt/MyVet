package com.vetos.modules.consentform.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Kliniğin yönettiği metin; hash hem sürümü hem tam metni kapsar. */
public record PrivacyNotice(long version, String text) {
    public String hash() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((version + "\n" + text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @com.fasterxml.jackson.annotation.JsonProperty("hash")
    public String jsonHash() { return hash(); }
}
