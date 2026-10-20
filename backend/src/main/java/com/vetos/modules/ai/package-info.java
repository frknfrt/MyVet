@org.springframework.modulith.ApplicationModule(
    displayName = "AI Servisleri",
    allowedDependencies = {
        "modules.encounter::domain", "platform::security", "platform::tenancy", "platform::exception", "platform::feature",
        "platform::observability", "platform::quota", "platform::http", "platform::ratelimit"
    }
)
package com.vetos.modules.ai;
