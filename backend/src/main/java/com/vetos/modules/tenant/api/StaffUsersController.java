package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.ChangePasswordRequest;
import com.vetos.modules.tenant.api.dto.CreateStaffUserRequest;
import com.vetos.modules.tenant.api.dto.SetStaffShiftRequest;
import com.vetos.modules.tenant.api.dto.StaffShiftResponse;
import com.vetos.modules.tenant.api.dto.StaffUserResponse;
import com.vetos.modules.tenant.api.dto.UpdateStaffUserRequest;
import com.vetos.modules.tenant.application.ChangePasswordUseCase;
import com.vetos.modules.tenant.application.CreateStaffUserUseCase;
import com.vetos.modules.tenant.application.DeactivateStaffUserUseCase;
import com.vetos.modules.tenant.application.GetStaffShiftTemplateUseCase;
import com.vetos.modules.tenant.application.ListStaffUsersUseCase;
import com.vetos.modules.tenant.application.ReactivateStaffUserUseCase;
import com.vetos.modules.tenant.application.SetStaffShiftTemplateUseCase;
import com.vetos.modules.tenant.application.UpdateStaffUserUseCase;
import com.vetos.modules.tenant.application.dto.ChangePasswordCommand;
import com.vetos.modules.tenant.application.dto.CreateStaffUserCommand;
import com.vetos.modules.tenant.application.dto.SetStaffShiftTemplateCommand;
import com.vetos.modules.tenant.application.dto.StaffShiftEntry;
import com.vetos.modules.tenant.application.dto.UpdateStaffUserCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff-users")
@RequiredArgsConstructor
public class StaffUsersController {

    private final ListStaffUsersUseCase listStaffUsersUseCase;
    private final ChangePasswordUseCase changePasswordUseCase;
    private final CreateStaffUserUseCase createStaffUserUseCase;
    private final UpdateStaffUserUseCase updateStaffUserUseCase;
    private final DeactivateStaffUserUseCase deactivateStaffUserUseCase;
    private final ReactivateStaffUserUseCase reactivateStaffUserUseCase;
    private final GetStaffShiftTemplateUseCase getStaffShiftTemplateUseCase;
    private final SetStaffShiftTemplateUseCase setStaffShiftTemplateUseCase;

    @GetMapping
    public List<StaffUserResponse> list(@AuthenticationPrincipal AuthenticatedStaffUser principal) {
        return listStaffUsersUseCase.execute(principal.branchIds().get(0)).stream()
            .map(StaffUserResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> create(@RequestBody @Valid CreateStaffUserRequest request) {
        UUID id = createStaffUserUseCase.execute(new CreateStaffUserCommand(
            request.branchId(), request.fullName(), request.email(), request.password(), request.role(),
            request.phone(), request.licenseNumber(), request.specialty(), request.bio()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/staff-users/" + id)).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void update(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @PathVariable UUID id,
        @RequestBody @Valid UpdateStaffUserRequest request
    ) {
        updateStaffUserUseCase.execute(new UpdateStaffUserCommand(
            id, principal.staffUserId(), request.fullName(), request.phone(), request.role(),
            request.licenseNumber(), request.specialty(), request.bio()
        ));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@AuthenticationPrincipal AuthenticatedStaffUser principal, @PathVariable UUID id) {
        deactivateStaffUserUseCase.execute(id, principal.staffUserId());
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public void activate(@PathVariable UUID id) {
        reactivateStaffUserUseCase.execute(id);
    }

    @GetMapping("/{id}/shifts")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StaffShiftResponse> getShifts(@PathVariable UUID id) {
        return getStaffShiftTemplateUseCase.execute(id).stream()
            .map(StaffShiftResponse::from)
            .toList();
    }

    @PutMapping("/{id}/shifts")
    @PreAuthorize("hasRole('ADMIN')")
    public void setShifts(@PathVariable UUID id, @RequestBody @Valid SetStaffShiftRequest request) {
        List<StaffShiftEntry> entries = request.shifts().stream()
            .map(shift -> new StaffShiftEntry(shift.dayOfWeek(), shift.startsAt(), shift.endsAt()))
            .toList();
        setStaffShiftTemplateUseCase.execute(new SetStaffShiftTemplateCommand(id, entries));
    }

    @PutMapping("/me/password")
    public void changePassword(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid ChangePasswordRequest request
    ) {
        changePasswordUseCase.execute(new ChangePasswordCommand(
            principal.staffUserId(), request.currentPassword(), request.newPassword()
        ));
    }
}
