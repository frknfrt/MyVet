package com.vetos.modules.lab.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LabReferenceRangeEvaluatorTest {

    @Test
    void should_returnNull_when_valueOrReferenceRangeIsNull() {
        assertThat(LabReferenceRangeEvaluator.evaluate(null, "3.5-5.5")).isNull();
        assertThat(LabReferenceRangeEvaluator.evaluate("4.0", null)).isNull();
    }

    @Test
    void should_returnNull_when_valueIsNotNumeric() {
        assertThat(LabReferenceRangeEvaluator.evaluate("Negatif", "3.5-5.5")).isNull();
    }

    @Test
    void should_returnLow_when_valueBelowRange() {
        assertThat(LabReferenceRangeEvaluator.evaluate("3.0", "3.5-5.5")).isEqualTo(LabValueFlag.LOW);
    }

    @Test
    void should_returnHigh_when_valueAboveRange() {
        assertThat(LabReferenceRangeEvaluator.evaluate("6.0", "3.5-5.5")).isEqualTo(LabValueFlag.HIGH);
    }

    @Test
    void should_returnNormal_when_valueWithinRange() {
        assertThat(LabReferenceRangeEvaluator.evaluate("4.2", "3.5-5.5")).isEqualTo(LabValueFlag.NORMAL);
    }

    @Test
    void should_swapMinAndMax_when_rangeIsReversed() {
        assertThat(LabReferenceRangeEvaluator.evaluate("6.0", "5.5-3.5")).isEqualTo(LabValueFlag.HIGH);
        assertThat(LabReferenceRangeEvaluator.evaluate("3.0", "5.5-3.5")).isEqualTo(LabValueFlag.LOW);
    }

    @Test
    void should_evaluateAgainstMaxOnly_when_referenceRangeHasNoLowerBound() {
        assertThat(LabReferenceRangeEvaluator.evaluate("10", "<=5")).isEqualTo(LabValueFlag.HIGH);
        assertThat(LabReferenceRangeEvaluator.evaluate("3", "<=5")).isEqualTo(LabValueFlag.NORMAL);
    }

    @Test
    void should_evaluateAgainstMinOnly_when_referenceRangeHasNoUpperBound() {
        assertThat(LabReferenceRangeEvaluator.evaluate("2", ">=5")).isEqualTo(LabValueFlag.LOW);
        assertThat(LabReferenceRangeEvaluator.evaluate("8", ">=5")).isEqualTo(LabValueFlag.NORMAL);
    }

    @Test
    void should_returnNull_when_referenceRangeFormatIsUnrecognized() {
        assertThat(LabReferenceRangeEvaluator.evaluate("4.0", "belirsiz")).isNull();
    }
}
