package com.vetos.modules.tenant.application;

import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetEnabledFeaturesUseCaseTest {

    @Mock private FeatureAccessPort featureAccessPort;

    @Test
    void should_returnSortedFeatureCodes_and_enforcementFlag() {
        UUID tenantId = UUID.randomUUID();
        when(featureAccessPort.enabledFeatures(tenantId)).thenReturn(Set.of(Feature.LAB_INTEGRATION, Feature.BOARDING));
        when(featureAccessPort.enforcementActive()).thenReturn(true);

        GetEnabledFeaturesUseCase.Result result = new GetEnabledFeaturesUseCase(featureAccessPort).execute(tenantId);

        assertThat(result.enforcementActive()).isTrue();
        assertThat(result.enabledFeatures()).containsExactly("BOARDING", "LAB_INTEGRATION");
    }
}
