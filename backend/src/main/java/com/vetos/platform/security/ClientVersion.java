package com.vetos.platform.security;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eklenti surumu (x.y.z), sayisal karsilastirma -- spec 2026-10-18 Faz 7 §1.
 * "0.10.0" > "0.9.0"; metin karsilastirmasi bunu ters cevirirdi.
 */
public record ClientVersion(int major, int minor, int patch) implements Comparable<ClientVersion> {

    public static final ClientVersion ZERO = new ClientVersion(0, 0, 0);
    /** X-Client-Version gondermeyen (0.1.0) eklentiler bu surum sayilir. */
    public static final ClientVersion WHEN_MISSING = new ClientVersion(0, 1, 0);

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})");
    private static final Comparator<ClientVersion> ORDER = Comparator
        .comparingInt(ClientVersion::major)
        .thenComparingInt(ClientVersion::minor)
        .thenComparingInt(ClientVersion::patch);

    public static Optional<ClientVersion> tryParse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = FORMAT.matcher(raw.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ClientVersion(
            Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
    }

    public static ClientVersion parse(String raw) {
        return tryParse(raw).orElseThrow(() -> new IllegalArgumentException("Gecersiz surum (x.y.z bekleniyor): " + raw));
    }

    @Override
    public int compareTo(ClientVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
