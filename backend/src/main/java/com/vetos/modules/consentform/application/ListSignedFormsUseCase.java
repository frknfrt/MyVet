package com.vetos.modules.consentform.application;

import com.vetos.modules.consentform.application.dto.SignedFormSummary;
import com.vetos.modules.consentform.domain.SignedForm;
import com.vetos.modules.consentform.domain.SignedFormRepository;
import com.vetos.modules.consentform.domain.exception.InvalidSignedFormValuesException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListSignedFormsUseCase {

    private final SignedFormRepository signedFormRepository;

    /** patientId verilirse hastaya, yoksa ownerId ile sahibe gore (signed_at azalan). */
    @Transactional(readOnly = true)
    public List<SignedFormSummary> execute(UUID patientId, UUID ownerId) {
        List<SignedForm> forms;
        if (patientId != null) {
            forms = signedFormRepository.findByPatientIdOrderBySignedAtDesc(patientId);
        } else if (ownerId != null) {
            forms = signedFormRepository.findByOwnerIdOrderBySignedAtDesc(ownerId);
        } else {
            throw new InvalidSignedFormValuesException("patientId veya ownerId verilmelidir");
        }
        return forms.stream().map(f -> new SignedFormSummary(
            f.getId(), f.getTemplateTitle(), f.getSignedAt(), f.getSignerName(), f.getSignerType(), f.getStatus())).toList();
    }
}
