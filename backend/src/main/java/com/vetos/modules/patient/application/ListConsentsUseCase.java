package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.ConsentRecordSummary;
import com.vetos.modules.patient.domain.ConsentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListConsentsUseCase {

    private final ConsentRecordRepository consentRecordRepository;

    @Transactional(readOnly = true)
    public List<ConsentRecordSummary> execute(UUID ownerId) {
        return consentRecordRepository.findByOwnerId(ownerId).stream()
            .map(record -> new ConsentRecordSummary(
                record.getId(), record.getConsentType(), record.isGranted(), record.getIpAddress(),
                record.getGrantedAt(), record.getRevokedAt()
            ))
            .toList();
    }
}
