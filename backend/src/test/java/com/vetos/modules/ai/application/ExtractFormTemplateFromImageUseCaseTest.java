package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.FormTemplateExtractionDraft;
import com.vetos.modules.ai.domain.FormTemplateExtractionPort;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrImageFormatException;
import com.vetos.modules.consentform.domain.FormItem;
import com.vetos.modules.consentform.domain.FormItemType;
import com.vetos.modules.consentform.domain.FormSourceImageForAi;
import com.vetos.modules.consentform.domain.FormSourceImageLookupPort;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.ratelimit.RateLimitProperties;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Dijital onay formlari Task 3 -- kagit form fotografindan sablon taslagi (sahte port, ag yok). */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ExtractFormTemplateFromImageUseCaseTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};

    @Mock private FormSourceImageLookupPort lookupPort;
    @Mock private RecordAiJobUseCase recordAiJobUseCase;
    @Mock private UsageQuotaPort quota;

    private FakeExtractionPort port;
    private ExtractFormTemplateFromImageUseCase useCase;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID imageId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();

    /** Sirali yanit donen sahte port; cagri sayisini tutar. */
    static class FakeExtractionPort implements FormTemplateExtractionPort {
        final Deque<FormTemplateExtractionDraft> responses = new ArrayDeque<>();
        int calls;
        OcrImageInput lastImage;

        @Override
        public FormTemplateExtractionDraft extract(OcrImageInput image) {
            calls++;
            lastImage = image;
            return responses.size() > 1 ? responses.poll() : responses.peek();
        }
    }

    @BeforeEach
    void setUp() {
        port = new FakeExtractionPort();
        useCase = new ExtractFormTemplateFromImageUseCase(
            lookupPort, port, recordAiJobUseCase, "claude", quota,
            new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(4).build()),
                new TenantRateLimiter(new RateLimitProperties(600, 10, 2000)))
        );
    }

    private void givenJpeg() {
        when(lookupPort.find(imageId)).thenReturn(new FormSourceImageForAi(imageId, "image/jpeg", JPEG));
        when(recordAiJobUseCase.execute(any())).thenReturn(UUID.randomUUID());
    }

    private void respond(String rawJson) {
        port.responses.add(new FormTemplateExtractionDraft(rawJson, true, "claude-test"));
    }

    private ExtractFormTemplateFromImageResult run() {
        return useCase.execute(new ExtractFormTemplateFromImageCommand(tenantId, imageId, staffId));
    }

    private static final String VALID = """
        {"title":"Tedavi Onay Formu","sections":[
          {"title":"Hasta Bilgileri","items":[
            {"type":"SHORT_TEXT","code":"hasta_adi","label":"Adı","text":null,"required":true,"binding":"patient.name","unit":null,"options":null,"multiple":false},
            {"type":"NUMBER","code":"kilo","label":"C.A (Kg)","text":null,"required":false,"binding":"patient.weightKg","unit":"kg","options":null,"multiple":false}
          ]},
          {"title":null,"items":[
            {"type":"TEXT_BLOCK","code":null,"label":null,"text":"{{klinik}} muayenehanesi yetkililerini sorumlu tutmayacağımı beyan ederim.","required":false,"binding":null,"unit":null,"options":null,"multiple":false},
            {"type":"SHORT_TEXT","code":"klinik","label":"Klinik adı","text":null,"required":false,"binding":"clinic.name","unit":null,"options":null,"multiple":false},
            {"type":"SIGNATURE","code":"imza","label":"İmza","text":null,"required":true,"binding":null,"unit":null,"options":null,"multiple":false}
          ]}
        ]}""";

    @Test
    void should_returnParsedContent_when_modelReturnsValidJson() {
        givenJpeg();
        respond(VALID);

        ExtractFormTemplateFromImageResult result = run();

        assertThat(result.modelConnected()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.content().title()).isEqualTo("Tedavi Onay Formu");
        assertThat(result.content().sections()).hasSize(2);
        assertThat(result.content().sections().get(0).items().get(0).binding()).isEqualTo("patient.name");
        assertThat(port.calls).isEqualTo(1);
        assertThat(port.lastImage.contentType()).isEqualTo("image/jpeg");
        verify(quota).reserve(tenantId, UsageKind.AI, 1);
        verify(quota, never()).release(any(), any(), anyInt());

        ArgumentCaptor<RecordAiJobCommand> cmd = ArgumentCaptor.forClass(RecordAiJobCommand.class);
        verify(recordAiJobUseCase).execute(cmd.capture());
        assertThat(cmd.getValue().taskType()).isEqualTo(AiTaskType.FORM_TEMPLATE_EXTRACTION);
        assertThat(cmd.getValue().encounterId()).isNull();
        assertThat(cmd.getValue().suggestionText()).isEqualTo("Form taslağı: Tedavi Onay Formu, 4 alan");
        assertThat(cmd.getValue().modelVersion()).isEqualTo("claude-test");
        assertThat(cmd.getValue().requestedByStaffUserId()).isEqualTo(staffId);
    }

    @Test
    void should_keepFirstSignatureOnly_andWarn() {
        givenJpeg();
        respond("""
            {"title":"Form","sections":[
              {"title":"A","items":[
                {"type":"SIGNATURE","code":"imza","label":"İmza","required":true},
                {"type":"SHORT_TEXT","code":"ad","label":"Ad","required":true}
              ]},
              {"title":"B","items":[
                {"type":"SIGNATURE","code":"imza_2","label":"Veteriner İmza","required":true}
              ]}
            ]}""");

        ExtractFormTemplateFromImageResult result = run();

        assertThat(result.warnings()).containsExactly("Birden fazla imza alanı bulundu; yalnızca ilki tutuldu.");
        List<FormItem> all = result.content().sections().stream().flatMap(s -> s.items().stream()).toList();
        assertThat(all.stream().filter(i -> i.type() == FormItemType.SIGNATURE)).hasSize(1);
        assertThat(all.stream().filter(i -> i.type() == FormItemType.SIGNATURE).findFirst().orElseThrow().code()).isEqualTo("imza");
        // yalniz fazla imzayi iceren bolum bos kalinca dusurulur
        assertThat(result.content().sections()).hasSize(1);
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void should_appendSignature_when_missing() {
        givenJpeg();
        respond("""
            {"title":"Form","sections":[
              {"title":"A","items":[{"type":"SHORT_TEXT","code":"ad","label":"Ad","required":true}]},
              {"title":"B","items":[{"type":"TEXT_BLOCK","text":"Kabul ederim."}]}
            ]}""");

        ExtractFormTemplateFromImageResult result = run();

        assertThat(result.warnings()).containsExactly("İmza alanı bulunamadı; sona eklendi.");
        List<FormItem> last = result.content().sections().get(1).items();
        FormItem sig = last.get(last.size() - 1);
        assertThat(sig.type()).isEqualTo(FormItemType.SIGNATURE);
        assertThat(sig.code()).isEqualTo("imza");
        assertThat(sig.label()).isEqualTo("İmza");
        assertThat(sig.required()).isTrue();
    }

    @Test
    void should_nullUnknownBinding_andWarn() {
        givenJpeg();
        respond("""
            {"title":"Form","sections":[
              {"title":"A","items":[
                {"type":"SHORT_TEXT","code":"veteriner","label":"Veteriner","required":false,"binding":"vet.fullName"},
                {"type":"SIGNATURE","code":"imza","label":"İmza","required":true}
              ]}
            ]}""");

        ExtractFormTemplateFromImageResult result = run();

        assertThat(result.content().sections().get(0).items().get(0).binding()).isNull();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("vet.fullName");
    }

    @Test
    void should_retryOnce_then_fallback_when_jsonInvalidTwice() {
        givenJpeg();
        respond("bu bir json degil");
        respond("{\"title\":\"\",\"sections\":[]}");

        ExtractFormTemplateFromImageResult result = run();

        assertThat(port.calls).isEqualTo(2);
        assertThat(result.modelConnected()).isTrue();
        assertThat(result.warnings()).containsExactly("Form otomatik okunamadı; alanları elle ekleyin.");
        assertFallback(result);
        verify(quota, times(1)).reserve(tenantId, UsageKind.AI, 1);
        verify(quota, never()).release(any(), any(), anyInt());
    }

    @Test
    void should_logWarning_perRejectedAttempt(CapturedOutput output) {
        givenJpeg();
        respond("bu bir json degil");
        respond("{\"title\":\"\",\"sections\":[]}");

        run();

        assertThat(output.getOut())
            .contains("Form sablonu taslagi reddedildi (deneme 1): InvalidFormTemplateException")
            .contains("ham uzunluk=17")
            .contains("bas=bu bir json degil")
            .contains("Form sablonu taslagi reddedildi (deneme 2): InvalidFormTemplateException: Form başlığı boş olamaz");
    }

    @Test
    void should_useSecondAttempt_when_firstInvalid() {
        givenJpeg();
        respond("```json\n{bozuk");
        respond(VALID);

        ExtractFormTemplateFromImageResult result = run();

        assertThat(port.calls).isEqualTo(2);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.content().title()).isEqualTo("Tedavi Onay Formu");
        verify(quota, times(1)).reserve(tenantId, UsageKind.AI, 1);
    }

    @Test
    void should_fallback_without_consumingQuota_when_modelNotConnected() {
        givenJpeg();
        port.responses.add(new FormTemplateExtractionDraft(null, false, "claude-test"));

        ExtractFormTemplateFromImageResult result = run();

        assertThat(port.calls).isEqualTo(1);
        assertThat(result.modelConnected()).isFalse();
        assertThat(result.warnings()).containsExactly("Form otomatik okunamadı; alanları elle ekleyin.");
        assertFallback(result);
        verify(quota).reserve(tenantId, UsageKind.AI, 1);
        verify(quota).release(tenantId, UsageKind.AI, 1);
    }

    @Test
    void should_releaseQuota_when_portThrows() {
        when(lookupPort.find(imageId)).thenReturn(new FormSourceImageForAi(imageId, "image/jpeg", JPEG));
        FormTemplateExtractionPort throwing = image -> { throw new IllegalStateException("boom"); };
        useCase = new ExtractFormTemplateFromImageUseCase(
            lookupPort, throwing, recordAiJobUseCase, "claude", quota,
            new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(4).build()),
                new TenantRateLimiter(new RateLimitProperties(600, 10, 2000)))
        );

        assertThatThrownBy(this::run).isInstanceOf(IllegalStateException.class);
        verify(quota).release(tenantId, UsageKind.AI, 1);
        verifyNoInteractions(recordAiJobUseCase);
    }

    @Test
    void should_rejectHeic() {
        byte[] heic = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        when(lookupPort.find(imageId)).thenReturn(new FormSourceImageForAi(imageId, "image/heic", heic));

        assertThatThrownBy(this::run)
            .isInstanceOf(UnsupportedOcrImageFormatException.class)
            .hasMessageContaining("HEIC");
        assertThat(port.calls).isZero();
        verifyNoInteractions(recordAiJobUseCase, quota);
    }

    private ExtractFormTemplateFromImageResult runWithField(String field) {
        givenJpeg();
        respond("{\"title\":\"Form\",\"sections\":[{\"items\":[" + field
            + ",{\"type\":\"SIGNATURE\",\"code\":\"imza\",\"label\":\"İmza\",\"required\":true}]}]}");
        return run();
    }

    @Test
    void should_keepAgeBindingAndForm_when_modelChoosesNumberForAge() {
        var result = runWithField("""
            {"type":"NUMBER","code":"yas","label":"Yaş","required":true,"binding":"patient.age","unit":"yıl"}
            """);
        var field = result.content().sections().get(0).items().get(0);
        assertThat(result.content().title()).isEqualTo("Form");
        assertThat(field.code()).isEqualTo("yas");
        assertThat(field.label()).isEqualTo("Yaş");
        assertThat(field.required()).isTrue();
        assertThat(field.type()).isEqualTo(FormItemType.SHORT_TEXT);
        assertThat(field.binding()).isEqualTo("patient.age");
        assertThat(field.unit()).isNull();
        assertThat(result.warnings()).anyMatch(w -> w.contains("Yaş") && w.contains("metin"));
        assertThat(port.calls).isEqualTo(1);
        assertThat(result.modelConnected()).isTrue();
        verify(quota, never()).release(any(), any(), anyInt());
    }

    @Test
    void should_stripIrrelevantMetadataFromText_andPreserveItsValueDefinition() {
        var result = runWithField("""
            {"type":"SHORT_TEXT","code":"ad","label":"Hasta adı","required":true,"binding":"patient.name",
             "text":"Yanlış metin","unit":"kg","options":["A","B"],"multiple":true}
            """);
        var field = result.content().sections().get(0).items().get(0);
        assertThat(field.code()).isEqualTo("ad");
        assertThat(field.label()).isEqualTo("Hasta adı");
        assertThat(field.required()).isTrue();
        assertThat(field.binding()).isEqualTo("patient.name");
        assertThat(field.text()).isNull();
        assertThat(field.unit()).isNull();
        assertThat(field.options()).isNull();
        assertThat(field.multiple()).isFalse();
        assertThat(result.warnings()).isEmpty();
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void should_stripCodeBindingAndInputMetadataFromParagraph_butPreserveText() {
        var result = runWithField("""
            {"type":"TEXT_BLOCK","code":"yanlis","label":null,"text":"Onay metni korunmalı.",
             "binding":"owner.fullName","unit":"kg","options":["A","B"],"multiple":true}
            """);
        var field = result.content().sections().get(0).items().get(0);
        assertThat(field.type()).isEqualTo(FormItemType.TEXT_BLOCK);
        assertThat(field.text()).isEqualTo("Onay metni korunmalı.");
        assertThat(field.code()).isNull();
        assertThat(field.binding()).isNull();
        assertThat(field.unit()).isNull();
        assertThat(field.options()).isNull();
        assertThat(field.multiple()).isFalse();
        assertThat(result.warnings()).isEmpty();
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void should_stripInputMetadataFromSignature_butPreserveItsIdentity() {
        givenJpeg();
        respond("""
            {"title":"Form","sections":[{"items":[
              {"type":"SIGNATURE","code":"onam_imza","label":"Sahip imzası","required":true,
               "binding":"owner.fullName","text":"Yanlış metin","unit":"kg","options":["A","B"],"multiple":true}
            ]}]}
            """);
        var result = run();
        var field = result.content().sections().get(0).items().get(0);
        assertThat(field.type()).isEqualTo(FormItemType.SIGNATURE);
        assertThat(field.code()).isEqualTo("onam_imza");
        assertThat(field.label()).isEqualTo("Sahip imzası");
        assertThat(field.required()).isTrue();
        assertThat(field.binding()).isNull();
        assertThat(field.text()).isNull();
        assertThat(field.unit()).isNull();
        assertThat(field.options()).isNull();
        assertThat(field.multiple()).isFalse();
        assertThat(result.warnings()).isEmpty();
        assertThat(port.calls).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   "})
    void should_normalizeBlankNumberUnitAndBindingToNull(String blank) {
        var result = runWithField("""
            {"type":"NUMBER","code":"sayi","label":"Sayı","unit":"%s","binding":"%s",
             "text":"Yanlış metin","options":["A","B"],"multiple":true}
            """.formatted(blank, blank));
        var field = result.content().sections().get(0).items().get(0);
        assertThat(field.type()).isEqualTo(FormItemType.NUMBER);
        assertThat(field.unit()).isNull();
        assertThat(field.binding()).isNull();
        assertThat(field.text()).isNull();
        assertThat(field.options()).isNull();
        assertThat(field.multiple()).isFalse();
        assertThat(result.warnings()).isEmpty();
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void should_preserveChoiceOptionsOrderAndMultiplicity_whileRemovingTextAndUnit() {
        var result = runWithField("""
            {"type":"CHOICE","code":"cinsiyet","label":"Cinsiyet","binding":"patient.sex",
             "text":"Yanlış metin","unit":"kg","options":["Erkek","Dişi"],"multiple":true}
            """);
        var field = result.content().sections().get(0).items().get(0);
        assertThat(field.type()).isEqualTo(FormItemType.CHOICE);
        assertThat(field.binding()).isEqualTo("patient.sex");
        assertThat(field.options()).containsExactly("Erkek", "Dişi");
        assertThat(field.multiple()).isTrue();
        assertThat(field.text()).isNull();
        assertThat(field.unit()).isNull();
        assertThat(result.warnings()).isEmpty();
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void should_appendSignatureWithUnusedCode_withoutRenamingExistingFields() {
        givenJpeg();
        respond("""
            {"title":"Form","sections":[{"items":[
              {"type":"SHORT_TEXT","code":"imza","label":"Alan 1"},
              {"type":"SHORT_TEXT","code":"imza_2","label":"Alan 2"}
            ]}]}
            """);
        var result = run();
        var fields = result.content().sections().get(0).items();
        assertThat(fields).extracting(FormItem::code).containsExactly("imza", "imza_2", "imza_3");
        assertThat(fields.get(2).type()).isEqualTo(FormItemType.SIGNATURE);
        assertThat(result.warnings()).containsExactly("İmza alanı bulunamadı; sona eklendi.");
        assertThat(port.calls).isEqualTo(1);
    }

    @Test
    void should_retryKnownButIncompatibleBinding_insteadOfSilentlyChangingFieldMeaning() {
        givenJpeg();
        respond(VALID.replace("patient.name", "patient.weightKg"));
        respond(VALID);
        var result = run();
        assertThat(port.calls).isEqualTo(2);
        var field = result.content().sections().get(0).items().get(0);
        assertThat(field.type()).isEqualTo(FormItemType.SHORT_TEXT);
        assertThat(field.binding()).isEqualTo("patient.name");
        assertThat(result.warnings()).isEmpty();
        verify(quota, times(1)).reserve(tenantId, UsageKind.AI, 1);
    }

    private static void assertFallback(ExtractFormTemplateFromImageResult result) {
        assertThat(result.content().title()).isEmpty();
        assertThat(result.content().sections()).hasSize(1);
        assertThat(result.content().sections().get(0).items()).hasSize(1);
        FormItem sig = result.content().sections().get(0).items().get(0);
        assertThat(sig.type()).isEqualTo(FormItemType.SIGNATURE);
        assertThat(sig.code()).isEqualTo("imza");
        assertThat(sig.label()).isEqualTo("İmza");
        assertThat(sig.required()).isTrue();
    }
}
