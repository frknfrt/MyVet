package com.vetos.modules.inventory.domain;

import java.time.LocalDate;

public record NewTarbilStockItem(
    String tarbilSystem, String productName, String presentation, String category,
    String lotNumber, LocalDate expiryDate, int quantity
) {}
