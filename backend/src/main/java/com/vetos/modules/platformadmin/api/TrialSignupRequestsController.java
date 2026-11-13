package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.TrialSignupRequestResponse;
import com.vetos.modules.platformadmin.application.ListTrialSignupRequestsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform admin "Potansiyel Musteriler" panelindeki deneme kayitlari bolumu. */
@RestController
@RequestMapping("/api/v1/platform-admin/signup-requests/trials")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class TrialSignupRequestsController {

    private final ListTrialSignupRequestsUseCase listTrialSignupRequestsUseCase;

    @GetMapping
    public List<TrialSignupRequestResponse> list() {
        return listTrialSignupRequestsUseCase.execute().stream().map(TrialSignupRequestResponse::from).toList();
    }
}
