package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.AdminMailbox;
import com.vetos.platform.email.SmtpMailClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SmtpAdminEmailAdapterTest {

    private final SmtpMailClient smtpMailClient = mock(SmtpMailClient.class);

    @Test
    void should_notBeConfigured_when_hostBlank() {
        SmtpAdminEmailAdapter adapter = new SmtpAdminEmailAdapter(
            smtpMailClient, "", 587, "info@vetly.com.tr", "sifre1", "destek@vetly.com.tr", "sifre2", "bildirim@vetly.com.tr", "sifre3"
        );

        assertThat(adapter.isConfigured()).isFalse();
    }

    @Test
    void should_notBeConfigured_when_oneMailboxCredentialMissing() {
        SmtpAdminEmailAdapter adapter = new SmtpAdminEmailAdapter(
            smtpMailClient, "mail.vetly.com.tr", 587, "info@vetly.com.tr", "sifre1", "destek@vetly.com.tr", "", "bildirim@vetly.com.tr", "sifre3"
        );

        assertThat(adapter.isConfigured()).isFalse();
    }

    @Test
    void should_beConfigured_when_hostAndAllThreeMailboxesComplete() {
        SmtpAdminEmailAdapter adapter = new SmtpAdminEmailAdapter(
            smtpMailClient, "mail.vetly.com.tr", 587, "info@vetly.com.tr", "sifre1", "destek@vetly.com.tr", "sifre2", "bildirim@vetly.com.tr", "sifre3"
        );

        assertThat(adapter.isConfigured()).isTrue();
    }

    @Test
    void should_onlyLog_andNeverCallSmtpClient_when_notConfigured() {
        SmtpAdminEmailAdapter adapter = new SmtpAdminEmailAdapter(smtpMailClient, "", 587, "", "", "", "", "", "");

        assertThatCode(() -> adapter.send(AdminMailbox.INFO, "test@klinik.com", "Konu", "<p>govde</p>"))
            .doesNotThrowAnyException();
        verifyNoInteractions(smtpMailClient);
    }
}
