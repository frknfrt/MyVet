package com.vetos.modules.encounter.api;

import com.vetos.modules.encounter.api.dto.CreateDrugRequest;
import com.vetos.modules.encounter.api.dto.DrugResponse;
import com.vetos.modules.encounter.application.CreateDrugCatalogUseCase;
import com.vetos.modules.encounter.application.ListDrugCatalogUseCase;
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

    @GetMapping
    public List<DrugResponse> list() {
        return listDrugCatalogUseCase.execute().stream().map(DrugResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DrugResponse> create(@RequestBody @Valid CreateDrugRequest request) {
        UUID id = createDrugCatalogUseCase.execute(request.name(), request.activeIngredient(), request.isControlled());
        return ResponseEntity.status(201).body(new DrugResponse(id, request.name(), request.activeIngredient(), request.isControlled()));
    }
}
