package com.vetos.modules.platformadmin.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {

    @Test
    void should_haveNoCatalogDetails_when_created() {
        Plan plan = Plan.create("PRO", "Profesyonel", new BigDecimal("2000.00"));

        assertThat(plan.getDescription()).isNull();
        assertThat(plan.getBadge()).isNull();
        assertThat(plan.getImageUrl()).isNull();
        assertThat(plan.getAnnualPrice()).isNull();
        assertThat(plan.getFeatures()).isEmpty();
        assertThat(plan.getEnabledFeatures()).isEmpty();
        assertThat(plan.isActive()).isTrue();
    }

    @Test
    void should_updateCatalogDetails_when_updateDetailsCalled() {
        Plan plan = Plan.create("PRO", "Profesyonel", new BigDecimal("2000.00"));

        plan.updateDetails(
            "Profesyonel",
            new BigDecimal("2000.00"),
            new BigDecimal("20000.00"),
            "Büyüyen klinikler için.",
            "En Popüler",
            "https://vetly.com/assets/pro.png",
            List.of("Laboratuvar", "AI Merkezi"),
            Set.of(PlanFeatureFlag.AI_ASSISTANT, PlanFeatureFlag.LAB_INTEGRATION)
        );

        assertThat(plan.getAnnualPrice()).isEqualByComparingTo("20000.00");
        assertThat(plan.getDescription()).isEqualTo("Büyüyen klinikler için.");
        assertThat(plan.getBadge()).isEqualTo("En Popüler");
        assertThat(plan.getImageUrl()).isEqualTo("https://vetly.com/assets/pro.png");
        assertThat(plan.getFeatures()).containsExactly("Laboratuvar", "AI Merkezi");
        assertThat(plan.getEnabledFeatures()).containsExactlyInAnyOrder(PlanFeatureFlag.AI_ASSISTANT, PlanFeatureFlag.LAB_INTEGRATION);
    }

    @Test
    void should_replaceFeatureList_when_updatedAgainWithFewerItems() {
        Plan plan = Plan.create("PRO", "Profesyonel", new BigDecimal("2000.00"));
        plan.updateDetails(
            "Profesyonel", new BigDecimal("2000.00"), null, null, null, null,
            List.of("A", "B", "C"), Set.of(PlanFeatureFlag.AI_ASSISTANT)
        );

        plan.updateDetails(
            "Profesyonel", new BigDecimal("2000.00"), null, null, null, null,
            List.of("A"), Set.of()
        );

        assertThat(plan.getFeatures()).containsExactly("A");
        assertThat(plan.getEnabledFeatures()).isEmpty();
    }
}
