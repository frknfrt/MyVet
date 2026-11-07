package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.SignedForm;
import com.vetos.modules.consentform.domain.SignedFormRepository;
import com.vetos.modules.consentform.domain.exception.InvalidSignedFormValuesException;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoidSignedFormUseCase {

    private final SignedFormRepository signedFormRepository;

    @Transactional
    public void execute(UUID staffId, UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw new InvalidSignedFormValuesException("İptal nedeni zorunludur");
        SignedForm f = signedFormRepository.findById(id).orElseThrow(() -> new SignedFormNotFoundException(id));
        f.voidForm(reason.strip(), staffId);
        signedFormRepository.save(f);
    }
}
