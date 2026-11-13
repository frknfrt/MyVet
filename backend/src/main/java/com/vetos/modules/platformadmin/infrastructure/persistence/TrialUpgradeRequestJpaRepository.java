package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TrialUpgradeRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface TrialUpgradeRequestJpaRepository extends JpaRepository<TrialUpgradeRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TrialUpgradeRequest r where r.id = :id")
    Optional<TrialUpgradeRequest> findByIdForUpdate(@Param("id") UUID id);
}
