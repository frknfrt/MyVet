@org.springframework.modulith.ApplicationModule(
    displayName = "Laboratuvar",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::exception"
    }
)
package com.vetos.modules.lab;
