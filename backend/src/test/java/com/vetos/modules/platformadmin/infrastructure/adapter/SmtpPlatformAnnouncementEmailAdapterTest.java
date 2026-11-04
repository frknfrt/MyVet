package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.platform.email.SmtpMailClient;
import com.vetos.platform.email.SmtpSendException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmtpPlatformAnnouncementEmailAdapterTest {

    private final SmtpMailClient smtpMailClient = mock(SmtpMailClient.class);

    private SmtpPlatformAnnouncementEmailAdapter configuredAdapter() {
        return new SmtpPlatformAnnouncementEmailAdapter(smtpMailClient, "mail.vetly.com.tr", 465, "info@vetly.com.tr", "sifre");
    }

    @Test
    void should_notCallSmtpClient_when_notConfigured() {
        SmtpPlatformAnnouncementEmailAdapter adapter = new SmtpPlatformAnnouncementEmailAdapter(smtpMailClient, "", 587, "", "");

        assertThatCode(() -> adapter.sendAnnouncement("Pati Vet", "billing@klinik.com", "Bakım Duyurusu", "Yarın bakım var."))
            .doesNotThrowAnyException();
        verifyNoInteractions(smtpMailClient);
    }

    @Test
    void should_sendRenderedHtml_fromInfoMailbox_when_configured() {
        SmtpPlatformAnnouncementEmailAdapter adapter = configuredAdapter();

        adapter.sendAnnouncement("Pati Vet", "billing@klinik.com", "Bakım Duyurusu", "Yarın bakım var.\nSistem 1 saat kapalı olacak.");

        verify(smtpMailClient).send(
            eq("mail.vetly.com.tr"), eq(465), eq("info@vetly.com.tr"), eq("sifre"),
            eq("info@vetly.com.tr"), eq("billing@klinik.com"), eq("Bakım Duyurusu"), argThat(html ->
                html.contains("Pati Vet") && html.contains("Bakım Duyurusu") && html.contains("Yarın bakım var.<br>Sistem 1 saat kapalı olacak.")
                    && !html.contains("{{")
            )
        );
    }

    @Test
    void should_escapeHtml_inTitleAndBody() {
        SmtpPlatformAnnouncementEmailAdapter adapter = configuredAdapter();

        adapter.sendAnnouncement("Pati <Vet>", "billing@klinik.com", "<b>Dikkat</b>", "Fiyat <%100> arttı");

        verify(smtpMailClient).send(
            any(), anyInt(), any(), any(), any(), any(), any(), argThat(html ->
                html.contains("&lt;b&gt;Dikkat&lt;/b&gt;") && html.contains("Fiyat &lt;%100&gt; arttı") && !html.contains("<b>Dikkat</b>")
            )
        );
    }

    @Test
    void should_notThrow_when_smtpClientFails() {
        SmtpPlatformAnnouncementEmailAdapter adapter = configuredAdapter();
        doThrow(new SmtpSendException("baglanti hatasi", new RuntimeException())).when(smtpMailClient)
            .send(any(), anyInt(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> adapter.sendAnnouncement("Pati Vet", "billing@klinik.com", "Duyuru", "Govde"))
            .doesNotThrowAnyException();
    }
}
