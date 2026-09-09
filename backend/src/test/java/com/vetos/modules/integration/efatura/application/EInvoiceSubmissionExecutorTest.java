package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.billing.domain.InvoiceLine;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.billing.domain.InvoiceLineSource;
import com.vetos.modules.integration.efatura.domain.*;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.OwnerSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * "hale" test vakasi -- soyadsiz sahip kaydinin faturaentegrator'da
 * "Soyad(FamilyName) alani 2 haneden az olamaz" hatasiyla resmilesme
 * asamasinda basarisiz olmasi -- bu sinifa on-dogrulama eklenmesine sebep
 * oldu. Bu test bu davranisi ve genel basari/hata akislarini kapsar.
 */
@ExtendWith(MockitoExtension.class)
class EInvoiceSubmissionExecutorTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private EInvoiceGatewayPort eInvoiceGatewayPort;
    @Mock private OwnerLookupPort ownerLookupPort;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private InvoiceEInvoiceUpdatePort invoiceEInvoiceUpdatePort;

    private EInvoiceSubmissionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EInvoiceSubmissionExecutor(
            eInvoiceSubmissionRepository, eInvoiceGatewayPort, ownerLookupPort, invoiceLineRepository, invoiceEInvoiceUpdatePort
        );
    }

    private EInvoiceSubmission aQueuedSubmission(UUID invoiceId, UUID ownerId) {
        return EInvoiceSubmission.queue(
            UUID.randomUUID(), invoiceId, ownerId, EInvoiceDocumentType.E_ARSIV, new BigDecimal("120.00"), new BigDecimal("20.00")
        );
    }

    private OwnerSummary ownerWithName(UUID ownerId, String fullName) {
        return new OwnerSummary(ownerId, fullName, "5551234567", "adres", "İstanbul", "Güngören", "***", true, true);
    }

    @Test
    void should_failFast_withoutCallingProvider_when_ownerHasNoSurname() {
        UUID submissionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        EInvoiceSubmission submission = aQueuedSubmission(UUID.randomUUID(), ownerId);
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(ownerLookupPort.findSummaryById(ownerId)).thenReturn(ownerWithName(ownerId, "hale"));

        executor.attemptSubmit(submissionId);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).contains("hale").contains("soyad");
        verifyNoInteractions(eInvoiceGatewayPort, invoiceLineRepository, invoiceEInvoiceUpdatePort);
        verify(eInvoiceSubmissionRepository).save(submission);
    }

    @Test
    void should_failFast_when_ownerSurnameIsSingleCharacter() {
        UUID submissionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        EInvoiceSubmission submission = aQueuedSubmission(UUID.randomUUID(), ownerId);
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(ownerLookupPort.findSummaryById(ownerId)).thenReturn(ownerWithName(ownerId, "hale a"));

        executor.attemptSubmit(submissionId);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.FAILED);
        verifyNoInteractions(eInvoiceGatewayPort);
    }

    @Test
    void should_submitToProvider_when_ownerHasValidSurname() {
        UUID submissionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        EInvoiceSubmission submission = aQueuedSubmission(invoiceId, ownerId);
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(ownerLookupPort.findSummaryById(ownerId)).thenReturn(ownerWithName(ownerId, "hale yilmaz"));
        InvoiceLine line = InvoiceLine.create(
            invoiceId, "muayene", 1, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("20"), null, null, InvoiceLineSource.MANUAL
        );
        // InvoiceLine.id gercek Hibernate persist'inde uretilir (@GeneratedValue) --
        // burada gercek bir persistence context olmadigi icin elle veriliyor,
        // aksi halde EInvoiceSubmissionExecutor.toLineItem()'daki line.getId().toString() NPE atar.
        ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
        when(invoiceLineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of(line));
        when(eInvoiceGatewayPort.submit(any())).thenReturn(EInvoiceSubmissionOutcome.pending("232420", "isleniyor"));

        executor.attemptSubmit(submissionId);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PROCESSING);
        assertThat(submission.getProviderReference()).isEqualTo("232420");
        verify(eInvoiceSubmissionRepository).save(submission);
    }

    @Test
    void should_markFailedWithProviderMessage_when_providerRejects() {
        UUID submissionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        EInvoiceSubmission submission = aQueuedSubmission(invoiceId, ownerId);
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(ownerLookupPort.findSummaryById(ownerId)).thenReturn(ownerWithName(ownerId, "hale yilmaz"));
        when(invoiceLineRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());
        when(eInvoiceGatewayPort.submit(any())).thenReturn(EInvoiceSubmissionOutcome.failure("saglayici istegi reddetti"));

        executor.attemptSubmit(submissionId);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).isEqualTo("saglayici istegi reddetti");
    }

    @Test
    void should_doNothing_when_submissionNotFound() {
        UUID submissionId = UUID.randomUUID();
        when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.empty());

        executor.attemptSubmit(submissionId);

        verifyNoInteractions(ownerLookupPort, eInvoiceGatewayPort, invoiceLineRepository, invoiceEInvoiceUpdatePort);
        verify(eInvoiceSubmissionRepository, never()).save(any());
    }
}
