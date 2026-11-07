package com.vetos.modules.dataimport.application;

import com.vetos.modules.dataimport.domain.ImportEntityType;
import com.vetos.modules.dataimport.domain.ImportRecordLinkRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetLegacyDataUseCase {

    private final ImportRecordLinkRepository linkRepository;

    /** Kiraci filtresi (@TenantId) otomatik uygulanir: baska klinigin kaydi bos doner. */
    @Transactional(readOnly = true)
    public Optional<LegacyData> execute(ImportEntityType type, UUID targetId) {
        return linkRepository.findByTarget(type, targetId)
            .map(l -> new LegacyData(l.getSourceSystem(), l.getSourceId(), l.getCreatedAt(), l.getLegacyData()));
    }
}
