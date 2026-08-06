package com.vetos.modules.boarding.api;

import com.vetos.modules.boarding.api.dto.BoardingStayResponse;
import com.vetos.modules.boarding.api.dto.CheckOutBoardingStayRequest;
import com.vetos.modules.boarding.api.dto.CreateBoardingStayRequest;
import com.vetos.modules.boarding.application.CancelBoardingStayUseCase;
import com.vetos.modules.boarding.application.CheckOutBoardingStayUseCase;
import com.vetos.modules.boarding.application.CreateBoardingStayUseCase;
import com.vetos.modules.boarding.application.ListBoardingStaysUseCase;
import com.vetos.modules.boarding.application.dto.CreateBoardingStayCommand;
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
@RequestMapping("/api/v1/boarding-stays")
@RequiredArgsConstructor
public class BoardingStaysController {

    private final CreateBoardingStayUseCase createBoardingStayUseCase;
    private final ListBoardingStaysUseCase listBoardingStaysUseCase;
    private final CheckOutBoardingStayUseCase checkOutBoardingStayUseCase;
    private final CancelBoardingStayUseCase cancelBoardingStayUseCase;

    @GetMapping
    public List<BoardingStayResponse> list() {
        return listBoardingStaysUseCase.execute(TenantContext.current()).stream()
            .map(BoardingStayResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'VET', 'TECHNICIAN', 'ADMIN')")
    public ResponseEntity<Void> create(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid CreateBoardingStayRequest request
    ) {
        UUID id = createBoardingStayUseCase.execute(new CreateBoardingStayCommand(
            TenantContext.current(), principal.branchIds().get(0), request.roomId(), request.patientId(),
            principal.staffUserId(), request.checkInDate(), request.expectedCheckOutDate(), request.notes()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/boarding-stays/" + id)).build();
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'VET', 'TECHNICIAN', 'ADMIN')")
    public void checkOut(@PathVariable UUID id, @RequestBody(required = false) CheckOutBoardingStayRequest request) {
        checkOutBoardingStayUseCase.execute(id, request != null ? request.actualCheckOutDate() : null);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'VET', 'TECHNICIAN', 'ADMIN')")
    public void cancel(@PathVariable UUID id) {
        cancelBoardingStayUseCase.execute(id);
    }
}
