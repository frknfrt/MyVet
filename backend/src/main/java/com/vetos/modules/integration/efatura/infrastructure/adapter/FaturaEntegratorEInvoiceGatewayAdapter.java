package com.vetos.modules.integration.efatura.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceLineItem;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionOutcome;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * faturaentegrator.com API'si uzerinden e-Fatura/e-Arsiv gonderimi.
 * POST /invoices ASENKRON calisir -- ilk yanitta workflow_status=processing
 * doner, gercek ETTN henuz yoktur (bkz. docs "Örnek yanıt": ettn/serial_number/
 * pdf_url null). Bu yuzden submit() burada DAIMA EInvoiceSubmissionOutcome
 * .pending(...) doner (finalResult=false); nihai sonuc callback_url'e gelen
 * bildirim TETIKLEYICI olarak kullanilip fetchStatus() ile (GET /invoices/{id})
 * sorgulanir (bkz. ApplyEInvoiceCallbackUseCase, FaturaEntegratorCallbackController
 * -- callback govdesi durum bilgisi ICERMEZ, sadece "bir sey degisti" der).
 *
 * efatura.provider=faturaentegrator oldugunda MockEInvoiceGatewayAdapter'in
 * yerini alir (bkz. o adapterdeki @ConditionalOnProperty).
 *
 * customer.id alani dokumantasyonda "number" tipinde ve zorunlu -- ama sahip
 * kayitlarimiz UUID. Gercek TCKN/e-posta ile eslestirme yapmadigimizdan
 * (bkz. buyerIdentifier="11111111111" anonim tuketici karari) sahip UUID'sinden
 * turetilen sabit bir sayisal ID gonderiyoruz: ayni sahip icin hep ayni deger
 * uretilir, boylece saglayici panelinde ayni musteri tekrar tekrar
 * cogaltilmiyor (bkz. deriveCustomerId).
 *
 * BILINEN SINIRLAMA: KDV orani 0 olan satirlar icin GIB istisna kodu/gerekcesi
 * (exemption_code/exemption_reason) modellenmiyor -- InvoiceLine'da bu bilgi
 * yok ve yanlis bir istisna kodu uydurmak yanlis bir resmi belge riski
 * tasir. Boyle bir satir gelirse istek saglayici tarafinda reddedilir ve
 * submission FAILED'e duser (gercek hata mesajiyla) -- sessizce yanlis
 * veri gondermek yerine bilincli bir tercih.
 */
@Component
@ConditionalOnProperty(prefix = "efatura", name = "provider", havingValue = "faturaentegrator")
@Slf4j
class FaturaEntegratorEInvoiceGatewayAdapter implements EInvoiceGatewayPort {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_COUNTRY = "TÜRKİYE";

    private final String apiKey;
    private final long invoiceIntegrationId;
    private final long saleChannelId;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;

    @Autowired
    FaturaEntegratorEInvoiceGatewayAdapter(
        @Value("${efatura.faturaentegrator.api-key:}") String apiKey,
        @Value("${efatura.faturaentegrator.invoice-integration-id:0}") long invoiceIntegrationId,
        @Value("${efatura.faturaentegrator.sale-channel-id:0}") long saleChannelId,
        ObjectMapper objectMapper
    ) {
        this(apiKey, invoiceIntegrationId, saleChannelId, objectMapper, "https://app.faturaentegrator.com/api");
    }

