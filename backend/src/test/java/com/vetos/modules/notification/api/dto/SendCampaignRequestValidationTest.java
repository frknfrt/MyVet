package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.domain.NotificationChannel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SendCampaignRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private SendCampaignRequest withRecipients(int count) {
        List<CampaignRecipientRequest> recipients = Collections.nCopies(count, null);
        return new SendCampaignRequest(NotificationChannel.SMS, "Merhaba", recipients);
    }

    @Test
    void should_accept_maxRecipients() {
        assertThat(validator.validateProperty(withRecipients(SendCampaignRequest.MAX_RECIPIENTS), "recipients")).isEmpty();
    }

    @Test
    void should_reject_moreThanMaxRecipients() {
        var violations = validator.validateProperty(withRecipients(SendCampaignRequest.MAX_RECIPIENTS + 1), "recipients");
        assertThat(violations).singleElement()
            .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Tek seferde en fazla 1000 alıcıya gönderilebilir"));
    }

    @Test
    void should_capAtOneThousand() {
        assertThat(SendCampaignRequest.MAX_RECIPIENTS).isEqualTo(1000);
    }
}
