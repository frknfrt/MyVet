package com.vetos.modules.lab.domain;

import java.util.List;
import java.util.UUID;

public interface LabResultItemRepository {
    LabResultItem save(LabResultItem item);
    List<LabResultItem> findByLabResultId(UUID labResultId);
    void deleteByLabResultId(UUID labResultId);
}
