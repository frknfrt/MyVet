package com.vetos.modules.patient.application.dto;

import java.util.List;
import java.util.UUID;

/** signedFormId yalnizca denetim logu icindir; bu modul onu dogrulamaz. */
public record FormWriteBackCommand(UUID ownerId, UUID patientId, UUID signedFormId, List<Item> items) {
    public record Item(String binding, String value) {}
}
