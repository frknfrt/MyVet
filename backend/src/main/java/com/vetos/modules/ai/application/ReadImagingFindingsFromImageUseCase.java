package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.ImagingFindingsOcrDraft;
import com.vetos.modules.ai.domain.ImagingFindingsOcrInput;
import com.vetos.modules.ai.domain.ImagingFindingsOcrPort;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.modules.ai.domain.OcrImageFormatDetector;
import com.vetos.modules.ai.domain.OcrImageNormalizer;
import com.vetos.modules.ai.domain.exception.NoFileSelectedForOcrException;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrFileTypeException;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrImageFormatException;
import com.vetos.modules.imaging.domain.ImagingRecordFileForOcr;
import com.vetos.modules.imaging.domain.ImagingRecordFileLookupPort;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29,
 * docs/superpowers/plans/2026-10-29-lab-goruntuleme-ai-foto-okuma.md Task 5).
 * ReadLabResultFromImageUseCase ile AYNI AiCallGate/UsageQuotaPort deseni --
 * tek fark: kural motoru yok, OCR'dan donen findings metni dogrudan taslaktir.
 *
 * REVIZE (2026-10-31, Task 11): kullanici talebi uzerine AI artik HAM rontgen/
 * USG goruntulerini de gorsel olarak tanimliyor (bkz. ImagingFindingsOcrDraft
 * javadoc'u ve Ollama/ClaudeImagingFindingsOcrAdapter'daki prompt revizyonu).
 * Guvenlik/sorumluluk nedeniyle, draft.rawImageInterpretation() true oldugunda
 * RAW_IMAGE_DISCLAIMER metni findings'in basina BURADA, DETERMINISTIK olarak
 * eklenir -- modelin kendisinin her zaman bir uyari yazacagina guvenilmez
 * (bkz. Task 10 markdown-fence dersi). Bu uyari, AI is kaydina (RecordAiJobUseCase)
 * yazilan metne VE frontend'e donen sonuca AYNI sekilde uygulanir, boylece
 * ikisi arasinda tutarsizlik olmaz.
 */
@Service
public class ReadImagingFindingsFromImageUseCase {

    private static final String MODEL_NOT_CONNECTED_FINDINGS = "[AI modeli fotografi okuyamadi -- bulgulari elle girin.]";
    // REVIZE (2026-11-01, canli QA bulgusu): bu metin Turkce karaktersiz (ASCII)
    // yazilmisti -- goruntuleme modulunun ESKI (Task 4/5) prompt konvansiyonunu
    // taklit etmistim, ama urunun geri kalani (ornegin PatientHistoryFormatter'in
    // AI'ya gonderdigi metin) ve lab-OCR'in "Genel Degerlendirme" ciktisi duzgun
    // Turkce karakter kullaniyor -- derleme/calisma zamaninda hicbir sorun yok
    // (pom.xml UTF-8), bu yuzden burada da duzgun Turkce karakter kullaniliyor.
    private static final String RAW_IMAGE_DISCLAIMER =
        "[AI görsel ön-değerlendirme -- bu kesin bir tanı DEĞİLDİR, görüntüyü mutlaka kendiniz değerlendirin] ";

    private final ImagingRecordFileLookupPort imagingRecordFileLookupPort;
    private final ImagingFindingsOcrPort imagingFindingsOcrPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String providerName;
    private final UsageQuotaPort usageQuotaPort;
    private final AiCallGate aiCallGate;

    public ReadImagingFindingsFromImageUseCase(
        ImagingRecordFileLookupPort imagingRecordFileLookupPort,
        ImagingFindingsOcrPort imagingFindingsOcrPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.provider:ollama}") String providerName,
        UsageQuotaPort usageQuotaPort,
        AiCallGate aiCallGate
    ) {
        this.imagingRecordFileLookupPort = imagingRecordFileLookupPort;
        this.imagingFindingsOcrPort = imagingFindingsOcrPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.providerName = providerName;
        this.usageQuotaPort = usageQuotaPort;
        this.aiCallGate = aiCallGate;
    }

    public ReadImagingFindingsFromImageResult execute(ReadImagingFindingsFromImageCommand command) {
        if (command.fileIds() == null || command.fileIds().isEmpty()) {
            throw new NoFileSelectedForOcrException();
        }

        List<ImagingRecordFileForOcr> files = imagingRecordFileLookupPort.findFiles(command.imagingRecordId(), command.fileIds());
        for (ImagingRecordFileForOcr file : files) {
            if (file.contentType() == null || !file.contentType().startsWith("image/")) {
                throw new UnsupportedOcrFileTypeException(file.id());
            }
        }
        // REVIZE (2026-10-31): bkz. ReadLabResultFromImageUseCase'deki ayni revizyon notu -- GERCEK bayt
        // formati kontrol edilir, declare edilen contentType'a guvenilmez.
        List<OcrImageInput> images = new ArrayList<>();
        for (ImagingRecordFileForOcr file : files) {
            OcrImageFormatDetector.Format format = OcrImageFormatDetector.detect(file.content());
            if (!OcrImageFormatDetector.isSupportedByVisionApis(format)) {
                throw new UnsupportedOcrImageFormatException(file.id(), format == OcrImageFormatDetector.Format.HEIC);
            }
            // REVIZE (2026-10-31): bkz. ReadLabResultFromImageUseCase'deki ayni revizyon notu -- EXIF
            // Orientation duzeltmesi piksel duzeyinde burada uygulanir.
            byte[] normalized = OcrImageNormalizer.normalize(format, file.content());
            images.add(new OcrImageInput(OcrImageFormatDetector.mediaTypeFor(format), normalized));
        }

        ImagingFindingsOcrDraft draft = aiCallGate.run(command.tenantId(), () -> {
            usageQuotaPort.reserve(command.tenantId(), UsageKind.AI, 1);
            ImagingFindingsOcrDraft generated;
            try {
                generated = imagingFindingsOcrPort.generate(new ImagingFindingsOcrInput(images));
            } catch (RuntimeException e) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
                throw e;
            }
            if (!generated.modelConnected()) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
            }
            return generated;
        });

        // REVIZE (2026-10-31, Task 11): uyari metni modelin kendi ciktisina degil, buradaki
        // deterministik kontrole birakilir -- bkz. sinif javadoc'u.
        String findings = (draft.rawImageInterpretation() && !draft.findings().isBlank())
            ? RAW_IMAGE_DISCLAIMER + draft.findings()
            : draft.findings();

        String jobText = draft.modelConnected()
            ? (findings.isBlank() ? "[Bulgu metni bos geldi -- elle girin.]" : findings)
            : MODEL_NOT_CONNECTED_FINDINGS;

        var aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.IMAGING_FINDINGS_OCR, null,
            jobText, providerName, draft.modelVersion(), command.requestedByStaffUserId()
        ));

        return new ReadImagingFindingsFromImageResult(aiJobId, findings, draft.modelConnected());
    }
}
