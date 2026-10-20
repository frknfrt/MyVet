package com.vetos.modules.platformadmin.api.dto;

import com.vetos.platform.quota.QuotaProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureOptionsResponseTest {

    @Test
    void should_listAllFeaturesInEnumOrder_withTurkishLabels() {
        FeatureOptionsResponse response = FeatureOptionsResponse.of(false, new QuotaProperties.Trial(30, 100));

        assertThat(response.enforcementActive()).isFalse();
        assertThat(response.options()).extracting(FeatureOptionsResponse.Option::code)
            .containsExactly("AI_ASSISTANT", "IMAGING", "LAB_INTEGRATION", "BOARDING", "INVENTORY", "E_FATURA");
        assertThat(response.options().get(0).label()).isEqualTo("AI Asistanı (Tanı / Tedavi Önerisi)");
    }

    @Test
    void should_includeTrialQuotas() {
        var response = FeatureOptionsResponse.of(false, new QuotaProperties.Trial(30, 100));
        assertThat(response.trialQuotas()).isEqualTo(new FeatureOptionsResponse.TrialQuotas(30, 100));
    }
}
