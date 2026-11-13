package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TrialSignupRequest;
import com.vetos.modules.platformadmin.domain.TrialSignupRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TrialSignupRequestJpaRepository extends JpaRepository<TrialSignupRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TrialSignupRequest r where r.id = :id")
    Optional<TrialSignupRequest> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByPhoneAndStatus(String phone, TrialSignupRequestStatus status);

    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    long countByCreatedAtAfter(Instant since);

    List<TrialSignupRequest> findAllByOrderByCreatedAtDesc();
}
