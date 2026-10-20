package com.vetos.platform.feature;

import java.util.Set;
import java.util.UUID;

/**
 * "Bu kiracinin paketinde su modul acik mi?" -- uygulamasi platformadmin modulunde
 * (PlanFeatureAccessAdapter). Moduller platformadmin'i dogrudan goremedigi icin arayuz burada.
 */
public interface FeatureAccessPort {

    boolean isEnabled(UUID tenantId, Feature feature);

    Set<Feature> enabledFeatures(UUID tenantId);

    /** features.enforcement.enabled -- kapaliyken her kiraci icin her ozellik acik doner. */
    boolean enforcementActive();
}
