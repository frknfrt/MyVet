package com.vetos;

import com.vetos.modules.encounter.domain.*;
import com.vetos.modules.imaging.domain.ImagingHistoryEntry;
import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordLookupPort;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.lab.domain.*;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.SpeciesRepository;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI hasta geçmişi okuma portlarının gerçek Postgres'teki davranışı (2026-10-25):
 * tarih aralığı veritabanında, en yeniden eskiye, üst sınır (limit) veritabanında, kalemler toplu okunur.
 * Kayıt tarihleri/durumları native UPDATE ile ayarlanır. Canlı docker-compose Postgres gerekir.
 */
@SpringBootTest
class PatientHistoryQueriesTest extends TenantScopedTestSupport {

    @Autowired TenantRepository tenantRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired OwnerRepository ownerRepository;
    @Autowired StaffUserRepository staffUserRepository;
    @Autowired SpeciesRepository speciesRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired EncounterRepository encounterRepository;
    @Autowired PrescriptionRepository prescriptionRepository;
    @Autowired PrescriptionItemRepository prescriptionItemRepository;
    @Autowired DrugCatalogRepository drugCatalogRepository;
    @Autowired VaccinationRecordRepository vaccinationRecordRepository;
    @Autowired LabResultRepository labResultRepository;
    @Autowired LabResultItemRepository labResultItemRepository;
    @Autowired ImagingRecordRepository imagingRecordRepository;
    @Autowired EncounterLookupPort encounterLookupPort;
    @Autowired VaccinationLookupPort vaccinationLookupPort;
    @Autowired LabResultLookupPort labResultLookupPort;
    @Autowired ImagingRecordLookupPort imagingRecordLookupPort;
    @PersistenceContext EntityManager entityManager;

    private UUID tenantId;
    private UUID staffId;
    private UUID patientId;
    private UUID encounterId;

    /** Pencere: [2025-10-01, 2026-10-01] günleri; Instant olarak [2025-09-30T21:00Z, 2026-10-01T21:00Z). */
    static final LocalDate FROM_DAY = LocalDate.of(2025, 10, 1);
    static final LocalDate TO_DAY = LocalDate.of(2026, 10, 1);
    static final Instant FROM = Instant.parse("2025-09-30T21:00:00Z");
    static final Instant BEFORE = Instant.parse("2026-10-01T21:00:00Z");

    @BeforeEach
    void fixture() {
        tenantId = inRootSession(() -> tenantRepository.save(Tenant.register("Gecmis " + UUID.randomUUID(), null)).getId());
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, "Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> ownerRepository.save(Owner.register(tenantId, "Sahip", "05551234567", null, null)).getId());
        staffId = asTenant(tenantId, () -> staffUserRepository.save(StaffUser.register(
            tenantId, branchId, "Dr. Test", "gecmis-" + UUID.randomUUID() + "@test.local", "hash", StaffRole.VET)).getId());
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        patientId = asTenant(tenantId, () -> patientRepository.save(
            Patient.register(tenantId, ownerId, speciesId, null, "Karabaş", Sex.MALE, null)).getId());
        encounterId = asTenant(tenantId, () -> encounterRepository.save(Encounter.start(tenantId, patientId, staffId, null, null)).getId());
    }

    @AfterEach
    void purge() {
        inRootSessionVoid(() -> List.of(
            "DELETE FROM prescription_items WHERE tenant_id = :t",
            "DELETE FROM prescriptions WHERE tenant_id = :t",
            "DELETE FROM vaccination_records WHERE tenant_id = :t",
            "DELETE FROM lab_result_items WHERE tenant_id = :t",
            "DELETE FROM lab_results WHERE tenant_id = :t",
            "DELETE FROM imaging_records WHERE tenant_id = :t",
            "DELETE FROM encounters WHERE tenant_id = :t",
            "DELETE FROM patients WHERE owner_id IN (SELECT id FROM owners WHERE tenant_id = :t)",
            "DELETE FROM owners WHERE tenant_id = :t",
            "DELETE FROM subscriptions WHERE tenant_id = :t",
            "DELETE FROM staff_users WHERE tenant_id = :t",
            "DELETE FROM branches WHERE tenant_id = :t",
            "DELETE FROM tenants WHERE id = :t"
        ).forEach(sql -> entityManager.createNativeQuery(sql).setParameter("t", tenantId).executeUpdate()));
    }

