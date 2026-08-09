package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.BranchOverviewResponse;
import com.vetos.modules.tenant.api.dto.BranchWorkingHoursResponse;
import com.vetos.modules.tenant.api.dto.CreateBranchRequest;
import com.vetos.modules.tenant.api.dto.SetBranchWorkingHoursRequest;
import com.vetos.modules.tenant.api.dto.UpdateBranchDetailsRequest;
import com.vetos.modules.tenant.application.CreateBranchUseCase;
import com.vetos.modules.tenant.application.GetBranchWorkingHoursUseCase;
import com.vetos.modules.tenant.application.GetCurrentBranchUseCase;
import com.vetos.modules.tenant.application.ListBranchesUseCase;
import com.vetos.modules.tenant.application.SetBranchWorkingHoursUseCase;
import com.vetos.modules.tenant.application.UpdateBranchDetailsUseCase;
import com.vetos.modules.tenant.application.dto.BranchWorkingHoursEntry;
import com.vetos.modules.tenant.application.dto.CreateBranchCommand;
import com.vetos.modules.tenant.application.dto.SetBranchWorkingHoursCommand;
import com.vetos.modules.tenant.application.dto.UpdateBranchDetailsCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchesController {

    private final GetCurrentBranchUseCase getCurrentBranchUseCase;
    private final UpdateBranchDetailsUseCase updateBranchDetailsUseCase;
    private final ListBranchesUseCase listBranchesUseCase;
    private final CreateBranchUseCase createBranchUseCase;
    private final GetBranchWorkingHoursUseCase getBranchWorkingHoursUseCase;
    private final SetBranchWorkingHoursUseCase setBranchWorkingHoursUseCase;

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

    @GetMapping
    public List<BranchOverviewResponse> list() {
        return listBranchesUseCase.execute(TenantContext.current()).stream()
            .map(BranchOverviewResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> create(@RequestBody @Valid CreateBranchRequest request) {
        UUID id = createBranchUseCase.execute(new CreateBranchCommand(
            TenantContext.current(), request.name(), request.address(), request.city(),
            request.timezone(), request.tarbilBranchCode()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/branches/" + id)).build();
    }

    @GetMapping("/{id}")
    public BranchOverviewResponse get(@PathVariable UUID id) {
        return BranchOverviewResponse.from(getCurrentBranchUseCase.execute(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void update(@PathVariable UUID id, @RequestBody @Valid UpdateBranchDetailsRequest request) {
        updateBranchDetailsUseCase.execute(new UpdateBranchDetailsCommand(
            id, request.address(), request.city(), request.timezone(), request.tarbilBranchCode()
        ));
    }

    @GetMapping("/{id}/working-hours")
    public List<BranchWorkingHoursResponse> getWorkingHours(@PathVariable UUID id) {
        return getBranchWorkingHoursUseCase.execute(id).stream()
            .map(BranchWorkingHoursResponse::from)
            .toList();
    }

    @PutMapping("/{id}/working-hours")
    @PreAuthorize("hasRole('ADMIN')")
    public void setWorkingHours(@PathVariable UUID id, @RequestBody @Valid SetBranchWorkingHoursRequest request) {
        List<BranchWorkingHoursEntry> entries = request.days().stream()
            .map(day -> new BranchWorkingHoursEntry(day.dayOfWeek(), day.closed(), day.opensAt(), day.closesAt()))
            .toList();
        setBranchWorkingHoursUseCase.execute(new SetBranchWorkingHoursCommand(id, entries));
    }
}
