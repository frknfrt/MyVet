package com.vetos.modules.boarding.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBoardingStayCommand(
    UUID tenantId, UUID branchId, UUID roomId, UUID patientId, UUID createdByStaffId,
    LocalDate checkInDate, LocalDate expectedCheckOutDate, String notes
) {}
