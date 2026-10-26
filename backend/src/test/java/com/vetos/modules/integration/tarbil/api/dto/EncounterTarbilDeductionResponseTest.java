package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.GetEncounterTarbilDeductionUseCase;
import com.vetos.modules.inventory.domain.PendingDeductionLine;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Teslim kartı doğru birimi göstersin diye satırda birim de gider (spec 2026-10-25 §4.4/12). */
class EncounterTarbilDeductionResponseTest {

    @Test
    void linesCarryTheItemUnit() {
        var line = new PendingDeductionLine(UUID.randomUUID(), UUID.randomUUID(), "Clavon", "CLAVON", "63016", new BigDecimal("1.5"),
            PendingDeductionStatus.PENDING, null, "ML", new BigDecimal("49"));
        var response = EncounterTarbilDeductionResponse.from(new GetEncounterTarbilDeductionUseCase.View(List.of(line), null, null));
        assertThat(response.lines().get(0).unit()).isEqualTo("ML");
    }
}
