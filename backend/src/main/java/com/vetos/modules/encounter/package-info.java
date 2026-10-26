@org.springframework.modulith.ApplicationModule(
    displayName = "Encounter (Klinik/SOAP)",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::audit", "platform::tenancy", "platform::event", "platform::exception", "platform::validation"
    }
)
package com.vetos.modules.encounter;
