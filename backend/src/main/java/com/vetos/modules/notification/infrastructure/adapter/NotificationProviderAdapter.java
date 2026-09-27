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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp: Meta WhatsApp Cloud API (Graph API) uzerinden gercek API cagrisi. SMS: Ileti
 * Merkezi'nin send-sms/json REST API'si uzerinden gercek gonderim (api anahtari
 * + hash ile kimlik dogrulama, bkz. https://www.iletimerkezi.com/docs/api/send-sms).
 * Her iki saglayici da kendi kimlik bilgisi grubu bos oldugunda (accessToken/phoneNumberId
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
class NotificationProviderAdapter implements NotificationSendPort {

    private static final double SIMULATED_FAILURE_RATE = 0.1;

    private final String metaAccessToken;
    private final String metaPhoneNumberId;
    private final String metaTemplateName;
    private final String metaTemplateLanguage;
    private final String metaApiVersion;
    private final String iletiMerkeziApiKey;
    private final String iletiMerkeziHash;
    private final String iletiMerkeziSender;
    private final String metaGraphApiBaseUrl;
    private final String iletiMerkeziSendUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    NotificationProviderAdapter(
        @Value("${notification.meta.access-token:}") String metaAccessToken,
        @Value("${notification.meta.phone-number-id:}") String metaPhoneNumberId,
        @Value("${notification.meta.template-name:}") String metaTemplateName,
        @Value("${notification.meta.template-language:en_US}") String metaTemplateLanguage,
        @Value("${notification.meta.api-version:v25.0}") String metaApiVersion,
        @Value("${notification.ileti-merkezi.api-key:}") String iletiMerkeziApiKey,
        @Value("${notification.ileti-merkezi.hash:}") String iletiMerkeziHash,
        @Value("${notification.ileti-merkezi.sender:vetly}") String iletiMerkeziSender
    ) {
        this(
            metaAccessToken, metaPhoneNumberId, metaTemplateName, metaTemplateLanguage, metaApiVersion,
            iletiMerkeziApiKey, iletiMerkeziHash, iletiMerkeziSender,
            "https://graph.facebook.com",
            "https://api.iletimerkezi.com/v1/send-sms/json"
        );
    }

    // paket-ozel: testler yerel sahte sunuculara yonlendirmek icin kullanir
    NotificationProviderAdapter(
        String metaAccessToken, String metaPhoneNumberId, String metaTemplateName, String metaTemplateLanguage, String metaApiVersion,
        String iletiMerkeziApiKey, String iletiMerkeziHash, String iletiMerkeziSender,
        String metaGraphApiBaseUrl, String iletiMerkeziSendUrl
    ) {
        this.metaAccessToken = metaAccessToken;
        this.metaPhoneNumberId = metaPhoneNumberId;
        this.metaTemplateName = metaTemplateName;
        this.metaTemplateLanguage = metaTemplateLanguage;
        this.metaApiVersion = metaApiVersion;
        this.iletiMerkeziApiKey = iletiMerkeziApiKey;
        this.iletiMerkeziHash = iletiMerkeziHash;
        this.iletiMerkeziSender = iletiMerkeziSender;
        this.metaGraphApiBaseUrl = metaGraphApiBaseUrl;
        this.iletiMerkeziSendUrl = iletiMerkeziSendUrl;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean isConfigured() {
        return metaConfigured() || iletiMerkeziConfigured();
    }

    @Override
    public boolean isSmsConfigured() {
        return iletiMerkeziConfigured();
    }

    @Override
    public boolean isWhatsappConfigured() {
        return metaConfigured();
    }

    private boolean metaConfigured() {
        return !metaAccessToken.isBlank() && !metaPhoneNumberId.isBlank();
    }

    private boolean iletiMerkeziConfigured() {
        return !iletiMerkeziApiKey.isBlank() && !iletiMerkeziHash.isBlank();
    }

    @Override
    public NotificationSendOutcome send(NotificationSendRequest request) {
        if (request.channel() == NotificationChannel.WHATSAPP && metaConfigured()) {
            return sendWhatsAppViaMeta(request);
        }
        if (request.channel() == NotificationChannel.SMS && iletiMerkeziConfigured()) {
            return sendSmsViaIletiMerkezi(request);
        }
        return sendSimulated(request);
    }

    private NotificationSendOutcome sendWhatsAppViaMeta(NotificationSendRequest request) {
        String to = toMsisdn(request.recipientContact());

        // Onayli bir sablon adi tanimli degilse serbest metin ("text") gonderilir.
        // WhatsApp Is Politikasi geregi bu, YALNIZCA aliciyla acik bir "musteri hizmeti
        // penceresi" varken (aliciden son 24 saat icinde gelen bir mesaj) calisir.
        // Pencere kapaliysa Meta 131047 hatasi ("re-engagement message") ile reddeder --
        // bu durumda notification.meta.template-name'i onayli bir sablonun adiyla doldurun.
        // Sablon govdesi tek bir degisken icerir (ornek: "Bildirim: {{1}}") -- klinik
        // markasina ozel sabit metin BILEREK yok, cunku degiskenin icine giren mesaj
        // NotificationSendExecutor tarafindan zaten "<Klinik Adi>: <mesaj>" seklinde
        // hazirlaniyor (bkz. o sinifin attemptSend metodu).
        Map<String, Object> body = metaTemplateName.isBlank()
            ? metaTextRequestBody(to, request.message())
            : metaTemplateRequestBody(to, metaTemplateName, metaTemplateLanguage, request.message());

        String url = metaGraphApiBaseUrl + "/" + metaApiVersion + "/" + metaPhoneNumberId + "/messages";

        try {
            String responseBody = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + metaAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toJson(body))
                .retrieve()
                .body(String.class);

            log.info("WhatsApp gonderimi (Meta): alici={}, yanit={}", request.recipientContact(), responseBody);
            return NotificationSendOutcome.success("Meta WhatsApp Cloud API'ye iletildi");
        } catch (RestClientResponseException e) {
            log.warn(
                "Meta WhatsApp gonderimi basarisiz: alici={}, durum={}, govde={}",
                request.recipientContact(), e.getStatusCode(), e.getResponseBodyAsString()
            );
            return NotificationSendOutcome.failure("Meta hatasi (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Meta WhatsApp gonderimi basarisiz: alici={}, hata={}", request.recipientContact(), e.getMessage());
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

    /** Meta Cloud API "to" alani basinda "+" beklemez (bkz. Graph API ornek istekleri). */
    private static String toMsisdn(String rawPhone) {
        String e164 = toE164(rawPhone);
        return e164.startsWith("+") ? e164.substring(1) : e164;
    }

    // paket-ozel: JSON kacislama testi dogrudan cagirir
    static Map<String, Object> metaTextRequestBody(String to, String messageText) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("body", messageText);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", to);
        body.put("type", "text");
        body.put("text", text);
        return body;
    }

    // paket-ozel: JSON kacislama testi dogrudan cagirir
    static Map<String, Object> metaTemplateRequestBody(String to, String templateName, String templateLanguage, String messageText) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        parameter.put("type", "text");
        parameter.put("text", messageText);

        Map<String, Object> bodyComponent = new LinkedHashMap<>();
        bodyComponent.put("type", "body");
        bodyComponent.put("parameters", List.of(parameter));

        Map<String, Object> language = new LinkedHashMap<>();
        language.put("code", templateLanguage);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", language);
        template.put("components", List.of(bodyComponent));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", to);
        body.put("type", "template");
        body.put("template", template);
        return body;
    }

    // paket-ozel: testler yerel sahte sunuculara yonlendirmek icin kullanir
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
            throw new IllegalStateException("Bildirim istek govdesi olusturulamadi", e);
        }
    }
}
