package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

import java.util.UUID;

public class TarbilStockSnapshotNotFoundException extends DomainException {
    public TarbilStockSnapshotNotFoundException(UUID id) {
        super("TARBIL_STOCK_SNAPSHOT_NOT_FOUND", "TARBIL stok goruntusu bulunamadi: " + id);
    }
}
