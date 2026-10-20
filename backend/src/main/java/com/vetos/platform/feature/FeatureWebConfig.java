package com.vetos.platform.feature;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class FeatureWebConfig implements WebMvcConfigurer {

    private final FeatureAccessPort featureAccess;

    FeatureWebConfig(FeatureAccessPort featureAccess) {
        this.featureAccess = featureAccess;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FeatureGateInterceptor(featureAccess));
    }
}
