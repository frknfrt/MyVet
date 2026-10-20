package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.ConsentRecord;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import com.vetos.modules.patient.domain.ConsentType;
import com.vetos.modules.patient.domain.exception.ConsentRecordNotFoundException;
import com.vetos.platform.audit.AuditAction;
import com.vetos.platform.audit.AuditLabels;
import com.vetos.platform.audit.AuditPort;
import com.vetos.platform.audit.AuditRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevokeConsentUseCase {

    private final ConsentRecordRepository consentRecordRepository;
    private final AuditPort auditPort;

    @Transactional
    public void execute(UUID consentRecordId) {
        ConsentRecord record = consentRecordRepository.findById(consentRecordId)
            .orElseThrow(() -> new ConsentRecordNotFoundException(consentRecordId));
        record.revoke();
        consentRecordRepository.save(record);
        auditPort.record(AuditRecord.of(AuditAction.CONSENT_REVOKED, "CONSENT", consentRecordId,
            consentLabel(record.getConsentType()), null));
    }

    /** Onay turu Turkce etiketi; bilinmeyen sabit enum adiyla yazilir. */
    private static String consentLabel(ConsentType type) {
        if (type == null) return null;
        return switch (type) {
            case KVKK_ACIK_RIZA -> "KVKK açık rıza";
            case PAZARLAMA -> "Pazarlama";
            case VERI_AKTARIMI -> "Veri aktarımı";
            default -> type.name();
        };
    }
}
