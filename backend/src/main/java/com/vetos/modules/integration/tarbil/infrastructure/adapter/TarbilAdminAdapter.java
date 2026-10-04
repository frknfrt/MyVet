package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.application.RetryTarbilSyncUseCase;
import com.vetos.modules.integration.tarbil.domain.TarbilAdminPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilAdminAdapter implements TarbilAdminPort {

    private final RetryTarbilSyncUseCase retryTarbilSyncUseCase;

    @Override
    public void retryNow(UUID syncLogId) {
        retryTarbilSyncUseCase.executeAsAdmin(syncLogId);
    }
}
