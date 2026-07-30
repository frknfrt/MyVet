package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.RecordConsentCommand;
import com.vetos.modules.patient.domain.ConsentRecord;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.patient.domain.exception.OwnerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecordConsentUseCase {

    private final ConsentRecordRepository consentRecordRepository;
    private final OwnerRepository ownerRepository;

    @Transactional
    public UUID execute(RecordConsentCommand command) {
        ownerRepository.findById(command.ownerId())
            .orElseThrow(() -> new OwnerNotFoundException(command.ownerId()));

        ConsentRecord record = ConsentRecord.grant(command.ownerId(), command.consentType(), command.ipAddress());
        return consentRecordRepository.save(record).getId();
    }
}
