package com.vetos.modules.patient.domain;
import java.util.UUID;
public interface OwnerCommunicationPreferencesPort {
    void apply(UUID ownerId, boolean sms, boolean whatsapp, boolean email);
}
