package com.vetos.modules.platformadmin.infrastructure.email;

import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.modules.platformadmin.domain.RenderedEmail;
import com.vetos.modules.platformadmin.domain.exception.MissingTemplateFieldException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTemplateRendererTest {

    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

    private Map<String, String> fieldsFor(EmailTemplateType type) {
        Map<String, String> fields = new HashMap<>();
        type.fields().forEach(f -> fields.put(f.key(), "DEGER-" + f.key()));
        return fields;
    }

    @Test
    void should_renderEveryTemplateType_withNoLeftoverTokens() {
        for (EmailTemplateType type : EmailTemplateType.values()) {
            RenderedEmail rendered = renderer.render(type, fieldsFor(type));
            assertThat(rendered.subject()).doesNotContain("{{");
            assertThat(rendered.html()).doesNotContain("{{");
            type.fields().forEach(f -> assertThat(rendered.html() + rendered.subject()).as(type + "." + f.key())
                .contains("DEGER-" + f.key()));
        }
    }

    @Test
    void should_throwMissingTemplateFieldException_when_requiredFieldBlank() {
        Map<String, String> fields = fieldsFor(EmailTemplateType.SALES);
        fields.put("KLINIK_ADI", "  ");

        assertThatThrownBy(() -> renderer.render(EmailTemplateType.SALES, fields))
            .isInstanceOf(MissingTemplateFieldException.class)
            .hasMessageContaining("KLINIK_ADI");
    }

    @Test
    void should_throwMissingTemplateFieldException_when_requiredFieldAbsent() {
        Map<String, String> fields = fieldsFor(EmailTemplateType.INVOICE_REMINDER);
        fields.remove("IBAN_NUMARASI");

        assertThatThrownBy(() -> renderer.render(EmailTemplateType.INVOICE_REMINDER, fields))
            .isInstanceOf(MissingTemplateFieldException.class)
            .hasMessageContaining("IBAN_NUMARASI");
    }

    @Test
    void should_substituteSubjectTokens_forInvoiceReminder() {
        Map<String, String> fields = fieldsFor(EmailTemplateType.INVOICE_REMINDER);

        RenderedEmail rendered = renderer.render(EmailTemplateType.INVOICE_REMINDER, fields);

        assertThat(rendered.subject()).isEqualTo("Vetly DEGER-AY_YIL Aylık Fatura Bildirimi — DEGER-KLINIK_ADI");
    }
}
