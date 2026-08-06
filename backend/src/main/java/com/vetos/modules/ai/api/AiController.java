package com.vetos.modules.ai.api;

import com.vetos.modules.ai.api.dto.GenerateSoapDraftRequest;
import com.vetos.modules.ai.api.dto.SoapDraftResponse;
import com.vetos.modules.ai.application.GenerateSoapDraftUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('VET', 'ADMIN')")
public class AiController {

    private final GenerateSoapDraftUseCase generateSoapDraftUseCase;

    @PostMapping("/soap-drafts")
    public SoapDraftResponse generateSoapDraft(@RequestBody @Valid GenerateSoapDraftRequest request) {
        return SoapDraftResponse.from(generateSoapDraftUseCase.execute(request.transcript()));
    }
}
