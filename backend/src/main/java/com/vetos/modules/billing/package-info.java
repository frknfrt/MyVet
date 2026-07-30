@org.springframework.modulith.ApplicationModule(
    displayName = "Billing (Finans)",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain", "modules.encounter::domain.event",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception"
    }
)
package com.vetos.modules.billing;
