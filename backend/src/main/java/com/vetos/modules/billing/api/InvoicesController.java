package com.vetos.modules.billing.api;

import com.vetos.modules.billing.api.dto.*;
import com.vetos.modules.billing.application.*;
import com.vetos.modules.billing.application.dto.AddInvoiceLineCommand;
import com.vetos.modules.billing.application.dto.RecordPaymentCommand;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * api-conventions.md rol matrisi: /invoices/**, /payments/** -> sadece
 * RECEPTIONIST ve ADMIN.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
public class InvoicesController {

    private final ListInvoicesUseCase listInvoicesUseCase;
    private final GetInvoiceUseCase getInvoiceUseCase;
    private final AddInvoiceLineUseCase addInvoiceLineUseCase;
    private final IssueInvoiceUseCase issueInvoiceUseCase;
    private final VoidInvoiceUseCase voidInvoiceUseCase;
    private final RecordPaymentUseCase recordPaymentUseCase;

    @GetMapping
    public List<InvoiceSummaryResponse> list() {
        return listInvoicesUseCase.execute(TenantContext.current()).stream().map(InvoiceSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return InvoiceResponse.from(getInvoiceUseCase.execute(id));
    }

    @PostMapping("/{id}/lines")
    public void addLine(@PathVariable UUID id, @RequestBody @Valid AddInvoiceLineRequest request) {
        addInvoiceLineUseCase.execute(new AddInvoiceLineCommand(
            id, request.description(), request.quantity(), request.unitPrice(), request.serviceTypeId()
        ));
    }

    @PostMapping("/{id}/issue")
    public void issue(@PathVariable UUID id) {
        issueInvoiceUseCase.execute(id);
    }

    @PostMapping("/{id}/void")
    public void voidInvoice(@PathVariable UUID id) {
        voidInvoiceUseCase.execute(id);
    }

    @PostMapping("/{id}/payments")
    public void recordPayment(@PathVariable UUID id, @RequestBody @Valid RecordPaymentRequest request) {
        recordPaymentUseCase.execute(new RecordPaymentCommand(id, request.method(), request.amount(), request.pspRef()));
    }
}
