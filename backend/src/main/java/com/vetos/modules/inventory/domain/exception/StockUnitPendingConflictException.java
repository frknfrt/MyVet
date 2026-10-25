package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;

/**
 * Bekleyen düşümün miktarı kalemin birimiyle okunur; birim değişirse "1 şişe" "1 ml" olur (değerlendirme I3) -> 409.
 */
public class StockUnitPendingConflictException extends DomainException {
    public StockUnitPendingConflictException() {
        super("STOCK_UNIT_PENDING",
            "Bu ürünün TARBİL'e gönderilmeyi bekleyen kaydı var. Birimi değiştirmeden önce o kaydı tamamlayın.");
    }
}
