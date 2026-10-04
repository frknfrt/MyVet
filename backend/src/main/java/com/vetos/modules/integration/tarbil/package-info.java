@org.springframework.modulith.ApplicationModule(
    displayName = "Integration: TARBIL",
    allowedDependencies = {
        "modules.patient::domain", "modules.patient::domain.event", "modules.encounter::domain.event",
        "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception"
    }
)
package com.vetos.modules.integration.tarbil;
