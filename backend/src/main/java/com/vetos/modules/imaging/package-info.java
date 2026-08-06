@org.springframework.modulith.ApplicationModule(
    displayName = "Görüntüleme",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::exception"
    }
)
package com.vetos.modules.imaging;
