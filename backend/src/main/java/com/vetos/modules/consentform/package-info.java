@org.springframework.modulith.ApplicationModule(
    displayName = "Dijital Onay Formlari",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain", "modules.encounter::domain",
        "platform::security", "platform::tenancy", "platform::exception", "platform::storage", "platform::feature"
    }
)
package com.vetos.modules.consentform;
