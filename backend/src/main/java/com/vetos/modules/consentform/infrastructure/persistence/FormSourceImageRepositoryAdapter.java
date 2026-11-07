package com.vetos.modules.consentform.infrastructure.persistence;

import com.vetos.modules.consentform.domain.FormSourceImage;
import com.vetos.modules.consentform.domain.FormSourceImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class FormSourceImageRepositoryAdapter implements FormSourceImageRepository {

    private final FormSourceImageJpaRepository jpaRepository;

    @Override
    public FormSourceImage save(FormSourceImage image) { return jpaRepository.save(image); }

    @Override
    public Optional<FormSourceImage> findById(UUID id) { return jpaRepository.findById(id); }
}
