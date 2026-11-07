package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.FormSourceImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface FormSourceImageJpaRepository extends JpaRepository<FormSourceImage, UUID> {
}
