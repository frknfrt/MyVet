@org.springframework.modulith.ApplicationModule(
    displayName = "Appointment (Randevu)",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception"
    }
)
package com.vetos.modules.appointment;
