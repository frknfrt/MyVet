@org.springframework.modulith.ApplicationModule(
    displayName = "Platform Admin",
    allowedDependencies = {
        "modules.tenant::domain", "modules.notification::domain", "modules.integration.efatura::domain",
        "modules.ai::domain",
        "platform::security", "platform::exception", "platform::tenancy", "platform::concurrency"
    }
)
package com.vetos.modules.platformadmin;
