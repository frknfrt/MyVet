package com.vetos.platform.validation;

import com.vetos.modules.integration.tarbil.api.dto.MarkSubmittedRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StockAmountTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    record Holder(@StockAmount BigDecimal amount) {}

    @ParameterizedTest
    @ValueSource(strings = {"0.004", "0", "-1", "1.234", "100000.01"})
    void rejects(String value) {
        assertThat(VALIDATOR.validate(new Holder(new BigDecimal(value)))).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1.25", "100000", "1.50"})
    void accepts(String value) {
        assertThat(VALIDATOR.validate(new Holder(new BigDecimal(value)))).isEmpty();
    }

    @Test
    void acceptsNull() {
        assertThat(VALIDATOR.validate(new Holder(null))).isEmpty();
    }

    @Test
    void markSubmittedAmount_keepsTarbilValueRules() {
        assertThat(VALIDATOR.validate(new MarkSubmittedRequest.Line("Clavon", "S1", 0, new BigDecimal("0.5"), "OPEN"))).isEmpty();
        assertThat(VALIDATOR.validate(new MarkSubmittedRequest.Line("Clavon", "S1", 0, new BigDecimal("250000"), "OPEN"))).isEmpty();
    }
}
