package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterInventoryUsageRepository;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.EncounterStatus;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.PrescriptionStatus;
import com.vetos.modules.encounter.domain.VaccinationRecordRepository;
import com.vetos.modules.encounter.domain.exception.EncounterInvalidTransitionException;
import com.vetos.modules.encounter.domain.exception.EncounterNotCancellableConflictException;
import com.vetos.modules.encounter.domain.exception.EncounterNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Taslak muayeneyi iptal eder (yanlis hasta acildi, muayeneden vazgecildi). Kayit silinmez,
 * CANCELLED olarak saklanir ve denetim kaydina yazilir. Stok, recete veya asi baglanmissa
 * iptal edilmez: bunlar stoku ve TARBIL'i etkiler, once kendi ekranlarindan geri alinmalidir.
 */
@Service
@RequiredArgsConstructor
public class CancelEncounterUseCase {

    private final EncounterRepository encounterRepository;
    private final EncounterInventoryUsageRepository inventoryUsageRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final VaccinationRecordRepository vaccinationRecordRepository;
    private final AuditPort auditPort;

    @Transactional
    public void execute(UUID encounterId) {
        Encounter encounter = encounterRepository.findById(encounterId)
            .orElseThrow(() -> new EncounterNotFoundException(encounterId));
        if (encounter.getStatus() != EncounterStatus.DRAFT) {
            throw new EncounterInvalidTransitionException(encounter.getStatus(), EncounterStatus.CANCELLED);
        }
        if (!inventoryUsageRepository.findByEncounterId(encounterId).isEmpty()) {
            throw new EncounterNotCancellableConflictException("kullanılan malzeme eklenmiş, önce malzemeleri kaldırın");
        }
        boolean activePrescription = prescriptionRepository.findByEncounterId(encounterId).stream()
            .anyMatch(p -> p.getStatus() != PrescriptionStatus.CANCELLED);
        if (activePrescription) {
            throw new EncounterNotCancellableConflictException("bu muayeneye bağlı reçete var, önce reçeteyi iptal edin");
        }
        if (vaccinationRecordRepository.existsByEncounterId(encounterId)) {
            throw new EncounterNotCancellableConflictException("bu muayeneye bağlı aşı kaydı var");
        }
        encounter.cancel();
        encounterRepository.save(encounter);
        auditPort.record(AuditRecord.of(AuditAction.ENCOUNTER_CANCELLED, "ENCOUNTER", encounterId,
            AuditLabels.shortId(encounterId), null));
    }
}