    private void sql(String statement, Object... params) {
        inRootSessionVoid(() -> {
            var q = entityManager.createNativeQuery(statement);
            for (int i = 0; i < params.length; i += 2) q.setParameter((String) params[i], params[i + 1]);
            q.executeUpdate();
        });
    }

    // --- lab ---

    private UUID lab(String name, LabResultStatus status, Instant requestedAt, Instant resultedAt) {
        UUID id = asTenant(tenantId, () -> labResultRepository.save(LabResult.request(tenantId, patientId, staffId, name, null)).getId());
        sql("UPDATE lab_results SET status = :s, requested_at = :rq, resulted_at = :rs WHERE id = :id",
            "s", status.name(), "rq", requestedAt, "rs", resultedAt, "id", id);
        return id;
    }

    @Test
    void labs_windowStatusOrderLimitAndItems() {
        lab("Eski", LabResultStatus.COMPLETED, Instant.parse("2025-09-01T09:00:00Z"), Instant.parse("2025-09-29T09:00:00Z"));
        lab("Bekleyen", LabResultStatus.PENDING, Instant.parse("2026-05-01T09:00:00Z"), null);
        lab("SonraIstendi", LabResultStatus.COMPLETED, Instant.parse("2026-10-02T09:00:00Z"), Instant.parse("2026-10-02T12:00:00Z"));
        // Muayene günü istendi, sonucu ertesi gün geldi: dahil.
        UUID sameDay = lab("BugunIstendi", LabResultStatus.COMPLETED, Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-02T10:00:00Z"));
        lab("SonucsuzTarih", LabResultStatus.COMPLETED, Instant.parse("2026-03-01T09:00:00Z"), null);
        lab("EnEski", LabResultStatus.COMPLETED, Instant.parse("2025-10-05T09:00:00Z"), Instant.parse("2025-10-06T09:00:00Z"));
        asTenantVoid(tenantId, () -> labResultItemRepository.save(
            LabResultItem.create(tenantId, sameDay, "BUN", "45", "mg/dL", "7-27", LabValueFlag.HIGH)));

        List<LabResultHistoryEntry> all = asTenant(tenantId, () -> labResultLookupPort.findCompletedBetween(patientId, FROM, BEFORE, 10));
        assertThat(all).extracting(LabResultHistoryEntry::testName).containsExactly("BugunIstendi", "SonucsuzTarih", "EnEski");
        assertThat(all.get(0).values()).containsExactly(new LabResultHistoryEntry.Value("BUN", "45", "mg/dL", "7-27", LabValueFlag.HIGH));
        assertThat(all.get(1).date()).isEqualTo(Instant.parse("2026-03-01T09:00:00Z"));

        List<LabResultHistoryEntry> limited = asTenant(tenantId, () -> labResultLookupPort.findCompletedBetween(patientId, FROM, BEFORE, 2));
        assertThat(limited).extracting(LabResultHistoryEntry::testName).containsExactly("BugunIstendi", "SonucsuzTarih");
    }

    // --- görüntüleme ---

    private void imaging(String region, String status, Instant requestedAt, Instant resultedAt) {
        UUID id = asTenant(tenantId, () -> imagingRecordRepository.save(
            ImagingRecord.request(tenantId, patientId, staffId, ImagingModality.XRAY, region, null)).getId());
        sql("UPDATE imaging_records SET status = :s, requested_at = :rq, resulted_at = :rs, findings = :f WHERE id = :id",
            "s", status, "rq", requestedAt, "rs", resultedAt, "f", "bulgu " + region, "id", id);
    }

    @Test
    void imaging_windowStatusOrderAndLimit() {
        imaging("Eski", "COMPLETED", Instant.parse("2025-09-01T09:00:00Z"), Instant.parse("2025-09-02T09:00:00Z"));
        imaging("Bekleyen", "PENDING", Instant.parse("2026-05-01T09:00:00Z"), null);
        imaging("Sonra", "COMPLETED", Instant.parse("2026-10-03T09:00:00Z"), Instant.parse("2026-10-03T10:00:00Z"));
        imaging("Toraks", "COMPLETED", Instant.parse("2026-06-01T09:00:00Z"), Instant.parse("2026-06-01T10:00:00Z"));
        imaging("Karin", "COMPLETED", Instant.parse("2026-01-01T09:00:00Z"), Instant.parse("2026-01-01T10:00:00Z"));

        List<ImagingHistoryEntry> all = asTenant(tenantId, () -> imagingRecordLookupPort.findCompletedBetween(patientId, FROM, BEFORE, 10));
        assertThat(all).extracting(ImagingHistoryEntry::bodyRegion).containsExactly("Toraks", "Karin");
        assertThat(all.get(0).findings()).isEqualTo("bulgu Toraks");

        assertThat(asTenant(tenantId, () -> imagingRecordLookupPort.findCompletedBetween(patientId, FROM, BEFORE, 1)))
            .extracting(ImagingHistoryEntry::bodyRegion).containsExactly("Toraks");
    }

    // --- reçete ---

    private UUID prescription(LocalDate issued, String status, UUID drugId, String dosage) {
        UUID id = asTenant(tenantId, () -> prescriptionRepository.save(
            Prescription.issue(tenantId, patientId, encounterId, staffId, false)).getId());
        asTenantVoid(tenantId, () -> prescriptionItemRepository.save(
            PrescriptionItem.add(tenantId, id, drugId, dosage, "günde 1", 5, DrugRoute.ORAL)));
        sql("UPDATE prescriptions SET issued_date = :d, status = :s WHERE id = :id", "d", issued, "s", status, "id", id);
        return id;
    }

    @Test
    void prescriptions_windowStatusOrderLimitAndCatalogNames() {
        DrugCatalog drug = inRootSession(() -> drugCatalogRepository.findAll().get(0));
        prescription(LocalDate.of(2025, 9, 30), "FULFILLED", drug.getId(), "eski");
        prescription(LocalDate.of(2026, 5, 1), "CANCELLED", drug.getId(), "iptal");
        prescription(LocalDate.of(2026, 10, 2), "ACTIVE", drug.getId(), "sonra");
        prescription(TO_DAY, "ACTIVE", drug.getId(), "bugun");
        prescription(LocalDate.of(2026, 2, 1), "FULFILLED", drug.getId(), "subat");
        prescription(FROM_DAY, "FULFILLED", drug.getId(), "ilk gun");

        List<PrescriptionHistoryEntry> all = asTenant(tenantId, () -> encounterLookupPort.findPrescriptionHistory(patientId, FROM_DAY, TO_DAY, 10));
        assertThat(all).extracting(e -> e.items().get(0).dosage()).containsExactly("bugun", "subat", "ilk gun");
        assertThat(all.get(0).items().get(0).drugName()).isEqualTo(drug.getName());
        assertThat(all.get(0).encounterId()).isEqualTo(encounterId);

        assertThat(asTenant(tenantId, () -> encounterLookupPort.findPrescriptionHistory(patientId, FROM_DAY, TO_DAY, 2)))
            .extracting(e -> e.items().get(0).dosage()).containsExactly("bugun", "subat");
    }

    // --- aşı ---

    private void vaccine(String name, LocalDate date, VaccinationStatus status) {
        asTenantVoid(tenantId, () -> vaccinationRecordRepository.save(VaccinationRecord.record(
            tenantId, patientId, null, name, null, date, null, staffId, status, null)));
    }

    @Test
    void vaccinations_windowStatusOrderAndLimit() {
        vaccine("Eski", LocalDate.of(2025, 9, 30), VaccinationStatus.ADMINISTERED);
        vaccine("Planli", LocalDate.of(2026, 5, 1), VaccinationStatus.SCHEDULED);
        vaccine("Sonra", LocalDate.of(2026, 10, 2), VaccinationStatus.ADMINISTERED);
        vaccine("Kuduz", TO_DAY, VaccinationStatus.ADMINISTERED);
        vaccine("Karma", FROM_DAY, VaccinationStatus.ADMINISTERED);

        assertThat(asTenant(tenantId, () -> vaccinationLookupPort.findAdministeredBetween(patientId, FROM_DAY, TO_DAY, 10)))
            .extracting(VaccinationHistoryEntry::vaccineName).containsExactly("Kuduz", "Karma");
        assertThat(asTenant(tenantId, () -> vaccinationLookupPort.findAdministeredBetween(patientId, FROM_DAY, TO_DAY, 1)))
            .extracting(VaccinationHistoryEntry::vaccineName).containsExactly("Kuduz");
    }
}
