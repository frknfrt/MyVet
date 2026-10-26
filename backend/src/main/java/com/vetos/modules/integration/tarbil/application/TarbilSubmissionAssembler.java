package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSubmission;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.VaccineKeyNormalizer;
import com.vetos.modules.inventory.domain.InventoryItemLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import com.vetos.modules.integration.tarbil.domain.TarbilDisease;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockDeductionPort;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** TarbilSubmission + asi + hasta + esletirmeleri tek gorunumde birlestirir. Asi iptal/silinmisse bos doner. */
@Component
@RequiredArgsConstructor
class TarbilSubmissionAssembler {

    private final VaccinationLookupPort vaccinationLookupPort;
    private final PatientLookupPort patientLookupPort;
    private final TarbilValueMappingRepository mappingRepository;
    private final InventoryItemLookupPort inventoryItemLookupPort;
    private final TarbilStockDeductionPort stockDeductionPort;
    private final TarbilDiseaseRepository diseaseRepository;

    /** Eklenti ve web ekrani ayni kurali kullansin: ayni kiracida, iptal edilmemis asi. Asi disi satirlar burada yok sayilir. */
    Optional<VaccinationTarbilView> liveVaccination(TarbilSubmission log) {
        if (log.getDocumentType() != TarbilDocumentType.VACCINATION) {
            return Optional.empty();
        }
        return vaccinationLookupPort.findForTarbil(log.getSourceId())
            .filter(v -> v.tenantId().equals(log.getTenantId()))
            .filter(v -> v.status() != VaccinationStatus.CANCELLED);
    }

    /** Bekleyen satir ancak asisi canliysa "bekliyor" sayilir; gonderilmis/bildirilmeyecek satirlar gecmis olarak kalir. */
    boolean isVisible(TarbilSubmission log) {
        if (log.getStatus() != TarbilSyncStatus.PENDING) {
            return true;
        }
        if (log.getDocumentType() == TarbilDocumentType.PRESCRIPTION) {
            return !pendingLines(log).isEmpty();
        }
        return liveVaccination(log).isPresent();
    }

    /** Stoktan dusen recetenin TARBIL dusumu bekleyen satirlari (spec 2026-10-09 S7). */
    private List<PendingDeductionLine> pendingLines(TarbilSubmission log) {
        StockReferenceType ref = log.sourceKindOrDefault().stockReference();
        if (log.getDocumentType() != TarbilDocumentType.PRESCRIPTION || ref == null) {
            return List.of();
        }
        return stockDeductionPort.listForReference(ref, log.getSourceId()).stream()
            .filter(l -> l.status() == PendingDeductionStatus.PENDING)
            .toList();
    }

    Optional<TarbilSubmissionView> assemble(TarbilSubmission log) {
        if (log.getDocumentType() == TarbilDocumentType.PRESCRIPTION) {
            return isVisible(log) ? Optional.of(assemblePrescription(log)) : Optional.empty();
        }
        Optional<VaccinationTarbilView> vaccination = liveVaccination(log);
        if (vaccination.isEmpty()) {
            return Optional.empty();
        }
        VaccinationTarbilView v = vaccination.get();
        Optional<PatientTarbilProfile> patient = patientLookupPort.findTarbilProfile(log.getPatientId());
        String vaccineKey = VaccineKeyNormalizer.normalize(v.vaccineName());
        String vaccineMapping = mappingRepository
            .findByTenantIdAndKindAndVetlyKey(log.getTenantId(), TarbilMappingKind.VACCINE, vaccineKey)
            .map(TarbilValueMapping::getTarbilFields).orElse(null);
        String speciesMapping = patient.map(PatientTarbilProfile::speciesId)
            .flatMap(sid -> mappingRepository.findByTenantIdAndKindAndVetlyKey(log.getTenantId(), TarbilMappingKind.SPECIES, sid.toString()))
            .map(TarbilValueMapping::getTarbilFields).orElse(null);
        return Optional.of(new TarbilSubmissionView(
            log.getId(), log.getSourceId(), log.getStatus(),
            patient.map(PatientTarbilProfile::name).orElse("—"),
            patient.map(PatientTarbilProfile::microchipNumber).orElse(null),
            patient.map(PatientTarbilProfile::passportNumber).orElse(null),
            patient.map(PatientTarbilProfile::speciesId).orElse(null),
            patient.map(PatientTarbilProfile::speciesName).orElse(null),
            patient.map(PatientTarbilProfile::breedName).orElse(null),
            patient.map(PatientTarbilProfile::sex).map(Enum::name).orElse(null),
            patient.map(PatientTarbilProfile::birthDate).orElse(null),
            v.vaccineName(), v.lotNumber(), inventoryItemLookupPort.findTarbilProductName(v.inventoryItemId()).orElse(null),
            v.administeredDate(),
            log.getSubmittedAt(), log.getConfirmationMethod(), log.getTarbilReference(),
            vaccineKey, vaccineMapping, speciesMapping, log.getDocumentType(), null,
            v.inventoryItemId() == null ? null : inventoryItemLookupPort.findVaccineStockSource(v.inventoryItemId()).orElse("WHOLE")
        ));
    }

