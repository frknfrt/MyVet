package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.AuthSessionResponse;
import com.vetos.modules.tenant.api.dto.LoginRequest;
import com.vetos.modules.tenant.api.dto.RegisterClinicRequest;
import com.vetos.modules.tenant.application.LoginUseCase;
import com.vetos.modules.tenant.application.RegisterClinicUseCase;
import com.vetos.modules.tenant.application.dto.LoginCommand;
import com.vetos.modules.tenant.application.dto.RegisterClinicCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterClinicUseCase registerClinicUseCase;
    private final LoginUseCase loginUseCase;

    @PostMapping("/register-clinic")
    public ResponseEntity<AuthSessionResponse> registerClinic(@RequestBody @Valid RegisterClinicRequest request) {
        var session = registerClinicUseCase.execute(new RegisterClinicCommand(
            request.tenantName(), request.taxNumber(), request.branchName(),
            request.adminFullName(), request.adminEmail(), request.adminPassword()
        ));
        return ResponseEntity.status(201).body(AuthSessionResponse.from(session));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@RequestBody @Valid LoginRequest request) {
        var session = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(AuthSessionResponse.from(session));
    }
}
