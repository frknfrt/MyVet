package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.TrialSignupVerifiedResponse;
import com.vetos.modules.platformadmin.api.dto.VerifyTrialSignupRequest;
import com.vetos.modules.platformadmin.application.VerifyTrialSignupUseCase;
import com.vetos.modules.platformadmin.api.dto.ResendTrialCodeRequest;
import com.vetos.modules.platformadmin.api.dto.StartTrialSignupRequest;
import com.vetos.modules.platformadmin.api.dto.TrialCodeResentResponse;
import com.vetos.modules.platformadmin.api.dto.TrialSignupStartedResponse;
import com.vetos.modules.platformadmin.application.ResendTrialCodeUseCase;
import com.vetos.modules.platformadmin.application.StartTrialSignupCommand;
import com.vetos.modules.platformadmin.application.StartTrialSignupUseCase;
import com.vetos.modules.platformadmin.application.TrialSignupStarted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vitrin sitesindeki 7 gunluk self-servis deneme kaydi (spec 2026-11-13). Kimlik dogrulama
 * gerektirmez (/api/v1/public/** permitAll); kotuye kullanima karsi RateLimitFilter bu yol
 * icin ayri ve siki bir havuz kullanir.
 */
@RestController
@RequestMapping("/api/v1/public/signup/trial")
@RequiredArgsConstructor
public class PublicTrialSignupController {

    private final StartTrialSignupUseCase startTrialSignupUseCase;
    private final ResendTrialCodeUseCase resendTrialCodeUseCase;
    private final VerifyTrialSignupUseCase verifyTrialSignupUseCase;

    /** Ikinci adim: kod dogruysa klinigi olusturur; yanit, e-postasi dolu gelen panel giris adresidir. */
    @PostMapping("/verify")
    public TrialSignupVerifiedResponse verify(@RequestBody @Valid VerifyTrialSignupRequest request) {
        return new TrialSignupVerifiedResponse(verifyTrialSignupUseCase.execute(request.requestId(), request.code()));
    }

    /** Ilk adim: istegi kaydeder, telefona dogrulama kodu gonderir. */
    @PostMapping("/start")
    public TrialSignupStartedResponse start(
        @RequestBody @Valid StartTrialSignupRequest request, HttpServletRequest httpRequest
    ) {
        TrialSignupStarted started = startTrialSignupUseCase.execute(new StartTrialSignupCommand(
            request.fullName(), request.phone(), request.email(), request.clinicName(), request.city(),
            request.chamberRegistrationNo(), request.password(), request.consentAccepted(), httpRequest.getRemoteAddr()
        ));
        return new TrialSignupStartedResponse(started.requestId(), started.resendAvailableInSeconds());
    }

    @PostMapping("/resend")
    public TrialCodeResentResponse resend(@RequestBody @Valid ResendTrialCodeRequest request) {
        return new TrialCodeResentResponse(resendTrialCodeUseCase.execute(request.requestId()));
    }
}
