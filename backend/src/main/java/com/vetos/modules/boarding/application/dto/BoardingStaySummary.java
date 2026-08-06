package com.vetos.modules.boarding.application.dto;

import com.vetos.modules.boarding.domain.BoardingStayStatus;

import java.time.LocalDate;
import java.util.UUID;

public record BoardingStaySummary(
    UUID id, UUID roomId, String roomName, String groupName,
    UUID patientId, String patientName, UUID ownerId, String ownerName,
    LocalDate checkInDate, LocalDate expectedCheckOutDate, LocalDate actualCheckOutDate,
    BoardingStayStatus status, String notes
) {}
