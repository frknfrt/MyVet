package com.vetos.modules.patient.infrastructure.persistence;

import com.vetos.modules.patient.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ConsentRecordJpaRepository extends JpaRepository<ConsentRecord, UUID> {
    List<ConsentRecord> findByOwnerId(UUID ownerId);
}
