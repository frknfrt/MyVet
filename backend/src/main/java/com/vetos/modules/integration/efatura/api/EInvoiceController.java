package com.vetos.modules.integration.efatura.api;

import com.vetos.modules.integration.efatura.api.dto.EInvoiceStatusResponse;
import com.vetos.modules.integration.efatura.api.dto.EInvoiceSubmissionResponse;
import com.vetos.modules.integration.efatura.application.GetEInvoiceStatusSummaryUseCase;
import com.vetos.modules.integration.efatura.application.ListEInvoiceSubmissionsUseCase;
import com.vetos.modules.integration.efatura.application.RetryEInvoiceSubmissionUseCase;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** api-conventions.md rol matrisi: /efatura/** -> sadece ADMIN. */
@RestController
@RequestMapping("/api/v1/efatura")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class EInvoiceController {

    private final GetEInvoiceStatusSummaryUseCase getEInvoiceStatusSummaryUseCase;
    private final ListEInvoiceSubmissionsUseCase listEInvoiceSubmissionsUseCase;
    private final RetryEInvoiceSubmissionUseCase retryEInvoiceSubmissionUseCase;

    @GetMapping("/status")
    public EInvoiceStatusResponse status() {
        return EInvoiceStatusResponse.from(getEInvoiceStatusSummaryUseCase.execute(TenantContext.current()));
    }

    @GetMapping("/submissions")
    public List<EInvoiceSubmissionResponse> submissions() {
        return listEInvoiceSubmissionsUseCase.execute(TenantContext.current()).stream()
            .map(EInvoiceSubmissionResponse::from)
            .toList();
    }

    @PostMapping("/submissions/{id}/retry")
    public void retry(@PathVariable UUID id) {
        retryEInvoiceSubmissionUseCase.execute(TenantContext.current(), id);
    }
}
