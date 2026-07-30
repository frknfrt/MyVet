package com.vetos.modules.appointment.api;

import com.vetos.modules.appointment.api.dto.PublicAppointmentRequest;
import com.vetos.modules.appointment.api.dto.ServiceTypeResponse;
import com.vetos.modules.appointment.application.ListServiceTypesUseCase;
import com.vetos.modules.appointment.application.ScheduleAppointmentUseCase;
import com.vetos.modules.appointment.application.dto.ScheduleAppointmentCommand;
import com.vetos.modules.appointment.domain.AppointmentSource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * @docs/implementation-plan.md Modul 8: web sitesine gomulu online randevu
 * widget'i. Gelen talep source=WIDGET ile Appointment modulune, hekim
 * atanmamis (REQUESTED) olarak duser -- resepsiyon Randevu Takvimi'nden
 * onaylayip hekim atar.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicAppointmentController {

    private final ListServiceTypesUseCase listServiceTypesUseCase;
    private final ScheduleAppointmentUseCase scheduleAppointmentUseCase;

    @GetMapping("/service-types")
    public List<ServiceTypeResponse> listServiceTypes(@RequestParam UUID tenantId) {
        return listServiceTypesUseCase.execute(tenantId).stream().map(ServiceTypeResponse::from).toList();
    }

    @PostMapping("/appointment-requests")
    public ResponseEntity<Void> requestAppointment(@RequestBody @Valid PublicAppointmentRequest request) {
        UUID id = scheduleAppointmentUseCase.execute(new ScheduleAppointmentCommand(
            request.tenantId(), request.branchId(), request.patientId(), request.ownerId(), null,
            request.serviceTypeId(), request.scheduledStart(), request.scheduledEnd(),
            AppointmentSource.WIDGET, request.notes()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/appointments/" + id)).build();
    }
}
