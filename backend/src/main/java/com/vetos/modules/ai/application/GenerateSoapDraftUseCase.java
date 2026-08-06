package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerateSoapDraftUseCase {

    private final SoapGenerationPort soapGenerationPort;

    public SoapDraft execute(String transcriptText) {
        return soapGenerationPort.generate(new AudioTranscript(transcriptText));
    }
}
