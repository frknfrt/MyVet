package com.vetos;

import com.vetos.modules.patient.domain.ConsentRecord;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import com.vetos.modules.patient.domain.ConsentType;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.Patient;
import com.vetos.modules.patient.domain.PatientRepository;
import com.vetos.modules.patient.domain.Sex;
import com.vetos.modules.patient.domain.SpeciesRepository;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bu turun GERCEK kabul kriteri (spec S8): Hibernate @TenantId
 * konfigurasyonunun prosa olarak degil, gercek davranis olarak dogru
 * oldugunu kanitlar. Her entity icin: B kiracisinin kaydi, A kiracisi
 * context'indeyken GORUNMEMELI.
 *
 * CALISTIRMA SARTI -- CANLI POSTGRES: Bu kod tabaninda baska hicbir
 * @SpringBootTest/@DataJpaTest yok (tum testler saf Mockito). Bu sinif,
 * gercek bir veritabani baglantisi ve Hibernate Session'i gerektirdigi
 * icin o kuralin BILINCLI ve DAR bir istisnasidir. Lokal calistirmadan
 * once:  cd backend && docker-compose up -d
 * Baglanti bilgisi application.yml'deki varsayilanlardan gelir
 * (localhost:5433/myvet). CI ayni ayarlarla bir Postgres servis
 * konteyneri saglar (.github/workflows/ci.yml). Testcontainers
 * BILINCLI olarak KULLANILMIYOR.
 *
 * Test metotlari @Transactional DEGIL (bkz. TenantScopedTestSupport
 * javadoc'u) -- olusturulan satirlar @AfterEach icinde elle silinir.
 */
@SpringBootTest
class TenantIsolationTest extends TenantScopedTestSupport {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SpeciesRepository speciesRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ConsentRecordRepository consentRecordRepository;

    @PersistenceContext private EntityManager entityManager;

    private final List<UUID> createdTenantIds = new ArrayList<>();

    /** Bir kiraci ve onun altindaki ortak ust kayitlar. */
    private record TenantFixture(UUID tenantId, UUID branchId, UUID ownerId) {}

    private TenantFixture createTenantFixture(String label) {
        UUID tenantId = inRootSession(() -> tenantRepository.save(Tenant.register(label, null)).getId());
        createdTenantIds.add(tenantId);
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, label + " Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> ownerRepository.save(
            Owner.register(tenantId, label + " Sahip", "05551234567", null, null)
        ).getId());
        return new TenantFixture(tenantId, branchId, ownerId);
    }

    private UUID anySpeciesId() {
        // species, kiraci-bagimsiz paylasimli referans verisi (V2'de tohumlanir).
        return inRootSession(() -> speciesRepository.findAll().get(0).getId());
    }

    @AfterEach
    void purgeCreatedTenants() {
        createdTenantIds.forEach(this::purgeTenant);
        createdTenantIds.clear();
    }

    /**
     * Test satirlarini FK sirasina gore siler. Native SQL, @TenantId
     * filtresinin disindadir (spec S7) ve root Session'da calisir --
     * TenantContext kurulu degil. Tablolarin bir kismi ilerideki
     * task'lara kadar bos kalir; bos tabloya DELETE zararsizdir.
     */
    private void purgeTenant(UUID tenantId) {
        inRootSessionVoid(() -> {
            String b = "(SELECT id FROM branches WHERE tenant_id = :t)";
            String o = "(SELECT id FROM owners WHERE tenant_id = :t)";
            String p = "(SELECT id FROM patients WHERE owner_id IN " + o + ")";
            String e = "(SELECT id FROM encounters WHERE patient_id IN " + p + ")";
            String ii = "(SELECT id FROM inventory_items WHERE branch_id IN " + b + ")";
            String inv = "(SELECT id FROM invoices WHERE tenant_id = :t)";
            String lr = "(SELECT id FROM lab_results WHERE tenant_id = :t)";
            String ir = "(SELECT id FROM imaging_records WHERE tenant_id = :t)";
            String aj = "(SELECT id FROM ai_jobs WHERE tenant_id = :t)";
            String pr = "(SELECT id FROM prescriptions WHERE patient_id IN " + p + ")";

            List<String> statements = List.of(
                "DELETE FROM prescription_items WHERE prescription_id IN " + pr,
                "DELETE FROM prescriptions WHERE patient_id IN " + p,
                "DELETE FROM encounter_inventory_usage WHERE encounter_id IN " + e,
                "DELETE FROM stock_movements WHERE inventory_item_id IN " + ii,
                "DELETE FROM ai_job_decisions WHERE ai_job_id IN " + aj,
                "DELETE FROM ai_jobs WHERE tenant_id = :t",
                "DELETE FROM lab_result_items WHERE lab_result_id IN " + lr,
                "DELETE FROM lab_result_files WHERE lab_result_id IN " + lr,
                "DELETE FROM lab_results WHERE tenant_id = :t",
                "DELETE FROM imaging_record_files WHERE imaging_record_id IN " + ir,
                "DELETE FROM imaging_records WHERE tenant_id = :t",
                "DELETE FROM payments WHERE invoice_id IN " + inv,
                "DELETE FROM invoice_lines WHERE invoice_id IN " + inv,
                "DELETE FROM invoices WHERE tenant_id = :t",
                "DELETE FROM encounters WHERE patient_id IN " + p,
                "DELETE FROM patients WHERE owner_id IN " + o,
                "DELETE FROM consent_records WHERE owner_id IN " + o,
                "DELETE FROM owners WHERE tenant_id = :t",
                "DELETE FROM cash_register_sessions WHERE branch_id IN " + b,
                "DELETE FROM inventory_items WHERE branch_id IN " + b,
                "DELETE FROM staff_users WHERE branch_id IN " + b,
                "DELETE FROM branches WHERE tenant_id = :t",
                "DELETE FROM tenants WHERE id = :t"
            );
            statements.forEach(sql ->
                entityManager.createNativeQuery(sql).setParameter("t", tenantId).executeUpdate()
            );
        });
    }

    // --- Task 1 ---

    @Test
    void patient_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID speciesId = anySpeciesId();

        UUID patientBId = asTenant(b.tenantId(), () -> patientRepository.save(
            Patient.register(b.tenantId(), b.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)
        ).getId());

        Optional<Patient> seenFromTenantA = asTenant(a.tenantId(), () -> patientRepository.findById(patientBId));

        assertThat(seenFromTenantA).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> patientRepository.findById(patientBId))).isPresent();
    }

    @Test
    void consentRecord_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        UUID consentBId = asTenant(b.tenantId(), () -> consentRecordRepository.save(
            ConsentRecord.grant(b.tenantId(), b.ownerId(), ConsentType.KVKK_ACIK_RIZA, "127.0.0.1")
        ).getId());

        Optional<ConsentRecord> seenFromTenantA = asTenant(a.tenantId(), () -> consentRecordRepository.findById(consentBId));

        assertThat(seenFromTenantA).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> consentRecordRepository.findById(consentBId))).isPresent();
    }

    @Test
    void rootSession_worksAndSeesAllTenants_whenNoTenantContext() {
        // Login (/api/v1/auth/login), /api/v1/public/**, platform admin,
        // @Scheduled isler ve @Async NotificationSendExecutor TenantContext
        // OLMADAN calisir. Hibernate, @TenantId'li EN AZ BIR entity varsa HER
        // Session acilisinda bir tenant identifier ister -- bu yuzden resolver
        // bu durumda ROOT_TENANT_ID doner ve isRoot() ile _tenantId filtresini
        // devre disi birakir. Bu test o davranisi kilitler: aksi halde
        // uygulamanin tamami kilitlenir (bkz. Global Constraints).
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID speciesId = anySpeciesId();
        UUID patientBId = asTenant(b.tenantId(), () -> patientRepository.save(
            Patient.register(b.tenantId(), b.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)
        ).getId());

        Optional<Patient> seenFromRoot = inRootSession(() -> patientRepository.findById(patientBId));

        assertThat(seenFromRoot).isPresent();
    }
}
