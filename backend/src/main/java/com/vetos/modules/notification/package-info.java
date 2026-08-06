@org.springframework.modulith.ApplicationModule(
    displayName = "Bildirimler (SMS/WhatsApp)",
    allowedDependencies = {
        "modules.patient::domain", "modules.tenant::domain", "modules.appointment::domain",
        "modules.appointment::domain.event",
        "platform::security", "platform::tenancy", "platform::event", "platform::exception"
    }
)
package com.vetos.modules.notification;
