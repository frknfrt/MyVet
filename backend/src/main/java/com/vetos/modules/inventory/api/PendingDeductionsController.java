package com.vetos.modules.inventory.api;

import com.vetos.modules.inventory.api.dto.PendingDeductionResponse;
import com.vetos.modules.inventory.application.ListPendingDeductionsUseCase;
import com.vetos.modules.inventory.application.SettleVetlyOnlyUseCase;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.RequiresFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** TARBIL dusumu bekleyen kalemler ve "Yalniz Vetly'den dus" (spec 2026-10-09 S5.5). */
@RequiresFeature(Feature.INVENTORY)
@RestController
@RequestMapping("/api/v1/inventory/pending-deductions")
@RequiredArgsConstructor
public class PendingDeductionsController {

    private final ListPendingDeductionsUseCase listPendingDeductionsUseCase;
    private final SettleVetlyOnlyUseCase settleVetlyOnlyUseCase;

    @GetMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'ADMIN')")
    public List<PendingDeductionResponse> list() {
        return listPendingDeductionsUseCase.execute().stream().map(PendingDeductionResponse::from).toList();
    }

    @PostMapping("/{referenceType}/{referenceId}/settle-vetly-only")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public ResponseEntity<Void> settleVetlyOnly(@PathVariable StockReferenceType referenceType, @PathVariable UUID referenceId,
                                                @AuthenticationPrincipal AuthenticatedStaffUser user) {
        settleVetlyOnlyUseCase.execute(referenceType, referenceId, user.staffUserId());
        return ResponseEntity.noContent().build();
    }
}
