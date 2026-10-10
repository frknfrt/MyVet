package com.vetos.modules.inventory.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PendingStockDeductionTest {

    private final UUID staff = UUID.randomUUID();

    private PendingStockDeduction pending() {
        return PendingStockDeduction.open(UUID.randomUUID(), UUID.randomUUID(), 2, StockReferenceType.ENCOUNTER, UUID.randomUUID());
    }

    @Test
    void settlesOnceWithTheGivenQuantity() {
        PendingStockDeduction p = pending();
        assertThat(p.settle(3, DeductionSettlement.TARBIL, staff, Instant.EPOCH)).isTrue();
        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
        assertThat(p.getSettledQuantity()).isEqualTo(3);
        assertThat(p.getSettledVia()).isEqualTo(DeductionSettlement.TARBIL);
        assertThat(p.settle(1, DeductionSettlement.VETLY_ONLY, staff, Instant.EPOCH)).isFalse();
        assertThat(p.getSettledQuantity()).isEqualTo(3);
    }

    @Test
    void cancelsOnlyWhilePending() {
        PendingStockDeduction p = pending();
        assertThat(p.cancel()).isTrue();
        assertThat(p.getStatus()).isEqualTo(PendingDeductionStatus.CANCELLED);
        assertThat(p.settle(2, DeductionSettlement.TARBIL, staff, Instant.EPOCH)).isFalse();

        PendingStockDeduction d = pending();
        d.settle(2, DeductionSettlement.TARBIL, staff, Instant.EPOCH);
        assertThat(d.cancel()).isFalse();
        assertThat(d.getStatus()).isEqualTo(PendingDeductionStatus.DEDUCTED);
    }
}
