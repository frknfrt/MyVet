package com.vetos.modules.notification.infrastructure.adapter;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationSendOutcome;
import com.vetos.modules.notification.domain.NotificationSendPort;
import com.vetos.modules.notification.domain.NotificationSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

/**
 * WhatsApp: Twilio WhatsApp Sandbox uzerinden gercek API cagrisi (kullanici
 * onayiyla, sadece bu kanal icin -- bkz. implementation-plan.md). SMS icin
 * gercek bir saglayici hesabi henuz yok; eski MockNotificationAdapter'daki
 * ayni simule davranis burada (sendSimulated) korunuyor -- gercek bir SMS
 * saglayicisi eklendiginde sadece sendSimulated'in SMS dalinin yerini
 * alacak bir metod eklenecek (TARBIL/e-Fatura ile ayni Open/Closed deseni).
 * Twilio kimlik bilgileri (accountSid/authToken) bos ise WhatsApp de simule
 * edilir -- kimlik bilgisi tanimlanmamis ortamlarda (CI, yerel gelistirme)
 * uygulama hata vermeden calismaya devam eder.
 */
@Component
@Slf4j
class TwilioNotificationAdapter implements NotificationSendPort {

    private static final double SIMULATED_FAILURE_RATE = 0.1;
    private static final String MESSAGES_URL = "https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json";

    private final String accountSid;
    private final String authToken;
    private final String whatsappFrom;
    private final RestClient restClient;

    TwilioNotificationAdapter(
        @Value("${notification.twilio.account-sid:}") String accountSid,
        @Value("${notification.twilio.auth-token:}") String authToken,
        @Value("${notification.twilio.whatsapp-from:whatsapp:+14155238886}") String whatsappFrom
    ) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.whatsappFrom = whatsappFrom;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean isConfigured() {
        return !accountSid.isBlank() && !authToken.isBlank();
    }

    @Override
    public NotificationSendOutcome send(NotificationSendRequest request) {
        if (request.channel() == NotificationChannel.WHATSAPP && isConfigured()) {
            return sendWhatsAppViaTwilio(request);
        }
        return sendSimulated(request);
    }

    private NotificationSendOutcome sendWhatsAppViaTwilio(NotificationSendRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", "whatsapp:" + toE164(request.recipientContact()));
        form.add("From", whatsappFrom);
        form.add("Body", request.message());

        String credentials = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        try {
            String responseBody = restClient.post()
                .uri(MESSAGES_URL, accountSid)
                .header("Authorization", "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);

            log.info("WhatsApp gonderimi (Twilio): alici={}, yanit={}", request.recipientContact(), responseBody);
            return NotificationSendOutcome.success("Twilio'ya iletildi");
        } catch (RestClientResponseException e) {
            log.warn(
                "Twilio WhatsApp gonderimi basarisiz: alici={}, durum={}, govde={}",
                request.recipientContact(), e.getStatusCode(), e.getResponseBodyAsString()
            );
            return NotificationSendOutcome.failure("Twilio hatasi (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Twilio WhatsApp gonderimi basarisiz: alici={}, hata={}", request.recipientContact(), e.getMessage());
            return NotificationSendOutcome.failure("Saglayiciya ulasilamadi: " + e.getMessage());
        }
    }

    private NotificationSendOutcome sendSimulated(NotificationSendRequest request) {
        log.info(
            "Bildirim gonderimi (mock): kanal={}, alici={}, mesaj=\"{}\"",
            request.channel(), request.recipientContact(), request.message()
        );
        if (Math.random() < SIMULATED_FAILURE_RATE) {
            return NotificationSendOutcome.failure("Saglayici gecici olarak yanit vermedi (simule edilmis hata)");
        }
        return NotificationSendOutcome.success("Iletildi (mock)");
    }

    /** Turkiye pazarina ozel basit normallestirme -- 0'la baslayan yerel numarayi +90'a cevirir. */
    private static String toE164(String rawPhone) {
        String digits = rawPhone.replaceAll("[^+0-9]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("90")) return "+" + digits;
        if (digits.startsWith("0")) return "+90" + digits.substring(1);
        return "+90" + digits;
    }
}
