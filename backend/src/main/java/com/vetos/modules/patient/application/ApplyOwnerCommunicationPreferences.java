package com.vetos.modules.patient.application;
import com.vetos.modules.patient.domain.*;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class ApplyOwnerCommunicationPreferences implements OwnerCommunicationPreferencesPort {
    private final OwnerRepository owners;
    @Transactional
    public void apply(UUID ownerId, boolean sms, boolean whatsapp, boolean email) {
        var owner = owners.findById(ownerId).orElseThrow(() -> new OwnerNotFoundException(ownerId));
        owner.setServiceCommunicationPreferences(sms,whatsapp,email);
        owners.save(owner);
    }
}
