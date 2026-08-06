package com.vetos.modules.lab.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LabResultFileRepository {
    LabResultFile save(LabResultFile file);
    Optional<LabResultFile> findById(UUID id);
    List<LabResultFile> findByLabResultId(UUID labResultId);
}
