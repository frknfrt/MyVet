package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class TarbilSyncLogNotFoundException extends DomainException {
    public TarbilSyncLogNotFoundException(UUID id) {
        super("TARBIL_SYNC_LOG_NOT_FOUND", "TARBIL senkron kaydi bulunamadi: " + id);
    }
}
