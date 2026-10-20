package com.vetos.modules.tenant.application;

import com.vetos.platform.feature.FeatureAccessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Klinik on yuzu icin acik moduller -- menu/rota gorunurlugu (asil koruma FeatureGateInterceptor'da). */
@Service
@RequiredArgsConstructor
public class GetEnabledFeaturesUseCase {

    private final FeatureAccessPort featureAccessPort;

    public record Result(boolean enforcementActive, List<String> enabledFeatures) {}

    public Result execute(UUID tenantId) {
        List<String> codes = featureAccessPort.enabledFeatures(tenantId).stream()
            .map(Enum::name)
            .sorted()
            .toList();
        return new Result(featureAccessPort.enforcementActive(), codes);
    }
}
