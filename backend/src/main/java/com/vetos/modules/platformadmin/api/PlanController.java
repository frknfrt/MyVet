package com.vetos.modules.platformadmin.api;

import com.vetos.modules.platformadmin.api.dto.CreatePlanRequest;
import com.vetos.modules.platformadmin.api.dto.PlanResponse;
import com.vetos.modules.platformadmin.api.dto.UpdatePlanRequest;
import com.vetos.modules.platformadmin.application.CreatePlanUseCase;
import com.vetos.modules.platformadmin.application.DeletePlanUseCase;
import com.vetos.modules.platformadmin.application.ListPlansUseCase;
import com.vetos.modules.platformadmin.application.UpdatePlanUseCase;
import com.vetos.modules.platformadmin.application.dto.CreatePlanCommand;
import com.vetos.modules.platformadmin.application.dto.UpdatePlanCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform-admin/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlanController {

    private final ListPlansUseCase listPlansUseCase;
    private final CreatePlanUseCase createPlanUseCase;
    private final UpdatePlanUseCase updatePlanUseCase;
    private final DeletePlanUseCase deletePlanUseCase;

    @GetMapping
    public List<PlanResponse> list() {
        return listPlansUseCase.execute().stream().map(PlanResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody @Valid CreatePlanRequest request) {
        createPlanUseCase.execute(new CreatePlanCommand(request.code(), request.name(), request.monthlyPrice()));
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/{id}")
    public void update(@PathVariable UUID id, @RequestBody @Valid UpdatePlanRequest request) {
        updatePlanUseCase.execute(new UpdatePlanCommand(id, request.name(), request.monthlyPrice(), request.active()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deletePlanUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
