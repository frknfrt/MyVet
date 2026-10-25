package com.vetos.modules.encounter.infrastructure.persistence;

import com.vetos.modules.encounter.domain.VaccinationHistoryEntry;
import com.vetos.modules.encounter.domain.VaccinationLookupPort;
import com.vetos.modules.encounter.domain.VaccinationReminderCandidate;
import com.vetos.modules.encounter.domain.VaccinationStatus;
import com.vetos.modules.encounter.domain.VaccinationTarbilView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

@Component
@RequiredArgsConstructor
class VaccinationLookupAdapter implements VaccinationLookupPort {

    private final VaccinationRecordJpaRepository jpaRepository;

    @Override
    public List<VaccinationReminderCandidate> findDueForReminder(UUID tenantId, LocalDate dueDate) {
        return jpaRepository.findByTenantIdAndNextDueDateAndStatusNot(tenantId, dueDate, VaccinationStatus.CANCELLED).stream()
            .map(r -> new VaccinationReminderCandidate(r.getId(), r.getPatientId(), r.getVaccineName(), r.getNextDueDate()))
            .toList();
    }

    @Override
    public Optional<VaccinationTarbilView> findForTarbil(UUID vaccinationRecordId) {
        return jpaRepository.findById(vaccinationRecordId).map(r -> new VaccinationTarbilView(
            r.getId(), r.getTenantId(), r.getPatientId(), r.getVaccineName(), r.getLotNumber(),
            r.getAdministeredDate(), r.getStatus(), r.getInventoryItemId()
        ));
    }

    @Override
    public List<VaccinationHistoryEntry> findAdministeredBetween(UUID patientId, LocalDate from, LocalDate to, int limit) {
        return jpaRepository.findByPatientIdAndStatusAndAdministeredDateBetweenOrderByAdministeredDateDesc(
                patientId, VaccinationStatus.ADMINISTERED, from, to, PageRequest.of(0, limit)).stream()
            .map(r -> new VaccinationHistoryEntry(r.getVaccineName(), r.getAdministeredDate(), r.getNextDueDate()))
            .toList();
    }
}
