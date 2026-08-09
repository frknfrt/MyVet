@org.springframework.modulith.ApplicationModule(
    displayName = "Platform Admin",
    allowedDependencies = {
        "modules.tenant::domain",
        "platform::security", "platform::exception"
    }
)
package com.vetos.modules.platformadmin;
