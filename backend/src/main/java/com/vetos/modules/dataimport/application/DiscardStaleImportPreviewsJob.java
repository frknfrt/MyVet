package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportBatchRepository;
import com.vetos.modules.dataimport.domain.ImportBatchStatus;
import com.vetos.platform.concurrency.AdvisoryLock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** KVKK: 7 gundur dokunulmayan onizlemeleri iptal eder (kaynak dosya NULL'lanir). */
@Component
@Slf4j
@RequiredArgsConstructor
public class DiscardStaleImportPreviewsJob {

    static final Duration MAX_AGE = Duration.ofDays(7);

    private static final long STALE_PREVIEW_LOCK_KEY = 7_301_010; // diger kilitlerden (7_301_001/002) farkli

    private final ImportBatchRepository batchRepository;
    private final AdvisoryLock advisoryLock;
    private final ImportTransactions transactions;

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void run() {
        if (!advisoryLock.tryAcquire(STALE_PREVIEW_LOCK_KEY)) {
            log.info("Eski önizleme temizleme kilidi başka bir instance'da -- atlanıyor");
            return;
        }
        // Yalnizca kimlikler: dis transaction'in persistence context'inde managed (ve sonradan eskiyebilecek)
        // ImportBatch tutulmaz. Kilit (pg_try_advisory_xact_lock) bu dis transaction boyunca surer.
        List<UUID> candidates = batchRepository.findIdsByStatusAndCreatedAtBefore(
            ImportBatchStatus.PREVIEWED, Instant.now().minus(MAX_AGE));
        int discarded = 0;
        for (UUID id : candidates) {
            if (discardIfStillPreviewed(id)) {
                discarded++;
            }
        }
        if (discarded > 0) {
            log.info("{} eski aktarım önizlemesi iptal edildi", discarded);
        }
    }

    /**
     * Tek parti, kendi TAZE (REQUIRES_NEW) transaction'inda: satir kilitlenerek okunur; bu arada aktarim
     * baslatildiysa (artik PREVIEWED degilse) dokunulmaz.
     *
     * @return parti iptal edildiyse true
     */
    public boolean discardIfStillPreviewed(UUID batchId) {
        return transactions.asRoot(() -> {
            var locked = batchRepository.findByIdForUpdate(batchId)
                .filter(b -> b.getStatus() == ImportBatchStatus.PREVIEWED);
            if (locked.isEmpty()) {
                return false;
            }
            locked.get().discard();
            batchRepository.save(locked.get());
            return true;
        });
    }
}
