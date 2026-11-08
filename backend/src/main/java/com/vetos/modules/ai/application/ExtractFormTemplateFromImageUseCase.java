package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.FormTemplateExtractionDraft;
import com.vetos.modules.ai.domain.FormTemplateExtractionPort;
import com.vetos.modules.ai.domain.OcrImageFormatDetector;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.modules.ai.domain.OcrImageNormalizer;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrImageFormatException;
import com.vetos.modules.consentform.domain.FormBinding;
import com.vetos.modules.consentform.domain.FormItem;
import com.vetos.modules.consentform.domain.FormItemType;
import com.vetos.modules.consentform.domain.FormSection;
import com.vetos.modules.consentform.domain.FormSourceImageForAi;
import com.vetos.modules.consentform.domain.FormSourceImageLookupPort;
import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.FormTemplateContentCodec;
import com.vetos.modules.consentform.domain.FormTemplateContentValidator;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dijital onay formlari (2026-11-07, Task 3): kagit onay formu fotografindan sablon TASLAGI.
 * ReadLabResultFromImageUseCase ile AYNI desen (gercek bayt formati, EXIF duzeltme, AiCallGate icinde
 * kota ayirma, model bagli degilse kota iadesi). Adaptor yalniz ham JSON doner; ayristirma, kural
 * tabanli temizlik (imza sayisi, bilinmeyen baglanti) ve dogrulama burada yapilir. Gecersizse ayni
 * kota rezervasyonu icinde BIR KEZ daha denenir; yine olmazsa bos taslak (yalniz imza) doner.
 */
@Service
@Slf4j
public class ExtractFormTemplateFromImageUseCase {

    static final String WARN_MULTIPLE_SIGNATURES = "Birden fazla imza alanı bulundu; yalnızca ilki tutuldu.";
    static final String WARN_SIGNATURE_APPENDED = "İmza alanı bulunamadı; sona eklendi.";
    static final String WARN_UNREADABLE = "Form otomatik okunamadı; alanları elle ekleyin.";
    private static final String UNREADABLE_SUMMARY = "[AI modeli formu okuyamadi -- alanlari elle ekleyin.]";
    private static final String SIGNATURE_CODE = "imza";
    private static final int RAW_LOG_PREFIX = 200;

    private final FormSourceImageLookupPort formSourceImageLookupPort;
    private final FormTemplateExtractionPort extractionPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String providerName;
    private final UsageQuotaPort usageQuotaPort;
    private final AiCallGate aiCallGate;

