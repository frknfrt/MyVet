package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionTokenSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilExtensionTokenRepository;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListExtensionTokensUseCase {

    private final TarbilExtensionTokenRepository repository;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public List<ExtensionTokenSummary> execute(UUID tenantId, UUID callerStaffId, boolean callerIsAdmin) {
        return repository.findByTenantId(tenantId).stream()
            .filter(t -> t.getPairedAt() != null)
            .filter(t -> callerIsAdmin || t.getStaffUserId().equals(callerStaffId))
            .map(t -> new ExtensionTokenSummary(
                t.getId(), t.getStaffUserId(), staffUserLookupPort.findSummaryById(t.getStaffUserId()).fullName(),
                t.getLabel(), t.getPairedAt(), t.getLastUsedAt(), t.getRevokedAt()))
            .toList();
    }
}
