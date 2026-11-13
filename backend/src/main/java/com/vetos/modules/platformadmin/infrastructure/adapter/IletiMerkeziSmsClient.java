package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.platform.http.OutboundHttp;
import com.vetos.platform.observability.IntegrationOutcomes;
import com.vetos.platform.observability.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ileti Merkezi SMS gonderimi -- platformadmin icindeki adaptorlerin (fatura, deneme kaydi)
 * paylastigi istemci. Kimlik bilgisi bossa gonderim simule edilir (mesaj loga yazilir),
 * hata firlatilmaz.
 */
@Slf4j
final class IletiMerkeziSmsClient {

    static final String DEFAULT_SEND_URL = "https://api.iletimerkezi.com/v1/send-sms/json";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final String apiKey;
    private final String hash;
    private final String sender;
    private final String sendUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationOutcomes outcomes;

    IletiMerkeziSmsClient(String apiKey, String hash, String sender, String sendUrl, IntegrationOutcomes outcomes) {
        this.apiKey = apiKey;
        this.hash = hash;
        this.sender = sender;
        this.sendUrl = sendUrl;
        this.outcomes = outcomes;
        this.restClient = OutboundHttp.restClient(CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    boolean isConfigured() {
        return !apiKey.isBlank() && !hash.isBlank();
    }

    /** @param label log satirlarinda mesajin turu (orn. "Platform fatura SMS'i") */
    /** @return true: saglayici kabul etti ya da (kimlik bilgisi yokken) simule edildi; false: gonderilemedi. */
    boolean send(String recipientPhone, String messageText, String label) {
        if (!isConfigured()) {
            log.info("{} (simule): to={}, mesaj=\"{}\"", label, LogMasking.phone(recipientPhone), messageText);
            return true;
        }

        String requestBody = toJson(requestBody(apiKey, hash, sender, messageText, toE164(recipientPhone)));

        try {
            Map<?, ?> response = restClient.post()
                .uri(sendUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);
            // Ileti Merkezi is-seviyesi hatalari (kimlik, bakiye) HTTP 200 + response.status.code olarak doner.
            int code = statusCode(response);
            if (code == 200) {
                outcomes.record(IntegrationOutcomes.SMS, true);
                log.info("{} gonderildi: to={}", label, LogMasking.phone(recipientPhone));
                return true;
            } else {
                outcomes.record(IntegrationOutcomes.SMS, code > 0 && !IntegrationOutcomes.isOutageStatus(code));
                log.warn("{} gonderilemedi: to={}, kod={}", label, LogMasking.phone(recipientPhone), code);
            }
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.SMS, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            log.warn("{} gonderilemedi: to={}, durum={}, govde={}",
                label, LogMasking.phone(recipientPhone), e.getStatusCode(), LogMasking.body(e.getResponseBodyAsString()));
        } catch (Exception e) {
            outcomes.record(IntegrationOutcomes.SMS, false);
            log.warn("{} gonderilemedi: to={}, hata={}", label, LogMasking.phone(recipientPhone), e.getMessage());
        }
        return false;
    }

    private static int statusCode(Map<?, ?> response) {
        Object responseNode = response == null ? null : response.get("response");
        Object status = responseNode instanceof Map<?, ?> node ? node.get("status") : null;
        Object code = status instanceof Map<?, ?> s ? s.get("code") : null;
        return code instanceof Number n ? n.intValue() : -1;
    }

    /** Turkiye pazarina ozel basit normallestirme -- 0 ile baslayan yerel numarayi +90 on ekine cevirir. */
    private static String toE164(String rawPhone) {
        String digits = rawPhone.replaceAll("[^+0-9]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("90")) return "+" + digits;
        if (digits.startsWith("0")) return "+90" + digits.substring(1);
        return "+90" + digits;
    }

    static Map<String, Object> requestBody(String apiKey, String hash, String sender, String messageText, String recipientE164) {
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

    String toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Ileti Merkezi istek govdesi olusturulamadi", e);
        }
    }
}
