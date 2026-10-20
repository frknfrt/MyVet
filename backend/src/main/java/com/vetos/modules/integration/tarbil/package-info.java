@org.springframework.modulith.ApplicationModule(
    displayName = "Integration: TARBIL",
    allowedDependencies = {
        "modules.patient::domain", "modules.encounter::domain", "modules.encounter::domain.event",
        "modules.tenant::domain", "modules.inventory::domain", "modules.inventory::domain.event",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::feature",
        "platform::audit", "platform::cache"
    }
)
package com.vetos.modules.integration.tarbil;
