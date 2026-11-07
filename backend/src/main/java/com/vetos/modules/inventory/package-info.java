@org.springframework.modulith.ApplicationModule(
    displayName = "Inventory (Stok)",
    allowedDependencies = {
        "modules.encounter::domain.event", "modules.encounter::domain",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::feature", "platform::validation"
    }
)
package com.vetos.modules.inventory;
