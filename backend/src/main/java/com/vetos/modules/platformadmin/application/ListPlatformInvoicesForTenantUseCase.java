package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListPlatformInvoicesForTenantUseCase {

    private final PlatformInvoiceRepository platformInvoiceRepository;

    @Transactional(readOnly = true)
    public List<PlatformInvoice> execute(UUID tenantId) {
        return platformInvoiceRepository.findByTenantId(tenantId).stream()
            .sorted(Comparator.comparing(PlatformInvoice::getIssuedAt).reversed())
            .toList();
    }
}
