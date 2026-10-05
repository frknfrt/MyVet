package com.vetos.modules.integration.tarbil.infrastructure.adapter;

import com.vetos.modules.integration.tarbil.domain.TarbilAdminPort;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilServerRetryUnsupportedConflictException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Eklenti modelinde (2026-10-05) TARBIL'e gonderimi klinikteki eklenti hekimle yapar; sunucudan yeniden deneme yok. */
@Component
class TarbilAdminAdapter implements TarbilAdminPort {

    @Override
    public void retryNow(UUID syncLogId) {
        throw new TarbilServerRetryUnsupportedConflictException();
    }
}
