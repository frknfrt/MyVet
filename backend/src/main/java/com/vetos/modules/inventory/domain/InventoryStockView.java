package com.vetos.modules.inventory.domain;

import java.time.LocalDate;
import java.util.UUID;

/** TARBIL stok esitlemesi icin salt-okunur stok kalemi gorunumu. */
public record InventoryStockView(UUID id, String name, String lotNumber, LocalDate expiryDate, int quantityOnHand, String tarbilProductName) {}
