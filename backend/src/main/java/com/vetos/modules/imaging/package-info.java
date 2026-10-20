@org.springframework.modulith.ApplicationModule(
    displayName = "Görüntüleme",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::exception", "platform::storage", "platform::feature"
    }
)
package com.vetos.modules.imaging;
