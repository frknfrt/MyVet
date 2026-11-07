package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.AuthSessionResponse;
import com.vetos.modules.tenant.api.dto.ConfirmPasswordResetRequest;
import com.vetos.modules.tenant.api.dto.LoginRequest;
import com.vetos.modules.tenant.api.dto.RequestPasswordResetRequest;
import com.vetos.modules.tenant.application.ConfirmPasswordResetUseCase;
import com.vetos.modules.tenant.application.LoginUseCase;
import com.vetos.modules.tenant.application.RequestPasswordResetUseCase;
import com.vetos.modules.tenant.application.dto.LoginCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.vetos.platform.security.SessionCookies;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;
    private final SessionCookies sessionCookies;

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@RequestBody @Valid LoginRequest request) {
        var session = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        // Tarayici anahtari HttpOnly cerezden kullanir (SessionCookies); govdedeki token yalniz API istemcileri icindir.
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, sessionCookies.create(session.token()))
            .body(AuthSessionResponse.from(session));
    }

    /** Oturum cerezini siler. Kimlik dogrulamasi gerekmez: suresi dolmus oturum da temizlenebilmeli. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessionCookies.clear()).build();
    }

    /** E-posta kayıtlı olsun olmasın her zaman 204 döner (kayıtlı adresler dışarıdan öğrenilemesin). */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody @Valid RequestPasswordResetRequest request) {
        requestPasswordResetUseCase.execute(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@RequestBody @Valid ConfirmPasswordResetRequest request) {
        confirmPasswordResetUseCase.execute(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
