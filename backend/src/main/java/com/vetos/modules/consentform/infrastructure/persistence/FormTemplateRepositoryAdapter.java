package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateRepository;
import com.vetos.modules.consentform.domain.FormTemplateStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class FormTemplateRepositoryAdapter implements FormTemplateRepository {

    private final FormTemplateJpaRepository jpaRepository;

    @Override
    public FormTemplate save(FormTemplate template) { return jpaRepository.save(template); }

    @Override
    public Optional<FormTemplate> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<FormTemplate> findByStatusOrderByTitle(FormTemplateStatus status) {
        return jpaRepository.findByStatusOrderByTitle(status);
    }

    @Override
    public List<FormTemplate> findAllByOrderByTitle() { return jpaRepository.findAllByOrderByTitle(); }
}