    public ExtractFormTemplateFromImageUseCase(
        FormSourceImageLookupPort formSourceImageLookupPort,
        FormTemplateExtractionPort extractionPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.provider:ollama}") String providerName,
        UsageQuotaPort usageQuotaPort,
        AiCallGate aiCallGate
    ) {
        this.formSourceImageLookupPort = formSourceImageLookupPort;
        this.extractionPort = extractionPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.providerName = providerName;
        this.usageQuotaPort = usageQuotaPort;
        this.aiCallGate = aiCallGate;
    }

    public ExtractFormTemplateFromImageResult execute(ExtractFormTemplateFromImageCommand command) {
        FormSourceImageForAi source = formSourceImageLookupPort.find(command.sourceImageId());

        // Lab OCR ile ayni: declare edilen contentType'a degil gercek baytlara bakilir (HEIC erken reddedilir).
        OcrImageFormatDetector.Format format = OcrImageFormatDetector.detect(source.content());
        if (!OcrImageFormatDetector.isSupportedByVisionApis(format)) {
            throw new UnsupportedOcrImageFormatException(source.id(), format == OcrImageFormatDetector.Format.HEIC);
        }
        OcrImageInput image = new OcrImageInput(
            OcrImageFormatDetector.mediaTypeFor(format), OcrImageNormalizer.normalize(format, source.content()));

        // Kota ayirma kapinin icinde; ikinci deneme ayni rezervasyonu kullanir (ek kota yok).
        Outcome outcome = aiCallGate.run(command.tenantId(), () -> {
            usageQuotaPort.reserve(command.tenantId(), UsageKind.AI, 1);
            FormTemplateExtractionDraft draft;
            Parsed parsed;
            try {
                draft = extractionPort.extract(image);
                parsed = draft.modelConnected() ? tryParse(draft.rawJson(), 1) : null;
                if (draft.modelConnected() && parsed == null) {
                    draft = extractionPort.extract(image);
                    parsed = draft.modelConnected() ? tryParse(draft.rawJson(), 2) : null;
                }
            } catch (RuntimeException e) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
                throw e;
            }
            if (!draft.modelConnected()) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
            }
            return new Outcome(draft, parsed);
        });

        FormTemplateContent content;
        List<String> warnings;
        String summary;
        if (outcome.parsed() != null) {
            content = outcome.parsed().content();
            warnings = outcome.parsed().warnings();
            summary = "Form taslağı: " + content.title() + ", " + countFields(content) + " alan";
        } else {
            content = fallbackContent();
            warnings = List.of(WARN_UNREADABLE);
            summary = UNREADABLE_SUMMARY;
        }

        UUID aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.FORM_TEMPLATE_EXTRACTION, null,
            summary, providerName, outcome.draft().modelVersion(), command.requestedByStaffUserId()
        ));

        return new ExtractFormTemplateFromImageResult(aiJobId, outcome.draft().modelConnected(), content, warnings);
    }

    /** Ayristir + temizle + dogrula; herhangi bir adim basarisizsa null (yeniden denenir). */
    private static Parsed tryParse(String rawJson, int attempt) {
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Form sablonu taslagi reddedildi (deneme {}): model bos yanit dondu", attempt);
            return null;
        }
        try {
            FormTemplateContent raw = FormTemplateContentCodec.fromJson(rawJson);
            List<String> warnings = new ArrayList<>();
            FormTemplateContent cleaned = clean(raw, warnings);
            FormTemplateContentValidator.validate(cleaned);
            return new Parsed(cleaned, List.copyOf(warnings));
        } catch (RuntimeException e) {
            // Genis catch bilincli (modul siniri); teshis icin sebep + ham yanitin basi loglanir
            // (yalniz form metni, hasta verisi bu akista yok).
            log.warn("Form sablonu taslagi reddedildi (deneme {}): {}: {} | ham uzunluk={} | bas={}",
                attempt, e.getClass().getSimpleName(), e.getMessage(), rawJson.length(),
                rawJson.substring(0, Math.min(RAW_LOG_PREFIX, rawJson.length())));
            return null;
        }
    }

    private static FormTemplateContent clean(FormTemplateContent raw, List<String> warnings) {
        List<FormSection> sections = new ArrayList<>();
        boolean signatureSeen = false;
        boolean extraSignatureDropped = false;
        Set<String> codes = new HashSet<>();

        if (raw.sections() != null) {
            for (FormSection section : raw.sections()) {
                if (section == null) continue;
                List<FormItem> items = new ArrayList<>();
                boolean droppedHere = false;
                if (section.items() != null) {
                    for (FormItem item : section.items()) {
                        if (item == null) continue;
                        if (item.type() == FormItemType.SIGNATURE) {
                            if (signatureSeen) {
                                extraSignatureDropped = true;
                                droppedHere = true;
                                continue;
                            }
                            signatureSeen = true;
                        }
                        FormItem normalized = normalize(item, warnings);
                        if (normalized.code() != null) codes.add(normalized.code());
                        items.add(normalized);
                    }
                }
                // Yalniz fazla imzayi iceren bolum bosalirsa atilir; baska sebeple bos bolum validator'a kalir.
                if (items.isEmpty() && droppedHere) continue;
                sections.add(new FormSection(section.title(), items));
            }
        }
        if (extraSignatureDropped) {
            warnings.add(WARN_MULTIPLE_SIGNATURES);
        }
        if (!signatureSeen && !sections.isEmpty()) {
            FormSection last = sections.remove(sections.size() - 1);
            List<FormItem> items = new ArrayList<>(last.items());
            items.add(signatureItem(uniqueCode(codes)));
            sections.add(new FormSection(last.title(), items));
            warnings.add(WARN_SIGNATURE_APPENDED);
        }
        return new FormTemplateContent(raw.title(), sections);
    }

    /** Ture uymayan metadata temizlenir; yas normalizasyonu ve bilinmeyen baglantilar uyariyla bildirilir. */
    private static FormItem normalize(FormItem item, List<String> warnings) {
        FormItemType type = item.type();
        boolean block = type == FormItemType.TEXT_BLOCK;
        String binding = blankToNull(item.binding());
        if (block || type == FormItemType.SIGNATURE) {
            binding = null;
        } else if (binding != null && FormBinding.fromKey(binding).isEmpty()) {
            warnings.add("Bilinmeyen veri bağlantısı kaldırıldı: " + binding
                + (item.label() != null ? " (" + item.label() + ")" : ""));
            binding = null;
        } else if (FormBinding.PATIENT_AGE.key().equals(binding) && type == FormItemType.NUMBER) {
            // Yas kayittan "2 yil 3 ay" gibi metin gelir; sayisal AI secimi otomatik doldurmayi bozmamali.
            type = FormItemType.SHORT_TEXT;
            warnings.add("Yaş alanı otomatik doldurma için metin alanına dönüştürüldü: " + item.label());
        }
        return new FormItem(
            type,
            block ? null : item.code(),
            item.label(),
            block ? item.text() : null,
            item.required(),
            binding,
            type == FormItemType.NUMBER ? blankToNull(item.unit()) : null,
            type == FormItemType.CHOICE ? item.options() : null,
            type == FormItemType.CHOICE && item.multiple()
        );
    }

    private static String uniqueCode(Set<String> codes) {
        String code = SIGNATURE_CODE;
        for (int i = 2; codes.contains(code); i++) {
            code = SIGNATURE_CODE + "_" + i;
        }
        return code;
    }

    private static FormItem signatureItem(String code) {
        return new FormItem(FormItemType.SIGNATURE, code, "İmza", null, true, null, null, null, false);
    }

    private static FormTemplateContent fallbackContent() {
        return new FormTemplateContent("", List.of(new FormSection(null, List.of(signatureItem(SIGNATURE_CODE)))));
    }

    private static long countFields(FormTemplateContent content) {
        return content.sections().stream()
            .flatMap(s -> s.items().stream())
            .filter(i -> i.type() != FormItemType.TEXT_BLOCK)
            .count();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private record Parsed(FormTemplateContent content, List<String> warnings) {}

    private record Outcome(FormTemplateExtractionDraft draft, Parsed parsed) {}
}
