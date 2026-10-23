package com.vetos.modules.tenant.infrastructure.adapter;

import com.vetos.modules.tenant.domain.StaffInvite;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.platform.email.SmtpMailClient;
import com.vetos.platform.email.SmtpSendException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmtpInviteEmailAdapterTest {

    private final SmtpMailClient smtpMailClient = mock(SmtpMailClient.class);

    private StaffInvite invite() {
        return StaffInvite.create(UUID.randomUUID(), UUID.randomUUID(), "yeni@klinik.com", "Ayşe Yılmaz", StaffRole.ADMIN, UUID.randomUUID());
    }

    @Test
    void should_notCallSmtpClient_when_notConfigured() {
        SmtpInviteEmailAdapter adapter = new SmtpInviteEmailAdapter(smtpMailClient, "", 587, "", "");

        assertThatCode(() -> adapter.sendInvite(invite(), "Pati Vet", "https://uygulama.vetly.com.tr/davet/abc"))
            .doesNotThrowAnyException();
        verifyNoInteractions(smtpMailClient);
    }

    @Test
    void should_sendRenderedHtml_fromDestekMailbox_when_configured() {
        SmtpInviteEmailAdapter adapter = new SmtpInviteEmailAdapter(
            smtpMailClient, "mail.vetly.com.tr", 587, "destek@vetly.com.tr", "sifre"
        );

        adapter.sendInvite(invite(), "Pati Vet", "https://uygulama.vetly.com.tr/davet/abc");

        verify(smtpMailClient).send(
            eq("mail.vetly.com.tr"), eq(587), eq("destek@vetly.com.tr"), eq("sifre"),
            eq("destek@vetly.com.tr"), eq("yeni@klinik.com"), contains("Pati Vet"), argThat(html ->
                html.contains("Pati Vet") && html.contains("Ayşe Yılmaz") && html.contains("https://uygulama.vetly.com.tr/davet/abc")
                    && html.contains("Yönetici") && !html.contains("{{")
            )
        );
    }

    @Test
    void should_notThrow_when_smtpClientFails() {
        SmtpInviteEmailAdapter adapter = new SmtpInviteEmailAdapter(
            smtpMailClient, "mail.vetly.com.tr", 587, "destek@vetly.com.tr", "sifre"
        );
        doThrow(new SmtpSendException("baglanti hatasi", new RuntimeException())).when(smtpMailClient)
            .send(any(), anyInt(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> adapter.sendInvite(invite(), "Pati Vet", "https://uygulama.vetly.com.tr/davet/abc"))
            .doesNotThrowAnyException();
    }
}
