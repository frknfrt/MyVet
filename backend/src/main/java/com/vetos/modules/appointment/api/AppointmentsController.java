package com.vetos.modules.appointment.api;

import com.vetos.modules.appointment.api.dto.AppointmentResponse;
import com.vetos.modules.appointment.api.dto.AssignStaffRequest;
import com.vetos.modules.appointment.api.dto.ScheduleAppointmentRequest;
import com.vetos.modules.appointment.application.AssignStaffToAppointmentUseCase;
import com.vetos.modules.appointment.application.GetWeeklyCalendarUseCase;
import com.vetos.modules.appointment.application.ScheduleAppointmentUseCase;
import com.vetos.modules.appointment.application.UpdateAppointmentStatusUseCase;
import com.vetos.modules.appointment.application.UpdateAppointmentStatusUseCase.Action;
import com.vetos.modules.appointment.application.dto.ScheduleAppointmentCommand;
import com.vetos.modules.appointment.domain.AppointmentSource;
import com.vetos.platform.security.AuthenticatedStaffUser;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentsController {

    private final ScheduleAppointmentUseCase scheduleAppointmentUseCase;
    private final GetWeeklyCalendarUseCase getWeeklyCalendarUseCase;
    private final UpdateAppointmentStatusUseCase updateAppointmentStatusUseCase;
    private final AssignStaffToAppointmentUseCase assignStaffToAppointmentUseCase;

    @PostMapping
    public ResponseEntity<Void> schedule(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestBody @Valid ScheduleAppointmentRequest request
    ) {
        UUID id = scheduleAppointmentUseCase.execute(new ScheduleAppointmentCommand(
            TenantContext.current(), principal.branchIds().get(0), request.patientId(), request.ownerId(),
            request.assignedStaffId(), request.serviceTypeId(), request.scheduledStart(), request.scheduledEnd(),
            request.source() != null ? request.source() : AppointmentSource.WALK_IN, request.notes()
        ));
        return ResponseEntity.created(java.net.URI.create("/api/v1/appointments/" + id)).build();
    }

    @GetMapping
    public List<AppointmentResponse> weeklyCalendar(
        @AuthenticationPrincipal AuthenticatedStaffUser principal,
        @RequestParam LocalDate weekStart
    ) {
        var rangeStart = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        var rangeEnd = weekStart.plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant();
        return getWeeklyCalendarUseCase.execute(principal.branchIds().get(0), rangeStart, rangeEnd).stream()
            .map(AppointmentResponse::from)
            .toList();
    }

    @PutMapping("/{id}/assign-staff")
    public void assignStaff(@PathVariable UUID id, @RequestBody @Valid AssignStaffRequest request) {
        assignStaffToAppointmentUseCase.execute(id, request.staffId());
    }

    @PostMapping("/{id}/confirm")
    public void confirm(@PathVariable UUID id) { updateAppointmentStatusUseCase.execute(id, Action.CONFIRM); }

    @PostMapping("/{id}/check-in")
    public void checkIn(@PathVariable UUID id) { updateAppointmentStatusUseCase.execute(id, Action.CHECK_IN); }

    @PostMapping("/{id}/start")
    public void start(@PathVariable UUID id) { updateAppointmentStatusUseCase.execute(id, Action.START); }

    @PostMapping("/{id}/complete")
    public void complete(@PathVariable UUID id) { updateAppointmentStatusUseCase.execute(id, Action.COMPLETE); }

    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id) { updateAppointmentStatusUseCase.execute(id, Action.CANCEL); }

    @PostMapping("/{id}/no-show")
    public void markNoShow(@PathVariable UUID id) { updateAppointmentStatusUseCase.execute(id, Action.NO_SHOW); }
}
