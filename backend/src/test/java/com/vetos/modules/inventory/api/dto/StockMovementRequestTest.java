package com.vetos.modules.inventory.api.dto;

import com.vetos.modules.inventory.domain.StockMovementType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Elle hareket: kapali adet ya da acik miktar farki dolu olmali (spec 2026-10-25 §4.4/14). */
class StockMovementRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void movement_requiresSomeChange() {
        assertThat(VALIDATOR.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 0, null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 0, BigDecimal.ZERO))).isNotEmpty();
    }

    @Test
    void openedOnlyAdjustment_isValid() {
        assertThat(VALIDATOR.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 0, new BigDecimal("-3")))).isEmpty();
    }

    @Test
    void openedDelta_withMoreThanTwoDecimals_isRejected() {
        assertThat(VALIDATOR.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 0, new BigDecimal("-0.005")))).isNotEmpty();
    }

    @Test
    void closedOnly_stillWorks() {
        assertThat(VALIDATOR.validate(new StockMovementRequest(StockMovementType.IN, 2, null))).isEmpty();
    }

    @Test
    void openedDelta_aboveLimit_isRejected() {
        assertThat(VALIDATOR.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 0, new BigDecimal("100000.01")))).isNotEmpty();
        assertThat(VALIDATOR.validate(new StockMovementRequest(StockMovementType.ADJUSTMENT, 0, new BigDecimal("-100000.01")))).isNotEmpty();
    }
}
