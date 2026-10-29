package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import com.vetos.modules.ai.domain.LabResultOcrDraft;
import com.vetos.modules.ai.domain.LabResultOcrInput;
import com.vetos.modules.ai.domain.LabResultOcrPort;
import com.vetos.modules.ai.domain.OcrImageInput;
import com.vetos.modules.ai.domain.OcrLabItem;
import com.vetos.modules.ai.domain.exception.NoFileSelectedForOcrException;
import com.vetos.modules.ai.domain.exception.UnsupportedOcrFileTypeException;
import com.vetos.modules.lab.domain.LabReferenceRangeEvaluator;
import com.vetos.modules.lab.domain.LabResultFileForOcr;
import com.vetos.modules.lab.domain.LabResultFileLookupPort;
import com.vetos.modules.lab.domain.LabValueFlag;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29,
 * docs/superpowers/plans/2026-10-29-lab-goruntuleme-ai-foto-okuma.md Task 5).
 * GenerateDiagnosisSuggestionUseCase ile AYNI AiCallGate/UsageQuotaPort deseni.
 * Tek fark/onemli nokta: referans araligini da OCR'dan okuyup dogrudan mevcut
 * kural motoruna (LabReferenceRangeEvaluator -- lab.domain'de, lab.application
 * DEGIL, bu yuzden ai modulunden erisilebilir) besler; flag/ozet BURADA
 * hesaplanir, ikinci bir AI cagrisi YAPILMAZ (spec 2026-10-28 §3.3/§8).
 */
@Service
public class ReadLabResultFromImageUseCase {

    private static final String MODEL_NOT_CONNECTED_SUMMARY = "[AI modeli fotografi okuyamadi -- degerleri elle girin.]";

    private final LabResultFileLookupPort labResultFileLookupPort;
    private final LabResultOcrPort labResultOcrPort;
    private final RecordAiJobUseCase recordAiJobUseCase;
    private final String providerName;
    private final UsageQuotaPort usageQuotaPort;
    private final AiCallGate aiCallGate;

    public ReadLabResultFromImageUseCase(
        LabResultFileLookupPort labResultFileLookupPort,
        LabResultOcrPort labResultOcrPort,
        RecordAiJobUseCase recordAiJobUseCase,
        @Value("${ai.provider:ollama}") String providerName,
        UsageQuotaPort usageQuotaPort,
        AiCallGate aiCallGate
    ) {
        this.labResultFileLookupPort = labResultFileLookupPort;
        this.labResultOcrPort = labResultOcrPort;
        this.recordAiJobUseCase = recordAiJobUseCase;
        this.providerName = providerName;
        this.usageQuotaPort = usageQuotaPort;
        this.aiCallGate = aiCallGate;
    }

    public ReadLabResultFromImageResult execute(ReadLabResultFromImageCommand command) {
        if (command.fileIds() == null || command.fileIds().isEmpty()) {
            throw new NoFileSelectedForOcrException();
        }

        List<LabResultFileForOcr> files = labResultFileLookupPort.findFiles(command.labResultId(), command.fileIds());
        for (LabResultFileForOcr file : files) {
            if (file.contentType() == null || !file.contentType().startsWith("image/")) {
                throw new UnsupportedOcrFileTypeException(file.id());
            }
        }
        List<OcrImageInput> images = files.stream()
            .map(f -> new OcrImageInput(f.contentType(), f.content()))
            .toList();

        // Kota ayirma kapinin icinde: AI yogunsa hak dusmez (ayni desen, bkz. GenerateDiagnosisSuggestionUseCase).
        LabResultOcrDraft draft = aiCallGate.run(command.tenantId(), () -> {
            usageQuotaPort.reserve(command.tenantId(), UsageKind.AI, 1);
            LabResultOcrDraft generated;
            try {
                generated = labResultOcrPort.generate(new LabResultOcrInput(images));
            } catch (RuntimeException e) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
                throw e;
            }
            if (!generated.modelConnected()) {
                usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
            }
            return generated;
        });

        List<ReadLabResultFromImageResult.Item> items = new ArrayList<>();
        String draftSummary;
        if (!draft.modelConnected()) {
            draftSummary = MODEL_NOT_CONNECTED_SUMMARY;
        } else {
            List<String> abnormalDescriptions = new ArrayList<>();
            for (OcrLabItem item : draft.items()) {
                LabValueFlag flag = LabReferenceRangeEvaluator.evaluate(item.value(), item.referenceRange());
                items.add(new ReadLabResultFromImageResult.Item(item.parameterName(), item.value(), item.unit(), item.referenceRange(), flag));
                if (flag == LabValueFlag.HIGH || flag == LabValueFlag.LOW) {
                    String direction = flag == LabValueFlag.HIGH ? "yuksek" : "dusuk";
                    String unitPart = item.unit() != null && !item.unit().isBlank() ? " " + item.unit() : "";
                    abnormalDescriptions.add(
                        item.parameterName() + " " + direction + " (" + item.value() + unitPart + ", referans: " + item.referenceRange() + ")"
                    );
                }
            }
            draftSummary = abnormalDescriptions.isEmpty()
                ? "Degerlendirilen parametrelerin tumu referans araliginda."
                : "Referans araligi disinda " + abnormalDescriptions.size() + " parametre tespit edildi: "
                    + String.join(", ", abnormalDescriptions)
                    + ". (Kural tabanli on-degerlendirme -- kaydetmeden once gozden gecirin.)";
        }

        var aiJobId = recordAiJobUseCase.execute(new RecordAiJobCommand(
            command.tenantId(), AiTaskType.LAB_RESULT_OCR, null,
            draftSummary, providerName, draft.modelVersion(), command.requestedByStaffUserId()
        ));

        return new ReadLabResultFromImageResult(aiJobId, items, draftSummary, draft.modelConnected());
    }
}
