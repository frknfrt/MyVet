package com.vetos.platform.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Oturum anahtarlari (JWT) tarayicida HttpOnly cerezde durur; JavaScript okuyamaz, sayfaya sizan bir script
 * anahtari calip baska yerden kullanamaz (2026-11-06, guvenlik incelemesi).
 *
 * - Klinik oturumu: {@link #NAME}, Path=/api. Platform admin oturumu: {@link #ADMIN_NAME}, Path=/api/v1/platform-admin.
 * - SameSite=Strict: baska sitelerden baslatilan isteklere eklenmez. Ayni "site" sayilan alt alan adlari
 *   (or. vetly.com.tr vitrini) icin filtreler ayrica {@link #CLIENT_HEADER} ister (CSRF).
 * - Secure: tarayicilar http://localhost'ta da kabul eder; canlida yalniz HTTPS.
 */
@Component
public class SessionCookies {

    public static final String NAME = "vetly_session";
    public static final String ADMIN_NAME = "vetly_admin_session";
    /** Cerezle gelen veri degistiren isteklerde zorunlu baslik; capraz kaynakli formlar bunu ekleyemez. */
    public static final String CLIENT_HEADER = "X-Vetly-Client";

    private static final String PATH = "/api";
    private static final String ADMIN_PATH = "/api/v1/platform-admin";

    private final Duration maxAge;
    private final boolean secure;

    public SessionCookies(
        @Value("${jwt.expiration-minutes:480}") long expirationMinutes,
        @Value("${app.session-cookie.secure:true}") boolean secure
    ) {
        this.maxAge = Duration.ofMinutes(expirationMinutes);
        this.secure = secure;
    }

    public String create(String token) {
        return base(NAME, PATH, token).maxAge(maxAge).build().toString();
    }

    public String clear() {
        return base(NAME, PATH, "").maxAge(Duration.ZERO).build().toString();
    }

    public String createAdmin(String token) {
        return base(ADMIN_NAME, ADMIN_PATH, token).maxAge(maxAge).build().toString();
    }

    public String clearAdmin() {
        return base(ADMIN_NAME, ADMIN_PATH, "").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String path, String value) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(secure).sameSite("Strict").path(path);
    }

    /** Istekteki cerez degeri; yoksa ya da bossa null. */
    static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    /** Cerezle kimlik dogrulanacak, veri degistiren bir istekte uygulamanin basligi yoksa true (CSRF suphesi). */
    static boolean failsCsrfCheck(HttpServletRequest request) {
        String m = request.getMethod();
        boolean unsafe = !("GET".equals(m) || "HEAD".equals(m) || "OPTIONS".equals(m));
        return unsafe && request.getHeader(CLIENT_HEADER) == null;
    }

    static void rejectCsrf(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
            "{\"errorCode\":\"CSRF_CHECK_FAILED\",\"message\":\"İstek doğrulanamadı; sayfayı yenileyip tekrar deneyin\"}");
    }
}
