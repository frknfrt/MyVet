package com.vetos;

import com.vetos.modules.billing.application.RecordPaymentUseCase;
import com.vetos.modules.billing.application.VoidInvoiceUseCase;
import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.*;
import com.vetos.modules.billing.domain.exception.InvoiceInvalidStateException;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.tenant.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.minimum-idle=0"})
class PaymentConsistencyTest extends TenantScopedTestSupport {
    @Autowired TenantRepository tenants;
    @Autowired BranchRepository branches;
    @Autowired OwnerRepository owners;
    @Autowired InvoiceRepository invoices;
    @Autowired PaymentRepository payments;
    @Autowired RecordPaymentUseCase recordPayment;
    @Autowired VoidInvoiceUseCase voidInvoice;
    @Autowired EntityManager entityManager;
    private UUID tenantId;
    private UUID invoiceId;

    @BeforeEach
    void createInvoice() {
        tenantId = inRootSession(() -> tenants.save(Tenant.register("H1 " + UUID.randomUUID(), null)).getId());
        UUID branchId = inRootSession(() -> branches.save(Branch.create(tenantId, "Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> owners.save(Owner.register(tenantId, "H1 Test", "5550000000", null, null)).getId());
        invoiceId = asTenant(tenantId, () -> {
            var invoice = Invoice.createDraft(tenantId, branchId, ownerId, null, null);
            invoice.recalculateTotal(new BigDecimal("100"));
            invoice.issue();
            return invoices.save(invoice).getId();
        });
    }

    @AfterEach
    void cleanup() {
        if (tenantId == null) return;
        inRootSessionVoid(() -> {
            for (String table : List.of("payments", "invoices", "owners", "subscriptions", "branches", "tenants")) {
                String column = table.equals("tenants") ? "id" : "tenant_id";
                entityManager.createNativeQuery("DELETE FROM " + table + " WHERE " + column + " = :tenant")
                    .setParameter("tenant", tenantId).executeUpdate();
            }
        });
    }

    private UUID pay(String amount, UUID requestId) {
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new com.vetos.platform.security.AuthenticatedStaffUser(UUID.randomUUID(), tenantId, List.of(), "ADMIN"),
            null, List.of()));
        try {
            return recordPayment.execute(new RecordPaymentCommand(invoiceId, PaymentMethod.CASH, new BigDecimal(amount), null, requestId));
        } finally {
            context.setAuthentication(previous);
        }
    }

    private void assertBalance(int count, String sum, InvoiceStatus status) {
        asTenantVoid(tenantId, () -> {
            var rows = payments.findByInvoiceId(invoiceId);
            assertThat(rows).hasSize(count);
            assertThat(rows.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(sum);
            assertThat(invoices.findById(invoiceId).orElseThrow().getStatus()).isEqualTo(status);
        });
    }

    @Test
    void concurrentPartialPaymentsBothCount() throws Exception {
        race(() -> pay("40", UUID.randomUUID()), () -> asTenant(tenantId, () -> pay("60", UUID.randomUUID())));
        assertBalance(2, "100", InvoiceStatus.PAID);
    }

    @Test
    void concurrentRetryCreatesOnePayment() throws Exception {
        UUID requestId = UUID.randomUUID();
        var results = race(() -> pay("60", requestId), () -> asTenant(tenantId, () -> pay("60", requestId)));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertBalance(1, "60", InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void fullPaymentCannotBeVoidedConcurrently() throws Exception {
        race(() -> pay("100", UUID.randomUUID()), () -> {
            assertThatThrownBy(() -> asTenantVoid(tenantId, () -> voidInvoice.execute(invoiceId))).isInstanceOf(InvoiceInvalidStateException.class);
            return null;
        });
        assertBalance(1, "100", InvoiceStatus.PAID);
    }

    @Test
    void requestIdIndexRejectsBypassingTheService() {
        UUID requestId = UUID.randomUUID();
        asTenant(tenantId, () -> pay("40", requestId));
        assertThatThrownBy(() -> asTenantVoid(tenantId, () -> {
            payments.save(Payment.record(tenantId, invoiceId, PaymentMethod.CASH, BigDecimal.TEN, null, requestId));
            entityManager.flush();
        })).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertBalance(1, "40", InvoiceStatus.PARTIALLY_PAID);
    }

    private List<UUID> race(Callable<UUID> firstWork, Callable<UUID> secondWork) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<UUID> first = executor.submit(() -> asTenant(tenantId, () -> {
                invoices.findByIdForUpdate(invoiceId).orElseThrow();
                UUID result = call(firstWork);
                locked.countDown();
                await(release);
                return result;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<UUID> second = executor.submit(() -> {
                    secondStarted.countDown();
                    return call(secondWork);
                });
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                return Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    private static UUID call(Callable<UUID> work) {
        try { return work.call(); }
        catch (RuntimeException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Lock wait expired");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
