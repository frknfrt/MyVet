package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.PlatformAdminAuthSessionResponse;
import com.vetos.modules.platformadmin.api.dto.PlatformAdminLoginRequest;
import com.vetos.modules.platformadmin.application.PlatformAdminLoginUseCase;
import com.vetos.modules.platformadmin.application.dto.PlatformAdminLoginCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/auth")
@RequiredArgsConstructor
public class PlatformAdminAuthController {

    private final PlatformAdminLoginUseCase platformAdminLoginUseCase;

    @PostMapping("/login")
    public ResponseEntity<PlatformAdminAuthSessionResponse> login(@RequestBody @Valid PlatformAdminLoginRequest request) {
        var session = platformAdminLoginUseCase.execute(new PlatformAdminLoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(PlatformAdminAuthSessionResponse.from(session));
    }
}
