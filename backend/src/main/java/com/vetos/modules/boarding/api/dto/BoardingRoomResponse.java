package com.vetos.modules.boarding.api.dto;

import com.vetos.modules.boarding.application.dto.BoardingRoomSummary;

import java.math.BigDecimal;
import java.util.UUID;

public record BoardingRoomResponse(
    UUID id, String groupName, String name, int capacity, BigDecimal dailyRate, String notes, boolean active
) {
    public static BoardingRoomResponse from(BoardingRoomSummary s) {
        return new BoardingRoomResponse(s.id(), s.groupName(), s.name(), s.capacity(), s.dailyRate(), s.notes(), s.active());
    }
}
