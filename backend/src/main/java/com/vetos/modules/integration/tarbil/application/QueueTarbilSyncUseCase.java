package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueTarbilSyncUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final TarbilSyncExecutor tarbilSyncExecutor;

    @Transactional
    public UUID execute(UUID patientId, TarbilSyncType syncType, String payload) {
        // Cagiranlar (PatientIdentificationUpdatedEventListener, VaccinationRecordedEventListener)
        // her zaman authenticate edilmis bir HTTP istegi icindeki senkron @EventListener'lardan
        // cagriliyor -- TenantContext zaten kurulu.
        TarbilSyncLog log = tarbilSyncLogRepository.save(TarbilSyncLog.queue(TenantContext.current(), patientId, syncType, payload));
        UUID logId = log.getId();

        // Cagiran kod (patient/encounter event listener'lari) genelde bir ust
        // @Transactional icinde calisiyor -- senkron denemesi, bu satir henuz
        // commit olmamis kaydi goremez diye transaction commit'ten SONRAYA
        // erteleniyor.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarbilSyncExecutor.attemptSync(logId);
                }
            });
        } else {
            tarbilSyncExecutor.attemptSync(logId);
        }

        return logId;
    }
}
