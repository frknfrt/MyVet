package com.vetos.modules.patient.application.dto;

import java.util.List;

public record FormWriteBackResult(List<String> applied, List<Skipped> skipped) {
    public record Skipped(String binding, String reason) {}
}
