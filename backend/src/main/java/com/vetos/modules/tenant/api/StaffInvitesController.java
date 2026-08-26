package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.InviteStaffMemberRequest;
import com.vetos.modules.tenant.api.dto.StaffInviteResponse;
import com.vetos.modules.tenant.application.InviteStaffMemberUseCase;
import com.vetos.modules.tenant.application.ListStaffInvitesUseCase;
import com.vetos.modules.tenant.application.RevokeStaffInviteUseCase;
import com.vetos.modules.tenant.application.dto.InviteStaffMemberCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * api-conventions.md rol matrisi: /settings/**, /users/** -> sadece ADMIN.
 * Ekip davetleri kullanici yonetiminin bir parcasi oldugu icin ayni kisitlama.
 */
@RestController
@RequestMapping("/api/v1/staff-invites")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StaffInvitesController {

    private final InviteStaffMemberUseCase inviteStaffMemberUseCase;
    private final ListStaffInvitesUseCase listStaffInvitesUseCase;
    private final RevokeStaffInviteUseCase revokeStaffInviteUseCase;

    @GetMapping
    public List<StaffInviteResponse> list(@AuthenticationPrincipal AuthenticatedStaffUser principal) {
        return listStaffInvitesUseCase.execute(principal.tenantId()).stream().map(StaffInviteResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<Void> invite(
        @AuthenticationPrincipal AuthenticatedStaffUser principal, @RequestBody @Valid InviteStaffMemberRequest request
    ) {
        UUID id = inviteStaffMemberUseCase.execute(new InviteStaffMemberCommand(
            principal.tenantId(), request.branchId(), request.email(), request.fullName(),
            request.role(), principal.staffUserId()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/staff-invites/" + id)).build();
    }

    @PostMapping("/{id}/revoke")
    public void revoke(@PathVariable UUID id) {
        revokeStaffInviteUseCase.execute(id);
    }
}
