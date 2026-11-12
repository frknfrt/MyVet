package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.modules.platformadmin.domain.TenantSignupRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

interface TenantSignupRequestJpaRepository extends JpaRepository<TenantSignupRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TenantSignupRequest r where r.id = :id")
    Optional<TenantSignupRequest> findByIdForUpdate(@Param("id") UUID id);

    List<TenantSignupRequest> findAllByOrderByCreatedAtDesc();
}
