package com.vetos.modules.dataimport.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gercek veritabaninda eski-varlik (stale entity) yarisi: zamanlanmis isin dis transaction'i partiyi listeleyip
 * persistence context'ine almisken baska bir transaction partiyi COMMITTING yapar. Parti basina adim kendi taze
 * transaction'inda okumazsa, managed kopyadaki eski PREVIEWED durumunu gorur ve DISCARDED yazar.
 * Canli docker-compose Postgres gerekir; satirlar @AfterEach'te silinir. run() bilerek cagrilmaz (paylasilan dev
 * veritabanindaki baska eski onizlemeleri iptal etmesin).
 */
@SpringBootTest
class DiscardStaleImportPreviewsJobIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ImportBatchRepository batchRepository;
    @Autowired private DiscardStaleImportPreviewsJob job;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;

    @BeforeEach
    void createTenant() {
        tenantId = tenantRepository.save(Tenant.register("Aktarim Eski Onizleme", null)).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM import_batches WHERE tenant_id = ?", tenantId);
        jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
    }

    private UUID newPreview() {
        return batchRepository.save(ImportBatch.preview(tenantId, "KolayVet", "a.xlsx", new byte[]{1},
            new ImportReport(Map.of(), List.of(), List.of(), List.of(), List.of(), List.of()), 1, UUID.randomUUID()))
            .getId();
    }

    private String status(UUID id) {
        return jdbc.queryForObject("SELECT status FROM import_batches WHERE id = ?", String.class, id);
    }

    @Test
    void should_leaveBatchCommitting_whenStartedAfterListing() {
        UUID id = newPreview();
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate other = new TransactionTemplate(transactionManager);
        other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Boolean discarded = outer.execute(s -> {
            // Isin listeleme adimi gibi: varlik dis persistence context'inde PREVIEWED olarak managed.
            assertThat(batchRepository.findById(id).orElseThrow().getStatus().name()).isEqualTo("PREVIEWED");
            // Bu arada admin aktarimi baslatir (ayri, commit edilen transaction).
            other.executeWithoutResult(s2 -> {
                ImportBatch b = batchRepository.findById(id).orElseThrow();
                b.startCommit(List.of());
                batchRepository.save(b);
            });
            return job.discardIfStillPreviewed(id);
        });

        assertThat(discarded).isFalse();
        assertThat(status(id)).isEqualTo("COMMITTING");
    }

    @Test
    void should_discard_whenStillPreviewed() {
        UUID id = newPreview();

        assertThat(job.discardIfStillPreviewed(id)).isTrue();

        assertThat(status(id)).isEqualTo("DISCARDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM import_batches WHERE id = ? AND source_file IS NULL",
            Integer.class, id)).isEqualTo(1);
    }
}
