package com.vetos.modules.consentform.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormTemplateRepository {
    FormTemplate save(FormTemplate template);
    Optional<FormTemplate> findById(UUID id);
    List<FormTemplate> findByStatusOrderByTitle(FormTemplateStatus status);
    List<FormTemplate> findAllByOrderByTitle();
}
