package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.application.dto.CompleteQuickSaleCommand;
import com.vetos.modules.billing.application.dto.QuickSaleLineCommand;
import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.PaymentMethod;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompleteQuickSaleUseCaseTest {

    @Mock private CreateManualInvoiceUseCase createManualInvoiceUseCase;
    @Mock private AddInvoiceLineUseCase addInvoiceLineUseCase;
    @Mock private IssueInvoiceUseCase issueInvoiceUseCase;
    @Mock private RecordPaymentUseCase recordPaymentUseCase;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private OwnerLookupPort ownerLookupPort;

    private CompleteQuickSaleUseCase useCase;

    // CompleteQuickSaleUseCase, ownerId null oldugunda anonim musteriyi cozmek
    // icin TenantContext.current() okur. Gercek istekte bu JwtAuthenticationFilter
    // tarafindan doldurulur; bu pure-Mockito testte istek zinciri olmadigindan
    // ayni thread-local'i elle kuruyoruz (deger onemsiz, ownerLookupPort.
    // getOrCreateAnonymousOwnerId(any()) ile eslesir).
    @BeforeEach
    void setUpTenantContext() {
        TenantContext.set(UUID.randomUUID());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private QuickSaleLineCommand aLine() {
        return new QuickSaleLineCommand(UUID.randomUUID(), "Kedi Maması", 1, BigDecimal.valueOf(200), BigDecimal.valueOf(20));
    }

    @Test
    void should_useProvidedOwner_when_ownerIdGiven() {
        useCase = new CompleteQuickSaleUseCase(
            createManualInvoiceUseCase, addInvoiceLineUseCase, issueInvoiceUseCase,
            recordPaymentUseCase, invoiceRepository, ownerLookupPort
        );
        UUID ownerId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        when(createManualInvoiceUseCase.execute(branchId, ownerId, staffId)).thenReturn(invoiceId);
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), branchId, ownerId, null, staffId);
        invoice.recalculateTotal(BigDecimal.valueOf(200));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        UUID result = useCase.execute(new CompleteQuickSaleCommand(
            branchId, ownerId, staffId, List.of(aLine()), PaymentMethod.CASH
        ));

        assertThat(result).isEqualTo(invoiceId);
        verifyNoInteractions(ownerLookupPort);
        verify(issueInvoiceUseCase).execute(invoiceId);
        verify(recordPaymentUseCase).execute(new RecordPaymentCommand(invoiceId, PaymentMethod.CASH, BigDecimal.valueOf(200), null));
    }

    @Test
    void should_resolveAnonymousOwner_when_ownerIdNull() {
        useCase = new CompleteQuickSaleUseCase(
            createManualInvoiceUseCase, addInvoiceLineUseCase, issueInvoiceUseCase,
            recordPaymentUseCase, invoiceRepository, ownerLookupPort
        );
        UUID branchId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID anonymousOwnerId = UUID.randomUUID();
        when(ownerLookupPort.getOrCreateAnonymousOwnerId(any())).thenReturn(anonymousOwnerId);
        when(createManualInvoiceUseCase.execute(branchId, anonymousOwnerId, staffId)).thenReturn(invoiceId);
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), branchId, anonymousOwnerId, null, staffId);
        invoice.recalculateTotal(BigDecimal.valueOf(200));
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        useCase.execute(new CompleteQuickSaleCommand(branchId, null, staffId, List.of(aLine()), PaymentMethod.CASH));

        verify(createManualInvoiceUseCase).execute(branchId, anonymousOwnerId, staffId);
    }

    @Test
    void should_addEachLine_beforeIssuing() {
        useCase = new CompleteQuickSaleUseCase(
            createManualInvoiceUseCase, addInvoiceLineUseCase, issueInvoiceUseCase,
            recordPaymentUseCase, invoiceRepository, ownerLookupPort
        );
        UUID ownerId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        QuickSaleLineCommand line1 = aLine();
        QuickSaleLineCommand line2 = new QuickSaleLineCommand(UUID.randomUUID(), "Köpek Tasması", 2, BigDecimal.valueOf(50), BigDecimal.TEN);
        when(createManualInvoiceUseCase.execute(branchId, ownerId, staffId)).thenReturn(invoiceId);
        Invoice invoice = Invoice.createDraft(UUID.randomUUID(), branchId, ownerId, null, staffId);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        useCase.execute(new CompleteQuickSaleCommand(branchId, ownerId, staffId, List.of(line1, line2), PaymentMethod.CARD));

        // KDV orani her kalem icin komuttan aynen tasinir (hardcoded 0 DEGIL),
        // indirim ise Hizli Satis akisinda her zaman 0.
        verify(addInvoiceLineUseCase).execute(new AddInvoiceLineCommand(
            invoiceId, line1.description(), line1.quantity(), line1.unitPrice(),
            BigDecimal.ZERO, BigDecimal.valueOf(20), null, line1.inventoryItemId()
        ));
        verify(addInvoiceLineUseCase).execute(new AddInvoiceLineCommand(
            invoiceId, line2.description(), line2.quantity(), line2.unitPrice(),
            BigDecimal.ZERO, BigDecimal.TEN, null, line2.inventoryItemId()
        ));
    }
}
