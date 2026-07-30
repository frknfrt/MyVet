package com.vetos.modules.appointment.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTypeRepository {
    ServiceType save(ServiceType serviceType);
    Optional<ServiceType> findById(UUID id);
    List<ServiceType> findByTenantId(UUID tenantId);
}
