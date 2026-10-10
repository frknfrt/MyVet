package com.vetos.modules.integration.tarbil.infrastructure.event;

import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.integration.tarbil.application.QueueTarbilSyncUseCase;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import com.vetos.modules.integration.tarbil.domain.exception.InvalidTarbilDiseaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Stoktan verilen kalemli recete TARBIL'e aktarilmak uzere kuyruga girer; iptalde bekleyen aktarim reddedilir. */
@Component
@RequiredArgsConstructor
class PrescriptionTarbilEventListener {

    static final String CANCELLED_REASON = "Reçete iptal edildi";

    private final QueueTarbilSyncUseCase queueTarbilSyncUseCase;
    private final TarbilDiseaseRepository diseaseRepository;

    @EventListener
    void onIssued(PrescriptionIssuedEvent event) {
        if (event.tarbilDiseaseId() != null && diseaseRepository.findById(event.tarbilDiseaseId()).isEmpty()) {
            throw new InvalidTarbilDiseaseException();
        }
        if (!event.stockItems().isEmpty()) {
            queueTarbilSyncUseCase.queuePrescription(event.prescriptionId(), event.patientId(), event.tarbilDiseaseId());
        }
    }

    @EventListener
    void onCancelled(PrescriptionCancelledEvent event) {
        queueTarbilSyncUseCase.dismissPrescription(event.prescriptionId(), CANCELLED_REASON);
    }
}
