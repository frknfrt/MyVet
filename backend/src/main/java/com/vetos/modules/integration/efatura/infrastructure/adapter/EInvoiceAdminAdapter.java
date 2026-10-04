package com.vetos.modules.integration.efatura.infrastructure.adapter;

import com.vetos.modules.integration.efatura.application.RetryEInvoiceSubmissionUseCase;
import com.vetos.modules.integration.efatura.domain.EInvoiceAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class EInvoiceAdminAdapter implements EInvoiceAdminPort {

    private final RetryEInvoiceSubmissionUseCase retryEInvoiceSubmissionUseCase;

    @Override
    public void retryNow(UUID submissionId) {
        retryEInvoiceSubmissionUseCase.executeAsAdmin(submissionId);
    }
}
