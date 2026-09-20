@org.springframework.modulith.ApplicationModule(
    displayName = "Görüntüleme",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::exception", "platform::storage"
    }
)
package com.vetos.modules.imaging;
