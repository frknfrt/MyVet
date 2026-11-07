package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.domain.SignedForm;
import com.vetos.modules.consentform.domain.SignedFormRepository;
import com.vetos.modules.consentform.domain.exception.SignedFormNotFoundException;
import com.vetos.platform.storage.FileStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetSignatureImageUseCase {

    private final SignedFormRepository signedFormRepository;
    private final FileStoragePort fileStoragePort;

    @Transactional(readOnly = true)
    public byte[] execute(UUID id) {
        SignedForm f = signedFormRepository.findById(id).orElseThrow(() -> new SignedFormNotFoundException(id));
        return fileStoragePort.retrieve(f.getSignatureStorageRef());
    }
}
