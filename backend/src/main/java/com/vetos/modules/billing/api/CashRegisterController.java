package com.vetos.modules.billing.api;

import com.vetos.modules.billing.api.dto.CashRegisterSessionResponse;
import com.vetos.modules.billing.api.dto.CloseCashRegisterRequest;
import com.vetos.modules.billing.api.dto.OpenCashRegisterRequest;
import com.vetos.modules.billing.application.CloseCashRegisterUseCase;
import com.vetos.modules.billing.application.GetCurrentCashRegisterUseCase;
import com.vetos.modules.billing.application.ListCashRegisterHistoryUseCase;
import com.vetos.modules.billing.application.OpenCashRegisterUseCase;
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
@RequestMapping("/api/v1/cash-register")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
public class CashRegisterController {

    private final OpenCashRegisterUseCase openCashRegisterUseCase;
    private final CloseCashRegisterUseCase closeCashRegisterUseCase;
    private final GetCurrentCashRegisterUseCase getCurrentCashRegisterUseCase;
    private final ListCashRegisterHistoryUseCase listCashRegisterHistoryUseCase;

    @PostMapping("/open")
    public ResponseEntity<Void> open(
        @AuthenticationPrincipal AuthenticatedStaffUser principal, @RequestBody @Valid OpenCashRegisterRequest request
    ) {
        UUID id = openCashRegisterUseCase.execute(
            principal.branchIds().get(0), principal.staffUserId(), request.openingBalance(), request.notes()
        );
        return ResponseEntity.created(java.net.URI.create("/api/v1/cash-register/" + id)).build();
    }

    @PostMapping("/{id}/close")
    public void close(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @PathVariable UUID id, @RequestBody @Valid CloseCashRegisterRequest request
    ) {
        closeCashRegisterUseCase.execute(id, principal.staffUserId(), request.closingBalance(), request.notes());
    }

    @GetMapping("/current")
    public CashRegisterSessionResponse current(@AuthenticationPrincipal AuthenticatedStaffUser principal) {
        return getCurrentCashRegisterUseCase.execute(principal.branchIds().get(0))
            .map(CashRegisterSessionResponse::from)
            .orElse(null);
    }

    @GetMapping("/history")
    public List<CashRegisterSessionResponse> history(@AuthenticationPrincipal AuthenticatedStaffUser principal) {
        return listCashRegisterHistoryUseCase.execute(principal.branchIds().get(0)).stream()
            .map(CashRegisterSessionResponse::from)
            .toList();
    }
}
