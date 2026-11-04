package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.platform.email.SmtpMailClient;
import com.vetos.platform.email.SmtpSendException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmtpPlatformBillingEmailAdapterTest {

    private final SmtpMailClient smtpMailClient = mock(SmtpMailClient.class);
    private static final String PAYMENT_INSTRUCTIONS = "IBAN TR00 0000 0000 0000 0000 0000 00 adresine odeyin.";

    private PlatformInvoice invoice() {
        return PlatformInvoice.issue(
            UUID.randomUUID(), "PRO", new BigDecimal("1999"),
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)
        );
    }

    private SmtpPlatformBillingEmailAdapter configuredAdapter() {
        return new SmtpPlatformBillingEmailAdapter(
            smtpMailClient, "mail.vetly.com.tr", 465, "bildirim@vetly.com.tr", "sifre", PAYMENT_INSTRUCTIONS
        );
    }

    @Test
    void should_notCallSmtpClient_when_notConfigured() {
        SmtpPlatformBillingEmailAdapter adapter = new SmtpPlatformBillingEmailAdapter(
            smtpMailClient, "", 587, "", "", PAYMENT_INSTRUCTIONS
        );

        assertThatCode(() -> adapter.sendInvoiceIssued(invoice(), "Pati Vet", "billing@klinik.com"))
            .doesNotThrowAnyException();
        verifyNoInteractions(smtpMailClient);
    }

    @Test
    void should_sendRenderedHtml_fromBildirimMailbox_when_invoiceIssued() {
        SmtpPlatformBillingEmailAdapter adapter = configuredAdapter();

        adapter.sendInvoiceIssued(invoice(), "Pati Vet", "billing@klinik.com");

        verify(smtpMailClient).send(
            eq("mail.vetly.com.tr"), eq(465), eq("bildirim@vetly.com.tr"), eq("sifre"),
            eq("bildirim@vetly.com.tr"), eq("billing@klinik.com"), contains("Pati Vet"), argThat(html ->
                html.contains("Pati Vet") && html.contains("1999") && html.contains("08.11.2026")
                    && html.contains(PAYMENT_INSTRUCTIONS) && !html.contains("{{")
            )
        );
    }

    @Test
    void should_sendRenderedHtml_when_invoiceDueSoon() {
        SmtpPlatformBillingEmailAdapter adapter = configuredAdapter();

        adapter.sendInvoiceDueSoon(invoice(), "Pati Vet", "billing@klinik.com");

        verify(smtpMailClient).send(
            eq("mail.vetly.com.tr"), eq(465), eq("bildirim@vetly.com.tr"), eq("sifre"),
            eq("bildirim@vetly.com.tr"), eq("billing@klinik.com"), anyString(), argThat(html ->
                html.contains("Pati Vet") && html.contains("1999") && !html.contains("{{")
            )
        );
    }

    @Test
    void should_sendRenderedHtml_when_tenantSuspended() {
        SmtpPlatformBillingEmailAdapter adapter = configuredAdapter();

        adapter.sendTenantSuspended("Pati Vet", "billing@klinik.com");

        verify(smtpMailClient).send(
            eq("mail.vetly.com.tr"), eq(465), eq("bildirim@vetly.com.tr"), eq("sifre"),
            eq("bildirim@vetly.com.tr"), eq("billing@klinik.com"), anyString(), argThat(html ->
                html.contains("Pati Vet") && html.contains(PAYMENT_INSTRUCTIONS) && !html.contains("{{")
            )
        );
    }

    @Test
    void should_notThrow_when_smtpClientFails() {
        SmtpPlatformBillingEmailAdapter adapter = configuredAdapter();
        doThrow(new SmtpSendException("baglanti hatasi", new RuntimeException())).when(smtpMailClient)
            .send(any(), anyInt(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> adapter.sendInvoiceIssued(invoice(), "Pati Vet", "billing@klinik.com"))
            .doesNotThrowAnyException();
    }
}
