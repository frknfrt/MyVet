package com.vetos;

import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.AppointmentSource;
import com.vetos.modules.appointment.domain.AppointmentStatus;
import com.vetos.modules.appointment.domain.ServiceType;
import com.vetos.modules.appointment.domain.ServiceTypeRepository;
import com.vetos.modules.billing.domain.BranchRevenueAggregate;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.SalesAggregate;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faz 6 ozet sorgularinin gercek Postgres'te dogrulugu: Istanbul ay siniri, durum filtresi,
 * sube gruplama, kiracilar arasi sizinti yok. Kayit zamanlari native UPDATE ile ayarlanir.
 * Canli docker-compose Postgres gerekir.
 */
@SpringBootTest
class DashboardSummaryQueriesTest extends TenantScopedTestSupport {

    // Istanbul Kasim 2026 = [2026-10-31T21:00Z, 2026-11-30T21:00Z)
    static final Instant NOV_START = Instant.parse("2026-10-31T21:00:00Z");
    static final Instant DEC_START = Instant.parse("2026-11-30T21:00:00Z");
    static final Set<InvoiceStatus> REVENUE = EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID);

    @Autowired TenantRepository tenantRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired OwnerRepository ownerRepository;
    @Autowired StaffUserRepository staffUserRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired SpeciesRepository speciesRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired ServiceTypeRepository serviceTypeRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @PersistenceContext EntityManager entityManager;

    record Fixture(UUID tenantId, UUID branchId, UUID ownerId, UUID staffUserId) {}

    private final List<UUID> createdTenantIds = new ArrayList<>();

    Fixture createTenantFixture(String label) {
        UUID tenantId = inRootSession(() -> tenantRepository.save(Tenant.register(label, null)).getId());
        createdTenantIds.add(tenantId);
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, label + " Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> ownerRepository.save(
            Owner.register(tenantId, label + " Sahip", "05551234567", null, null)).getId());
        UUID staffUserId = asTenant(tenantId, () -> staffUserRepository.save(StaffUser.register(
            tenantId, branchId, "Dr. " + label, "ozet-" + UUID.randomUUID() + "@test.local", "hash", StaffRole.VET
        )).getId());
        return new Fixture(tenantId, branchId, ownerId, staffUserId);
    }

    UUID createBranch(Fixture f, String name) {
        return inRootSession(() -> branchRepository.save(Branch.create(f.tenantId(), name)).getId());
    }

    UUID createInvoice(Fixture f, UUID branchId, InvoiceStatus status, Instant issuedAt, String amount) {
        UUID id = asTenant(f.tenantId(), () -> invoiceRepository.save(
            Invoice.createDraft(f.tenantId(), branchId, f.ownerId(), null, f.staffUserId())).getId());
        inRootSessionVoid(() -> entityManager.createNativeQuery(
                "UPDATE invoices SET status = :s, issued_at = :at, total_amount = :amt WHERE id = :id")
            .setParameter("s", status.name()).setParameter("at", issuedAt)
            .setParameter("amt", new BigDecimal(amount)).setParameter("id", id).executeUpdate());
        return id;
    }

    UUID createPatient(Fixture f, Instant createdAt) {
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID id = asTenant(f.tenantId(), () -> patientRepository.save(
            Patient.register(f.tenantId(), f.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)).getId());
        inRootSessionVoid(() -> entityManager.createNativeQuery("UPDATE patients SET created_at = :at WHERE id = :id")
            .setParameter("at", createdAt).setParameter("id", id).executeUpdate());
        return id;
    }

    UUID createAppointment(Fixture f, Instant start, AppointmentStatus status) {
        UUID patientId = createPatient(f, Instant.parse("2026-01-01T09:00:00Z"));
        UUID serviceTypeId = asTenant(f.tenantId(), () -> serviceTypeRepository.save(
            ServiceType.create(f.tenantId(), "Muayene", 30, BigDecimal.TEN)).getId());
        UUID id = asTenant(f.tenantId(), () -> appointmentRepository.save(Appointment.schedule(
            f.tenantId(), f.branchId(), patientId, f.ownerId(), f.staffUserId(), serviceTypeId,
            start, start.plusSeconds(1800), AppointmentSource.PHONE, null)).getId());
        inRootSessionVoid(() -> entityManager.createNativeQuery("UPDATE appointments SET status = :s WHERE id = :id")
            .setParameter("s", status.name()).setParameter("id", id).executeUpdate());
        return id;
    }

    @AfterEach
    void purge() {
        for (UUID t : createdTenantIds) {
            inRootSessionVoid(() -> List.of(
                "DELETE FROM appointments WHERE tenant_id = :t",
                "DELETE FROM service_types WHERE tenant_id = :t",
                "DELETE FROM invoices WHERE tenant_id = :t",
                "DELETE FROM patients WHERE owner_id IN (SELECT id FROM owners WHERE tenant_id = :t)",
                "DELETE FROM owners WHERE tenant_id = :t",
                "DELETE FROM subscriptions WHERE tenant_id = :t",
                "DELETE FROM staff_users WHERE tenant_id = :t",
                "DELETE FROM branches WHERE tenant_id = :t",
                "DELETE FROM tenants WHERE id = :t"
            ).forEach(sql -> entityManager.createNativeQuery(sql).setParameter("t", t).executeUpdate()));
        }
        createdTenantIds.clear();
    }

    @Test
    void revenue_respectsIstanbulMonth_status_branch_and_tenant() {
        Fixture a = createTenantFixture("Ozet A");
        Fixture b = createTenantFixture("Ozet B");
        UUID subeA = createBranch(a, "Ozet A Sube");

        createInvoice(a, a.branchId(), InvoiceStatus.PAID, Instant.parse("2026-10-31T21:30:00Z"), "100");      // 1 Kasim 00:30 -> Kasim
        createInvoice(a, a.branchId(), InvoiceStatus.ISSUED, Instant.parse("2026-11-30T20:30:00Z"), "50");     // 30 Kasim 23:30 -> Kasim
        createInvoice(a, subeA, InvoiceStatus.PARTIALLY_PAID, Instant.parse("2026-11-10T09:00:00Z"), "30");
        createInvoice(a, a.branchId(), InvoiceStatus.PAID, Instant.parse("2026-10-31T20:30:00Z"), "999");      // 31 Ekim 23:30 -> Ekim
        createInvoice(a, a.branchId(), InvoiceStatus.VOID, Instant.parse("2026-11-10T09:00:00Z"), "777"); // gelir sayilmaz
        createInvoice(b, b.branchId(), InvoiceStatus.PAID, Instant.parse("2026-11-10T09:00:00Z"), "555");      // baska kiraci

        SalesAggregate nov = asTenant(a.tenantId(), () -> invoiceRepository.sumIssuedBetween(a.tenantId(), REVENUE, NOV_START, DEC_START));
        assertThat(nov.totalAmount()).isEqualByComparingTo("180");
        assertThat(nov.count()).isEqualTo(3);

        List<BranchRevenueAggregate> byBranch = asTenant(a.tenantId(),
            () -> invoiceRepository.sumIssuedByBranchBetween(a.tenantId(), REVENUE, NOV_START, DEC_START));
        assertThat(byBranch).hasSize(2);
        assertThat(byBranch).filteredOn(x -> x.branchId().equals(a.branchId())).singleElement()
            .satisfies(x -> assertThat(x.totalAmount()).isEqualByComparingTo("150"));
        assertThat(byBranch).filteredOn(x -> x.branchId().equals(subeA)).singleElement()
            .satisfies(x -> assertThat(x.totalAmount()).isEqualByComparingTo("30"));

        assertThat(asTenant(a.tenantId(), () -> invoiceRepository.sumIssuedByBranchBetween(
            a.tenantId(), REVENUE, Instant.parse("2026-12-31T21:00:00Z"), Instant.parse("2027-01-31T21:00:00Z")))).isEmpty();
    }

    @Test
    void patientCount_respectsIstanbulMonth_and_tenant() {
        Fixture a = createTenantFixture("Ozet A");
        Fixture b = createTenantFixture("Ozet B");
        createPatient(a, Instant.parse("2026-10-31T21:30:00Z")); // 1 Kasim 00:30 -> Kasim
        createPatient(a, Instant.parse("2026-11-15T09:00:00Z"));
        createPatient(a, Instant.parse("2026-10-31T20:30:00Z")); // 31 Ekim 23:30 -> Ekim
        createPatient(b, Instant.parse("2026-11-15T09:00:00Z")); // baska kiraci

        assertThat(asTenant(a.tenantId(), () -> patientRepository.countByTenantIdAndCreatedAtRange(a.tenantId(), NOV_START, DEC_START)))
            .isEqualTo(2);
    }

    @Test
    void appointmentCounts_totalAndNoShow_perTenant() {
        Fixture a = createTenantFixture("Ozet A");
        Fixture b = createTenantFixture("Ozet B");
        createAppointment(a, Instant.parse("2026-11-03T07:00:00Z"), AppointmentStatus.NO_SHOW);
        createAppointment(a, Instant.parse("2026-11-04T07:00:00Z"), AppointmentStatus.COMPLETED);
        createAppointment(a, Instant.parse("2026-10-31T20:00:00Z"), AppointmentStatus.NO_SHOW); // Ekim
        createAppointment(b, Instant.parse("2026-11-05T07:00:00Z"), AppointmentStatus.NO_SHOW); // baska kiraci

        assertThat(asTenant(a.tenantId(), () -> appointmentRepository.countByTenantIdAndDateRange(a.tenantId(), NOV_START, DEC_START)))
            .isEqualTo(2);
        assertThat(asTenant(a.tenantId(), () -> appointmentRepository.countByTenantIdAndDateRangeAndStatus(
            a.tenantId(), NOV_START, DEC_START, AppointmentStatus.NO_SHOW))).isEqualTo(1);
    }
}
