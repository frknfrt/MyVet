package com.vetos.modules.boarding.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BoardingRoomSummary(
    UUID id, String groupName, String name, int capacity, BigDecimal dailyRate, String notes, boolean active
) {}
