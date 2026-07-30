package com.vetos.modules.appointment.api;

import com.vetos.modules.appointment.api.dto.CreateServiceTypeRequest;
import com.vetos.modules.appointment.api.dto.ServiceTypeResponse;
import com.vetos.modules.appointment.application.CreateServiceTypeUseCase;
import com.vetos.modules.appointment.application.ListServiceTypesUseCase;
import com.vetos.modules.appointment.application.dto.CreateServiceTypeCommand;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/service-types")
@RequiredArgsConstructor
public class ServiceTypesController {

    private final ListServiceTypesUseCase listServiceTypesUseCase;
    private final CreateServiceTypeUseCase createServiceTypeUseCase;

    @GetMapping
    public List<ServiceTypeResponse> list() {
        return listServiceTypesUseCase.execute(TenantContext.current()).stream()
            .map(ServiceTypeResponse::from)
            .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceTypeResponse> create(@RequestBody @Valid CreateServiceTypeRequest request) {
        UUID id = createServiceTypeUseCase.execute(new CreateServiceTypeCommand(
            TenantContext.current(), request.name(), request.defaultDurationMin(), request.defaultPrice()
        ));
        return ResponseEntity.status(201).body(new ServiceTypeResponse(id, request.name(), request.defaultDurationMin(), request.defaultPrice()));
    }
}
