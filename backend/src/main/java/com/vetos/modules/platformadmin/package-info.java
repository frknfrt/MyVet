@org.springframework.modulith.ApplicationModule(
    displayName = "Platform Admin",
    allowedDependencies = {
        "modules.tenant::domain", "modules.notification::domain", "modules.integration.efatura::domain",
        "modules.integration.tarbil::domain", "modules.ai::domain",
        "platform::security", "platform::exception", "platform::tenancy", "platform::concurrency",
        "platform::http", "platform::feature", "platform::observability", "platform::quota", "platform::audit", "platform::cache", "platform::email"
    }
)
package com.vetos.modules.platformadmin;
