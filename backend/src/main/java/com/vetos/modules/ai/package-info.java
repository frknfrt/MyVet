@org.springframework.modulith.ApplicationModule(
    displayName = "AI Servisleri",
    allowedDependencies = {
        "modules.encounter::domain", "platform::security", "platform::tenancy", "platform::exception"
    }
)
package com.vetos.modules.ai;
