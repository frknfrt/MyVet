package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidTarbilStockSnapshotException extends DomainException {
    public InvalidTarbilStockSnapshotException(String reason) {
        super("INVALID_TARBIL_STOCK_SNAPSHOT", "Gecersiz TARBIL stok goruntusu: " + reason);
    }
}
