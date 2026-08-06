package com.vetos.modules.lab.infrastructure.persistence;

import com.vetos.modules.lab.domain.LabResultFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface LabResultFileJpaRepository extends JpaRepository<LabResultFile, UUID> {
    List<LabResultFile> findByLabResultId(UUID labResultId);
}