    FaturaEntegratorEInvoiceGatewayAdapter(
        String apiKey, long invoiceIntegrationId, long saleChannelId, ObjectMapper objectMapper, String apiBaseUrl
    ) {
        this.apiKey = apiKey;
        this.invoiceIntegrationId = invoiceIntegrationId;
        this.saleChannelId = saleChannelId;
        this.objectMapper = objectMapper;
        this.apiBaseUrl = apiBaseUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(30_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && invoiceIntegrationId > 0 && saleChannelId > 0;
    }

    @Override
    public EInvoiceSubmissionOutcome submit(EInvoiceSubmissionRequest request) {
        if (!isConfigured()) {
            return EInvoiceSubmissionOutcome.failure(
                "faturaentegrator yapilandirilmamis (EFATURA_FATURAENTEGRATOR_API_KEY / "
                    + "invoice-integration-id / sale-channel-id eksik)"
            );
        }
        try {
            String raw = restClient.post()
                .uri(apiBaseUrl + "/invoices")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequestBody(request))
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(raw).path("data");
            String providerReference = data.path("id").asText(null);
            String workflowStatus = data.path("workflow_status").asText("processing");
            if (providerReference == null || providerReference.isBlank()) {
                log.warn("faturaentegrator beklenmeyen yanit dondu (data.id yok): {}", raw);
                return EInvoiceSubmissionOutcome.failure("faturaentegrator yaniti beklenmeyen bicimde (data.id eksik)");
            }
            return EInvoiceSubmissionOutcome.pending(
                providerReference, "Fatura saglayiciya iletildi, GIB resmilesmesi bekleniyor (workflow_status=" + workflowStatus + ")"
            );
        } catch (RestClientResponseException e) {
            log.warn("faturaentegrator gonderimi basarisiz: durum={}, govde={}", e.getStatusCode(), e.getResponseBodyAsString());
            return EInvoiceSubmissionOutcome.failure("faturaentegrator reddetti (HTTP " + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("faturaentegrator gonderimi basarisiz: {}", e.getMessage());
            return EInvoiceSubmissionOutcome.failure("faturaentegrator cagrisi basarisiz: " + e.getMessage());
        }
    }

    /**
     * GET /invoices/{id} -- callback bildirimi durum bilgisi tasimadigi icin
     * (bkz. sinif javadoc'u) guncel sonucu almak icin ayrica cagrilir.
     * ettn dolmussa (resmilesme tamamlandi) basarili/finalResult=true,
     * errors[] doluysa basarisiz, ikisi de yoksa hala isleniyor (pending) sayilir.
     */
    @Override
    public EInvoiceSubmissionOutcome fetchStatus(String providerReference) {
        try {
            String raw = restClient.get()
                .uri(apiBaseUrl + "/invoices/" + providerReference)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(String.class);

            JsonNode data = objectMapper.readTree(raw).path("data");
            String ettn = data.path("ettn").asText(null);
            JsonNode errors = data.path("errors");
            if (ettn != null && !ettn.isBlank()) {
                return EInvoiceSubmissionOutcome.success(ettn, "GIB resmilesmesi tamamlandi");
            }
            if (errors.isArray() && !errors.isEmpty()) {
                return EInvoiceSubmissionOutcome.failure("faturaentegrator hata bildirdi: " + errors);
            }
            String workflowStatus = data.path("workflow_status").asText("processing");
            return EInvoiceSubmissionOutcome.pending(providerReference, "Hala isleniyor (workflow_status=" + workflowStatus + ")");
        } catch (RestClientResponseException e) {
            log.warn("faturaentegrator durum sorgusu basarisiz: durum={}, govde={}", e.getStatusCode(), e.getResponseBodyAsString());
            return EInvoiceSubmissionOutcome.failure("faturaentegrator durum sorgusu basarisiz (HTTP " + e.getStatusCode() + ")");
        } catch (Exception e) {
            log.warn("faturaentegrator durum sorgusu basarisiz: {}", e.getMessage());
            return EInvoiceSubmissionOutcome.failure("faturaentegrator durum sorgusu basarisiz: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRequestBody(EInvoiceSubmissionRequest request) {
        ZonedDateTime now = ZonedDateTime.now(ISTANBUL);
        LocalDate issueDate = now.toLocalDate();
        LocalTime issueTime = now.toLocalTime();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sale_channel_id", saleChannelId);
        body.put("invoice_integration_id", invoiceIntegrationId);
        body.put("type", "SATIS");
        body.put("issue_date", issueDate.format(DATE_FORMAT));
        body.put("issue_time", issueTime.format(TIME_FORMAT));
        body.put("cash_sale", true);
        body.put("payment_date", issueDate.format(DATE_FORMAT));
        body.put("currency", "TRY");
        body.put("currency_rate", 1);
        body.put("callback_url", request.callbackUrl());
        body.put("lines", request.lines().stream().map(this::toLineMap).toList());
        body.put("customer", buildCustomer(request));
        return body;
    }

    private Map<String, Object> toLineMap(EInvoiceLineItem line) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", line.externalId());
        map.put("name", line.name());
        map.put("quantity", line.quantity());
        map.put("unit", line.unit());
        map.put("unit_price", line.unitPrice());
        map.put("tax_rate", formatVatRate(line.vatRatePercent()));
        return map;
    }

    /** API'nin kabul ettigi tam sayi yuzde string kumesine ("0","1","2","8","10","18","20") map eder. */
    private String formatVatRate(BigDecimal vatRatePercent) {
        return vatRatePercent.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString();
    }

    private Map<String, Object> buildCustomer(EInvoiceSubmissionRequest request) {
        String[] nameParts = splitName(request.buyerName());
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", deriveCustomerId(request.ownerId()));
        customer.put("type", "person");
        customer.put("tax_number", request.buyerIdentifier());
        customer.put("name", nameParts[0]);
        customer.put("surname", nameParts[1]);
        customer.put("country", DEFAULT_COUNTRY);
        customer.put("address", nullToDash(request.buyerAddress()));
        customer.put("city", nullToDash(request.buyerCity()));
        customer.put("district", nullToDash(request.buyerDistrict()));
        return customer;
    }

    private String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[] {"-", "-"};
        }
        String trimmed = fullName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new String[] {trimmed, "-"};
        }
        return new String[] {trimmed.substring(0, lastSpace).trim(), trimmed.substring(lastSpace + 1).trim()};
    }

    /**
     * Sahip (owner) UUID'sinden sabit, panel-uyumlu bir sayisal musteri ID'si
     * turetir -- ayni sahip icin her zaman ayni deger uretilir, boylece
     * saglayici panelinde musteri kaydi tekrar tekrar cogaltilmaz (bkz. sinif
     * javadoc'u). GIB'e iletilmez, sadece saglayicinin ic musteri eslestirmesi
     * icindir.
     */
    private long deriveCustomerId(UUID ownerId) {
        return Math.floorMod(ownerId.getMostSignificantBits(), 1_000_000_000L);
    }
}
