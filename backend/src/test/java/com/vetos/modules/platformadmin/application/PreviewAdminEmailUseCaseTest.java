package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.EmailTemplateType;
import com.vetos.modules.platformadmin.domain.RenderedEmail;
import com.vetos.modules.platformadmin.infrastructure.email.EmailTemplateRenderer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewAdminEmailUseCaseTest {

    private final PreviewAdminEmailUseCase useCase = new PreviewAdminEmailUseCase(new EmailTemplateRenderer());

    @Test
    void should_renderWithoutSendingOrPersisting() {
        Map<String, String> fields = new HashMap<>();
        EmailTemplateType.WELCOME.fields().forEach(f -> fields.put(f.key(), "deger"));

        RenderedEmail rendered = useCase.execute(EmailTemplateType.WELCOME, fields);

        assertThat(rendered.html()).contains("deger");
        assertThat(rendered.html()).doesNotContain("{{");
    }
}
