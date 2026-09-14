package com.vetos.modules.inventory.domain;

import java.util.UUID;

/**
 * Diger moduller (billing) stok dusumune SADECE bu port uzerinden erisir --
 * RecordStockMovementUseCase'i (application katmani) ASLA import etmezler.
 */
public interface StockDeductionPort {
    /** quantityOnHand yetersizse InsufficientStockException firlatir, aksi halde OUT hareketi kaydeder. */
    void deductForSale(UUID inventoryItemId, int quantity, UUID invoiceId);
}
