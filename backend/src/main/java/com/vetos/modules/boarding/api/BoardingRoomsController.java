package com.vetos.modules.boarding.api;

import com.vetos.modules.boarding.api.dto.BoardingRoomResponse;
import com.vetos.modules.boarding.api.dto.CreateBoardingRoomRequest;
import com.vetos.modules.boarding.application.CreateBoardingRoomUseCase;
import com.vetos.modules.boarding.application.ListBoardingRoomsUseCase;
import com.vetos.modules.boarding.application.dto.CreateBoardingRoomCommand;
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
@RequestMapping("/api/v1/boarding-rooms")
@RequiredArgsConstructor
public class BoardingRoomsController {

    private final CreateBoardingRoomUseCase createBoardingRoomUseCase;
    private final ListBoardingRoomsUseCase listBoardingRoomsUseCase;

    @GetMapping
    public List<BoardingRoomResponse> list() {
        return listBoardingRoomsUseCase.execute(TenantContext.current()).stream()
            .map(BoardingRoomResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'ADMIN')")
    public ResponseEntity<Void> create(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid CreateBoardingRoomRequest request
    ) {
        UUID id = createBoardingRoomUseCase.execute(new CreateBoardingRoomCommand(
            TenantContext.current(), principal.branchIds().get(0), request.groupName(), request.name(),
            request.capacity(), request.dailyRate(), request.notes()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/boarding-rooms/" + id)).build();
    }
}
