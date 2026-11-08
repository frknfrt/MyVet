package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.SignedForm;
import com.vetos.modules.consentform.domain.SignedFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class SignedFormRepositoryAdapter implements SignedFormRepository {

    private final SignedFormJpaRepository jpaRepository;

    @Override
    public SignedForm save(SignedForm form) { return jpaRepository.save(form); }

    @Override
    public Optional<SignedForm> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<SignedForm> findByPatientIdOrderBySignedAtDesc(UUID patientId) {
        return jpaRepository.findByPatientIdOrderBySignedAtDesc(patientId);
    }

    @Override
    public List<SignedForm> findByPatientIdAndOwnerIdOrderBySignedAtDesc(UUID patientId, UUID ownerId) {
        return jpaRepository.findByPatientIdAndOwnerIdOrderBySignedAtDesc(patientId, ownerId);
    }

    @Override
    public List<SignedForm> findByOwnerIdOrderBySignedAtDesc(UUID ownerId) {
        return jpaRepository.findByOwnerIdOrderBySignedAtDesc(ownerId);
    }

    @Override
    public Map<UUID, Long> countByTemplateIds(List<UUID> templateIds) {
        if (templateIds.isEmpty()) return Map.of();
        return jpaRepository.countByTemplateIds(templateIds).stream()
            .collect(Collectors.toMap(SignedFormJpaRepository.TemplateCount::getTemplateId,
                SignedFormJpaRepository.TemplateCount::getTotal));
    }
}
