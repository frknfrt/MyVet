package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.TodaySalesSummary;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTodaySalesSummaryUseCaseTest {

    @Mock private InvoiceRepository invoiceRepository;

    private GetTodaySalesSummaryUseCase useCase;

    private Invoice issuedInvoiceAt(Instant issuedAt, BigDecimal total) throws Exception {
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null);
        invoice.recalculateTotal(total);
        invoice.issue();
        setPrivateField(invoice, "issuedAt", issuedAt);
        return invoice;
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void should_sumOnlyTodaysIssuedInvoices() throws Exception {
        useCase = new GetTodaySalesSummaryUseCase(invoiceRepository);
        UUID tenantId = UUID.randomUUID();
        Invoice today1 = issuedInvoiceAt(Instant.now(), BigDecimal.valueOf(100));
        Invoice today2 = issuedInvoiceAt(Instant.now().minus(1, ChronoUnit.HOURS), BigDecimal.valueOf(50));
        Invoice yesterday = issuedInvoiceAt(Instant.now().minus(2, ChronoUnit.DAYS), BigDecimal.valueOf(999));
        when(invoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(today1, today2, yesterday));

        TodaySalesSummary result = useCase.execute(tenantId);

        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(result.saleCount()).isEqualTo(2);
    }

    @Test
    void should_returnZero_when_noInvoicesToday() {
        useCase = new GetTodaySalesSummaryUseCase(invoiceRepository);
        UUID tenantId = UUID.randomUUID();
        when(invoiceRepository.findByTenantId(tenantId)).thenReturn(List.of());

        TodaySalesSummary result = useCase.execute(tenantId);

        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.saleCount()).isZero();
    }
}
