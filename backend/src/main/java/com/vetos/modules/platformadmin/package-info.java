@org.springframework.modulith.ApplicationModule(
    displayName = "Platform Admin",
    allowedDependencies = {
        "modules.tenant::domain",
        "platform::security", "platform::exception", "platform::tenancy"
    }
)
package com.vetos.modules.platformadmin;
