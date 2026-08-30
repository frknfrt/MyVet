package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform fatura SMS bildirimlerini Ileti Merkezi'nin send-sms/json REST
 * API'si uzerinden gonderir (ayni saglayici, notification.TwilioNotificationAdapter'in
 * SMS dali ile ayni API -- ama PlatformBillingSmsPort'un javadoc'unda
 * aciklanan "paralel altyapi" felsefesiyle tutarli sekilde bilerek AYRI, izole
 * bir istemci). ILETI_MERKEZI_API_KEY/ILETI_MERKEZI_HASH bos ise (varsayilan)
 * gonderim tamamen simule edilir -- kimlik bilgisi tanimlanmamis ortamlarda
 * (CI, yerel gelistirme) uygulama hata vermeden calismaya devam eder.
 *
 * Bu port hicbir istisna firlatmaz: cagiran taraf (FlagOverdueAndSuspendUseCase,
 * GenerateDueInvoicesUseCase, RemindDueSoonInvoicesUseCase) her tenant'i tek tek
 * isler, bir SMS gonderim hatasi butun batch'i durdurmamali.
 */
@Component
@Slf4j
class IletiMerkeziPlatformBillingSmsAdapter implements PlatformBillingSmsPort {

    private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final String apiKey;
    private final String hash;
    private final String sender;
    private final String sendUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    IletiMerkeziPlatformBillingSmsAdapter(
        @Value("${platform-billing.ileti-merkezi.api-key:}") String apiKey,
        @Value("${platform-billing.ileti-merkezi.hash:}") String hash,
        @Value("${platform-billing.ileti-merkezi.sender:vetly}") String sender
    ) {
        this(apiKey, hash, sender, "https://api.iletimerkezi.com/v1/send-sms/json");
    }

    // paket-ozel: testler yerel sahte sunuculara yonlendirmek icin kullanir
    IletiMerkeziPlatformBillingSmsAdapter(String apiKey, String hash, String sender, String sendUrl) {
        this.apiKey = apiKey;
        this.hash = hash;
        this.sender = sender;
        this.sendUrl = sendUrl;
        this.restClient = RestClient.create();
    }

    private boolean isConfigured() {
        return !apiKey.isBlank() && !hash.isBlank();
    }

    @Override
    public void sendInvoiceIssued(PlatformInvoice invoice, String tenantName, String recipientPhone) {
        send(recipientPhone, "Vetly: %s icin %s TL tutarinda yeni fatura olusturuldu (son odeme: %s). Detaylar icin panelinize giris yapabilirsiniz.".formatted(
            tenantName, invoice.getAmount().toPlainString(), DUE_DATE_FORMAT.format(invoice.getDueDate())
        ));
    }

    @Override
    public void sendInvoiceDueSoon(PlatformInvoice invoice, String tenantName, String recipientPhone) {
        send(recipientPhone, "Vetly: %s icin %s TL tutarindaki faturanizin son odeme tarihi %s. Lutfen odemenizi zamaninda tamamlayin.".formatted(
            tenantName, invoice.getAmount().toPlainString(), DUE_DATE_FORMAT.format(invoice.getDueDate())
        ));
    }

    @Override
    public void sendTenantSuspended(String tenantName, String recipientPhone) {
        send(recipientPhone, "Vetly: %s hesabiniz odenmemis fatura nedeniyle askiya alindi. Devam etmek icin lutfen odemenizi tamamlayin.".formatted(tenantName));
    }

    private void send(String recipientPhone, String messageText) {
        if (!isConfigured()) {
            log.info("Platform fatura SMS'i (simule): to={}, mesaj=\"{}\"", recipientPhone, messageText);
            return;
        }

        String requestBody = toJson(requestBody(apiKey, hash, sender, messageText, toE164(recipientPhone)));

        try {
            restClient.post()
                .uri(sendUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);
            log.info("Platform fatura SMS'i gonderildi: to={}", recipientPhone);
        } catch (RestClientResponseException e) {
            log.warn("Platform fatura SMS'i gonderilemedi: to={}, durum={}, govde={}", recipientPhone, e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Platform fatura SMS'i gonderilemedi: to={}, hata={}", recipientPhone, e.getMessage());
        }
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
