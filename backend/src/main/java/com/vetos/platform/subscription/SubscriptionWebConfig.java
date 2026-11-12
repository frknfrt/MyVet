package com.vetos.platform.subscription;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class SubscriptionWebConfig implements WebMvcConfigurer {

    private final SubscriptionAccessPort subscriptionAccess;

    SubscriptionWebConfig(SubscriptionAccessPort subscriptionAccess) {
        this.subscriptionAccess = subscriptionAccess;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SubscriptionGateInterceptor(subscriptionAccess));
    }
}
