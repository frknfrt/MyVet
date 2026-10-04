package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

public class TarbilStockBranchMissingException extends DomainException {
    public TarbilStockBranchMissingException() {
        super("TARBIL_STOCK_BRANCH_MISSING", "Kullanicinin subesi yok; TARBIL stogu esitlenemez");
    }
}
