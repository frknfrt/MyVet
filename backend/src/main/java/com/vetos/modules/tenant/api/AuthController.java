package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.AuthSessionResponse;
import com.vetos.modules.tenant.api.dto.LoginRequest;
import com.vetos.modules.tenant.application.LoginUseCase;
import com.vetos.modules.tenant.application.dto.LoginCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(@RequestBody @Valid LoginRequest request) {
        var session = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(AuthSessionResponse.from(session));
    }
}
