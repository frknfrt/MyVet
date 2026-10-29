package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.ImagingFindingsOcrDraft;
import com.vetos.modules.ai.domain.ImagingFindingsOcrInput;
import com.vetos.modules.ai.domain.ImagingFindingsOcrPort;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.modules.ai.domain.exception.NoFileSelectedForOcrException;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrFileTypeException;
import com.vetos.modules.imaging.domain.ImagingRecordFileForOcr;
import com.vetos.modules.imaging.domain.ImagingRecordFileLookupPort;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29,
 * docs/superpowers/plans/2026-10-29-lab-goruntuleme-ai-foto-okuma.md Task 5).
 * ReadLabResultFromImageUseCase ile AYNI AiCallGate/UsageQuotaPort deseni --
 * tek fark: kural motoru yok, OCR'dan donen findings metni dogrudan taslaktir.
 */
@Service
public class ReadImagingFindingsFromImageUseCase {

    private static final String MODEL_NOT_CONNECTED_FINDINGS = "[AI modeli fotografi okuyamadi -- bulgulari elle girin.]";

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
        List<OcrImageInput> images = files.stream()
            .map(f -> new OcrImageInput(f.contentType(), f.content()))
            .toList();

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

        String jobText = draft.modelConnected()
            ? (draft.findings().isBlank() ? "[Bulgu metni bos geldi -- elle girin.]" : draft.findings())
            : MODEL_NOT_CONNECTED_FINDINGS;

        var aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.IMAGING_FINDINGS_OCR, null,
            jobText, providerName, draft.modelVersion(), command.requestedByStaffUserId()
        ));

        return new ReadImagingFindingsFromImageResult(aiJobId, draft.findings(), draft.modelConnected());
    }
}
