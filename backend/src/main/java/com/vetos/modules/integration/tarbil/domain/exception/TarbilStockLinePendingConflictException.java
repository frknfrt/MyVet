package com.vetos.modules.integration.tarbil.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Secilen satirin stok kalemi icin TARBIL'e islenmemis aktarim var; once o tamamlanmali (2026-10-21). */
public class TarbilStockLinePendingConflictException extends DomainException {
    public TarbilStockLinePendingConflictException() {
        super("TARBIL_STOCK_LINE_PENDING",
            "Seçilen ürünlerden birinin TARBİL'e gönderilmeyi bekleyen kaydı var. Önce o kaydı TARBİL'de tamamlayın, sonra stoğu yeniden gönderin.");
    }
}
