package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.platform.subscription.TrialProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

/** Deneme kaydi use-case testlerinin ortak yardimcilari. */
final class TrialTestSupport {

    static final Instant NOW = Instant.parse("2026-11-13T09:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final TrialProperties PROPERTIES = new TrialProperties(7, 300, 5, 60, 3, 3);
    static final String PHONE = "+905321112233";

    /** Gercek hash yerine okunabilir, deterministik sahte kodlayici. */
    static final PasswordEncoder ENCODER = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hash(" + raw + ")";
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return encode(raw).equals(encoded);
        }
    };

    private TrialTestSupport() {
    }

    static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    /** Kodu "123456" olarak verilmis, bekleyen bir kayit istegi. */
    static TrialSignupRequest pendingRequest(UUID id) {
        TrialSignupRequest request = TrialSignupRequest.create(
            "Ayşe Yılmaz", "ayse@ornek.com", PHONE, "Pati Klinik", "İzmir", "35-1234",
            "hash(Sifre12345)", NOW, "10.0.0.1"
        );
        request.issueCode("hash(123456)", NOW, 300);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }
}
