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
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
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
        return log.getStatus() != TarbilSyncStatus.PENDING || liveVaccination(log).isPresent();
    }

    Optional<TarbilSubmissionView> assemble(TarbilSubmission log) {
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
            patient.map(PatientTarbilProfile::speciesId).orElse(null),
            patient.map(PatientTarbilProfile::speciesName).orElse(null),
            patient.map(PatientTarbilProfile::breedName).orElse(null),
            patient.map(PatientTarbilProfile::sex).map(Enum::name).orElse(null),
            patient.map(PatientTarbilProfile::birthDate).orElse(null),
            v.vaccineName(), v.lotNumber(), v.administeredDate(),
            log.getSubmittedAt(), log.getConfirmationMethod(), log.getTarbilReference(),
            vaccineKey, vaccineMapping, speciesMapping, log.getDocumentType()
        ));
    }
}
