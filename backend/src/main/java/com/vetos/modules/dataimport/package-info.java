@org.springframework.modulith.ApplicationModule(
    displayName = "Data Import (Veri Aktarımı)",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::exception", "platform::web", "platform::concurrency"
    }
)
package com.vetos.modules.dataimport;
