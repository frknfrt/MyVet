@org.springframework.modulith.ApplicationModule(
    displayName = "Platform Admin",
    allowedDependencies = {
        "modules.tenant::domain", "modules.notification::domain", "modules.integration.efatura::domain",
        "modules.integration.tarbil::domain", "modules.ai::domain", "modules.dataimport::domain.event",
        "platform::security", "platform::exception", "platform::tenancy", "platform::concurrency",
        "platform::http", "platform::feature", "platform::observability", "platform::quota", "platform::audit", "platform::cache", "platform::email", "platform::branch", "platform::staff"
    }
)
package com.vetos.modules.platformadmin;
