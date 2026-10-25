package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Hareket listesi açık miktar değişimini de döner; ekran "-1 ml açık" gösterebilsin. */
@ExtendWith(MockitoExtension.class)
class ListStockMovementsUseCaseTest {

    @Mock private StockMovementRepository movements;

    @Test
    void returnsTheOpenedDeltaOfEachMovement() {
        UUID item = UUID.randomUUID();
        when(movements.findByInventoryItemId(item)).thenReturn(List.of(
            StockMovement.record(UUID.randomUUID(), item, StockMovementType.OUT, 0, new BigDecimal("-1"), StockReferenceType.ENCOUNTER, null)));

        var result = new ListStockMovementsUseCase(movements).execute(item);

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.quantity()).isZero();
            assertThat(m.openedDelta()).isEqualByComparingTo("-1");
        });
    }
}
