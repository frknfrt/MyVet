package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;

/** Receteden stoktan verilen kalem bu klinikte yok ya da TARBIL'e bagli degil (review 2026-10-10) -> 422. */
public class InvalidPrescriptionStockItemException extends DomainException {
    public InvalidPrescriptionStockItemException() {
        super("INVALID_PRESCRIPTION_STOCK_ITEM", "Stoktan verilecek ürün bulunamadı ya da TARBİL'e bağlı değil");
    }
}
