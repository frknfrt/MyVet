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
import com.vetos.modules.billing.domain.CashRegisterSession;
import com.vetos.modules.billing.domain.CashRegisterSessionRepository;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceLineSource;
import com.vetos.modules.billing.domain.Payment;
import com.vetos.modules.billing.domain.PaymentRepository;
import com.vetos.modules.billing.domain.PaymentMethod;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import com.vetos.modules.imaging.domain.ImagingModality;
import com.vetos.modules.imaging.domain.ImagingRecord;
import com.vetos.modules.imaging.domain.ImagingRecordFile;
import com.vetos.modules.imaging.domain.ImagingRecordFileRepository;
import com.vetos.modules.imaging.domain.ImagingRecordRepository;
import com.vetos.modules.lab.domain.LabResult;
import com.vetos.modules.lab.domain.LabResultFile;
import com.vetos.modules.lab.domain.LabResultFileRepository;
import com.vetos.modules.lab.domain.LabResultItem;
import com.vetos.modules.lab.domain.LabResultItemRepository;
import com.vetos.modules.lab.domain.LabResultRepository;
import com.vetos.modules.lab.domain.LabValueFlag;
import com.vetos.modules.ai.domain.AiJob;
import com.vetos.modules.ai.domain.AiJobDecision;
import com.vetos.modules.ai.domain.AiJobDecisionRepository;
import com.vetos.modules.ai.domain.AiJobRepository;
import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.encounter.domain.Encounter;
import com.vetos.modules.encounter.domain.EncounterRepository;
import com.vetos.modules.encounter.domain.Prescription;
import com.vetos.modules.encounter.domain.PrescriptionRepository;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired private StaffUserRepository staffUserRepository;
    @Autowired private InventoryItemRepository inventoryItemRepository;
    @Autowired private CashRegisterSessionRepository cashRegisterSessionRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private InvoiceLineRepository invoiceLineRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ImagingRecordRepository imagingRecordRepository;
    @Autowired private ImagingRecordFileRepository imagingRecordFileRepository;
    @Autowired private LabResultRepository labResultRepository;
    @Autowired private LabResultFileRepository labResultFileRepository;
    @Autowired private LabResultItemRepository labResultItemRepository;
    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private AiJobDecisionRepository aiJobDecisionRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private PrescriptionRepository prescriptionRepository;
    @Autowired private StockMovementRepository stockMovementRepository;

    @PersistenceContext private EntityManager entityManager;

    private final List<UUID> createdTenantIds = new ArrayList<>();

    /** Bir kiraci ve onun altindaki ortak ust kayitlar. */
    private record TenantFixture(UUID tenantId, UUID branchId, UUID ownerId, UUID staffUserId) {}

    private TenantFixture createTenantFixture(String label) {
        UUID tenantId = inRootSession(() -> tenantRepository.save(Tenant.register(label, null)).getId());
        createdTenantIds.add(tenantId);
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, label + " Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> ownerRepository.save(
            Owner.register(tenantId, label + " Sahip", "05551234567", null, null)
        ).getId());
        // staff_users.email GLOBAL unique -- her fixture benzersiz bir e-posta almali.
        UUID staffUserId = asTenant(tenantId, () -> staffUserRepository.save(StaffUser.register(
            tenantId, branchId, "Dr. " + label, "izolasyon-" + UUID.randomUUID() + "@test.local", "hash", StaffRole.VET
        )).getId());
        return new TenantFixture(tenantId, branchId, ownerId, staffUserId);
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

    @Test
    void patientPersist_rejectsExplicitTenantId_thatDiffersFromSessionTenant() {
        // Global Constraints'teki kritik kural: elle atanan @TenantId degeri,
        // session'in tenant kimliginden FARKLIYSA (ve root degilse) persist
        // sirasinda reddedilir. Task 2-8'in TUM cagri yeri tasarimi (factory
        // konvansiyonu: tenantId ilk parametre, cagiran her zaman kendi
        // TenantContext.current()'ini gecirir) bu garantiye dayanir -- eger
        // Hibernate bunu sessizce KABUL ETSEYDI, bir cagiranin yanlislikla
        // baska bir tenantId gecirmesi veriyi baska bir kiraciya sessizce
        // yazardi.
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID speciesId = anySpeciesId();

        // Gercek davranis (dogrulandi): Hibernate, flush sirasinda
        // org.hibernate.PropertyValueException firlatiyor, Spring Data JPA'nin
        // exception-translation katmani bunu DataIntegrityViolationException'a
        // ceviriyor -- mesaji Hibernate'in kendi kontrolunu birebir tasiyor.
        assertThatThrownBy(() -> asTenant(a.tenantId(), () -> patientRepository.save(
            Patient.register(b.tenantId(), b.ownerId(), speciesId, null, "Yanlis Kiraci", Sex.MALE, null)
        )))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
            .hasMessageContaining("assigned tenant id differs from current tenant id");
    }

    // --- Task 2 ---

    @Test
    void staffUser_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        assertThat(asTenant(a.tenantId(), () -> staffUserRepository.findById(b.staffUserId()))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> staffUserRepository.findById(b.staffUserId()))).isPresent();
    }

    @Test
    void staffUser_isVisibleToRootSession_soLoginKeepsWorking() {
        // LoginUseCase.findByEmail, tenant HENUZ BILINMEDEN calisir -- koprulme
        // imkansiz. Bu yuzden root Session'da StaffUser gorunur kalmali.
        TenantFixture b = createTenantFixture("Izolasyon B");
        String email = inRootSession(() -> staffUserRepository.findById(b.staffUserId()).orElseThrow().getEmail());

        assertThat(inRootSession(() -> staffUserRepository.findByEmail(email))).isPresent();
    }

    @Test
    void inventoryItem_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        UUID itemBId = asTenant(b.tenantId(), () -> inventoryItemRepository.save(InventoryItem.create(
            b.tenantId(), b.branchId(), "Mama", "Gida", null, 10, 1, null, null, BigDecimal.TEN
        )).getId());

        assertThat(asTenant(a.tenantId(), () -> inventoryItemRepository.findById(itemBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> inventoryItemRepository.findById(itemBId))).isPresent();
    }

    @Test
    void cashRegisterSession_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");

        UUID sessionBId = asTenant(b.tenantId(), () -> cashRegisterSessionRepository.save(
            CashRegisterSession.open(b.tenantId(), b.branchId(), b.staffUserId(), BigDecimal.valueOf(100), null)
        ).getId());

        assertThat(asTenant(a.tenantId(), () -> cashRegisterSessionRepository.findById(sessionBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> cashRegisterSessionRepository.findById(sessionBId))).isPresent();
    }

    // --- Task 3 ---

    private UUID createInvoice(TenantFixture f) {
        return asTenant(f.tenantId(), () -> invoiceRepository.save(
            Invoice.createDraft(f.tenantId(), f.branchId(), f.ownerId(), null, f.staffUserId())
        ).getId());
    }

    @Test
    void invoiceLine_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID invoiceBId = createInvoice(b);

        asTenantVoid(b.tenantId(), () -> invoiceLineRepository.save(InvoiceLine.create(
            b.tenantId(), invoiceBId, "Muayene", 1, BigDecimal.valueOf(500), null, null, InvoiceLineSource.MANUAL
        )));

        // InvoiceLineRepository'de findById yok -- mevcut turetilmis sorgu
        // (findByInvoiceId) uzerinden dogrulanir; @TenantId turetilmis
        // sorgulara da uygulanir.
        assertThat(asTenant(a.tenantId(), () -> invoiceLineRepository.findByInvoiceId(invoiceBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> invoiceLineRepository.findByInvoiceId(invoiceBId))).hasSize(1);
    }

    @Test
    void payment_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID invoiceBId = createInvoice(b);

        asTenantVoid(b.tenantId(), () -> paymentRepository.save(Payment.record(
            b.tenantId(), invoiceBId, PaymentMethod.CASH, BigDecimal.valueOf(500), null
        )));

        assertThat(asTenant(a.tenantId(), () -> paymentRepository.findByInvoiceId(invoiceBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> paymentRepository.findByInvoiceId(invoiceBId))).hasSize(1);
    }

    // --- Task 4 ---

    private UUID createPatient(TenantFixture f) {
        UUID speciesId = anySpeciesId();
        return asTenant(f.tenantId(), () -> patientRepository.save(
            Patient.register(f.tenantId(), f.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)
        ).getId());
    }

    private UUID createEncounter(TenantFixture f, UUID patientId) {
        return asTenant(f.tenantId(), () -> encounterRepository.save(
            Encounter.start(f.tenantId(), patientId, f.staffUserId(), null, null)
        ).getId());
    }

    @Test
    void imagingRecordFile_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);

        UUID fileBId = asTenant(b.tenantId(), () -> {
            ImagingRecord record = imagingRecordRepository.save(ImagingRecord.request(
                b.tenantId(), patientBId, b.staffUserId(), ImagingModality.XRAY, "Toraks", null
            ));
            return imagingRecordFileRepository.save(ImagingRecordFile.create(
                b.tenantId(), record.getId(), "film.png", "image/png", new byte[] {1, 2, 3}
            )).getId();
        });

        assertThat(asTenant(a.tenantId(), () -> imagingRecordFileRepository.findById(fileBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> imagingRecordFileRepository.findById(fileBId))).isPresent();
    }

    @Test
    void labResultFile_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);

        UUID fileBId = asTenant(b.tenantId(), () -> {
            LabResult result = labResultRepository.save(LabResult.request(
                b.tenantId(), patientBId, b.staffUserId(), "Hemogram", null
            ));
            return labResultFileRepository.save(LabResultFile.create(
                b.tenantId(), result.getId(), "sonuc.pdf", "application/pdf", new byte[] {1, 2, 3}
            )).getId();
        });

        assertThat(asTenant(a.tenantId(), () -> labResultFileRepository.findById(fileBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> labResultFileRepository.findById(fileBId))).isPresent();
    }

    @Test
    void labResultItem_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);

        UUID labResultBId = asTenant(b.tenantId(), () -> {
            LabResult result = labResultRepository.save(LabResult.request(
                b.tenantId(), patientBId, b.staffUserId(), "Hemogram", null
            ));
            labResultItemRepository.save(LabResultItem.create(
                b.tenantId(), result.getId(), "WBC", "12.3", "10^3/uL", "6-17", LabValueFlag.NORMAL
            ));
            return result.getId();
        });

        assertThat(asTenant(a.tenantId(), () -> labResultItemRepository.findByLabResultId(labResultBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> labResultItemRepository.findByLabResultId(labResultBId))).hasSize(1);
    }

    @Test
    void aiJobDecision_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);
        UUID encounterBId = createEncounter(b, patientBId);

        UUID aiJobBId = asTenant(b.tenantId(), () -> {
            AiJob job = aiJobRepository.save(AiJob.create(
                b.tenantId(), AiTaskType.DIAGNOSIS_SUGGESTION, encounterBId,
                "oneri metni", "test-model", "v1", b.staffUserId()
            ));
            aiJobDecisionRepository.save(AiJobDecision.createPending(b.tenantId(), job.getId()));
            return job.getId();
        });

        assertThat(asTenant(a.tenantId(), () -> aiJobDecisionRepository.findByAiJobId(aiJobBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> aiJobDecisionRepository.findByAiJobId(aiJobBId))).isPresent();
    }

    // --- Task 5 ---

    private UUID createInventoryItem(TenantFixture f) {
        return asTenant(f.tenantId(), () -> inventoryItemRepository.save(InventoryItem.create(
            f.tenantId(), f.branchId(), "Mama", "Gida", null, 10, 1, null, null, BigDecimal.TEN
        )).getId());
    }

    @Test
    void encounter_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID encounterBId = createEncounter(b, createPatient(b));

        assertThat(asTenant(a.tenantId(), () -> encounterRepository.findById(encounterBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> encounterRepository.findById(encounterBId))).isPresent();
    }

    @Test
    void prescription_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID patientBId = createPatient(b);
        UUID encounterBId = createEncounter(b, patientBId);

        UUID prescriptionBId = asTenant(b.tenantId(), () -> prescriptionRepository.save(
            Prescription.issue(b.tenantId(), patientBId, encounterBId, b.staffUserId(), false)
        ).getId());

        assertThat(asTenant(a.tenantId(), () -> prescriptionRepository.findById(prescriptionBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> prescriptionRepository.findById(prescriptionBId))).isPresent();
    }

    @Test
    void stockMovement_isIsolatedAcrossTenants() {
        TenantFixture a = createTenantFixture("Izolasyon A");
        TenantFixture b = createTenantFixture("Izolasyon B");
        UUID itemBId = createInventoryItem(b);

        asTenantVoid(b.tenantId(), () -> stockMovementRepository.save(StockMovement.record(
            b.tenantId(), itemBId, StockMovementType.IN, 5, StockReferenceType.MANUAL, null
        )));

        assertThat(asTenant(a.tenantId(), () -> stockMovementRepository.findByInventoryItemId(itemBId))).isEmpty();
        assertThat(asTenant(b.tenantId(), () -> stockMovementRepository.findByInventoryItemId(itemBId))).hasSize(1);
    }
}
