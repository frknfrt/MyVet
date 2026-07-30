package com.vetos.modules.appointment.infrastructure.persistence;

import com.vetos.modules.appointment.domain.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ServiceTypeJpaRepository extends JpaRepository<ServiceType, UUID> {
    List<ServiceType> findByTenantId(UUID tenantId);
}
