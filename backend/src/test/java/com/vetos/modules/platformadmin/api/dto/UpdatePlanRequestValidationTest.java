package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePlanRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UpdatePlanRequest request(Integer ai, Integer message) {
        return new UpdatePlanRequest("Pro", BigDecimal.TEN, null, null, null, null, List.of(), Set.of(), true, ai, message);
    }

    @Test
    void should_allowNullAndZero() {
        assertThat(validator.validate(request(null, null))).isEmpty();
        assertThat(validator.validate(request(0, 0))).isEmpty();
    }

    @Test
    void should_rejectNegative() {
        assertThat(validator.validate(request(-1, null))).hasSize(1);
        assertThat(validator.validate(request(null, -1))).hasSize(1);
    }
}
