package com.vetos.modules.inventory.api;

import com.vetos.modules.inventory.api.dto.*;
import com.vetos.modules.inventory.application.*;
import com.vetos.modules.inventory.application.dto.CreateInventoryItemCommand;
import com.vetos.modules.inventory.application.dto.UpdateInventoryItemCommand;
import com.vetos.platform.security.AuthenticatedStaffUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** api-conventions.md rol matrisi: /inventory/** (yazma) -> sadece TECHNICIAN ve ADMIN. */
@RestController
@RequestMapping("/api/v1/inventory-items")
@RequiredArgsConstructor
public class InventoryItemsController {

    private final ListInventoryItemsUseCase listInventoryItemsUseCase;
    private final CreateInventoryItemUseCase createInventoryItemUseCase;
    private final UpdateInventoryItemUseCase updateInventoryItemUseCase;
    private final RecordStockMovementUseCase recordStockMovementUseCase;
    private final ListStockMovementsUseCase listStockMovementsUseCase;

    @GetMapping
    public List<InventoryItemResponse> list(@AuthenticationPrincipal AuthenticatedStaffUser principal) {
        return listInventoryItemsUseCase.execute(principal.branchIds().get(0)).stream()
            .map(InventoryItemResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public ResponseEntity<Void> create(
        @AuthenticationPrincipal AuthenticatedStaffUser principal, @RequestBody @Valid CreateInventoryItemRequest request
    ) {
        UUID id = createInventoryItemUseCase.execute(new CreateInventoryItemCommand(
            principal.branchIds().get(0), request.name(), request.category(), request.skuBarcode(),
            request.initialQuantity(), request.reorderThreshold(), request.expiryDate(), request.lotNumber(), request.unitCost()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/inventory-items/" + id)).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public void update(@PathVariable UUID id, @RequestBody @Valid UpdateInventoryItemRequest request) {
        updateInventoryItemUseCase.execute(new UpdateInventoryItemCommand(
            id, request.name(), request.category(), request.reorderThreshold(), request.unitCost()
        ));
    }

    @PostMapping("/{id}/movements")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public void recordMovement(@PathVariable UUID id, @RequestBody @Valid StockMovementRequest request) {
        recordStockMovementUseCase.execute(
            id, request.movementType(), request.quantity(),
            com.vetos.modules.inventory.domain.StockReferenceType.MANUAL, null
        );
    }

    @GetMapping("/{id}/movements")
    public List<StockMovementResponse> listMovements(@PathVariable UUID id) {
        return listStockMovementsUseCase.execute(id).stream().map(StockMovementResponse::from).toList();
    }
}
