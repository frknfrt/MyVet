package com.vetos.modules.inventory.domain;

import java.math.BigDecimal;

/** Bir stok islemindeki degisim: kapali adet farki ve acik miktar farki. */
public record StockChange(int closedDelta, BigDecimal openedDelta) {}
