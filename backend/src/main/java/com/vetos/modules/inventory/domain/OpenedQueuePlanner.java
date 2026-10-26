package com.vetos.modules.inventory.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * TARBIL gonderim plani icin kuyruk hesabi (spec 2026-10-25 §4.3/9): ayni kalemin daha once kuyruga girmis bekleyen
 * dusumleri {@link InventoryItem#consume} kuraliyla (acik yetmezse yeni paket acilir) uygulanir; kalan acik miktar doner.
 * Boylece iki Clavon recetesi 49 ml'lik acik siseden ikisi de "acik stok" diye planlanmaz.
 */
public final class OpenedQueuePlanner {

    private OpenedQueuePlanner() {}

    public static BigDecimal openedBefore(BigDecimal opened, BigDecimal packageSize, List<BigDecimal> earlierQuantities) {
        BigDecimal left = opened == null ? BigDecimal.ZERO : opened;
        if (packageSize == null || packageSize.signum() <= 0) {
            return left;
        }
        for (BigDecimal q : earlierQuantities) {
            if (q == null || q.signum() <= 0) {
                continue;
            }
            while (left.compareTo(q) < 0) {
                left = left.add(packageSize);
            }
            left = left.subtract(q);
        }
        return left;
    }
}
