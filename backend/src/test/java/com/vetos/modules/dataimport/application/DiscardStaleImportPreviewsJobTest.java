package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatch;
import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.modules.dataimport.domain.ImportReport;
import com.vetos.platform.concurrency.AdvisoryLock;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscardStaleImportPreviewsJobTest {

    private static class FakeRepo implements ImportBatchRepository {
        final List<ImportBatch> all = new ArrayList<>();
        /** Kilitli yeniden okumada donecek guncel hal (aradaki eszamanli degisikligi taklit eder). */
        final java.util.Map<UUID, ImportBatch> current = new java.util.HashMap<>();
        public ImportBatch save(ImportBatch b) { return b; }
        public Optional<ImportBatch> findById(UUID id) { return Optional.empty(); }
        public Optional<ImportBatch> findByIdForUpdate(UUID id) {
            if (current.containsKey(id)) {
                return Optional.of(current.get(id));
            }
            return all.stream().filter(b -> id.equals(b.getId())).findFirst();
        }
        public List<ImportBatch> findByStatus(ImportBatchStatus s) {
            return all.stream().filter(b -> b.getStatus() == s).toList();
        }
        public List<ImportBatch> findByTenantIdOrderByCreatedAtDesc(UUID t) { return List.of(); }
        public List<String> findSourceSystems(UUID t) { return List.of(); }
        public List<UUID> findIdsByStatusAndCreatedAtBefore(ImportBatchStatus s, Instant before) {
            return all.stream().filter(b -> b.getStatus() == s && b.getCreatedAt().isBefore(before))
                .map(ImportBatch::getId).toList();
        }
    }

    private ImportBatch batch(Duration age, boolean committed) {
        ImportBatch b = ImportBatch.preview(UUID.randomUUID(), "KolayVet", "a.xlsx", new byte[]{1},
            new ImportReport(null, List.of(), List.of(), List.of(), List.of(), List.of()), 1, UUID.randomUUID());
        ReflectionTestUtils.setField(b, "createdAt", Instant.now().minus(age));
        ReflectionTestUtils.setField(b, "id", UUID.randomUUID());
        if (committed) {
            b.startCommit(List.of());
            b.markCommitted();
        }
        return b;
    }

    @Test
    void should_discardStalePreviewsOnly() {
        FakeRepo repo = new FakeRepo();
        ImportBatch stale = batch(Duration.ofDays(8), false);
        ImportBatch fresh = batch(Duration.ofDays(1), false);
        ImportBatch committed = batch(Duration.ofDays(8), true);
        repo.all.addAll(List.of(stale, fresh, committed));

        AdvisoryLock lock = mock(AdvisoryLock.class);
        when(lock.tryAcquire(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        new DiscardStaleImportPreviewsJob(repo, lock, new ImportTransactions(mock(org.springframework.transaction.PlatformTransactionManager.class))).run();

        assertThat(stale.getStatus()).isEqualTo(ImportBatchStatus.DISCARDED);
        assertThat(stale.getSourceFile()).isNull();
        assertThat(fresh.getStatus()).isEqualTo(ImportBatchStatus.PREVIEWED);
        assertThat(committed.getStatus()).isEqualTo(ImportBatchStatus.COMMITTED);
    }

    @Test
    void should_doNothing_whenLockNotAcquired() {
        FakeRepo repo = new FakeRepo();
        ImportBatch stale = batch(Duration.ofDays(8), false);
        repo.all.add(stale);
        AdvisoryLock lock = mock(AdvisoryLock.class);
        when(lock.tryAcquire(org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);

        new DiscardStaleImportPreviewsJob(repo, lock, new ImportTransactions(mock(org.springframework.transaction.PlatformTransactionManager.class))).run();

        assertThat(stale.getStatus()).isEqualTo(ImportBatchStatus.PREVIEWED);
    }

    @Test
    void should_skipCandidate_thatWasStartedMeanwhile() {
        FakeRepo repo = new FakeRepo();
        ImportBatch stale = batch(Duration.ofDays(8), false);
        repo.all.add(stale);
        // Aday listelendikten sonra admin aktarimi baslatti: kilitli okuma COMMITTING gorur.
        ImportBatch nowCommitting = batch(Duration.ofDays(8), false);
        nowCommitting.startCommit(List.of());
        repo.current.put(stale.getId(), nowCommitting);
        AdvisoryLock lock = mock(AdvisoryLock.class);
        when(lock.tryAcquire(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);

        new DiscardStaleImportPreviewsJob(repo, lock, new ImportTransactions(mock(org.springframework.transaction.PlatformTransactionManager.class))).run();

        assertThat(nowCommitting.getStatus()).isEqualTo(ImportBatchStatus.COMMITTING);
        assertThat(nowCommitting.getSourceFile()).isNotNull();
    }
}
