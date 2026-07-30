package com.vetos.modules.appointment.infrastructure.persistence;

import com.vetos.modules.appointment.domain.ServiceType;
import com.vetos.modules.appointment.domain.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ServiceTypeRepositoryAdapter implements ServiceTypeRepository {

    private final ServiceTypeJpaRepository jpaRepository;

    @Override
    public ServiceType save(ServiceType serviceType) { return jpaRepository.save(serviceType); }

    @Override
    public Optional<ServiceType> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<ServiceType> findByTenantId(UUID tenantId) { return jpaRepository.findByTenantId(tenantId); }
}
