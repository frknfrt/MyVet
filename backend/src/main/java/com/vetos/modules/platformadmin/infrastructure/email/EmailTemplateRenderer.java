package com.vetos.modules.platformadmin.infrastructure.email;

import com.vetos.modules.platformadmin.domain.EmailTemplateField;
import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.modules.platformadmin.domain.RenderedEmail;
import com.vetos.modules.platformadmin.domain.exception.AdminEmailTemplateRenderException;
import com.vetos.modules.platformadmin.domain.exception.MissingTemplateFieldException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * email-templates/*.html icindeki {{TOKEN}} yer tutucularini verilen alan
 * degerleriyle degistirir. Sablon govdeleri classpath'ten okunup bellekte
 * cache'lenir (degismezler, uygulama yeniden baslamadan degismezler).
 *
 * Validasyon iki asamalidir: (1) EmailTemplateType.fields() listesindeki
 * HER alan dolu olmali (bkz. MissingTemplateFieldException) -- bu, formda
 * unutulan bir alani erkenden yakalar; (2) render sonrasi govde/konuda hala
 * "{{" kalmamali (bkz. AdminEmailTemplateRenderException) -- bu da sablon
 * icindeki bir tokenin fields() listesinde KARSILIGI OLMAYAN bir isim
 * kullandigi (yazim hatasi) durumunu yakalar.
 */
@Component
public class EmailTemplateRenderer {

    private static final Pattern UNRESOLVED_TOKEN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}");

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public RenderedEmail render(EmailTemplateType type, Map<String, String> fields) {
        for (EmailTemplateField field : type.fields()) {
            String value = fields.get(field.key());
            if (value == null || value.isBlank()) {
                throw new MissingTemplateFieldException(type, field.key());
            }
        }

        String subject = substitute(type.subjectTemplate(), fields);
        String html = substitute(loadTemplate(type), fields);

        requireFullyResolved(subject);
        requireFullyResolved(html);

        return new RenderedEmail(subject, html);
    }

    private String substitute(String text, Map<String, String> fields) {
        String result = text;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private void requireFullyResolved(String rendered) {
        Matcher matcher = UNRESOLVED_TOKEN.matcher(rendered);
        if (matcher.find()) {
            throw new AdminEmailTemplateRenderException(matcher.group(1));
        }
    }

    private String loadTemplate(EmailTemplateType type) {
        return templateCache.computeIfAbsent(type.templateResourcePath(), this::readClasspathResource);
    }

    private String readClasspathResource(String path) {
        try {
            byte[] bytes = new ClassPathResource(path).getContentAsByteArray();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("E-posta sablonu okunamadi: " + path, e);
        }
    }
}
