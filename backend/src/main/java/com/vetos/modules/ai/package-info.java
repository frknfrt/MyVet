@org.springframework.modulith.ApplicationModule(
    displayName = "AI Servisleri",
    allowedDependencies = {
        "modules.encounter::domain", "modules.patient::domain", "modules.lab::domain", "modules.imaging::domain",
        "platform::security", "platform::tenancy", "platform::exception", "platform::feature",
        "platform::observability", "platform::quota", "platform::http", "platform::ratelimit"
    }
)
package com.vetos.modules.ai;
