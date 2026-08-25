package com.vetos.modules.encounter.api;

import com.vetos.modules.encounter.api.dto.CheckDrugInteractionsRequest;
import com.vetos.modules.encounter.api.dto.CheckDrugInteractionsResponse;
import com.vetos.modules.encounter.api.dto.CreateDrugRequest;
import com.vetos.modules.encounter.api.dto.DrugResponse;
import com.vetos.modules.encounter.api.dto.UpdateDrugRequest;
import com.vetos.modules.encounter.application.CheckDrugInteractionsUseCase;
import com.vetos.modules.encounter.application.CreateDrugCatalogUseCase;
import com.vetos.modules.encounter.application.ListDrugCatalogUseCase;
import com.vetos.modules.encounter.application.UpdateDrugCatalogUseCase;
import com.vetos.modules.encounter.application.dto.UpdateDrugCatalogCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drugs")
@RequiredArgsConstructor
public class DrugsController {

    private final ListDrugCatalogUseCase listDrugCatalogUseCase;
    private final CreateDrugCatalogUseCase createDrugCatalogUseCase;
    private final UpdateDrugCatalogUseCase updateDrugCatalogUseCase;
    private final CheckDrugInteractionsUseCase checkDrugInteractionsUseCase;

    @GetMapping
    public List<DrugResponse> list() {
        return listDrugCatalogUseCase.execute().stream().map(DrugResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DrugResponse> create(@RequestBody @Valid CreateDrugRequest request) {
        UUID id = createDrugCatalogUseCase.execute(request.name(), request.activeIngredient(), request.isControlled());
        return ResponseEntity.status(201)
            .body(new DrugResponse(id, request.name(), request.activeIngredient(), request.isControlled(), List.of()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void update(@PathVariable UUID id, @RequestBody @Valid UpdateDrugRequest request) {
        updateDrugCatalogUseCase.execute(new UpdateDrugCatalogCommand(
            id, request.name(), request.activeIngredient(), request.isControlled(), request.interactingDrugIds()
        ));
    }

    @PostMapping("/check-interactions")
    @PreAuthorize("hasAnyRole('VET', 'ADMIN')")
    public CheckDrugInteractionsResponse checkInteractions(@RequestBody @Valid CheckDrugInteractionsRequest request) {
        return CheckDrugInteractionsResponse.from(checkDrugInteractionsUseCase.execute(request.drugIds()));
    }
}
