@org.springframework.modulith.ApplicationModule(
    displayName = "Integration: e-Fatura",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "modules.billing::domain", "modules.billing::domain.event",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception"
    }
)
package com.vetos.modules.integration.efatura;