    private TarbilSubmissionView assemblePrescription(TarbilSubmission log) {
        Optional<PatientTarbilProfile> patient = Optional.ofNullable(log.getPatientId()).flatMap(patientLookupPort::findTarbilProfile);
        String speciesMapping = patient.map(PatientTarbilProfile::speciesId)
            .flatMap(sid -> mappingRepository.findByTenantIdAndKindAndVetlyKey(log.getTenantId(), TarbilMappingKind.SPECIES, sid.toString()))
            .map(TarbilValueMapping::getTarbilFields).orElse(null);
        var payload = new TarbilSubmissionView.PrescriptionPayload(
            log.sourceKindOrDefault().name(), log.getTarbilDiseaseId(),
            log.getTarbilDiseaseId() == null ? null : diseaseRepository.findById(log.getTarbilDiseaseId()).map(TarbilDisease::getName).orElse(null),
            LocalDate.ofInstant(log.getQueuedAt(), ISTANBUL),
            pendingLines(log).stream()
                .map(TarbilSubmissionAssembler::planLine)
                .toList());
        return new TarbilSubmissionView(
            log.getId(), log.getSourceId(), log.getStatus(),
            patient.map(PatientTarbilProfile::name).orElse("—"),
            patient.map(PatientTarbilProfile::microchipNumber).orElse(null),
            patient.map(PatientTarbilProfile::passportNumber).orElse(null),
            patient.map(PatientTarbilProfile::speciesId).orElse(null),
            patient.map(PatientTarbilProfile::speciesName).orElse(null),
            patient.map(PatientTarbilProfile::breedName).orElse(null),
            patient.map(PatientTarbilProfile::sex).map(Enum::name).orElse(null),
            patient.map(PatientTarbilProfile::birthDate).orElse(null),
            "Reçete", null, null, null,
            log.getSubmittedAt(), log.getConfirmationMethod(), log.getTarbilReference(),
            "", null, speciesMapping, log.getDocumentType(), payload, null);
    }

    /**
     * Eklentiye plan (spec 2026-10-24): ADET kalem bugunku gibi adetle; paketli kalemde TARBIL yalniz Miktar alir (Adet kapali,
     * 2026-10-24 canli) - acik miktar yetiyorsa Acik Stok, yoksa Butun Stok listesinden secilir.
     */
    static TarbilSubmissionView.PrescriptionPayload.Line planLine(com.vetos.modules.inventory.domain.PendingDeductionLine l) {
        boolean packaged = l.unit() != null && !"ADET".equals(l.unit());
        if (!packaged) {
            // Kesirli ADET miktari yukari yuvarlanir (degerlendirme madde 7; C1 kurali ile ayni).
            java.math.BigDecimal whole = l.quantity().setScale(0, java.math.RoundingMode.CEILING);
            return new TarbilSubmissionView.PrescriptionPayload.Line(l.itemName(), l.tarbilProductName(), l.lotNumber(),
                whole.intValueExact(), whole, "ADET", "WHOLE");
        }
        boolean open = l.plannedOpenedQuantity() != null && l.plannedOpenedQuantity().compareTo(l.quantity()) >= 0;
        return new TarbilSubmissionView.PrescriptionPayload.Line(l.itemName(), l.tarbilProductName(), l.lotNumber(),
            0, l.quantity(), l.unit(), open ? "OPEN" : "WHOLE");
    }

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
}
