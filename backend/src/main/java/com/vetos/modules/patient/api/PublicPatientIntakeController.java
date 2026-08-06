package com.vetos.modules.patient.api;

import com.vetos.modules.patient.api.dto.*;
import com.vetos.modules.patient.application.ListSpeciesUseCase;
import com.vetos.modules.patient.application.RegisterOwnerUseCase;
import com.vetos.modules.patient.application.RegisterPatientUseCase;
import com.vetos.modules.patient.application.dto.RegisterOwnerCommand;
import com.vetos.modules.patient.application.dto.RegisterPatientCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @docs/implementation-plan.md Modul 8: online randevu widget'i, henuz
 * kayitli olmayan bir sahip/hasta icin bu uc noktalarla kayit acar.
 * Kimlik dogrulama gerektirmez (SecurityConfig: /api/v1/public/** permitAll) --
 * bu yuzden tenantId TenantContext'ten degil, govdeden acikca alinir.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicPatientIntakeController {

    private final RegisterOwnerUseCase registerOwnerUseCase;
    private final RegisterPatientUseCase registerPatientUseCase;
    private final ListSpeciesUseCase listSpeciesUseCase;

    @GetMapping("/species")
    public List<SpeciesResponse> listSpecies() {
        return listSpeciesUseCase.execute().stream().map(SpeciesResponse::from).toList();
    }

    @PostMapping("/owners")
    public ResponseEntity<OwnerResponse> registerOwner(@RequestBody @Valid PublicRegisterOwnerRequest request) {
        UUID id = registerOwnerUseCase.execute(new RegisterOwnerCommand(
            request.tenantId(), request.fullName(), null, request.phone(), null, request.email(), null,
            null, null, null, null, null, null, null, false, true, true, true, null
        ));
        return ResponseEntity.status(201).body(new OwnerResponse(id, request.fullName()));
    }

    @PostMapping("/patients")
    public ResponseEntity<PatientResponse> registerPatient(@RequestBody @Valid PublicRegisterPatientRequest request) {
        UUID id = registerPatientUseCase.execute(new RegisterPatientCommand(
            request.ownerId(), request.speciesId(), null, request.name(), null, null,
            null, null, null, false, null, null, null, null, null, null
        ));
        return ResponseEntity.status(201).body(new PatientResponse(id, request.name()));
    }
}
