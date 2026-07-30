package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.ServiceTypeSummary;
import com.vetos.modules.appointment.domain.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListServiceTypesUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional(readOnly = true)
    public List<ServiceTypeSummary> execute(UUID tenantId) {
        return serviceTypeRepository.findByTenantId(tenantId).stream()
            .map(st -> new ServiceTypeSummary(st.getId(), st.getName(), st.getDefaultDurationMin(), st.getDefaultPrice()))
            .toList();
    }
}
