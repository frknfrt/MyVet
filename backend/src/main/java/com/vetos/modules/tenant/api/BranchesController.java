package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.BranchOverviewResponse;
import com.vetos.modules.tenant.api.dto.UpdateBranchDetailsRequest;
import com.vetos.modules.tenant.application.GetCurrentBranchUseCase;
import com.vetos.modules.tenant.application.UpdateBranchDetailsUseCase;
import com.vetos.modules.tenant.application.dto.UpdateBranchDetailsCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchesController {

    private final GetCurrentBranchUseCase getCurrentBranchUseCase;
    private final UpdateBranchDetailsUseCase updateBranchDetailsUseCase;

    @GetMapping("/current")
    public BranchOverviewResponse getCurrent(@AuthenticationPrincipal AuthenticatedStaffUser principal) {
        return BranchOverviewResponse.from(getCurrentBranchUseCase.execute(principal.branchIds().get(0)));
    }

    @PutMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public void updateCurrent(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid UpdateBranchDetailsRequest request
    ) {
        updateBranchDetailsUseCase.execute(new UpdateBranchDetailsCommand(
            principal.branchIds().get(0), request.address(), request.city(), request.timezone(), request.tarbilBranchCode()
        ));
    }
}
