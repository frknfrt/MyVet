package com.vetos.modules.notification.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationSendOutcome;
import com.vetos.modules.notification.domain.NotificationSendPort;
import com.vetos.modules.notification.domain.NotificationSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp: Twilio WhatsApp Sandbox uzerinden gercek API cagrisi. SMS: Ileti
 * Merkezi'nin send-sms/json REST API'si uzerinden gercek gonderim (api anahtari
 * + hash ile kimlik dogrulama, bkz. https://www.iletimerkezi.com/docs/api/send-sms).
 * Her iki saglayici da kendi kimlik bilgisi grubu bos oldugunda (accountSid/authToken
 * ya da iletiMerkeziApiKey/iletiMerkeziHash) o kanal icin simule edilir -- kimlik
 * bilgisi tanimlanmamis ortamlarda (CI, yerel gelistirme) uygulama hata vermeden
 * calismaya devam eder. isConfigured() en az bir kanal gercek bir saglayiciya
 * bagliysa true doner (bkz. NotificationSendPort javadoc).
 *
 * IYS (Ileti Yonetim Sistemi) alani her zaman "0" (ticari olmayan/bilgilendirme)
 * olarak gonderilir -- kampanya/pazarlama SMS'leri icin gercek ticari onay/IYS
 * listesi entegrasyonu kapsam disi.
 */
@Component
@Slf4j
class TwilioNotificationAdapter implements NotificationSendPort {

    private static final double SIMULATED_FAILURE_RATE = 0.1;

    private final String accountSid;
    private final String authSid;
    private final String authToken;
    private final String whatsappFrom;
    private final String iletiMerkeziApiKey;
    private final String iletiMerkeziHash;
    private final String iletiMerkeziSender;
    private final String twilioMessagesUrl;
    private final String iletiMerkeziSendUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    TwilioNotificationAdapter(
        @Value("${notification.twilio.account-sid:}") String accountSid,
        @Value("${notification.twilio.auth-sid:}") String authSid,
        @Value("${notification.twilio.auth-token:}") String authToken,
        @Value("${notification.twilio.whatsapp-from:whatsapp:+14155238886}") String whatsappFrom,
        @Value("${notification.ileti-merkezi.api-key:}") String iletiMerkeziApiKey,
        @Value("${notification.ileti-merkezi.hash:}") String iletiMerkeziHash,
        @Value("${notification.ileti-merkezi.sender:vetly}") String iletiMerkeziSender
    ) {
        this(
            accountSid, authSid, authToken, whatsappFrom, iletiMerkeziApiKey, iletiMerkeziHash, iletiMerkeziSender,
            "https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json",
            "https://api.iletimerkezi.com/v1/send-sms/json"
        );
    }

    // paket-ozel: testler yerel sahte sunuculara yonlendirmek icin kullanir
    TwilioNotificationAdapter(
        String accountSid, String authSid, String authToken, String whatsappFrom,
        String iletiMerkeziApiKey, String iletiMerkeziHash, String iletiMerkeziSender,
        String twilioMessagesUrl, String iletiMerkeziSendUrl
    ) {
        this.accountSid = accountSid;
        // Twilio, Basic Auth kullanici adi olarak ya gercek Account SID'i (AC...)
        // ya da ayri bir API Key SID'i (SK...) kabul eder -- API Key kullanilan
        // hesaplarda bu ikisi FARKLI degerlerdir. authSid bos ise (klasik
        // Account SID + Auth Token modu) accountSid'e geri duser.
        this.authSid = authSid.isBlank() ? accountSid : authSid;
        this.authToken = authToken;
        this.whatsappFrom = whatsappFrom;
        this.iletiMerkeziApiKey = iletiMerkeziApiKey;
        this.iletiMerkeziHash = iletiMerkeziHash;
        this.iletiMerkeziSender = iletiMerkeziSender;
        this.twilioMessagesUrl = twilioMessagesUrl;
        this.iletiMerkeziSendUrl = iletiMerkeziSendUrl;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean isConfigured() {
        return twilioConfigured() || iletiMerkeziConfigured();
    }

    @Override
    public boolean isSmsConfigured() {
        return iletiMerkeziConfigured();
    }

    @Override
    public boolean isWhatsappConfigured() {
        return twilioConfigured();
    }

    private boolean twilioConfigured() {
        return !accountSid.isBlank() && !authToken.isBlank();
    }

    private boolean iletiMerkeziConfigured() {
        return !iletiMerkeziApiKey.isBlank() && !iletiMerkeziHash.isBlank();
    }

    @Override
    public NotificationSendOutcome send(NotificationSendRequest request) {
        if (request.channel() == NotificationChannel.WHATSAPP && twilioConfigured()) {
            return sendWhatsAppViaTwilio(request);
        }
        if (request.channel() == NotificationChannel.SMS && iletiMerkeziConfigured()) {
            return sendSmsViaIletiMerkezi(request);
        }
        return sendSimulated(request);
    }

    private NotificationSendOutcome sendWhatsAppViaTwilio(NotificationSendRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", "whatsapp:" + toE164(request.recipientContact()));
        form.add("From", whatsappFrom);
        form.add("Body", request.message());

        String credentials = Base64.getEncoder().encodeToString((authSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        try {
            String responseBody = restClient.post()
                .uri(twilioMessagesUrl, accountSid)
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

    @SuppressWarnings("unchecked")
    private NotificationSendOutcome sendSmsViaIletiMerkezi(NotificationSendRequest request) {
        String requestBody = toJson(iletiMerkeziRequestBody(
            iletiMerkeziApiKey, iletiMerkeziHash, iletiMerkeziSender, request.message(), toE164(request.recipientContact())
        ));

        try {
            Map<String, Object> response = restClient.post()
                .uri(iletiMerkeziSendUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

            Map<String, Object> responseNode = response == null ? null : (Map<String, Object>) response.get("response");
            Map<String, Object> status = responseNode == null ? null : (Map<String, Object>) responseNode.get("status");
            int code = status != null && status.get("code") instanceof Number n ? n.intValue() : -1;
            String statusMessage = status == null ? "bos yanit" : String.valueOf(status.get("message"));

            if (code == 200) {
                log.info("SMS gonderimi (Ileti Merkezi): alici={}", request.recipientContact());
                return NotificationSendOutcome.success("Ileti Merkezi'ne iletildi");
            }
            log.warn("Ileti Merkezi SMS gonderimi basarisiz: alici={}, kod={}, mesaj={}", request.recipientContact(), code, statusMessage);
            return NotificationSendOutcome.failure("Ileti Merkezi hatasi (" + code + "): " + statusMessage);
        } catch (RestClientResponseException e) {
            log.warn(
                "Ileti Merkezi SMS gonderimi basarisiz: alici={}, durum={}, govde={}",
                request.recipientContact(), e.getStatusCode(), e.getResponseBodyAsString()
            );
            return NotificationSendOutcome.failure("Ileti Merkezi hatasi (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Ileti Merkezi SMS gonderimi basarisiz: alici={}, hata={}", request.recipientContact(), e.getMessage());
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

    // paket-ozel: JSON kacislama testi dogrudan cagirir
    static Map<String, Object> iletiMerkeziRequestBody(
        String apiKey, String hash, String sender, String messageText, String recipientE164
    ) {
        Map<String, Object> authentication = new LinkedHashMap<>();
        authentication.put("key", apiKey);
        authentication.put("hash", hash);

        Map<String, Object> receipents = new LinkedHashMap<>();
        receipents.put("number", List.of(recipientE164));

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("text", messageText);
        message.put("receipents", receipents);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("sender", sender);
        order.put("iys", "0");
        order.put("message", message);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("authentication", authentication);
        request.put("order", order);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("request", request);
        return root;
    }

    // paket-ozel: govde her zaman gecerli JSON uretmeli, elle String birlestirme
    // kacislanmamis tirnaklarla (mesaj metni, telefon) govdeyi bozabilirdi
    String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ileti Merkezi istek govdesi olusturulamadi", e);
        }
    }
}
