package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMapping;
import com.vetos.modules.integration.tarbil.domain.TarbilValueMappingRepository;
import com.vetos.modules.integration.tarbil.domain.VaccineKeyNormalizer;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.patient.domain.PatientTarbilProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** TarbilSyncLog + asi + hasta + esletirmeleri tek gorunumde birlestirir. Asi iptal/silinmisse bos doner. */
@Component
@RequiredArgsConstructor
class TarbilSubmissionAssembler {

    private final VaccinationLookupPort vaccinationLookupPort;
    private final PatientLookupPort patientLookupPort;
    private final TarbilValueMappingRepository mappingRepository;

    Optional<TarbilSubmissionView> assemble(TarbilSyncLog log) {
        Optional<VaccinationTarbilView> vaccination = vaccinationLookupPort.findForTarbil(log.getVaccinationRecordId())
            .filter(v -> v.tenantId().equals(log.getTenantId()))
            .filter(v -> v.status() != VaccinationStatus.CANCELLED);
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
            log.getId(), log.getVaccinationRecordId(), log.getStatus(),
            patient.map(PatientTarbilProfile::name).orElse("—"),
            patient.map(PatientTarbilProfile::microchipNumber).orElse(null),
            patient.map(PatientTarbilProfile::speciesId).orElse(null),
            patient.map(PatientTarbilProfile::speciesName).orElse(null),
            patient.map(PatientTarbilProfile::breedName).orElse(null),
            patient.map(PatientTarbilProfile::sex).map(Enum::name).orElse(null),
            patient.map(PatientTarbilProfile::birthDate).orElse(null),
            v.vaccineName(), v.lotNumber(), v.administeredDate(),
            log.getSubmittedAt(), log.getConfirmationMethod(), log.getTarbilReference(),
            vaccineKey, vaccineMapping, speciesMapping
        ));
    }
}
