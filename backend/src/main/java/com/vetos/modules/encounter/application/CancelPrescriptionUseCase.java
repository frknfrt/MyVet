package com.vetos.modules.encounter.application;

import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.exception.PrescriptionNotCancellableConflictException;
import com.vetos.modules.encounter.domain.exception.PrescriptionNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Aktif recete iptali (spec 2026-10-09 S5.6). TARBIL'de dusulmus stok iade edilmez; TARBIL'de iptal hekimde. */
@Service
@RequiredArgsConstructor
public class CancelPrescriptionUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final DomainEventPublisher eventPublisher;
    private final AuditPort auditPort;

    @Transactional
    public void execute(UUID prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new PrescriptionNotFoundException(prescriptionId));
        if (!prescription.cancel()) {
            throw new PrescriptionNotCancellableConflictException();
        }
        prescriptionRepository.save(prescription);
        eventPublisher.publish(new PrescriptionCancelledEvent(prescriptionId));
        auditPort.record(AuditRecord.of(AuditAction.PRESCRIPTION_CANCELLED, "PRESCRIPTION", prescriptionId,
            AuditLabels.shortId(prescriptionId), null));
    }
}
