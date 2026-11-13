package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.platformadmin.domain.PlatformBillingSmsPort;
import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.platform.http.OutboundHttp;
import com.vetos.platform.observability.IntegrationOutcomes;
import com.vetos.platform.observability.LogMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform fatura SMS bildirimlerini Ileti Merkezi'nin send-sms/json REST
 * API'si uzerinden gonderir (ayni saglayici, notification.NotificationProviderAdapter'in
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

    private static final String LABEL = "Platform fatura SMS'i";
    private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final IletiMerkeziSmsClient client;

    @Autowired
    IletiMerkeziPlatformBillingSmsAdapter(
        @Value("${platform-billing.ileti-merkezi.api-key:}") String apiKey,
        @Value("${platform-billing.ileti-merkezi.hash:}") String hash,
        @Value("${platform-billing.ileti-merkezi.sender:vetly}") String sender,
        IntegrationOutcomes outcomes
    ) {
        this(apiKey, hash, sender, IletiMerkeziSmsClient.DEFAULT_SEND_URL, outcomes);
    }

    IletiMerkeziPlatformBillingSmsAdapter(String apiKey, String hash, String sender, String sendUrl, IntegrationOutcomes outcomes) {
        this.client = new IletiMerkeziSmsClient(apiKey, hash, sender, sendUrl, outcomes);
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
        client.send(recipientPhone, messageText, LABEL);
    }

    // paket-ozel: JSON kacislama testi dogrudan cagirir
    static Map<String, Object> requestBody(String apiKey, String hash, String sender, String messageText, String recipientE164) {
        return IletiMerkeziSmsClient.requestBody(apiKey, hash, sender, messageText, recipientE164);
    }

    String toJson(Map<String, Object> body) {
        return client.toJson(body);
    }
}
