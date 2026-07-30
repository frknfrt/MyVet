@org.springframework.modulith.ApplicationModule(
    displayName = "Encounter (Klinik/SOAP)",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception"
    }
)
package com.vetos.modules.encounter;
