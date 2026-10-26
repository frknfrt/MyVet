package com.vetos.modules.inventory.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kuyruktaki onceki dusumler uygulandiktan sonra kalan acik miktar (spec 2026-10-25 §4.3/9). */
class OpenedQueuePlannerTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void noEarlierDeductions_keepsOpened() {
        assertThat(OpenedQueuePlanner.openedBefore(bd("49"), bd("50"), List.of())).isEqualByComparingTo("49");
    }

    @Test
    void earlierFromOpened_reducesIt() {
        assertThat(OpenedQueuePlanner.openedBefore(bd("49"), bd("50"), List.of(bd("30")))).isEqualByComparingTo("19");
    }

    @Test
    void earlierThatOpensANewBottle_addsAPackage() {
        assertThat(OpenedQueuePlanner.openedBefore(bd("49"), bd("50"), List.of(bd("30"), bd("30")))).isEqualByComparingTo("39");
    }

    @Test
    void unknownPackageSize_keepsOpened() {
        assertThat(OpenedQueuePlanner.openedBefore(bd("3"), null, List.of(bd("1")))).isEqualByComparingTo("3");
    }
}
