package com.vetos.modules.consentform.domain;

import java.util.Optional;
import java.util.UUID;

public interface FormSourceImageRepository {
    FormSourceImage save(FormSourceImage image);
    Optional<FormSourceImage> findById(UUID id);
}
