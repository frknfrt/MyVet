package com.vetos.modules.patient.application;

import com.vetos.modules.patient.domain.ConsentRecord;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import com.vetos.modules.patient.domain.exception.ConsentRecordNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevokeConsentUseCase {

    private final ConsentRecordRepository consentRecordRepository;

    @Transactional
    public void execute(UUID consentRecordId) {
        ConsentRecord record = consentRecordRepository.findById(consentRecordId)
            .orElseThrow(() -> new ConsentRecordNotFoundException(consentRecordId));
        record.revoke();
        consentRecordRepository.save(record);
    }
}
