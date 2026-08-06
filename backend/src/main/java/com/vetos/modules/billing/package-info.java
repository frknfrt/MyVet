@org.springframework.modulith.ApplicationModule(
    displayName = "Billing (Finans)",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain", "modules.encounter::domain.event",
        "modules.boarding::domain.event",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::web"
    }
)
package com.vetos.modules.billing;
