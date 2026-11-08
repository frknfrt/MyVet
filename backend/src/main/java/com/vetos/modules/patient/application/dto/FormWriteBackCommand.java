package com.vetos.modules.patient.application.dto;

import java.util.List;
import java.util.UUID;

/** Geri yazma, doğrulanmış imzalı formdaki alan ve değerlerle sınırlıdır. */
public record FormWriteBackCommand(UUID ownerId, UUID patientId, UUID signedFormId, List<Item> items) {
    public record Item(String binding, String value) {}
}
