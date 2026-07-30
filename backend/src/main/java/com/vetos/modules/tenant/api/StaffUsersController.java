package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.StaffSummaryResponse;
import com.vetos.modules.tenant.application.ListStaffUsersUseCase;
import com.vetos.platform.security.AuthenticatedStaffUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff-users")
@RequiredArgsConstructor
public class StaffUsersController {

    private final ListStaffUsersUseCase listStaffUsersUseCase;

    @GetMapping
    public List<StaffSummaryResponse> list(@AuthenticationPrincipal AuthenticatedStaffUser principal) {
        return listStaffUsersUseCase.execute(principal.branchIds().get(0)).stream()
            .map(StaffSummaryResponse::from)
            .toList();
    }
}
