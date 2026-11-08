package com.vetos;

import com.vetos.modules.consentform.application.ListFormTemplatesUseCase;
import com.vetos.modules.consentform.application.dto.FormTemplateSummary;
import com.vetos.modules.consentform.domain.*;
import jakarta.persistence.EntityManager;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(FormTemplateCountsIntegrationTest.SqlConfiguration.class)
class FormTemplateCountsIntegrationTest extends TenantScopedTestSupport {
    @Autowired ListFormTemplatesUseCase list;
    @Autowired FormTemplateRepository templates;
    @Autowired SignedFormRepository forms;
    @Autowired EntityManager entityManager;
    private final UUID tenantA = UUID.randomUUID(), tenantB = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private static final ThreadLocal<List<String>> SQL = new ThreadLocal<>();

    @TestConfiguration
    static class SqlConfiguration {
        @Bean
        HibernatePropertiesCustomizer countSql() {
            return properties -> properties.put("hibernate.session_factory.statement_inspector",
                (StatementInspector) sql -> {
                    if (SQL.get() != null) SQL.get().add(sql);
                    return sql;
                });
        }
    }

    @AfterEach
    void cleanup() {
        SQL.remove();
        inRootSessionVoid(() -> {
            for (UUID tenant : List.of(tenantA, tenantB)) {
                entityManager.createNativeQuery("DELETE FROM signed_forms WHERE tenant_id = :t")
                    .setParameter("t", tenant).executeUpdate();
            }
            for (UUID tenant : List.of(tenantA, tenantB)) {
                entityManager.createNativeQuery("DELETE FROM form_templates WHERE tenant_id = :t")
                    .setParameter("t", tenant).executeUpdate();
            }
        });
    }

    private UUID template(UUID tenant, String title, boolean archived) {
        return asTenant(tenant, () -> {
            var t = FormTemplate.create(tenant, title, "{}", null, staff);
            if (archived) t.archive(staff);
            return templates.save(t).getId();
        });
    }

    private void signed(UUID tenant, UUID templateId, boolean voided) {
        asTenantVoid(tenant, () -> {
            var form = SignedForm.create(tenant, templateId, "Form", "{}", "{}", "fixture-ref",
                UUID.randomUUID(), UUID.randomUUID(), null, SignerType.OWNER, "Owner", null, null,
                staff, Instant.now(), null, null, "a".repeat(64));
            if (voided) form.voidForm("Test", staff);
            forms.save(form);
        });
    }

    private List<FormTemplateSummary> capturedList(UUID tenant, boolean archived, int expectedQueries) {
        var statements = new ArrayList<String>();
        SQL.set(statements);
        try {
            var result = asTenant(tenant, () -> list.execute(archived));
            assertThat(statements).hasSize(expectedQueries);
            if (expectedQueries == 2) {
                assertThat(statements.stream().filter(sql -> sql.toLowerCase(Locale.ROOT).contains("count(")
                    && sql.contains("signed_forms")).toList()).hasSize(1);
            }
            return result;
        } finally {
            SQL.remove();
        }
    }

    @Test
    void activeListUsesTwoQueriesAndPreservesCountsOrderAndZero() {
        UUID a = template(tenantA, "A", false);
        template(tenantA, "B", false);
        UUID c = template(tenantA, "C", true);
        signed(tenantA, a, false);
        signed(tenantA, a, true);
        signed(tenantA, c, false);
        // Deliberately inconsistent fixture: a foreign tenant references the same template.
        // It must never inflate tenant A's count, even though the database FK permits it.
        signed(tenantB, a, false);
        var result = capturedList(tenantA, false, 2);
        assertThat(result).extracting(FormTemplateSummary::title).containsExactly("A", "B");
        assertThat(result).extracting(FormTemplateSummary::signedCount).containsExactly(2L, 0L);
    }

    @Test
    void allListIncludesArchivedTemplatesWithOneGroupedCountQuery() {
        UUID c = template(tenantA, "C", true);
        template(tenantA, "B", false);
        UUID a = template(tenantA, "A", false);
        signed(tenantA, a, false);
        signed(tenantA, c, true);
        var result = capturedList(tenantA, true, 2);
        assertThat(result).extracting(FormTemplateSummary::title).containsExactly("A", "B", "C");
        assertThat(result).extracting(FormTemplateSummary::signedCount).containsExactly(1L, 0L, 1L);
        assertThat(result.get(2).status()).isEqualTo(FormTemplateStatus.ARCHIVED);
    }

    @Test
    void groupedCountCannotReadAnotherTenantsFormsEvenWithTheirTemplateId() {
        UUID a = template(tenantA, "A", false);
        signed(tenantA, a, false);
        signed(tenantA, a, false);
        signed(tenantB, a, false);
        assertThat(asTenant(tenantA, () -> forms.countByTemplateIds(List.of(a)))).containsExactlyEntriesOf(Map.of(a, 2L));
        assertThat(asTenant(tenantB, () -> forms.countByTemplateIds(List.of(a)))).containsExactlyEntriesOf(Map.of(a, 1L));
    }

    @Test
    void emptyListUsesOnlyTheTemplateQuery() {
        template(tenantB, "Other tenant", false);
        assertThat(capturedList(tenantA, false, 1)).isEmpty();
        assertThat(capturedList(tenantA, true, 1)).isEmpty();
    }
}
