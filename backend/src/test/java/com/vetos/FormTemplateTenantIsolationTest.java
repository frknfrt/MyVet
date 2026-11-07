package com.vetos;

import com.vetos.modules.consentform.application.GetFormTemplateUseCase;
import com.vetos.modules.consentform.application.ListFormTemplatesUseCase;
import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateContentCodec;
import com.vetos.modules.consentform.domain.FormTemplateRepository;
import com.vetos.modules.consentform.domain.exception.FormTemplateNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sablon okuma/listeleme gercek Postgres'te kiraciya gore suzulur. Canli docker-compose Postgres gerekir. */
@SpringBootTest
class FormTemplateTenantIsolationTest extends TenantScopedTestSupport {

    @Autowired FormTemplateRepository templateRepository;
    @Autowired GetFormTemplateUseCase getFormTemplateUseCase;
    @Autowired ListFormTemplatesUseCase listFormTemplatesUseCase;
    @PersistenceContext EntityManager entityManager;

    private final List<UUID> tenants = new ArrayList<>();

    @AfterEach
    void purge() {
        for (UUID t : tenants) {
            inRootSessionVoid(() -> entityManager.createNativeQuery("DELETE FROM form_templates WHERE tenant_id = :t")
                .setParameter("t", t).executeUpdate());
        }
        tenants.clear();
    }

    private UUID createTemplate(UUID tenantId, String title) {
        tenants.add(tenantId);
        String json = FormTemplateContentCodec.toJson(new FormTemplateContent(title, List.of()));
        return asTenant(tenantId, () -> templateRepository.save(
            FormTemplate.create(tenantId, title, json, null, UUID.randomUUID())).getId());
    }

    @Test
    void get_should_return404_forOtherTenantsTemplate() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID templateId = createTemplate(tenantA, "A Formu");

        assertThat(asTenant(tenantA, () -> getFormTemplateUseCase.execute(templateId)).title()).isEqualTo("A Formu");
        assertThatThrownBy(() -> asTenant(tenantB, () -> getFormTemplateUseCase.execute(templateId)))
            .isInstanceOf(FormTemplateNotFoundException.class);
    }

    @Test
    void list_should_onlyReturnOwnTenantsTemplates() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        createTemplate(tenantA, "A Formu");

        assertThat(asTenant(tenantA, () -> listFormTemplatesUseCase.execute(true))).hasSize(1);
        assertThat(asTenant(tenantB, () -> listFormTemplatesUseCase.execute(true))).isEmpty();
    }
}
