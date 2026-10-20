package com.vetos.modules.platformadmin.api.dto;

import com.vetos.platform.feature.Feature;
import com.vetos.platform.quota.QuotaProperties;

import java.util.Arrays;
import java.util.List;

/** Admin "Modul Erisimi" kutulari -- liste ve etiketler tek kaynaktan (Feature). */
public record FeatureOptionsResponse(boolean enforcementActive, List<Option> options, TrialQuotas trialQuotas) {

    public record Option(String code, String label) {}

    /** Salt-okunur bilgi: deneme paketinin kotalari sunucu ayarindan (vetly.quota.trial). */
    public record TrialQuotas(int ai, int message) {}

    public static FeatureOptionsResponse of(boolean enforcementActive, QuotaProperties.Trial trial) {
        List<Option> options = Arrays.stream(Feature.values())
            .map(f -> new Option(f.name(), f.label()))
            .toList();
        return new FeatureOptionsResponse(enforcementActive, options, new TrialQuotas(trial.ai(), trial.message()));
    }
}
