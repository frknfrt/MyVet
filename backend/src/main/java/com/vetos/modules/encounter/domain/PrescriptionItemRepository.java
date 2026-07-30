package com.vetos.modules.encounter.domain;

import java.util.List;
import java.util.UUID;

public interface PrescriptionItemRepository {
    PrescriptionItem save(PrescriptionItem item);
    List<PrescriptionItem> findByPrescriptionId(UUID prescriptionId);
}
