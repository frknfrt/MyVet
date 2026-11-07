package com.vetos;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLink;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.modules.dataimport.domain.ImportReport.EntityTotals;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Veri aktarimi tablolari/varliklari. TenantScopedTestSupport paket-ozel
 * oldugu icin test com.vetos paketinde durur. Satirlar @AfterEach'te elle silinir.
 */
@SpringBootTest
class ImportPersistenceIntegrationTest extends TenantScopedTestSupport {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ImportBatchRepository batchRepository;
    @Autowired private ImportRecordLinkRepository linkRepository;
    @PersistenceContext private EntityManager entityManager;

    private final List<UUID> createdTenantIds = new ArrayList<>();

    private UUID newTenant(String label) {
        UUID id = inRootSession(() -> tenantRepository.save(Tenant.register(label, null)).getId());
        createdTenantIds.add(id);
        return id;
    }

    private ImportReport report(int toCreate) {
        return new ImportReport(
            Map.of(ImportEntityType.OWNER, new EntityTotals(10, toCreate, 2, 1, 0)),
            List.of(), List.of(), List.of(), List.of("Kanarya"), List.of());
    }

    private UUID newBatch(UUID tenantId, int toCreate) {
        return inRootSession(() -> batchRepository.save(
            ImportBatch.preview(tenantId, "KolayVet", "dosya.xlsx", new byte[]{1, 2, 3},
                report(toCreate), 10, UUID.randomUUID())).getId());
    }

    @AfterEach
    void cleanUp() {
        createdTenantIds.forEach(t -> inRootSessionVoid(() -> {
            for (String sql : List.of(
                "DELETE FROM import_record_links WHERE tenant_id = :t",
                "DELETE FROM import_batches WHERE tenant_id = :t",
                "DELETE FROM tenants WHERE id = :t")) {
                entityManager.createNativeQuery(sql).setParameter("t", t).executeUpdate();
            }
        }));
        createdTenantIds.clear();
    }

    @Test
    void should_roundTripBatchWithReport() {
        UUID tenantId = newTenant("Aktarim A");
        UUID batchId = newBatch(tenantId, 7);

        ImportBatch loaded = inRootSession(() -> batchRepository.findById(batchId)).orElseThrow();

        assertThat(loaded.getReport().totals().get(ImportEntityType.OWNER).toCreate()).isEqualTo(7);
        assertThat(loaded.getReport().newSpecies()).containsExactly("Kanarya");
        assertThat(loaded.getReport().canCommit()).isTrue();
        assertThat(loaded.getStatus()).isEqualTo(ImportBatchStatus.PREVIEWED);
        assertThat(loaded.getSourceFile()).containsExactly(1, 2, 3);
        assertThat(inRootSession(() -> batchRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))).hasSize(1);
    }

    @Test
    void should_rejectDuplicateSourceIdPerTenant() {
        UUID tenantId = newTenant("Aktarim A");
        UUID batchId = newBatch(tenantId, 1);

        asTenantVoid(tenantId, () -> linkRepository.saveAll(List.of(ImportRecordLink.of(
            tenantId, batchId, "KolayVet", ImportEntityType.OWNER, "17", UUID.randomUUID(), true, Map.of("k", "v")))));

        assertThatThrownBy(() -> asTenantVoid(tenantId, () -> linkRepository.saveAll(List.of(ImportRecordLink.of(
            tenantId, batchId, "KolayVet", ImportEntityType.OWNER, "17", UUID.randomUUID(), true, Map.of())))))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(asTenant(tenantId, () -> linkRepository.existingSourceIds(
            tenantId, "KolayVet", ImportEntityType.OWNER, List.of("17", "18")))).containsExactly("17");
    }

    @Test
    void should_hideLinksOfOtherTenant() {
        UUID a = newTenant("Aktarim A");
        UUID b = newTenant("Aktarim B");
        UUID batchId = newBatch(a, 1);
        UUID targetId = UUID.randomUUID();

        asTenantVoid(a, () -> linkRepository.saveAll(List.of(ImportRecordLink.of(
            a, batchId, "KolayVet", ImportEntityType.OWNER, "17", targetId, true, Map.of()))));

        Optional<ImportRecordLink> seenFromB = asTenant(b, () -> linkRepository.findByTarget(ImportEntityType.OWNER, targetId));
        Optional<ImportRecordLink> seenFromA = asTenant(a, () -> linkRepository.findByTarget(ImportEntityType.OWNER, targetId));

        assertThat(seenFromB).isEmpty();
        assertThat(seenFromA).isPresent();
    }
}
