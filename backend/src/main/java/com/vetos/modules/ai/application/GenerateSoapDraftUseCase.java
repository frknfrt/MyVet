package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateSoapDraftUseCase {

    private final SoapGenerationPort soapGenerationPort;
    private final UsageQuotaPort usageQuotaPort;
    private final AiCallGate aiCallGate;

    public SoapDraft execute(UUID tenantId, String transcriptText) {
        // Kota ayirma kapinin icinde: AI yogunsa hak dusmez.
        return aiCallGate.run(tenantId, () -> {
            usageQuotaPort.reserve(tenantId, UsageKind.AI, 1);
            SoapDraft draft;
            try {
                draft = soapGenerationPort.generate(new AudioTranscript(transcriptText));
            } catch (RuntimeException e) {
                usageQuotaPort.release(tenantId, UsageKind.AI, 1);
                throw e;
            }
            if (!draft.modelConnected()) {
                // Adaptor hatayi yutup bos taslak dondurdu -- klinik bosuna hak yemesin.
                usageQuotaPort.release(tenantId, UsageKind.AI, 1);
            }
            return draft;
        });
    }
}
