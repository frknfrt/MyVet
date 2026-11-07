package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.FormTemplate;
import com.vetos.modules.consentform.domain.FormTemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface FormTemplateJpaRepository extends JpaRepository<FormTemplate, UUID> {
    List<FormTemplate> findByStatusOrderByTitle(FormTemplateStatus status);
    List<FormTemplate> findAllByOrderByTitle();
}
