package com.vetos.modules.tenant.api;

import com.vetos.modules.tenant.api.dto.AcceptStaffInviteRequest;
import com.vetos.modules.tenant.api.dto.StaffInvitePublicResponse;
import com.vetos.modules.tenant.application.AcceptStaffInviteUseCase;
import com.vetos.modules.tenant.application.GetStaffInviteByTokenUseCase;
import com.vetos.modules.tenant.application.dto.AcceptStaffInviteCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @docs/implementation-plan.md Modul 8'deki public uc nokta deseniyle ayni:
 * kimlik dogrulama gerektirmeyen davet kabul akisi. SecurityConfig'de
 * /api/v1/public/** zaten permitAll.
 */
@RestController
@RequestMapping("/api/v1/public/staff-invites")
@RequiredArgsConstructor
public class PublicStaffInviteController {

    private final GetStaffInviteByTokenUseCase getStaffInviteByTokenUseCase;
    private final AcceptStaffInviteUseCase acceptStaffInviteUseCase;

    @GetMapping("/{token}")
    public StaffInvitePublicResponse get(@PathVariable String token) {
        return StaffInvitePublicResponse.from(getStaffInviteByTokenUseCase.execute(token));
    }

    @PostMapping("/{token}/accept")
    public void accept(@PathVariable String token, @RequestBody @Valid AcceptStaffInviteRequest request) {
        acceptStaffInviteUseCase.execute(new AcceptStaffInviteCommand(token, request.password()));
    }
}
