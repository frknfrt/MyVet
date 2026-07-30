package com.vetos.modules.patient.api;

import com.vetos.modules.patient.api.dto.*;
import com.vetos.modules.patient.application.CreateBreedUseCase;
import com.vetos.modules.patient.application.CreateSpeciesUseCase;
import com.vetos.modules.patient.application.ListBreedsBySpeciesUseCase;
import com.vetos.modules.patient.application.ListSpeciesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/species")
@RequiredArgsConstructor
public class SpeciesController {

    private final ListSpeciesUseCase listSpeciesUseCase;
    private final CreateSpeciesUseCase createSpeciesUseCase;
    private final ListBreedsBySpeciesUseCase listBreedsBySpeciesUseCase;
    private final CreateBreedUseCase createBreedUseCase;

    @GetMapping
    public List<SpeciesResponse> list() {
        return listSpeciesUseCase.execute().stream().map(SpeciesResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SpeciesResponse> create(@RequestBody @Valid CreateSpeciesRequest request) {
        UUID id = createSpeciesUseCase.execute(request.name());
        return ResponseEntity.status(201).body(new SpeciesResponse(id, request.name()));
    }

    @GetMapping("/{speciesId}/breeds")
    public List<BreedResponse> listBreeds(@PathVariable UUID speciesId) {
        return listBreedsBySpeciesUseCase.execute(speciesId).stream().map(BreedResponse::from).toList();
    }

    @PostMapping("/{speciesId}/breeds")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BreedResponse> createBreed(@PathVariable UUID speciesId, @RequestBody @Valid CreateBreedRequest request) {
        UUID id = createBreedUseCase.execute(speciesId, request.name());
        return ResponseEntity.status(201).body(new BreedResponse(id, request.name()));
    }
}
