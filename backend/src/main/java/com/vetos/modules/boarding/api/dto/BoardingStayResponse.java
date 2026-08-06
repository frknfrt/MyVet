package com.vetos.modules.boarding.api.dto;

import com.vetos.modules.boarding.application.dto.BoardingStaySummary;
import com.vetos.modules.boarding.domain.BoardingStayStatus;

import java.time.LocalDate;
import java.util.UUID;

public record BoardingStayResponse(
    UUID id, UUID roomId, String roomName, String groupName,
    UUID patientId, String patientName, UUID ownerId, String ownerName,
    LocalDate checkInDate, LocalDate expectedCheckOutDate, LocalDate actualCheckOutDate,
    BoardingStayStatus status, String notes
) {
    public static BoardingStayResponse from(BoardingStaySummary s) {
        return new BoardingStayResponse(
            s.id(), s.roomId(), s.roomName(), s.groupName(),
            s.patientId(), s.patientName(), s.ownerId(), s.ownerName(),
            s.checkInDate(), s.expectedCheckOutDate(), s.actualCheckOutDate(),
            s.status(), s.notes()
        );
    }
}
