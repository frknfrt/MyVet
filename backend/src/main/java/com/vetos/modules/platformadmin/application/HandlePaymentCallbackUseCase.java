package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.application.dto.RecordPlatformPaymentCommand;
import com.vetos.modules.platformadmin.domain.CheckoutResult;
import com.vetos.modules.platformadmin.domain.PaymentGatewayPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;
import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import com.vetos.modules.platformadmin.domain.exception.PlatformInvoiceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * iyzico'nun checkout callback'i sonrasi cagrilir. Idempotent: ayni token
 * (veya invoice zaten PAID) icin tekrar cagrilirsa, status kontrolu ile
 * RecordPlatformPaymentUseCase'i cagirmaktan kacinir -- transaction rollback-only
 * marklanmasini ve UnexpectedRollbackException'i engellemek icin.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HandlePaymentCallbackUseCase {

    private final PaymentGatewayPort paymentGatewayPort;
    private final PlatformInvoiceRepository platformInvoiceRepository;
    private final RecordPlatformPaymentUseCase recordPlatformPaymentUseCase;

    @Transactional
    public boolean execute(String token, LocalDate today) {
        CheckoutResult result = paymentGatewayPort.retrieveCheckoutResult(token);
        if (!result.success()) {
            log.info("iyzico odeme basarisiz: conversationId={}", result.conversationId());
            return false;
        }

        UUID invoiceId = UUID.fromString(result.conversationId());
        PlatformInvoice invoice = platformInvoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new PlatformInvoiceNotFoundException(invoiceId));

        // Check if invoice is already resolved (PAID or VOID) — idempotent retry
        if (invoice.getStatus() != PlatformInvoiceStatus.ISSUED && invoice.getStatus() != PlatformInvoiceStatus.OVERDUE) {
            log.info("iyzico callback tekrarlandi, fatura zaten cozumlenmis: invoiceId={}", invoiceId);
            return true;
        }

        recordPlatformPaymentUseCase.execute(new RecordPlatformPaymentCommand(
            invoice.getId(), invoice.getAmount(), PlatformPaymentMethod.CARD_ONLINE,
            today, "iyzico odeme referansi: " + result.paymentId(), null
        ));
        return true;
    }
}
