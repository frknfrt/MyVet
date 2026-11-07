package com.vetos.modules.patient.api;

import com.vetos.modules.patient.api.dto.FormWriteBackRequest;
import com.vetos.modules.patient.application.ApplyFormWriteBacksUseCase;
import com.vetos.modules.patient.application.dto.FormWriteBackCommand;
import com.vetos.modules.patient.application.dto.FormWriteBackResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/form-write-backs")
@RequiredArgsConstructor
public class FormWriteBacksController {

    private final ApplyFormWriteBacksUseCase applyFormWriteBacksUseCase;

    @PostMapping
    @PreAuthorize("hasAnyRole('VET', 'TECHNICIAN', 'RECEPTIONIST', 'ADMIN')")
    public FormWriteBackResult apply(@RequestBody @Valid FormWriteBackRequest request) {
        return applyFormWriteBacksUseCase.execute(new FormWriteBackCommand(
            request.ownerId(), request.patientId(), request.signedFormId(),
            request.items().stream()
                .map(i -> new FormWriteBackCommand.Item(i.binding(), i.value()))
                .toList()
        ));
    }
}
